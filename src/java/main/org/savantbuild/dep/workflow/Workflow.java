/*
 * Copyright (c) 2008, Inversoft, All Rights Reserved.
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
package org.savantbuild.dep.workflow;

import org.savantbuild.dep.ArtifactMetaDataMissingException;
import org.savantbuild.dep.ArtifactMissingException;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactMetaData;
import org.savantbuild.dep.workflow.process.ProcessFailureException;
import org.savantbuild.dep.xml.ArtifactTools;

import java.nio.file.Path;

/**
 * This class models a grouping of a fetch and publish workflow.
 *
 * @author Brian Pontarelli
 */
public class Workflow {
  private final FetchWorkflow fetchWorkflow;

  private final PublishWorkflow publishWorkflow;

  public Workflow(FetchWorkflow fetchWorkflow, PublishWorkflow publishWorkflow) {
    this.fetchWorkflow = fetchWorkflow;
    this.publishWorkflow = publishWorkflow;
  }

  /**
   * Fetches the artifact itself. Every artifact in a Savant dependency graph is required to exist. Therefore, Savant
   * never negative caches artifact files and this method will always return the artifact file or throw an
   * ArtifactMissingException.
   *
   * @param artifact The artifact to fetch.
   * @return The Path of the artifact and never null.
   * @throws ArtifactMissingException If the artifact could not be found.
   * @throws ProcessFailureException  If any of the processes encountered a failure while attempting to fetch the
   *                                  artifact.
   */
  public Path fetchArtifact(Artifact artifact) throws ArtifactMetaDataMissingException, ProcessFailureException {
    Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactFile(), publishWorkflow);
    if (file == null) {
      throw new ArtifactMissingException(artifact);
    }

    return file;
  }

  /**
   * Fetches the artifact meta data. Every artifact in Savant is required to have an AMD file. Otherwise, it is
   * considered a missing artifact entirely. Therefore, Savant never negative caches AMD files and this method will
   * always return an AMD file or throw an ArtifactMetaDataMissingException.
   *
   * @param artifact The artifact to fetch the meta data for.
   * @return The ArtifactMetaData object and never null.
   * @throws ArtifactMetaDataMissingException
   *                                 If the AMD file could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the AMD
   *                                 file.
   */
  public ArtifactMetaData fetchMetaData(Artifact artifact)
      throws ArtifactMetaDataMissingException, ProcessFailureException {
    Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactMetaDataFile(), publishWorkflow);
    if (file == null) {
      throw new ArtifactMetaDataMissingException(artifact);
    }

    return ArtifactTools.parseArtifactMetaData(file);
  }

  /**
   * Fetches the source of the artifact. If a source file is missing, this method stores a negative file in the cache so
   * that an attempt to download the source file isn't made each time. This is required so that offline work can be done
   * by only hitting the local cache of dependencies.
   *
   * @param artifact The artifact to fetch the source for.
   * @return The Path of the source or null if it doesn't exist.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the source
   *                                 file.
   */
  public Path fetchSource(Artifact artifact) throws ProcessFailureException {
    Path file = fetchWorkflow.fetchItem(artifact, artifact.getArtifactSourceFile(), publishWorkflow);
    if (file == null) {
      publishWorkflow.publishNegative(artifact, artifact.getArtifactSourceFile());
    }

    return file;
  }

  public FetchWorkflow getFetchWorkflow() {
    return fetchWorkflow;
  }

  public PublishWorkflow getPublishWorkflow() {
    return publishWorkflow;
  }
}
