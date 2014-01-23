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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.Publication;
import org.savantbuild.dep.graph.ArtifactGraph;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.dep.workflow.Workflow;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.security.MD5Exception;
import org.savantbuild.util.CyclicException;

/**
 * Provides all of the dependency management services. The main workflow for managing dependencies is:
 * <p>
 * <pre>
 *   1. Download and parse all of the AMD (AbstractArtifact Meta Data) files to build a dependency graph.
 *   2. Traverse the graph and verify that it is a valid graph (doesn't contain conflicting versions of specific
 * artifacts)
 *   3. Traverse the graph and download the dependencies
 * </pre>
 *
 * @author Brian Pontarelli
 */
public interface DependencyService {
  /**
   * Builds a dependency graph for the given dependencies of the given project.
   *
   * @param project      The artifact that represents the project.
   * @param dependencies The declared dependencies of the project.
   * @param workflow     The workflow to use for downloading and caching the AMD files.
   * @return The dependency graph.
   * @throws ArtifactMetaDataMissingException If any artifacts AMD files could not be downloaded or found locally.
   * @throws ProcessFailureException          If a workflow process failed while fetching the meta-data.
   * @throws MD5Exception                     If any MD5 files didn't match the AMD file when downloading.
   */
  DependencyGraph buildGraph(Artifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception;

  /**
   * Publishes the given Publication (artifact, meta-data, source file, etc) with the given workflow.
   *
   * @param publication The publication to publish.
   * @param workflow    The workflow to publish with.
   * @throws PublishException If the publication failed.
   */
  void publish(Publication publication, PublishWorkflow workflow) throws PublishException;

  /**
   * Reduces the DependencyGraph by ensuring that each dependency only has one version. This also prunes unused
   * dependencies and ensures there are no compatibility issues in the graph.
   *
   * @param graph The dependency graph.
   * @return The reduced graph.
   * @throws CompatibilityException If an dependency has incompatible versions.
   * @throws CyclicException        If the graph has a cycle in it.
   */
  ArtifactGraph reduce(DependencyGraph graph) throws CompatibilityException, CyclicException;

  /**
   * Resolves the graph by downloading the artifacts. This will use the Workflow to download the artifacts in the graph.
   * This does not check version compatibility. That is done in the {@link #reduce(DependencyGraph)} method.
   *
   * @param graph         The ArtifactGraph to resolve.
   * @param workflow      THe workflow used to resolve the artifacts.
   * @param configuration The resolution configuration that controls which artifact groups to resolve and how they are
   *                      resolved.
   * @param listeners     Any listeners that want to receive callbacks when artifacts are resolved.
   * @return The resolved graph.
   * @throws ProcessFailureException  If a workflow process failed while fetching an artifact or its source.
   * @throws ArtifactMissingException If any of the required artifacts are missing.
   * @throws CyclicException          If any of the artifact graph has any cycles in it.
   * @throws MD5Exception             If the item's MD5 file did not match the item.
   * @throws LicenseException         If an invalid license is encountered during the resolution process.
   */
  ResolvedArtifactGraph resolve(ArtifactGraph graph, Workflow workflow, ResolveConfiguration configuration,
                                DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, MD5Exception, LicenseException;

  /**
   * Controls how resolution functions for each dependency-group. This determines if sources are fetched or if
   * transitive dependencies are fetch.
   */
  public static class ResolveConfiguration {
    Map<String, TypeResolveConfiguration> groupConfigurations = new HashMap<>();

    public ResolveConfiguration with(String type, TypeResolveConfiguration typeResolveConfiguration) {
      groupConfigurations.put(type, typeResolveConfiguration);
      return this;
    }

    public static class TypeResolveConfiguration {
      public final Set<License> disallowedLicenses = new HashSet<>();

      public final boolean fetchSource;

      public final boolean transitive;

      public final Set<String> transitiveGroups = new HashSet<>();

      public TypeResolveConfiguration(boolean fetchSource, boolean transitive) {
        this.fetchSource = fetchSource;
        this.transitive = transitive;
      }

      public TypeResolveConfiguration(boolean fetchSource, boolean transitive, License... disallowedLicenses) {
        Collections.addAll(this.disallowedLicenses, disallowedLicenses);
        this.fetchSource = fetchSource;
        this.transitive = transitive;
      }

      public TypeResolveConfiguration(boolean fetchSource, String... transitiveGroups) {
        Collections.addAll(this.transitiveGroups, transitiveGroups);
        this.fetchSource = fetchSource;
        this.transitive = true;
      }

      /**
       * Construct that assists with calling from Groovy code.
       *
       * @param fetchSource      Determines if the source should be fetched.
       * @param transitive       Determines if transitive dependencies are fetched.
       * @param transitiveGroups If transitive dependencies are fetched, this controls the transitive groups that are
       *                         fetched.
       */
      public TypeResolveConfiguration(boolean fetchSource, boolean transitive, Collection<String> transitiveGroups) {
        this.fetchSource = fetchSource;
        this.transitive = transitive;

        if (transitive) {
          this.transitiveGroups.addAll(transitiveGroups);
        }
      }

      public TypeResolveConfiguration withDisallowedLicenses(License... disallowedLicenses) {
        Collections.addAll(this.disallowedLicenses, disallowedLicenses);
        return this;
      }

      public TypeResolveConfiguration withTransitiveGroups(String... transitiveGroups) {
        Collections.addAll(this.transitiveGroups, transitiveGroups);
        return this;
      }
    }
  }
}
