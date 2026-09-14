//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.ZebraProfiles;
import net.nmoncho.faradn.printer.epl.EplRenderer;
import net.nmoncho.faradn.printer.zpl.ZplRenderer;
import net.nmoncho.faradn.transport.DumpTransport;

/**
 * The production print path (HTML -> IR -> renderer -> transport) for the Zebra
 * ZD421 label profiles, using {@link DumpTransport} so it runs in CI without a
 * printer. Each asserts the bytes {@link Printer#print} writes match rendering
 * the label backend directly - proving the profile's language selects the right
 * renderer - and, because the label languages report no realtime status, that
 * the job is written without a status pre-flight stalling it.
 */
class ZebraPrintTest {

  private static final File LABEL = new File("src/test/resources/printjobs/zebra-label.html");

  @Test
  void zplPrintsThroughTheProductionPath() {
    final Document doc = Document.from(LABEL);
    final PrinterProfile profile = ZebraProfiles.zd421Zpl203();
    final DumpTransport transport = new DumpTransport();

    Printer.print(transport, doc, profile);

    final byte[] bytes = transport.bytes();
    assertTrue(bytes.length > 0, "the label printed something");
    assertTrue(new String(bytes, StandardCharsets.UTF_8).startsWith("^XA"), "ZPL label framing");
    assertArrayEquals(new ZplRenderer(profile).render(doc.blocks(profile.dpi())), bytes);
  }

  @Test
  void eplPrintsThroughTheProductionPath() {
    final Document doc = Document.from(LABEL);
    final PrinterProfile profile = ZebraProfiles.zd421Epl203();
    final DumpTransport transport = new DumpTransport();

    Printer.print(transport, doc, profile);

    final byte[] bytes = transport.bytes();
    assertTrue(bytes.length > 0, "the label printed something");
    assertTrue(new String(bytes, Charset.forName("IBM437")).startsWith("I8,0,001"), "EPL label framing");
    assertArrayEquals(new EplRenderer(profile).render(doc.blocks(profile.dpi())), bytes);
  }
}
