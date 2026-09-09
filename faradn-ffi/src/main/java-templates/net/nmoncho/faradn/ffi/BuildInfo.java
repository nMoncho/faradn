package net.nmoncho.faradn.ffi;

/**
 * Build-time constants filtered from the Maven build. Generated from
 * {@code src/main/java-templates} by the templating-maven-plugin; do not edit.
 */
final class BuildInfo {

  /** The faradn-ffi artifact version, filtered from {@code ${project.version}}. */
  static final String VERSION = "${project.version}";

  private BuildInfo() {
  }
}
