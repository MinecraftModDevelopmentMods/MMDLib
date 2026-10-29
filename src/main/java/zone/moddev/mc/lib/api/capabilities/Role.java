package zone.moddev.mc.lib.api.capabilities;

/**
 * What automation may do with one slot or tank, whatever the face allows. The owning block entity
 * is never limited by roles; it works on the storage directly.
 */
public enum Role {
  /** Automation may insert and extract. */
  STORAGE(true, true),
  /** Automation may insert only, e.g. a furnace's ingredient slot. */
  INPUT(true, false),
  /** Automation may extract only; players cannot place into it either. */
  OUTPUT(false, true),
  /** Only players, through the screen, e.g. upgrade slots. */
  MANUAL(false, false);

  private final boolean insert;
  private final boolean extract;

  Role(boolean insert, boolean extract) {
    this.insert = insert;
    this.extract = extract;
  }

  /** Whether automation may insert into a slot or tank with this role. */
  public boolean canInsert() {
    return insert;
  }

  /** Whether automation may extract from a slot or tank with this role. */
  public boolean canExtract() {
    return extract;
  }
}
