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
package org.savantbuild.dep.workflow;

import org.savantbuild.dep.domain.AbstractArtifact;

/**
 * Thrown when an artifact is missing an AMD file during the dependency graph building process.
 * <p>
 * This exception is not permanent and usually is fixed by changing to a different server. This exception should not
 * cause a negative cache file to be written and should repeat itself if nothing else changes.
 *
 * @author Brian Pontarelli
 */
public class ArtifactMetaDataMissingException extends RuntimeException {
  public final AbstractArtifact artifactMissingAMD;

  public ArtifactMetaDataMissingException(AbstractArtifact artifactMissingAMD) {
    super("The AMD file for the artifact [" + artifactMissingAMD + "] could not be located using your workflow");
    this.artifactMissingAMD = artifactMissingAMD;
  }
}
