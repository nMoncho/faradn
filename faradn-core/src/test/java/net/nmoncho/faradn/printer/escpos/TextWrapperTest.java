package net.nmoncho.faradn.printer.escpos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.TextRun;

public class TextWrapperTest {

  private static final ComputedStyle PLAIN = ComputedStyle.INITIAL;

  @Test
  void shortTextStaysOnOneLine() {
    List<List<TextRun>> lines = TextWrapper.wrap(List.of(run("hello world")), 42);

    assertEquals(1, lines.size());
    assertEquals("hello world", text(lines.get(0)));
  }

  @Test
  void breaksAtSpaces() {
    List<List<TextRun>> lines = TextWrapper.wrap(List.of(run("aaa bbb ccc")), 7);

    assertEquals(2, lines.size());
    assertEquals("aaa bbb", text(lines.get(0)));
    assertEquals("ccc", text(lines.get(1)));
  }

  @Test
  void hardSplitsAWordLongerThanTheLine() {
    List<List<TextRun>> lines = TextWrapper.wrap(List.of(run("abcdefgh")), 3);

    assertEquals(3, lines.size());
    assertEquals("abc", text(lines.get(0)));
    assertEquals("def", text(lines.get(1)));
    assertEquals("gh", text(lines.get(2)));
  }

  @Test
  void doubleWidthCharactersCostTwoColumns() {
    ComputedStyle wide = new ComputedStyle(false, false, 2, 1, Alignment.LEFT, false);

    List<List<TextRun>> lines = TextWrapper.wrap(List.of(new TextRun("ab", wide)), 3);

    assertEquals(2, lines.size());
    assertEquals("a", text(lines.get(0)));
    assertEquals("b", text(lines.get(1)));
  }

  @Test
  void preservesStyleSegmentsWithinALine() {
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    List<List<TextRun>> lines = TextWrapper.wrap(List.of(run("a"), new TextRun("b", bold)), 42);

    assertEquals(1, lines.size());
    List<TextRun> line = lines.get(0);
    assertEquals(2, line.size());
    assertEquals("a", line.get(0).text());
    assertEquals(PLAIN, line.get(0).style());
    assertEquals("b", line.get(1).text());
    assertEquals(bold, line.get(1).style());
  }

  private static TextRun run(String text) {
    return new TextRun(text, PLAIN);
  }

  private static String text(List<TextRun> line) {
    StringBuilder sb = new StringBuilder();
    for (TextRun run : line) {
      sb.append(run.text());
    }
    return sb.toString();
  }
}
