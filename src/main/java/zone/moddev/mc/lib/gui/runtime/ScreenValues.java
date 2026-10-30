package zone.moddev.mc.lib.gui.runtime;

import java.math.BigInteger;
import java.util.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.load.BoundValue;

/** Immutable wire snapshots; whole numbers never pass through a long or a double. */
public final class ScreenValues {
  private ScreenValues() {}

  public static Object snapshot(Object value, net.minecraft.core.RegistryAccess registries) {
    if (value instanceof Component text) return copyText(text, registries);
    if (value instanceof Collection<?> list)
      return list.stream().map(v -> copyText((Component) v, registries)).toList();
    return snapshot(value);
  }

  private static Component copyText(Component text, net.minecraft.core.RegistryAccess registries) {
    var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
    try {
      ComponentSerialization.STREAM_CODEC.encode(buffer, text);
      return ComponentSerialization.STREAM_CODEC.decode(buffer);
    } finally {
      buffer.release();
    }
  }

  public static Object snapshot(Object value) {
    if (value == null) return null;
    if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite GUI number");
      return number;
    }
    if (value instanceof Number) return BoundValue.whole(value);
    if (value instanceof Boolean) return value;
    if (value instanceof FluidStack fluid) return fluid.copy();
    throw new IllegalArgumentException("Unsupported GUI value: " + value.getClass());
  }

  public static boolean equal(Object a, Object b) {
    if (a instanceof FluidStack x && b instanceof FluidStack y) return FluidStack.matches(x, y);
    return Objects.equals(a, b);
  }

  public static Map<Integer, Object> changed(
      Map<Integer, Object> previous, Map<Integer, Object> current, boolean initial) {
    var delta = new HashMap<Integer, Object>();
    current.forEach(
        (key, value) -> {
          if (initial || !previous.containsKey(key) || !equal(previous.get(key), value))
            delta.put(key, value);
        });
    return delta;
  }

  public static void write(RegistryFriendlyByteBuf buf, Object value) {
    switch (value) {
      case null -> buf.writeByte(0);
      case BigInteger n -> {
        buf.writeByte(1);
        buf.writeByteArray(n.toByteArray());
      }
      case Double n -> {
        buf.writeByte(2);
        buf.writeDouble(n);
      }
      case Boolean b -> {
        buf.writeByte(3);
        buf.writeBoolean(b);
      }
      case FluidStack f -> {
        buf.writeByte(4);
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, f);
      }
      case Component c -> {
        buf.writeByte(5);
        ComponentSerialization.STREAM_CODEC.encode(buf, c);
      }
      case List<?> list -> {
        buf.writeByte(6);
        buf.writeVarInt(list.size());
        for (Object c : list) ComponentSerialization.STREAM_CODEC.encode(buf, (Component) c);
      }
      default -> throw new IllegalArgumentException("Unsupported GUI wire value");
    }
  }

  public static Object read(RegistryFriendlyByteBuf buf) {
    return switch (buf.readUnsignedByte()) {
      case 0 -> null;
      case 1 -> new BigInteger(buf.readByteArray(1048576));
      case 2 -> buf.readDouble();
      case 3 -> buf.readBoolean();
      case 4 -> FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
      case 5 -> ComponentSerialization.STREAM_CODEC.decode(buf);
      case 6 -> {
        int count = buf.readVarInt();
        if (count < 0 || count > 4096)
          throw new IllegalArgumentException("GUI text list too large");
        var list = new ArrayList<Component>(count);
        for (int i = 0; i < count; i++) list.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        yield List.copyOf(list);
      }
      default -> throw new IllegalArgumentException("Unknown GUI wire value");
    };
  }
}
