//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Controls how {@link Image} resolves an {@code <img src>} URL: which URL
 * schemes may be fetched, and the limits on those fetches. This is the main
 * defense against server-side request forgery (SSRF), where untrusted HTML
 * makes the process fetch a URL of the attacker's choosing (an internal host,
 * a cloud metadata endpoint, a local {@code file:}).
 * <p>
 * {@code data:} URIs are always decoded and are never governed by this policy.
 * The default is {@link #DATA_URIS_ONLY}, which fetches nothing; widen it with
 * {@link Image#policy(ImagePolicy)} only when the HTML is trusted. The HTTP
 * server keeps the default unless {@code --allow-remote-images} is passed.
 *
 * @param allowedUrlSchemes
 *        the URL schemes that may be fetched (e.g. {@code file}, {@code http})
 * @param connectTimeoutMillis
 *        connect timeout for network fetches, or 0 for the platform default
 * @param readTimeoutMillis
 *        read timeout for network fetches, or 0 for the platform default
 * @param maxBytes
 *        the largest image the fetch will read before failing
 */
public record ImagePolicy(Set<String> allowedUrlSchemes, int connectTimeoutMillis, int readTimeoutMillis,
    long maxBytes) {

  private static final long DEFAULT_MAX_BYTES = 16L * 1024 * 1024;

  /**
   * Fetch nothing: only {@code data:} URIs are decoded. The safe default for
   * untrusted input.
   */
  public static final ImagePolicy DATA_URIS_ONLY = new ImagePolicy(Set.of(), 0, 0, DEFAULT_MAX_BYTES);

  /** Also read local {@code file:} images, but nothing over the network. */
  public static final ImagePolicy LOCAL_FILES = new ImagePolicy(Set.of("file"), 0, 0, DEFAULT_MAX_BYTES);

  public ImagePolicy {
    if (allowedUrlSchemes == null) {
      throw new IllegalArgumentException("allowedUrlSchemes must not be null");
    }
    if (maxBytes < 1) {
      throw new IllegalArgumentException("maxBytes must be positive, got " + maxBytes);
    }
    final Set<String> normalized = new HashSet<>();
    for (String scheme : allowedUrlSchemes) {
      normalized.add(scheme.toLowerCase(Locale.ROOT));
    }
    allowedUrlSchemes = Set.copyOf(normalized);
  }

  /** Local files plus {@code http}/{@code https}, with the given fetch limits. */
  public static ImagePolicy allowingRemote(int connectTimeoutMillis, int readTimeoutMillis, long maxBytes) {
    return new ImagePolicy(Set.of("file", "http", "https"), connectTimeoutMillis, readTimeoutMillis, maxBytes);
  }

  /**
   * Local files plus {@code http}/{@code https} with conservative defaults
   * (3&nbsp;s / 5&nbsp;s, 8&nbsp;MB).
   */
  public static ImagePolicy allowingRemote() {
    return allowingRemote(3_000, 5_000, 8L * 1024 * 1024);
  }

  /** Whether a URL scheme (any case) may be fetched under this policy. */
  boolean allows(String scheme) {
    return scheme != null && allowedUrlSchemes.contains(scheme.toLowerCase(Locale.ROOT));
  }
}
