package net.nmoncho.faradn.document;

import java.util.ArrayList;
import java.util.List;

/**
 * A bounded page-mode print area with absolutely-positioned children. The
 * renderer draws it via ESC/POS page mode ({@code ESC L … FF}): every child is
 * placed at an exact dot position within a fixed {@code widthDots × heightDots}
 * area, then the whole area is printed at once.
 * <p>
 * Suited to bounded regions (headers, coupons, labels) whose height is known up
 * front; content beyond the area is clipped by the printer.
 */
public record Canvas(int widthDots, int heightDots, Direction direction, List<Placement> placements)
    implements
      Block {

  /** Reading direction of the whole area, which rotates it by 0/90/180/270°. */
  public enum Direction {
    NORMAL, ROTATE_90_CW, ROTATE_180, ROTATE_90_CCW
  }

  public Canvas {
    if (widthDots <= 0 || heightDots <= 0) {
      throw new IllegalArgumentException("canvas size must be positive, got " + widthDots + "x" + heightDots);
    }
    if (direction == null) {
      throw new IllegalArgumentException("direction must not be null");
    }
    if (placements == null) {
      throw new IllegalArgumentException("placements must not be null");
    }
    placements = List.copyOf(placements);
  }

  /**
   * Starts a builder for a {@code widthDots × heightDots} canvas (NORMAL
   * direction).
   */
  public static Builder of(int widthDots, int heightDots) {
    return new Builder(widthDots, heightDots);
  }

  /** Fluent builder: {@code Canvas.of(w, h).place(x, y, content)….build()}. */
  public static final class Builder {
    private final int widthDots;
    private final int heightDots;
    private Direction direction = Direction.NORMAL;
    private final List<Placement> placements = new ArrayList<>();

    private Builder(int widthDots, int heightDots) {
      this.widthDots = widthDots;
      this.heightDots = heightDots;
    }

    public Builder direction(Direction direction) {
      this.direction = direction;
      return this;
    }

    public Builder place(int xDots, int yDots, Placeable content) {
      placements.add(new Placement(xDots, yDots, content));
      return this;
    }

    public Canvas build() {
      return new Canvas(widthDots, heightDots, direction, placements);
    }
  }
}
