package zone.moddev.mc.lib.gui.model;

/** What a slot is for. */
public enum SlotKind {
  /** An ordinary slot. */
  NORMAL,
  /** An upgrade slot: one item, gold tint, ghost outline. */
  UPGRADE,
  /** A JEI ghost slot: sets the item the slot expects, never holds real items. */
  GHOST
}
