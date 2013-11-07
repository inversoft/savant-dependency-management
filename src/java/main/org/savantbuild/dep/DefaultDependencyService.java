/*
 * Copyright (c) 2001-2013, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep;

import org.savantbuild.dep.DependencyService.ResolveConfiguration.TypeResolveConfiguration;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.CyclicException;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.DependencyLinkValue;
import org.savantbuild.dep.graph.GraphLink;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.util.MinMax;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.ProcessFailureException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * Default implementation of the dependency service.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyService implements DependencyService {
  private final static Logger logger = Logger.getLogger(DefaultDependencyService.class.getName());

  /**
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException {
    logger.fine("Building DependencyGraph");
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, new Dependency(project.id, project.version, false), dependencies, workflow, new HashSet<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResolvedArtifactGraph resolve(DependencyGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                       DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, MD5Exception {
    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);
    Deque<ArtifactID> visited = new ArrayDeque<>();
    visited.push(root.id);
    Deque<ArtifactID> resolved = new ArrayDeque<>();
    resolved.push(root.id);
    resolve(graph, resolvedGraph, workflow, configuration, root, visited, resolved, listeners);
    return resolvedGraph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void verifyCompatibility(DependencyGraph graph) throws CompatibilityException {
    graph.getNodes().forEach((node) -> {
      if (node.getInboundLinks().isEmpty()) {
        System.out.println("Node [" + node.value + "] is empty");
        return;
      }

      MinMax<Version> minMax = new MinMax<>();
      Set<ArtifactID> dependents = node.getInboundLinks().stream().map((link) -> link.origin.value).collect(Collectors.toSet());
      System.out.println("Node [" + node.value + "] dependents " + dependents);
      dependents.forEach((dependent) -> {
        Version maxDependentVersion = node.getInboundLinks()
                                          .stream()
                                          .filter((link) -> link.origin.equals(graph.getNode(dependent)))
                                          .map((link) -> link.value.dependentVersion)
                                          .max(Version::compareTo)
                                          .get();

        Version dependentMax = node.getInboundLinks(graph.getNode(dependent))
                                   .stream()
                                   .filter((link) -> link.value.dependentVersion.equals(maxDependentVersion))
                                   .map((link) -> link.value.dependencyVersion)
                                   .findFirst()
                                   .get();
        minMax.add(dependentMax);

        System.out.println("Dependent [" + dependent + "] max version [" + maxDependentVersion + "] dependent max [" + dependentMax + "]");
      });

      if (!minMax.min.isCompatibleWith(minMax.max)) {
        throw new CompatibilityException(node.value, minMax.min, minMax.max);
      }
    });
  }

  /**
   * Recursively populates the DependencyGraph starting with the given origin and its dependencies. This fetches the
   * ArtifactMetaData for all of the dependencies and performs a breadth first traversal of the graph. If an dependency
   * has already been encountered and traversed, this does not traverse it again. The Set is used to track the
   * dependencies that have already been encountered.
   *
   * @param graph             The Graph to populate.
   * @param origin            The origin artifact that is dependent on the Dependencies given.
   * @param dependencies      The list of dependencies to extract the artifacts from.
   * @param workflow          The workflow used to fetch the AMD files.
   * @param artifactsRecursed The set of artifacts already resolved and recursed for.
   */
  private void populateGraph(DependencyGraph graph, Dependency origin, Dependencies dependencies, Workflow workflow,
                             Set<Artifact> artifactsRecursed) throws ArtifactMetaDataMissingException, ProcessFailureException {
    dependencies.groups.forEach((type, group) -> {
      for (Dependency dependency : group.dependencies) {
        // Create a link using nodes so that we can be explicit
        DependencyLinkValue link = new DependencyLinkValue(origin.version, dependency.version, type, dependency.optional);
        graph.addLink(origin.id, dependency.id, link);

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(dependency)) {
          continue;
        }

        // Recurse
        ArtifactMetaData amd = workflow.fetchMetaData(dependency);
        if (amd.dependencies != null) {
          populateGraph(graph, dependency, amd.dependencies, workflow, artifactsRecursed);
        }

        // Add the artifact to the list
        artifactsRecursed.add(dependency);
      }
    });
  }

  private void resolve(DependencyGraph graph, ResolvedArtifactGraph resolvedGraph, Workflow workflow,
                       ResolveConfiguration configuration, ResolvedArtifact origin, Deque<ArtifactID> visited,
                       Deque<ArtifactID> resolved, DependencyListener... listeners)
  throws CyclicException, ArtifactMissingException, ProcessFailureException {
    Dependencies dependencies = graph.getDependencies(origin);
    dependencies.groups.forEach((type, group) -> {
      if (!configuration.groupConfigurations.containsKey(type)) {
        return;
      }

      group.dependencies.forEach((dependency) -> {
        if (dependency.optional) {
          return;
        }

        if (visited.contains(dependency.id)) {
          throw new CyclicException("The dependency [" + dependency + "] was encountered twice. This means you have a cyclic in your dependencies");
        }

        Version latest = graph.getLatestVersion(dependency.id);
        ResolvedArtifact resolvedArtifact = new ResolvedArtifact(dependency.id, latest);
        resolvedArtifact.file = workflow.fetchArtifact(resolvedArtifact);
        resolvedGraph.addLink(origin, resolvedArtifact, type);

        // Optionally fetch the source
        TypeResolveConfiguration typeResolveConfiguration = configuration.groupConfigurations.get(type);
        if (typeResolveConfiguration.fetchSource) {
          workflow.fetchSource(resolvedArtifact);
        }

        // Call the listeners
        asList(listeners).forEach((listener) -> {
          listener.artifactFetched(resolvedArtifact);
        });

        // Recurse if the configuration is set to transitive and this dependency hasn't been recursed yet
        if (typeResolveConfiguration.transitive && !resolved.contains(resolvedArtifact.id)) {
          visited.push(resolvedArtifact.id);
          resolve(graph, resolvedGraph, workflow, configuration, resolvedArtifact, visited, resolved, listeners);
          resolved.add(resolvedArtifact.id);
          visited.pop();
        }
      });
    });
  }
}
