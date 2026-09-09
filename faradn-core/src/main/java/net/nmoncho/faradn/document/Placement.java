package net.nmoncho.faradn.document;

/**
 * One piece of content placed at an absolute {@code (xDots, yDots)} position,
 * measured in dots from the top-left of a {@link Canvas}'s print area.
 * <p>
 * An optional {@code rotation} rotates this one placement independently of the
 * canvas ({@code transform: rotate(…)} on the child): the renderer re-issues
 * {@code ESC T} for it and restores the canvas direction afterwards, so a
 * caption can run down the side of an otherwise-upright region. A {@code null}
 * rotation (the default) inherits the canvas's own {@link Canvas.Direction}.
 */
public record Placement(int xDots, int yDots, Placeable content, Canvas.Direction rotation) {

  public Placement {
    if (xDots < 0 || yDots < 0) {
      throw new IllegalArgumentException("placement position must be >= 0, got (" + xDots + ", " + yDots + ")");
    }
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    // rotation may be null: inherit the canvas direction (no per-placement ESC T).
  }

  /**
   * A placement that inherits the canvas direction (no per-placement rotation).
   */
  public Placement(int xDots, int yDots, Placeable content) {
    this(xDots, yDots, content, null);
  }
}
