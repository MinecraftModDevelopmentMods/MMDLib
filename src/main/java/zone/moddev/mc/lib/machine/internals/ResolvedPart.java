package zone.moddev.mc.lib.machine.internals;

import java.util.List;
import java.util.OptionalInt;

import zone.moddev.mc.lib.machine.stat.StatModifier;

/**
 * A part file applied to one item, with every amount worked out from its material.
 *
 * @param durability wear points before it breaks, at least 1; empty if it never wears
 */
public record ResolvedPart(String socket, List<StatModifier> modifiers, OptionalInt durability) {
  /** Copies the modifier list. */
  public ResolvedPart {
    modifiers = List.copyOf(modifiers);
  }
}
