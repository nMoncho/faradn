package net.nmoncho.faradn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class FaradnTest {

  @Test
  void versionIsFilteredFromTheBuild() {
    final String[] version = new CommandLine(new Faradn()).getCommandSpec().version();

    assertEquals(1, version.length);
    assertTrue(version[0].startsWith("faradn "), "version should be prefixed with the command name");
    assertFalse(version[0].contains("$"), "the ${project.version} placeholder was not filtered");
  }
}
