package net.nmoncho.faradn.document;

/**
 * One piece of content placed at an absolute {@code (xDots, yDots)} position,
 * measured in dots from the top-left of a {@link Canvas}'s print area.
 */
public record Placement(int xDots, int yDots, Placeable content) {

  public Placement {
    if (xDots < 0 || yDots < 0) {
      throw new IllegalArgumentException("placement position must be >= 0, got (" + xDots + ", " + yDots + ")");
    }
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
  }
}
