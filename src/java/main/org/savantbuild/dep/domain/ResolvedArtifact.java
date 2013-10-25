/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.domain;

import java.nio.file.Path;

/**
 * This class defines a resolved artifact, which is an artifact after it has been downloaded as a dependency and is
 * fully resolved. This form of an artifact has a file (as a Path) to where the artifact is stored on the local disk.
 *
 * @author Brian Pontarelli
 */
public class ResolvedArtifact extends Artifact {
  public Path file;

  public ResolvedArtifact(ArtifactID id, Version version) {
    this.id = id;
    this.version = version;
  }
}
