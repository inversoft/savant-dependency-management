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

import com.sun.net.httpserver.HttpServer;
import org.savantbuild.dep.DependencyService.ResolveConfiguration;
import org.savantbuild.dep.DependencyService.ResolveConfiguration.TypeResolveConfiguration;
import org.savantbuild.dep.domain.AbstractArtifact;
import org.savantbuild.dep.domain.Artifact;
import org.savantbuild.dep.domain.ArtifactID;
import org.savantbuild.dep.domain.CompatibilityException;
import org.savantbuild.dep.domain.Dependencies;
import org.savantbuild.dep.domain.Dependency;
import org.savantbuild.dep.domain.DependencyGroup;
import org.savantbuild.dep.domain.License;
import org.savantbuild.dep.domain.ResolvedArtifact;
import org.savantbuild.dep.domain.Version;
import org.savantbuild.dep.graph.ArtifactGraph;
import org.savantbuild.dep.graph.DependencyEdgeValue;
import org.savantbuild.dep.graph.DependencyGraph;
import org.savantbuild.dep.graph.ResolvedArtifactGraph;
import org.savantbuild.dep.io.FileTools;
import org.savantbuild.dep.io.MD5Exception;
import org.savantbuild.dep.workflow.ArtifactMetaDataMissingException;
import org.savantbuild.dep.workflow.ArtifactMissingException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the default dependency service.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class DefaultDependencyServiceTest extends BaseTest {
  public Dependencies dependencies;

  public DependencyGraph goodGraph;

  public ArtifactGraph goodReducedGraph;

  public Artifact integrationBuild = new Artifact(new ArtifactID("org.savantbuild.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"), License.Apachev2);

  public Artifact intermediate = new Artifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), License.Apachev2);

  public Artifact leaf1 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar"), new Version("1.0.0"), License.GPL);

  public Artifact leaf1_1 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"), new Version("1.0.0"), License.Commercial);

  public Artifact leaf2 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar"), new Version("1.0.0"), License.LGPL);

  public Artifact leaf2_2 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"), License.OtherNonDistributableOpenSource);

  public Artifact leaf3_3 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"), License.Apachev2);

  public Artifact multipleVersions = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), License.Apachev2);

  public Artifact multipleVersionsDifferentDeps = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), License.Apachev2);

  public Artifact project = new Artifact("org.savantbuild.test:project:1.0", License.Apachev2);

  public ResolvedArtifact projectResolved = new ResolvedArtifact(project.id, project.version, License.Apachev2, null);

  public HttpServer server;

  public DefaultDependencyService service = new DefaultDependencyService();

  @AfterMethod
  public void afterMethodStopFileServer() {
    server.stop(0);
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |              |
   *              |->(1.0.0)intermediate
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0,1.1.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   */
  @BeforeClass
  public void beforeClass() {
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(project.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Apachev1));
    goodGraph.addEdge(project.id, intermediate.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Apachev2));
    goodGraph.addEdge(project.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Apachev2));
    goodGraph.addEdge(intermediate.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false, License.Apachev2));
    goodGraph.addEdge(intermediate.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "run", false, License.Apachev2));
    goodGraph.addEdge(multipleVersions.id, leaf1.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.GPL));
    goodGraph.addEdge(multipleVersions.id, leaf1.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.GPL));
    goodGraph.addEdge(multipleVersions.id, integrationBuild.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", false, License.Apachev2));
    goodGraph.addEdge(multipleVersions.id, integrationBuild.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", false, License.Apachev2));
    goodGraph.addEdge(multipleVersionsDifferentDeps.id, leaf2.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.LGPL));
    goodGraph.addEdge(multipleVersionsDifferentDeps.id, leaf1_1.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    goodGraph.addEdge(multipleVersionsDifferentDeps.id, leaf1_1.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    goodGraph.addEdge(multipleVersionsDifferentDeps.id, leaf2_2.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.OtherNonDistributableOpenSource));
    goodGraph.addEdge(multipleVersionsDifferentDeps.id, leaf3_3.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "run", true, License.Apachev2));

    goodReducedGraph = new ArtifactGraph(project);
    goodReducedGraph.addEdge(project, multipleVersions, "compile");
    goodReducedGraph.addEdge(project, intermediate, "run");
    goodReducedGraph.addEdge(project, multipleVersionsDifferentDeps, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersions, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersionsDifferentDeps, "run");
    goodReducedGraph.addEdge(multipleVersions, leaf1, "compile");
    goodReducedGraph.addEdge(multipleVersions, integrationBuild, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Dependency(multipleVersions.id, new Version("1.0.0"), false),
            new Dependency(multipleVersionsDifferentDeps.id, new Version("1.0.0"), false)
        ),
        new DependencyGroup("run", true,
            new Dependency(intermediate.id, new Version("1.0.0"), false)
        )
    );
  }

  @BeforeMethod
  public void beforeMethodStartFileServer() throws IOException {
    server = makeFileServer(null, null);
    FileTools.prune(cache);
    assertFalse(Files.isDirectory(cache));
  }

  @Test
  public void buildGraph() throws Exception {
    DependencyGraph actual = service.buildGraph(project, dependencies, workflow);
    assertEquals(actual, goodGraph);
  }

  @Test
  public void buildGraphFailureBadAMDMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:bad-amd-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when downloading item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]");
    }
  }

  @Test
  public void buildGraphFailureMissingAMD() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-amd:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new AbstractArtifact("org.savantbuild.test:missing-amd:1.0.0") {
      });
    }
  }

  @Test
  public void buildGraphFailureMissingDependency() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new AbstractArtifact("org.savantbuild.test:missing:1.0.0") {
      });
    }
  }

  @Test
  public void buildGraphFailureMissingMD5() throws Exception {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.savantbuild.test:missing-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new AbstractArtifact("org.savantbuild.test:missing-md5:1.0.0") {
      });
    }
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |           (1.0.0)
   *              |->(1.0.0)intermediate
   *              |           (1.0.0)
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(2.0.0)leaf1:leaf1 // This is the upgrade
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   * <p/>
   * Notice that the leaf1:leaf1 node gets upgrade across a major version. This is allowed because the
   * multiple-versions-different-dependencies node gets upgrade to 1.1.0 and therefore all of the dependencies below it
   * are from the 1.1.0 version.
   */
  @Test
  public void reduceComplex() throws Exception {
    Artifact leaf1 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf1", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact leaf2 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf2", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact leaf1_1 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf1", "leaf1", "jar"), new Version("2.0.0"), License.Commercial);
    Artifact leaf2_2 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact leaf3_3 = new Artifact(new ArtifactID("org.savantbuild.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact integrationBuild = new Artifact(new ArtifactID("org.savantbuild.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"), License.Commercial);
    Artifact intermediate = new Artifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact multipleVersions = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), License.Commercial);
    Artifact multipleVersionsDifferentDeps = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), License.Commercial);

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(project.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(project.id, intermediate.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    graph.addEdge(project.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersions.id, leaf1.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersions.id, leaf1.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersions.id, integrationBuild.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersions.id, integrationBuild.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf2.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf1_1.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf1_1.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf2_2.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf3_3.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "run", true, License.Commercial));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "run");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "run");
    expected.addEdge(multipleVersions, leaf1, "compile");
    expected.addEdge(multipleVersions, integrationBuild, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(2.0.0)leaf:leaf
   *              |              ^                         (1.0.0) (2.0.0)
   *              |              |                           |       ^
   *              |->(1.0.0)intermediate                     |       |
   *              |              |                           |       |
   *              |             \/                           |       |
   *              |          (1.1.0)                      (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p/>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will upgrade leaf to 2.0.0, it should ignore the 1.0.0 version of it and not generate an
   * error.
   */
  @Test
  public void reduceComplexCross() throws Exception {
    Artifact leaf = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar"), new Version("2.0.0"), License.Commercial);
    Artifact intermediate = new Artifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact multipleVersions = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), License.Commercial);
    Artifact multipleVersionsDifferentDeps = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), License.Commercial);

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(project.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(project.id, intermediate.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    graph.addEdge(project.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersions.id, leaf.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersions.id, leaf.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "run", false, License.Commercial));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "run");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "run");
    expected.addEdge(multipleVersions, leaf, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "run");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)intermediate2:intermediate2(2.0.0)-->(2.0.0)leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)intermediate2:intermediate2                (1.0.0)
   *              |              ^                                  (2.0.0) (1.0.0)                        ^
   *              |              |                                    ^      ^                             |
   *              |->(1.0.0)intermediate                    ----------|      |                             |
   *              |              |                     -----|  --------------                              |
   *              |             \/                    |       |                                            |
   *              |          (1.1.0)               (1.0.0) (1.1.0)                                         |
   *              |-->(1.0.0)multiple-versions-different-dependencies(1.1.0)-------------------------------|
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p/>
   * Notice that the intermediate2 has two versions, 1.0.0 and 2.0.0. However, multiple-versions-different-dependencies
   * gets upgraded to 1.1.0, which means that intermediate2 gets downgraded to 1.0.0. This also means that leaf should
   * be downgraded to 1.0.0 since the dependency from intermediate2(2.0.0) should be ignored since that version is never
   * used in the graph.
   */
  @Test
  public void reduceDowngrade() throws Exception {
    Artifact leaf = new Artifact(new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact intermediate = new Artifact(new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact intermediate2 = new Artifact(new ArtifactID("org.savantbuild.test", "intermediate2", "intermediate2", "jar"), new Version("1.0.0"), License.Commercial);
    Artifact multipleVersions = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), License.Commercial);
    Artifact multipleVersionsDifferentDeps = new Artifact(new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), License.Commercial);

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(project.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(project.id, intermediate.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    graph.addEdge(project.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersions.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false, License.Commercial));
    graph.addEdge(intermediate.id, multipleVersionsDifferentDeps.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "run", false, License.Commercial));
    graph.addEdge(intermediate2.id, leaf.id, new DependencyEdgeValue(new Version("2.0.0"), new Version("2.0.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersions.id, intermediate2.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersions.id, intermediate2.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, intermediate2.id, new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "run", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, intermediate2.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    graph.addEdge(multipleVersionsDifferentDeps.id, leaf.id, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "run");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "run");
    expected.addEdge(multipleVersions, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "compile");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)leaf
   *              |     * (2.0.0)
   *              |          ^
   *              |          |
   *              |          |
   *              |->(1.0.0)intermediate
   * </pre>
   * <p/>
   * Notice that leaf has two versions, 1.0.0 and 2.0.0. Since this artifact is reachable from the root node, it will
   * cause a failure.
   */
  @Test
  public void reduceFailureFromRoot() throws Exception {
    ArtifactID leaf = new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(project.id, leaf, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    incompatible.addEdge(project.id, intermediate, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    incompatible.addEdge(intermediate, leaf, new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "compile", false, License.Commercial));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.artifactID, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
      e.printStackTrace();
    }
  }

  /**
   * Graph:
   * <p/>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf
   *              |              ^                          (1.0.0) (2.0.0)
   *              |              |                              ^      ^
   *              |->(1.0.0)intermediate                        |      |
   *              |              |                              |      |
   *              |             \/                              |      |
   *              |          (1.1.0)                        (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p/>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will encounter two incompatible versions, it will cause a failure.
   */
  @Test
  public void reduceFailureNested() throws Exception {
    ArtifactID leaf = new ArtifactID("org.savantbuild.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.savantbuild.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.savantbuild.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.savantbuild.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(project.id, multipleVersions, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    incompatible.addEdge(project.id, intermediate, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    incompatible.addEdge(project.id, multipleVersionsDifferentDeps, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    incompatible.addEdge(intermediate, multipleVersions, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", false, License.Commercial));
    incompatible.addEdge(intermediate, multipleVersionsDifferentDeps, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "run", false, License.Commercial));
    incompatible.addEdge(multipleVersions, leaf, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    incompatible.addEdge(multipleVersions, leaf, new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", false, License.Commercial));
    incompatible.addEdge(multipleVersionsDifferentDeps, leaf, new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "run", false, License.Commercial));
    incompatible.addEdge(multipleVersionsDifferentDeps, leaf, new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile", false, License.Commercial));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.artifactID, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
      e.printStackTrace();
    }
  }

  @Test
  public void reduceSimple() throws Exception {
    ArtifactGraph actual = service.reduce(goodGraph);
    assertEquals(actual, goodReducedGraph);
  }

  @Test
  public void resolveGraph() throws Exception {
    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    ResolvedArtifact intermediate = new ResolvedArtifact("org.savantbuild.test:intermediate:1.0.0", License.Apachev2, cache.resolve("org/savantbuild/test/intermediate/1.0.0/intermediate-1.0.0.jar"));
    ResolvedArtifact multipleVersions = new ResolvedArtifact("org.savantbuild.test:multiple-versions:1.1.0", License.Apachev2, cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar"));
    ResolvedArtifact multipleVersionsDifferentDeps = new ResolvedArtifact("org.savantbuild.test:multiple-versions-different-dependencies:1.1.0", License.Apachev2, cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar"));
    ResolvedArtifact leaf1 = new ResolvedArtifact("org.savantbuild.test:leaf:leaf1:1.0.0:jar", License.GPL, cache.resolve("org/savantbuild/test/leaf/1.0.0/leaf1-1.0.0.jar"));
    ResolvedArtifact leaf1_1 = new ResolvedArtifact("org.savantbuild.test:leaf1:1.0.0", License.Commercial, cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar"));
    ResolvedArtifact leaf2_2 = new ResolvedArtifact("org.savantbuild.test:leaf2:1.0.0", License.OtherNonDistributableOpenSource, cache.resolve("org/savantbuild/test/leaf2/1.0.0/leaf2-1.0.0.jar"));
    ResolvedArtifact integrationBuild = new ResolvedArtifact("org.savantbuild.test:integration-build:2.1.1-{integration}", License.Apachev2, cache.resolve("org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));

    expected.addEdge(projectResolved, multipleVersions, "compile");
    expected.addEdge(projectResolved, intermediate, "run");
    expected.addEdge(projectResolved, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "run");
    expected.addEdge(multipleVersions, leaf1, "compile");
    expected.addEdge(multipleVersions, integrationBuild, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");

    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true))
                                  .with("run", new TypeResolveConfiguration(true, true))
    );

    assertEquals(actual, expected);

    // Verify that all the artifacts have files and they all exist (except for the project)
    Set<ResolvedArtifact> artifacts = actual.values();
    artifacts.remove(projectResolved);
    artifacts.forEach((artifact) -> assertTrue(Files.isRegularFile(artifact.file)));
  }

  @Test
  public void resolveGraphFailureBadLicense() throws Exception {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    try {
      service.resolve(artifactGraph, workflow,
          new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true, License.GPL))
                                    .with("run", new TypeResolveConfiguration(true, true))
      );
    } catch (LicenseException e) {
      assertEquals(e.artifact, leaf1);
    }
  }

  @Test
  public void resolveGraphFailureMD5() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:bad-md5:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true)));
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when downloading item from [http://localhost:7000/test-deps/savant/org/savantbuild/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]");
    }
  }

  @Test
  public void resolveGraphFailureMissingDependency() throws Exception {
    DependencyGraph graph = makeSimpleGraph("org.savantbuild.test:missing-item:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, true)));
    } catch (ArtifactMissingException e) {
      assertEquals(e.artifact, new AbstractArtifact("org.savantbuild.test:missing-item:1.0.0") {
      });
    }
  }

  @Test
  public void resolveGraphNonTransitiveSpecificGroups() throws Exception {
    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    ResolvedArtifact multipleVersions = new ResolvedArtifact("org.savantbuild.test:multiple-versions:1.1.0", License.Apachev2, cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar"));
    ResolvedArtifact multipleVersionsDifferentDeps = new ResolvedArtifact("org.savantbuild.test:multiple-versions-different-dependencies:1.1.0", License.Apachev2, cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar"));

    expected.addEdge(projectResolved, multipleVersions, "compile");
    expected.addEdge(projectResolved, multipleVersionsDifferentDeps, "compile");

    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new ResolveConfiguration().with("compile", new TypeResolveConfiguration(true, false))
    );

    assertEquals(actual, expected);

    // Verify that all the artifacts have files and they all exist (except for the project)
    Set<ResolvedArtifact> artifacts = actual.values();
    artifacts.remove(projectResolved);
    artifacts.forEach((artifact) -> assertTrue(Files.isRegularFile(artifact.file)));
  }

  private Dependencies makeSimpleDependencies(String dependency) {
    return new Dependencies(
        new DependencyGroup("compile", true,
            new Dependency(dependency, false)
        )
    );
  }

  /**
   * Builds a simple DependencyGraph that only contains an edge from the project to a single dependency.
   *
   * @param dependency The dependency.
   * @return The graph.
   */
  private DependencyGraph makeSimpleGraph(String dependency) {
    DependencyGraph graph = new DependencyGraph(project);
    AbstractArtifact artifact = new AbstractArtifact(dependency) {
    };
    graph.addEdge(project.id, artifact.id, new DependencyEdgeValue(project.version, artifact.version, "compile", false, License.Commercial));
    return graph;
  }
}
