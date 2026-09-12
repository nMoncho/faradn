//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * A small registry of hand-authored profiles for models the escpos-printer-db
 * capability database does not cover (label printers, and Star's native
 * StarPRNT). {@link PrinterProfile#load(String)} and
 * {@link PrinterProfile#available()} consult it after the database, so these
 * profiles are reachable by name (for example the CLI {@code --profile}) and
 * not
 * only from code.
 * <p>
 * Keys are stable short names, matched case-insensitively. Profiles are
 * immutable
 * value objects, so each lookup builds a fresh instance from its factory.
 */
final class BuiltinProfiles {

  private static final Map<String, Supplier<PrinterProfile>> PROFILES;

  static {
    final Map<String, Supplier<PrinterProfile>> profiles = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    profiles.put("star-tsp143iv", StarProfiles::tsp143iv);
    profiles.put("zd421-zpl-203", ZebraProfiles::zd421Zpl203);
    profiles.put("zd421-zpl-300", ZebraProfiles::zd421Zpl300);
    PROFILES = Collections.unmodifiableMap(profiles);
  }

  private BuiltinProfiles() {
  }

  /**
   * The built-in profile for a name, matched case-insensitively.
   *
   * @param name
   *        the short name (e.g. {@code "zd421-zpl-203"})
   * @return the profile, or empty if no built-in has that name
   */
  static Optional<PrinterProfile> find(String name) {
    if (name == null) {
      return Optional.empty();
    }
    final Supplier<PrinterProfile> factory = PROFILES.get(name.strip());
    return factory == null ? Optional.empty() : Optional.of(factory.get());
  }

  /** The built-in profile names. */
  static Set<String> names() {
    return PROFILES.keySet();
  }
}
