package zone.moddev.mc.lib.gui.model;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * The attributes every element can take; see the table in {@code docs/gui-screens.md}.
 *
 * @param id names the element so others can refer to it; unique within a screen
 * @param value the getter the element shows
 * @param max the getter for the element's maximum
 * @param tooltip the tooltip text
 * @param tooltipShift the detailed tooltip shown while Shift is held
 * @param clicks click handler IDs
 * @param onHover a client-only handler run when the mouse enters and leaves the element
 * @param hoverShows the ID of an element shown while hovering
 * @param jeiRecipes JEI recipe categories opened by clicking, when JEI is installed
 * @param offsetX a pixel nudge applied after layout
 * @param offsetY a pixel nudge applied after layout
 * @param line the line in the screen file, for error messages
 */
public record Common(
    Optional<String> id,
    Optional<ValueRef> value,
    Optional<ValueRef> max,
    Optional<Text> tooltip,
    Optional<Text> tooltipShift,
    Clicks clicks,
    Optional<ResourceLocation> onHover,
    Optional<String> hoverShows,
    List<ResourceLocation> jeiRecipes,
    int offsetX,
    int offsetY,
    int line) {
  /** Copies the recipe list. */
  public Common {
    jeiRecipes = List.copyOf(jeiRecipes);
  }

  /**
   * Server-side click handler IDs, from extension files.
   *
   * @param click left click
   * @param rightClick right click
   * @param shiftClick left click with Shift held; used instead of {@code click}
   * @param shiftRightClick right click with Shift held; used instead of {@code rightClick}
   */
  public record Clicks(
      Optional<ResourceLocation> click,
      Optional<ResourceLocation> rightClick,
      Optional<ResourceLocation> shiftClick,
      Optional<ResourceLocation> shiftRightClick) {
    /** No handlers. */
    public static final Clicks NONE =
        new Clicks(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    /** Whether any handler is set. */
    public boolean any() {
      return click.isPresent()
          || rightClick.isPresent()
          || shiftClick.isPresent()
          || shiftRightClick.isPresent();
    }
  }
}
