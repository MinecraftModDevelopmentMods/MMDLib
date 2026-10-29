package zone.moddev.mc.lib.gui.load;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;

import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.model.ValueRef;

/**
 * A {@code value} or {@code max} getter resolved at load: a method handle taking the block entity
 * and returning the value, and the kind of value it returns. Handles are bound to an instance only
 * when a screen opens, on the server.
 *
 * @param ref the reference as written
 * @param kind what the getter returns
 * @param handle {@code (Object blockEntity) -> Object}
 */
public record BoundValue(ValueRef ref, Kind kind, MethodHandle handle) {
  /** Checks the fields. */
  public BoundValue {
    Objects.requireNonNull(ref);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(handle);
  }

  /** The kinds of value a getter can return. */
  public enum Kind {
    /**
     * {@code byte}, {@code short}, {@code int}, {@code long}, their boxes, or {@code BigInteger}.
     */
    WHOLE,
    /** {@code float}, {@code double} or their boxes. */
    DECIMAL,
    /** A declared {@code Number}: whole or decimal, decided when read. */
    NUMBER,
    /** {@code boolean} or {@code Boolean}. */
    BOOLEAN,
    /** A {@code FluidStack}: a fluid and its amount. */
    FLUID,
    /** A text {@code Component}. */
    TEXT,
    /** A collection of text {@code Component}s, checked when read. */
    TEXT_LIST;

    /** Whether this kind is numeric. */
    public boolean numeric() {
      return this == WHOLE || this == DECIMAL || this == NUMBER;
    }

    /** The kind of {@code type}, or null if a getter may not return it. */
    public static Kind of(Class<?> type) {
      if (type == byte.class
          || type == short.class
          || type == int.class
          || type == long.class
          || type == Byte.class
          || type == Short.class
          || type == Integer.class
          || type == Long.class
          || type == BigInteger.class) return WHOLE;
      if (type == float.class
          || type == double.class
          || type == Float.class
          || type == Double.class) return DECIMAL;
      if (type == Number.class) return NUMBER;
      if (type == boolean.class || type == Boolean.class) return BOOLEAN;
      if (type == FluidStack.class) return FLUID;
      if (Component.class.isAssignableFrom(type)) return TEXT;
      if (Collection.class.isAssignableFrom(type)) return TEXT_LIST;
      return null;
    }
  }

  /**
   * Calls the getter on {@code blockEntity}.
   *
   * @throws Throwable whatever the getter throws; callers log it once and show the value as
   *     unavailable
   */
  public Object read(Object blockEntity) throws Throwable {
    return handle.invoke(blockEntity);
  }

  /**
   * A whole-number value as a {@code BigInteger}, so values of any size display correctly.
   *
   * @throws IllegalStateException if the value is not a whole number
   */
  public static BigInteger whole(Object value) {
    return switch (value) {
      case BigInteger big -> big;
      case Long l -> BigInteger.valueOf(l);
      case Integer i -> BigInteger.valueOf(i);
      case Short s -> BigInteger.valueOf(s);
      case Byte b -> BigInteger.valueOf(b);
      default -> throw new IllegalStateException("Not a whole number: " + value);
    };
  }
}
