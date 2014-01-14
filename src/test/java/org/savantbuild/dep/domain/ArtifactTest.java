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

import org.savantbuild.dep.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Tests the artifact domain object.
 *
 * @author Brian Pontarelli
 */
public class ArtifactTest extends BaseUnitTest {
  @Test
  public void construct() {
    assertEquals(new Artifact("group:name:2.0", License.Apachev1), new Artifact(new ArtifactID("group", "name", "name", "jar"), new Version("2.0"), License.Apachev1));
    assertEquals(new Artifact("group:name:2.0:zip", License.Apachev2), new Artifact(new ArtifactID("group", "name", "name", "zip"), new Version("2.0"), License.Apachev2));
    assertEquals(new Artifact("group:project:name:2.0:zip", License.Commercial), new Artifact(new ArtifactID("group", "project", "name", "zip"), new Version("2.0"), License.Commercial));
    assertNotEquals(new Artifact("group:project:name:1.0:zip", License.Apachev1), new Artifact(new ArtifactID("group", "project", "name", "zip"), new Version("1.0"), License.Commercial));
  }

  @Test
  public void syntheticMethods() {
    assertEquals(new Artifact("group:name:2.0", License.Apachev2).getArtifactFile(), "name-2.0.0.jar");
    assertEquals(new Artifact("group:name:2.0", License.Apachev2).getArtifactMetaDataFile(), "name-2.0.0.jar.amd");
    assertEquals(new Artifact("group:name:2.0", License.Apachev2).getArtifactSourceFile(), "name-2.0.0-src.jar");
  }
}
