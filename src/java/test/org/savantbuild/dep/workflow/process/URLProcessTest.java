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
package org.savantbuild.dep.workflow.process;

import com.sun.net.httpserver.HttpServer;
import org.savantbuild.dep.BaseUnitTest;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.workflow.PublishWorkflow;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * This class tests the SavantInternetFetchProcess class.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class URLProcessTest extends BaseUnitTest {
  private HttpServer server;

  @Test(dataProvider = "fetchData")
  public void fetch(String url, String name, String version, String result) throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:" + name + ":" + name + ":" + version + ":jar");
    URLProcess ufp = new URLProcess(url, null, null);
    Path file = ufp.fetch(artifact, artifact.getArtifactFile(), new PublishWorkflow(new CacheProcess("build/test/cache")));
    assertNotNull(file);

    assertEquals(file.toAbsolutePath(), Paths.get(result).toAbsolutePath());
  }

  @DataProvider(name = "fetchData")
  public Object[][] fetchData() {
    return new Object[][]{
        {makeLocalURL(), "dependencies", "1.0.0", "build/test/cache/org/savantbuild/test/dependencies/1.0.0/dependencies-1.0.0.jar"},
        {makeLocalURL(), "dependencies-with-groups", "1.0.0", "build/test/cache/org/savantbuild/test/dependencies-with-groups/1.0.0/dependencies-with-groups-1.0.0.jar"},
        {makeLocalURL(), "integration-build", "2.1.1-{integration}", "build/test/cache/org/savantbuild/test/major-compat/1.1/major-compat-1.1.jar"},
        {"http://localhost:7000/test-deps/savant", "dependencies", "1.0.0", "build/test/cache/org/savantbuild/test/dependencies/1.0.0/dependencies-1.0.0.jar"},
        {"http://localhost:7000/test-deps/savant", "dependencies-with-groups", "1.0.0", "build/test/cache/org/savantbuild/test/dependencies-with-groups/1.0.0/dependencies-with-groups-1.0.0.jar"},
        {"http://localhost:7000/test-deps/savant", "integration-build", "2.1.1-{integration}", "build/test/cache/org/savantbuild/test/major-compat/1.1/major-compat-1.1.jar"}
    };
  }

  @Test(dataProvider = "urls")
  public void integration(String url) throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:integration-build:integration-build:2.1.1-{integration}:jar");

    CacheProcess process = new CacheProcess("build/test/cache");
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(url, null, null);
    Path file = ufp.fetch(artifact, artifact.getArtifactFile(), pw);
    assertNotNull(file);
  }

  @Test(dataProvider = "urls")
  public void metaData(String url) throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:dependencies:dependencies:1.0:jar");

    CacheProcess process = new CacheProcess("build/test/cache");
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(url, null, null);
    Path amd = ufp.fetch(artifact, artifact.getArtifactMetaDataFile(), pw);
    assertNotNull(amd);
  }

  @Test(dataProvider = "urls")
  public void missingAMD(String url) throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:missing-item:missing-item:1.0:jar");

    CacheProcess process = new CacheProcess("build/test/cache");
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(url, null, null);
    Path file = ufp.fetch(artifact, artifact.getArtifactMetaDataFile(), pw);
    assertNull(file);
  }

  @Test(dataProvider = "urls")
  public void missingItem(String url) throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:missing-item:missing-item:1.0:jar");

    CacheProcess process = new CacheProcess("build/test/cache");
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(url, null, null);
    Path file = ufp.fetch(artifact, artifact.getArtifactFile(), pw);
    assertNull(file);
  }

  @Test
  public void missingMD5() throws Exception {
    FileTools.prune(Paths.get("build/test/cache"));

    Artifact artifact = new Artifact("org.savantbuild.test:missing-md5:missing-md5:1.0:jar");

    CacheProcess process = new CacheProcess("build/test/cache");
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(makeLocalURL(), null, null);
    Path file = ufp.fetch(artifact, artifact.getArtifactFile(), pw);
    assertNull(file);
  }

  @BeforeMethod
  public void setupFileServer() throws Exception {
    server = makeFileServer(null, null);
  }

  @AfterMethod
  public void stopFileServer() {
    server.stop(0);
  }

  @DataProvider(name = "urls")
  public Object[][] urls() {
    return new Object[][]{
        {makeLocalURL()},
        {"http://localhost:7000/test-deps/savant"}
    };
  }

  private String makeLocalURL() {
    return Paths.get("").toAbsolutePath().toUri().toString() + "/test-deps/savant";
  }
}
