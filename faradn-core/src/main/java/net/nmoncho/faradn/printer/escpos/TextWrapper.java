package net.nmoncho.faradn.printer.escpos;

import java.util.ArrayList;
import java.util.List;

import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.TextRun;

/**
 * Greedy word-wrapping over styled text runs. Breaks a paragraph's runs into
 * visual lines that fit a column budget, preferring to break at spaces and only
 * hard-splitting a word longer than a whole line. A character's width is its
 * style's {@code widthMultiple}, so a double-width glyph costs two columns.
 * <p>
 * Each returned line is itself a list of runs (segments), with adjacent
 * same-style characters merged, ready for the renderer to diff and emit.
 */
final class TextWrapper {

  private record StyledChar(char ch, ComputedStyle style) {
  }

  private TextWrapper() {
  }

  static List<List<TextRun>> wrap(List<TextRun> runs, int maxColumns) {
    final List<StyledChar> chars = new ArrayList<>();
    for (TextRun run : runs) {
      for (int i = 0; i < run.text().length(); i++) {
        chars.add(new StyledChar(run.text().charAt(i), run.style()));
      }
    }

    final List<List<TextRun>> lines = new ArrayList<>();
    int lineStart = 0;
    int width = 0;
    int lastSpace = -1;
    int i = 0;
    while (i < chars.size()) {
      final StyledChar sc = chars.get(i);
      final int w = sc.style().widthMultiple();
      if (sc.ch() == ' ') {
        lastSpace = i;
      }
      if (width + w > maxColumns && i > lineStart) {
        final int breakAt;
        final int nextStart;
        if (lastSpace > lineStart) {
          breakAt = lastSpace; // exclusive: drop the breaking space
          nextStart = lastSpace + 1;
        } else {
          breakAt = i; // long word: hard split before the current char
          nextStart = i;
        }
        lines.add(segments(chars, lineStart, breakAt));
        lineStart = nextStart;
        i = nextStart;
        width = 0;
        lastSpace = -1;
        continue;
      }
      width += w;
      i++;
    }
    if (lineStart < chars.size()) {
      lines.add(segments(chars, lineStart, chars.size()));
    }
    return lines;
  }

  private static List<TextRun> segments(List<StyledChar> chars, int from, int to) {
    final List<TextRun> segs = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();
    ComputedStyle current = null;
    for (int k = from; k < to; k++) {
      final StyledChar sc = chars.get(k);
      if (current != null && !sc.style().equals(current)) {
        segs.add(new TextRun(sb.toString(), current));
        sb.setLength(0);
      }
      current = sc.style();
      sb.append(sc.ch());
    }
    if (sb.length() > 0) {
      segs.add(new TextRun(sb.toString(), current));
    }
    return segs;
  }
}
