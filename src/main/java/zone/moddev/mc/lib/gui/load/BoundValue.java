package zone.moddev.mc.lib.gui.load;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.Objects;

import net.minecraft.core.RegistryAccess;

import zone.moddev.mc.lib.gui.model.ValueRef;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * A getter resolved at load time against its registered value contract.
 *
 * @param ref the reference as written
 * @param kind the registered type responsible for normalizing and copying the result
 * @param handle {@code (Object blockEntity) -> Object}
 */
public record BoundValue(ValueRef ref, ValueType<?> kind, MethodHandle handle) {
  public BoundValue {
    Objects.requireNonNull(ref);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(handle);
  }

  /** Calls the getter; failures are logged once by the snapshot collector. */
  public Object read(Object blockEntity) throws Throwable {
    return handle.invoke(blockEntity);
  }

  /** Copies through the declared getter contract, preserving null as unavailable. */
  public Object snapshot(Object blockEntity, RegistryAccess registries) throws Throwable {
    var value = read(blockEntity);
    return value == null ? null : kind.snapshot(value, registries);
  }

  /** Converts a supported whole number without losing precision. */
  public static BigInteger whole(Object value) {
    return ValueTypes.whole(value);
  }
}
