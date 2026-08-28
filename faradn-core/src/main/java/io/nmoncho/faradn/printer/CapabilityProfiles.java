package io.nmoncho.faradn.printer;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * Builds {@link PrinterProfile}s from the bundled escpos-printer-db capability
 * database ({@code capabilities.conf}), matched case-insensitively by device
 * name (the profile's key or its {@code name} field).
 * <p>
 * A profile is only usable when the database gives both a printable width and a
 * Font&nbsp;A column budget; otherwise (e.g. the generic {@code default}
 * profile,
 * whose width is {@code "Unknown"}) the lookup reports the profile as not
 * found.
 * See {@code scripts/fetch_capabilities.py} for how the file is produced.
 */
final class CapabilityProfiles {

  private static final String RESOURCE = "capabilities.conf";

  // escpos-printer-db code-page names → the ESC/POS pages faradn can select.
  private static final Map<String, CodePage> CODE_PAGES = Map.of(
      "CP437", CodePage.PC437,
      "CP1252", CodePage.WPC1252,
      "CP850", CodePage.PC850,
      "CP858", CodePage.PC858,
      "CP852", CodePage.PC852,
      "CP866", CodePage.PC866,
      "CP860", CodePage.PC860,
      "CP863", CodePage.PC863,
      "CP865", CodePage.PC865);

  private CapabilityProfiles() { }

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
    final OptionalInt columns = fontAColumns(profile);

    if (dotsPerLine.isEmpty() || columns.isEmpty()) {
      return Optional.empty(); // width or column budget unknown: not renderable
    }

    final int dpi = intAt(profile, "media.dpi").orElse(0);
    final boolean supportsCut = flag(profile, "features.paperPartCut") || flag(profile, "features.paperFullCut");

    return Optional.of(PrinterProfile.of(displayName(key, profile), dotsPerLine.getAsInt(),
        columns.getAsInt(), dpi, supportsCut, defaultCodePage(profile)));
  }

  private static String displayName(String key, Config profile) {
    final String name = profile.hasPath("name") ? profile.getString("name") : key;
    final String vendor = profile.hasPath("vendor") ? profile.getString("vendor").strip() : "";

    if (vendor.isEmpty() || name.toLowerCase().startsWith(vendor.toLowerCase())) {
      return name;
    }

    return vendor + " " + name;
  }

  /** Font A (slot {@code "0"}) sets the base column budget. */
  private static OptionalInt fontAColumns(Config profile) {
    if (!profile.hasPath("fonts")) {
      return OptionalInt.empty();
    }

    final ConfigValue fontA = profile.getObject("fonts").get("0");
    if (fontA instanceof ConfigObject font) {
      return intAt(font.toConfig(), "columns");
    }

    return OptionalInt.empty();
  }

  /**
   * Slot {@code "0"} is the page selected at reset (ESC t 0); CP437 is the usual
   * default.
   */
  private static CodePage defaultCodePage(Config profile) {
    if (profile.hasPath("codePages")) {
      final ConfigValue slot0 = profile.getObject("codePages").get("0");
      if (slot0 != null && slot0.valueType() == ConfigValueType.STRING) {
        final CodePage mapped = CODE_PAGES.get((String) slot0.unwrapped());
        if (mapped != null) {
          return mapped;
        }
      }
    }

    return CodePage.PC437;
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
