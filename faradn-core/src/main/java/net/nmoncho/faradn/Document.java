//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.jsoup.Jsoup;

import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.internal.html.BlockBuilder;

/**
 * A parsed HTML document, the entry point for rendering: {@link #from(String)}
 * parses HTML and {@link #blocks()} projects it into the intermediate
 * representation the renderer consumes.
 * <p>
 * <strong>Thread-safety:</strong> a {@code Document} is effectively immutable
 * after parsing and {@link #blocks()} is a read-only projection, but sharing
 * one
 * across threads is not a supported guarantee. A document is cheap, so prefer
 * one
 * per rendering.
 */
public class Document {

  /**
   * The resolution assumed when translating physical CSS lengths ({@code mm}/
   * {@code cm}) without a printer profile. The print path passes the target
   * profile's real dpi via {@link #blocks(int)}; {@code px} positions are exact
   * regardless of this value.
   */
  private static final int DEFAULT_DPI = 203;

  private final org.jsoup.nodes.Document doc;

  private Document(org.jsoup.nodes.Document doc) {
    this.doc = doc;
  }

  /**
   * Translates this document into the intermediate representation: a flat,
   * reading-order sequence of blocks with fully resolved styles. Renderers
   * consume this instead of the DOM.
   *
   * @return immutable list of blocks
   */
  public List<Block> blocks() {
    return blocks(DEFAULT_DPI);
  }

  /**
   * Translates this document into the intermediate representation, resolving
   * physical CSS lengths ({@code mm}/{@code cm} in page-mode layouts) against the
   * given resolution. The print path supplies the target profile's dpi so
   * positions land on the right dots.
   *
   * @param dpi
   *        the printer resolution in dots per inch
   * @return immutable list of blocks
   */
  public List<Block> blocks(int dpi) {
    return BlockBuilder.build(doc, dpi);
  }

  @Override
  public String toString() {
    return "Document:\n" + doc.toString();
  }

  /**
   * Parses HTML into a Document.
   *
   * @param html
   *        HTML to parse
   * @return a valid Document
   */
  public static Document from(String html) {
    return new Document(Jsoup.parse(html));
  }

  /**
   * Parses an HTML file into a Document.
   *
   * @param f
   *        the HTML file to parse
   * @return a valid Document
   * @throws PrintingException
   *         if the file cannot be read
   */
  public static Document from(File f) {
    try {
      return from(Files.readString(f.toPath()));
    } catch (IOException ex) {
      throw new PrintingException("Failed to read document from [" + f.toPath() + "]", ex);
    }
  }

  /**
   * Parses HTML into a Document.
   *
   * @param html
   *        HTML as a string to parse
   * @param baseUri
   *        Used to resolve relative URLs to absolute URLs
   * @return a valid Document
   */
  public static Document from(String html, String baseUri) {
    return new Document(Jsoup.parse(html, baseUri));
  }

}
