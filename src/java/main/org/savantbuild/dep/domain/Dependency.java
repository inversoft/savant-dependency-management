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
package org.savantbuild.dep.domain;

/**
 * This class defines a artifact that is a dependency of a project/artifact. It contains many attributes that are only
 * valid when the artifact is a dependency.
 *
 * @author Brian Pontarelli
 */
public class Dependency extends Artifact {
  public boolean optional;

  public Dependency() {
    super();
  }

  public Dependency(ArtifactID id, Version version, boolean optional) {
    super(id, version);
    this.optional = optional;
  }
}
