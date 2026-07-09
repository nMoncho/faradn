package io.nmoncho.faradn;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

public class Document {

  private final org.jsoup.nodes.Document doc;

  private Document(org.jsoup.nodes.Document doc) {
    this.doc = doc;
  }

  public org.jsoup.nodes.Document getDoc() {
    return doc;
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
