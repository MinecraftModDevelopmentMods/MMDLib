package zone.moddev.mc.lib.gui.sync;

import java.util.Arrays;

/** Immutable encoded snapshot; equality compares payload contents for ordinary delta transport. */
public final class ElementStateValue {
  private final byte[] data;

  public ElementStateValue(byte[] data) {
    if (data.length > ElementSync.MAX_BYTES)
      throw new IllegalArgumentException("Element state is too large");
    this.data = data.clone();
  }

  public byte[] data() {
    return data.clone();
  }

  public int size() {
    return data.length;
  }

  public boolean equals(Object other) {
    return other instanceof ElementStateValue value && Arrays.equals(data, value.data);
  }

  public int hashCode() {
    return Arrays.hashCode(data);
  }
}
