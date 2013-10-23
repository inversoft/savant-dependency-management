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

import org.savantbuild.dep.graph.DependencyGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class models a set of dependencies on Dependency (artifacts) objects broken into DependencyGroups.
 *
 * @author Brian Pontarelli
 */
public class Dependencies {
  public final Map<String, DependencyGroup> groups = new HashMap<>();

  public DependencyGraph graph;

  public String name;

  public Dependencies() {
  }

  public Dependencies(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Dependencies that = (Dependencies) o;
    return groups.equals(that.groups) && !(name != null ? !name.equals(that.name) : that.name != null);

  }

  /**
   * Collects all of the artifacts from all of the groups.
   *
   * @return All of the artifacts.
   */
  public Set<Artifact> getAllArtifacts() {
    Set<Artifact> set = new HashSet<>();
    groups.values().forEach((group) -> set.addAll(group.dependencies));
    return set;
  }

  @Override
  public int hashCode() {
    int result = groups.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
