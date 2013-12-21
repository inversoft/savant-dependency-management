/*
 * Copyright (c) 2001-2006, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.workflow.process;

import org.savantbuild.dep.BaseTest;
import org.savantbuild.dep.domain.AbstractArtifact;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.io.MD5;
import org.savantbuild.dep.util.RuntimeTools;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.savantbuild.io.FileTools;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * SVNProcess Tester.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class SVNProcessTest extends BaseTest {
  @BeforeMethod
  public void deleteRepository() throws Exception {
    if (Files.isDirectory(projectDir.resolve("build/test/svn-repository"))) {
      FileTools.prune(projectDir.resolve("build/test/svn-repository"));
    }

    if (Files.isDirectory(projectDir.resolve("build/test/cache"))) {
      FileTools.prune(projectDir.resolve("build/test/cache"));
    }

    assertFalse(Files.exists(projectDir.resolve("build/test/cache")));
    assertFalse(Files.exists(projectDir.resolve("build/test/svn-repository")));
    assertTrue(RuntimeTools.exec("svnadmin", "create", projectDir.toString() + "/build/test/svn-repository"));
    assertTrue(Files.exists(projectDir.resolve("build/test/svn-repository")));
  }

  public void run() throws IOException {
    AbstractArtifact artifact = new Artifact("org.savantbuild.test:svn-process-test:1.0", License.Apachev2);

    Path md5File = FileTools.createTempPath("savant-process", "md5", true);
    Path file = projectDir.resolve("src/java/test/org/savantbuild/dep/io/MD5Test.txt");
    MD5.writeMD5(MD5.fromBytes(Files.readAllBytes(file), "MD5Test.txt"), md5File);

    SVNProcess process = new SVNProcess(output, "file:///" + projectDir.resolve("build/test/svn-repository").toAbsolutePath().toString(), null, null);
    process.publish(artifact, artifact.getArtifactFile() + ".md5", md5File);
    process.publish(artifact, artifact.getArtifactFile(), file);

    process.fetch(artifact, artifact.getArtifactFile(), new PublishWorkflow(new CacheProcess(output, cache.toString())));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/cache/org/savantbuild/test/svn-process-test/1.0.0/svn-process-test-1.0.0.jar")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/cache/org/savantbuild/test/svn-process-test/1.0.0/svn-process-test-1.0.0.jar.md5")));
  }
}
