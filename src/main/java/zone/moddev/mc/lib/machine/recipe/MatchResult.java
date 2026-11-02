package zone.moddev.mc.lib.machine.recipe;

import javax.annotation.Nullable;

/**
 * Whether a recipe can run in a machine, and if not, where it failed, for screens and debugging.
 *
 * @param group the group key that failed; null when {@link #ok()}
 * @param index the position in that group's input or output list; -1 when not about one entry
 */
public record MatchResult(Reason reason, @Nullable String group, int index) {
  /** The recipe can run. */
  public static final MatchResult OK = new MatchResult(Reason.OK, null, -1);

  /** Why a recipe can or can't run. */
  public enum Reason {
    /** Everything is present and every product has room. */
    OK,
    /** The recipe names a group the machine doesn't have. */
    NO_GROUP,
    /** The group holds a different kind: items asked of a fluid group, and so on. */
    WRONG_KIND,
    /** The group doesn't hold enough of an input. */
    MISSING_INPUT,
    /** The group has no room for a product. */
    NO_ROOM
  }

  /** Whether the recipe can run. */
  public boolean ok() {
    return reason == Reason.OK;
  }
}
