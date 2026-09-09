//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

/**
 * Build-time constants filtered from the Maven build. Generated from
 * {@code src/main/java-templates} by the templating-maven-plugin; do not edit.
 */
final class BuildInfo {

  /** The faradn-cli artifact version, filtered from {@code ${project.version}}. */
  static final String VERSION = "${project.version}";

  private BuildInfo() {
  }
}
