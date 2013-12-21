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
import org.savantbuild.dep.domain.AbstractArtifact;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.ArtifactGraph;
import org.savantbuild.dep.graph.CyclicException;
import org.savantbuild.dep.graph.DependencyEdgeValue;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.Graph.Edge;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;
import org.savantbuild.io.FileTools;
import org.savantbuild.output.Output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * Default implementation of the dependency service.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyService implements DependencyService {
  private final Output output;

  public DefaultDependencyService(Output output) {
    this.output = output;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException {
    output.debug("Building DependencyGraph");
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, project, dependencies, workflow, new HashSet<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(Publication publication, PublishWorkflow workflow) throws PublishException {
    try {
      Path amdFile = ArtifactTools.generateXML(publication.metaData);
      publishItem(publication.artifact, publication.artifact.getArtifactMetaDataFile(), amdFile, workflow);
      publishItem(publication.artifact, publication.artifact.getArtifactFile(), publication.file, workflow);

      if (publication.sourceFile != null) {
        publishItem(publication.artifact, publication.artifact.getArtifactSourceFile(), publication.sourceFile, workflow);
      }
    } catch (IOException e) {
      throw new PublishException(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArtifactGraph reduce(DependencyGraph graph) throws CompatibilityException {

    // Traverse graph. At each node, if the node's parents haven't all been checked. Skip it.
    // If the node's parents have all been checked, for each parent, get the version of the node for the version of the
    // parent that was kept. Ensure all these versions are compatible. Select the highest one. Add that to the
    // ArtifactGraph. Store the kept version. Continue.

    ArtifactGraph artifactGraph = new ArtifactGraph(graph.root);
    Map<ArtifactID, Artifact> artifacts = new HashMap<>();
    artifacts.put(graph.root.id, graph.root);

    graph.traverse(graph.root.id, (origin, destination, edgeValue, depth) -> {
      // If this edge optional, skip it and don't continue traversal down
      if (edgeValue.optional) {
        return false;
      }

      List<Edge<ArtifactID, DependencyEdgeValue>> inbound = graph.getInboundEdges(destination);
      boolean alreadyCheckedAllParents = inbound.size() > 0 && inbound.stream().allMatch((
          edge) -> artifacts.containsKey(edge.getOrigin()));
      if (alreadyCheckedAllParents) {
        List<Edge<ArtifactID, DependencyEdgeValue>> significantInbound = inbound.stream()
                                                                                .filter((
                                                                                    edge) -> edge.getValue().dependentVersion.equals(artifacts.get(edge.getOrigin()).version))
                                                                                .collect(Collectors.toList());

        // This is the complex part, for each inbound edge, grab the one where the origin is the correct version (based
        // on the versions we have already kept). Then for each of those, map to the dependency version (the version of
        // the destination node). Then get the min and max.
        Version min = significantInbound.stream()
                                        .map((edge) -> edge.getValue().dependencyVersion)
                                        .min(Version::compareTo)
                                        .orElse(null);
        Version max = significantInbound.stream()
                                        .map((edge) -> edge.getValue().dependencyVersion)
                                        .max(Version::compareTo)
                                        .orElse(null);

        // This dependency is no longer used
        if (min == null || max == null) {
          return false;
        }

        // Ensure min and max are compatible
        if (!min.isCompatibleWith(max)) {
          throw new CompatibilityException(destination, min, max);
        }

        // Build the artifact for this node, save it in the Map and put it in the ArtifactGraph
        Artifact destinationArtifact = new Artifact(destination, max, edgeValue.license);
        artifacts.put(destination, destinationArtifact);

        significantInbound.stream()
                          .forEach((edge) -> {
                            Artifact originArtifact = artifacts.get(edge.getOrigin());
                            artifactGraph.addEdge(originArtifact, destinationArtifact, edge.getValue().type);
                          });
      }

      return true; // Always continue traversal
    });

    return artifactGraph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResolvedArtifactGraph resolve(ArtifactGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                       DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, MD5Exception, LicenseException {
    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version, graph.root.license, null);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);

    Map<Artifact, ResolvedArtifact> map = new HashMap<>();
    map.put(graph.root, root);

    graph.traverse(graph.root, (origin, destination, group, depth) -> {
      // Only resolve specified types
      TypeResolveConfiguration typeResolveConfiguration = configuration.groupConfigurations.get(group);
      if (typeResolveConfiguration == null) {
        return false;
      }

      if (typeResolveConfiguration.disallowedLicenses.contains(destination.license)) {
        throw new LicenseException("Invalid license encountered", destination);
      }

      Path file = workflow.fetchArtifact(destination);
      ResolvedArtifact resolvedArtifact = new ResolvedArtifact(destination.id, destination.version, destination.license, file);
      resolvedGraph.addEdge(map.get(origin), resolvedArtifact, group);
      map.put(destination, resolvedArtifact);

      // Optionally fetch the source
      if (typeResolveConfiguration.fetchSource) {
        workflow.fetchSource(resolvedArtifact);
      }

      // Call the listeners
      asList(listeners).forEach((listener) -> listener.artifactFetched(resolvedArtifact));

      // Recurse if the configuration is set to transitive (or not set)
      return typeResolveConfiguration.transitive;
    });

    return resolvedGraph;
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
  private void populateGraph(DependencyGraph graph, Artifact origin, Dependencies dependencies, Workflow workflow,
                             Set<Dependency> artifactsRecursed)
      throws ArtifactMetaDataMissingException, ProcessFailureException {
    dependencies.groups.forEach((type, group) -> {
      for (Dependency dependency : group.dependencies) {
        ArtifactMetaData amd = workflow.fetchMetaData(dependency);

        // Create an edge using nodes so that we can be explicit
        DependencyEdgeValue edge = new DependencyEdgeValue(origin.version, dependency.version, type, dependency.optional, amd.license);
        graph.addEdge(origin.id, dependency.id, edge);

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(dependency)) {
          continue;
        }

        // Recurse
        if (amd.dependencies != null) {
          Artifact artifact = amd.toLicensedArtifact(dependency);
          populateGraph(graph, artifact, amd.dependencies, workflow, artifactsRecursed);
        }

        // Add the artifact to the list
        artifactsRecursed.add(dependency);
      }
    });
  }

  /**
   * Publishes a single item for the given artifact.
   *
   * @param artifact The artifact.
   * @param item     The item to publish.
   * @param file     The file to publish.
   * @param workflow The publish workflow.
   * @throws IOException If the publication fails.
   */
  private void publishItem(AbstractArtifact artifact, String item, Path file, PublishWorkflow workflow)
      throws IOException {
    MD5 md5 = MD5.fromPath(file);
    Path md5File = FileTools.createTempPath("artifact-item", "md5", true);
    MD5.writeMD5(md5, md5File);
    workflow.publish(artifact, item + ".md5", md5File);
    workflow.publish(artifact, item, file);
  }
}
