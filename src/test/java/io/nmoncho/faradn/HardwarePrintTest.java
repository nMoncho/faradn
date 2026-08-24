package io.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Manual hardware check for Phase 1 (Hardware checkpoint 1). It talks to a real
 * USB printer, so it is skipped unless {@code -Dfaradn.hardware=true} is set:
 *
 * <pre>{@code mvn test -Dfaradn.hardware=true -Dtest=HardwarePrintTest}</pre>
 *
 * It prints a text-only receipt on an Epson (USB vendor {@code 0x04b8}); verify
 * by eye that the heading sizes, bold, centering, alignment, underline and
 * rules
 * come out right. Images and barcodes are a later milestone, so the fixture is
 * deliberately text only.
 */
@Tag("hardware")
@EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
public class HardwarePrintTest {

  @Test
  void printsTextReceipt() {
    Document doc = Document.from(new File("src/test/resources/printjobs/receipt-text.html"));

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }
}
