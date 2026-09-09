//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link PrinterProfile}s from the bundled escpos-printer-db capability
 * database ({@code capabilities.conf}), matched case-insensitively by device
 * name (the profile's key or its {@code name} field).
 * <p>
 * A profile's {@code codePages} map is the printer's own {@code ESC t n} slot →
 * encoding table, so it drives {@link PrinterProfile#codePages()} directly:
 * every slot whose encoding resolves to a single-byte Java charset becomes a
 * selectable page. A profile is only usable when the database gives a printable
 * width and a Font&nbsp;A column budget; otherwise (e.g. the generic
 * {@code default} profile, whose width is {@code "Unknown"}) the lookup reports
 * it as not found. See {@code scripts/fetch_capabilities.py} for how the file
 * is produced.
 */
final class CapabilityProfiles {

  private static final String RESOURCE = "capabilities.conf";

  private static final Logger log = LoggerFactory.getLogger(CapabilityProfiles.class);

  // A profile is only trusted when its geometry is internally consistent: the
  // printable width must be a plausible receipt/label width, and the Font A cell
  // (dotsPerLine / columns) a plausible character width. Data outside these bands
  // (customer displays, mislabelled widths) would silently corrupt the character
  // grid, so such profiles are treated as unavailable.
  private static final int MIN_DOTS_PER_LINE = 128;
  private static final double MIN_CELL_DOTS = 8.0;
  private static final double MAX_CELL_DOTS = 18.0;

  /**
   * Fallback when the database lists no page faradn can select (PC437 at slot 0).
   */
  private static final CodePage DEFAULT_PAGE = new CodePage(0, Charset.forName("IBM437"));

  private CapabilityProfiles() {
  }

  /** Parsed once, on first use. */
  private static final class Holder {
    private static final Config PROFILES = ConfigFactory.parseResources(RESOURCE).getConfig("profiles");
  }

  static Optional<PrinterProfile> find(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    final String target = name.strip();
    for (Map.Entry<String, ConfigValue> entry : Holder.PROFILES.root().entrySet()) {
      final String key = entry.getKey();
      final Config profile = ((ConfigObject) entry.getValue()).toConfig();
      final String profileName = profile.hasPath("name") ? profile.getString("name") : key;
      if (key.equalsIgnoreCase(target) || profileName.equalsIgnoreCase(target)) {
        try {
          return build(key, profile);
        } catch (ConfigException e) {
          return Optional.empty(); // a malformed entry is treated as unusable
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<PrinterProfile> build(String key, Config profile) {
    final OptionalInt dotsPerLine = intAt(profile, "media.width.pixels");
    final List<Font> fonts = fonts(profile);
    final Optional<Font> fontA = fonts.stream().filter(font -> font.id() == 0).findFirst();

    if (dotsPerLine.isEmpty() || fontA.isEmpty()) {
      return Optional.empty(); // width or Font A column budget unknown: not renderable
    }

    final int width = dotsPerLine.getAsInt();
    if (!plausibleGeometry(key, width, fontA.get().columns())) {
      return Optional.empty(); // internally inconsistent numbers: would corrupt layout
    }

    final int dpi = intAt(profile, "media.dpi").orElse(0);
    final boolean supportsCut = flag(profile, "features.paperPartCut") || flag(profile, "features.paperFullCut");
    final PrinterProfile base = PrinterProfile.of(displayName(key, profile), width, fonts, dpi, supportsCut,
        codePages(profile));

    return Optional.of(new CapabilityAwareProfile(base,
        flag(profile, "features.barcodeA") || flag(profile, "features.barcodeB"),
        flag(profile, "features.qrCode"),
        flag(profile, "features.pdf417Code"),
        flag(profile, "features.bitImageRaster") || flag(profile, "features.graphics")));
  }

  /**
   * The database name of every profile that builds to a usable, geometry-checked
   * profile, sorted case-insensitively. Each name is the database key and
   * resolves with {@link #find(String)}.
   */
  static List<String> available() {
    final TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<String, ConfigValue> entry : Holder.PROFILES.root().entrySet()) {
      final String key = entry.getKey();
      final Config profile = ((ConfigObject) entry.getValue()).toConfig();
      try {
        if (build(key, profile).isPresent()) {
          names.add(key);
        }
      } catch (ConfigException ignored) {
        // a malformed entry is simply not offered
      }
    }
    return List.copyOf(names);
  }

  /**
   * Whether a profile's printable width and Font&nbsp;A column budget are
   * internally consistent enough to render a correct character grid. The verbatim
   * escpos-printer-db import can carry implausible numbers (customer displays,
   * mislabelled widths) that would silently corrupt layout, so those are
   * rejected.
   */
  private static boolean plausibleGeometry(String key, int dotsPerLine, int fontAColumns) {
    if (dotsPerLine < MIN_DOTS_PER_LINE) {
      log.debug("Ignoring profile [{}]: printable width {} dots is implausibly small", key, dotsPerLine);
      return false;
    }
    final double cell = (double) dotsPerLine / fontAColumns;
    if (cell < MIN_CELL_DOTS || cell > MAX_CELL_DOTS) {
      log.debug("Ignoring profile [{}]: {} dots across {} Font A columns is {} dots/char, an inconsistent grid",
          key, dotsPerLine, fontAColumns, String.format("%.1f", cell));
      return false;
    }
    return true;
  }

  /** A base profile plus the capability flags read from the database. */
  private record CapabilityAwareProfile(PrinterProfile base, boolean barcodes, boolean qr, boolean pdf417,
      boolean images) implements PrinterProfile {

    @Override
    public String name() {
      return base.name();
    }

    @Override
    public int dotsPerLine() {
      return base.dotsPerLine();
    }

    @Override
    public List<Font> fonts() {
      return base.fonts();
    }

    @Override
    public int dpi() {
      return base.dpi();
    }

    @Override
    public boolean supportsCut() {
      return base.supportsCut();
    }

    @Override
    public CodePage codePage() {
      return base.codePage();
    }

    @Override
    public List<CodePage> codePages() {
      return base.codePages();
    }

    @Override
    public boolean supportsBarcodes() {
      return barcodes;
    }

    @Override
    public boolean supportsQrCode() {
      return qr;
    }

    @Override
    public boolean supportsPdf417() {
      return pdf417;
    }

    @Override
    public boolean supportsImages() {
      return images;
    }
  }

  private static String displayName(String key, Config profile) {
    final String name = profile.hasPath("name") ? profile.getString("name") : key;
    final String vendor = profile.hasPath("vendor") ? profile.getString("vendor").strip() : "";
    if (vendor.isEmpty() || name.toLowerCase().startsWith(vendor.toLowerCase())) {
      return name;
    }

    return vendor + " " + name;
  }

  /**
   * The printer's fonts, from its {@code fonts} map (slot id → {@code columns}).
   * Slot 0 is Font A, 1 Font B, 2 Font C, …; slots without a numeric column
   * count are skipped. Ordered by slot id.
   */
  private static List<Font> fonts(Config profile) {
    final List<Font> fonts = new ArrayList<>();

    if (profile.hasPath("fonts")) {
      for (Map.Entry<String, ConfigValue> slot : profile.getObject("fonts").entrySet()) {
        final OptionalInt id = parseId(slot.getKey());

        if (id.isEmpty() || !(slot.getValue() instanceof ConfigObject font)) {
          continue;
        }

        intAt(font.toConfig(), "columns").ifPresent(columns -> fonts.add(new Font(id.getAsInt(), columns)));
      }
    }

    fonts.sort(Comparator.comparingInt(Font::id));

    return List.copyOf(fonts);
  }

  /**
   * The printer's {@code ESC t} pages, from its {@code codePages} map (slot id →
   * encoding name). Slots whose encoding faradn cannot select - unknown to Java,
   * or multi-byte (e.g. Shift-JIS), which {@code ESC t} does not switch to - are
   * dropped. Ordered by slot id so selection is deterministic.
   */
  private static List<CodePage> codePages(Config profile) {
    if (!profile.hasPath("codePages")) {
      return List.of(DEFAULT_PAGE);
    }
    final List<CodePage> pages = new ArrayList<>();
    for (Map.Entry<String, ConfigValue> slot : profile.getObject("codePages").entrySet()) {
      final OptionalInt id = parseId(slot.getKey());
      if (id.isEmpty() || slot.getValue().valueType() != ConfigValueType.STRING) {
        continue;
      }
      charsetFor((String) slot.getValue().unwrapped())
          .ifPresent(charset -> pages.add(new CodePage(id.getAsInt(), charset)));
    }
    if (pages.isEmpty()) {
      return List.of(DEFAULT_PAGE);
    }
    pages.sort(Comparator.comparingInt(CodePage::id));
    return List.copyOf(pages);
  }

  /**
   * A single-byte Java charset for the database encoding name, if one selectable
   * via {@code ESC t} exists.
   */
  private static Optional<Charset> charsetFor(String dbName) {
    try {
      final Charset charset = Charset.forName(dbName);
      // ESC t selects single-byte pages only; skip multi-byte encodings.
      if (charset.newEncoder().maxBytesPerChar() <= 1.0f) {
        return Optional.of(charset);
      }
    } catch (IllegalArgumentException unknownCharset) {
      // Unknown or unsupported encoding name: not selectable.
    }
    return Optional.empty();
  }

  private static OptionalInt parseId(String key) {
    try {
      return OptionalInt.of(Integer.parseInt(key));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  private static OptionalInt intAt(Config config, String path) {
    if (!config.hasPath(path)) {
      return OptionalInt.empty();
    }
    final ConfigValue value = config.getValue(path);
    if (value.valueType() == ConfigValueType.NUMBER) {
      return OptionalInt.of(((Number) value.unwrapped()).intValue());
    }
    return OptionalInt.empty();
  }

  private static boolean flag(Config config, String path) {
    return config.hasPath(path)
        && config.getValue(path).valueType() == ConfigValueType.BOOLEAN
        && config.getBoolean(path);
  }
}
