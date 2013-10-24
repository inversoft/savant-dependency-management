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
package org.savantbuild.dep;

import org.savantbuild.dep.domain.Artifact;

import java.nio.file.Path;

/**
 * This interface is a listener that is notified when the {@link DependencyService} fetches and publishes artifacts.
 *
 * @author Brian Pontarelli
 */
public interface DependencyListener {
  /**
   * Handle when an artifact is fetched by a mediator.
   *
   * @param file     The file that references the artifact.
   * @param artifact The artifact fetched.
   */
  void artifactFetched(Path file, Artifact artifact);

  /**
   * Handle when an artifact is published by a mediator.
   *
   * @param artifact The artifact being published.
   */
  void artifactPublished(Artifact artifact);
}
