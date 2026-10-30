package zone.moddev.mc.lib.common.types;

import java.util.Locale;

/**
 * The kinds of file the older {@link zone.moddev.mc.lib.util.DataLoaders} loading reads; each maps
 * to a directory in {@link Constants.PathBits}.
 */
public enum LocationType {
  /** No location; nothing is loaded for it. */
  NONE("NONE"),
  /** Material files. */
  MATERIAL("MATERIAL"),
  /** Block files. */
  BLOCK("BLOCK"),
  /** Item files. */
  ITEM("ITEM"),
  /** Config files. */
  CONFIG("CONFIG");

  private final String myTypeName;

  LocationType(String typeName) {
    myTypeName = typeName;
  }

  @Override
  public String toString() {
    return "LocationType{" + "myTypeName='" + myTypeName.toLowerCase(Locale.ENGLISH) + '\'' + '}';
  }

  /** The lower-case directory name for this kind. */
  public String getMyTypeName() {
    return myTypeName.toLowerCase(Locale.ENGLISH);
  }
}
