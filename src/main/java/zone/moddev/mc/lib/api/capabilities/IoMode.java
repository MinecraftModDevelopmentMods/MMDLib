package zone.moddev.mc.lib.api.capabilities;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What one face of a machine lets automation do with one resource (items, fluids or energy). */
public enum IoMode implements StringRepresentable {
  DISABLED(false, false),
  INPUT(true, false),
  OUTPUT(false, true),
  BOTH(true, true);

  public static final Codec<IoMode> CODEC = StringRepresentable.fromEnum(IoMode::values);

  private final boolean insert;
  private final boolean extract;

  IoMode(boolean insert, boolean extract) {
    this.insert = insert;
    this.extract = extract;
  }

  /** Whether automation may insert through a face with this mode. */
  public boolean canInsert() {
    return insert;
  }

  /** Whether automation may extract through a face with this mode. */
  public boolean canExtract() {
    return extract;
  }

  /** The next mode in the order used by side-configuration buttons. */
  public IoMode next() {
    return values()[(ordinal() + 1) % values().length];
  }

  @Override
  public String getSerializedName() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
