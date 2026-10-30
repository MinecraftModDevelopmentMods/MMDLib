package zone.moddev.mc.lib.gui.runtime;

import java.math.BigInteger;
import java.util.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.gui.client.ScreenText;

/** Protocol and snapshot regression checks, including numbers far beyond long or double range. */
public final class GuiProtocolTest {
  public static void main(String[] args) {
    var access = RegistryAccess.EMPTY;
    var huge = BigInteger.TEN.pow(2000).add(BigInteger.valueOf(43));
    var before = new HashMap<Integer, Object>();
    Object[] values = {null, huge, huge.negate(), 42L, 1.25D, true};
    for (int i = 0; i < values.length; i++) before.put(i, ScreenValues.snapshot(values[i]));
    var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
    try {
      ScreenNetwork.Values.CODEC.encode(wire, new ScreenNetwork.Values(73, before));
      var decoded = ScreenNetwork.Values.CODEC.decode(wire);
      require(decoded.menu() == 73 && decoded.delta().size() == values.length, "Wire header");
      before.forEach(
          (k, v) -> require(ScreenValues.equal(v, decoded.delta().get(k)), "Wire value " + k));
      require(wire.readableBytes() == 0, "Trailing bytes");
    } finally {
      wire.release();
    }
    require(
        ScreenValues.changed(before, new HashMap<>(before), false).isEmpty(),
        "Unchanged values sent");
    var next = new HashMap<>(before);
    next.put(1, huge.add(BigInteger.ONE));
    next.put(2, null);
    var delta = ScreenValues.changed(before, next, false);
    require(
        delta.size() == 2 && delta.containsKey(2) && delta.get(2) == null,
        "Delta or unavailable value lost");
    require(
        ScreenValues.changed(Map.of(), before, true).size() == values.length,
        "Initial snapshot incomplete");
    require(
        ScreenText.ratio(huge, huge.multiply(BigInteger.TWO)) == 0.5, "BigInteger ratio overflow");
    require(ScreenText.ratio(huge, BigInteger.ZERO) == 0, "Zero maximum");
    require(
        ScreenText.number(huge, true).equals(huge.toString()), "Detailed number lost precision");
    for (int element : new int[] {0, 4095})
      for (int kind = 0; kind < 4; kind++)
        for (int button = 0; button < 2; button++)
          for (int mods = 0; mods < 8; mods++) {
            var action = new ScreenNetwork.Action(73, element, kind, button, mods, 4095);
            require(
                action.equals(ScreenNetwork.Action.unpack(73, action.pack())),
                "Button channel lost fields");
          }
    try {
      new ScreenNetwork.Action(1, 4096, 0, 0, 0, 0).pack();
      throw new AssertionError("Out-of-range button accepted");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println(
        "GUI wire round trips, arbitrary precision, immutable snapshots, deltas, and button channel"
            + " passed");
  }

  private static void require(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
  }
}
