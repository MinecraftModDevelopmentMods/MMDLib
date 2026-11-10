package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.NativeSlotRenderer;

/** Exercises the real screen's vanilla tooltip bridge for occupied and empty reserved slots. */
final class SlotTooltipClientSmoke {
  private SlotTooltipClientSmoke() {}

  static void check(MachineScreen screen, int index, boolean filled) {
    var menu = screen.getMenu();
    var entry = menu.nativeSlots().entry(index);
    var slot = entry.slot();
    var element = entry.element();
    var bridge = new NativeSlotRenderer();
    require(
        bridge.usesItemTooltip(menu.nativeSlots(), slot, element) == filled,
        "Only filled slots route their lines through vanilla's tooltip");
    var other = menu.nativeSlots().entry(index == 0 ? 1 : 0).element();
    require(
        !bridge.usesItemTooltip(menu.nativeSlots(), slot, other),
        "A different element must keep its own tooltip even over a filled slot");
    List<Component> vanilla = List.of(Component.literal("vanilla"));
    require(
        bridge.itemTooltip(
                menu.nativeSlots(),
                slot,
                other,
                vanilla,
                ignored -> {
                  throw new AssertionError("Unrelated tooltip was evaluated");
                })
            == vanilla,
        "Unrelated elements leave the native item tooltip alone");
    require(!bridge.usesItemTooltip(menu.nativeSlots(), null, element), "No hovered native slot");

    var bounds = screen.bounds(element);
    int x = bounds.x() + bounds.width() / 2;
    int y = bounds.y() + bounds.height() / 2;
    var mc = Minecraft.getInstance();
    var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
    screen.render(graphics, x, y, 0);
    graphics.flush();
    var expected = screen.tooltipLines(element, x, y);
    require(!expected.isEmpty(), "Reserved slot has element tooltip lines");
    if (!filled) return; // Empty slots render those lines as the element tooltip above.
    policies(expected);
    try {
      var method =
          MachineScreen.class.getDeclaredMethod("getTooltipFromContainerItem", ItemStack.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      var actual = (List<Component>) method.invoke(screen, slot.getItem());
      require(actual.size() > expected.size(), "Vanilla item tooltip is retained");
      require(
          actual.subList(actual.size() - expected.size(), actual.size()).equals(expected),
          "Slot lines append to the actual screen's item tooltip");
      for (var line : expected)
        require(
            actual.stream().filter(line::equals).count() == 1, "Slot tooltip line appears once");
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Cannot inspect native item tooltip", failure);
    }
  }

  private static void policies(List<Component> parents) {
    if (Boolean.getBoolean("mmdlib.tooltipSmokeOnly"))
      require(
          parents.stream().anyMatch(row -> row.getString().equals("server outer")),
          "Real ancestor rows reach the native bridge");
    for (int policy = 0; policy < 3; policy++) {
      final int mode = policy;
      var element =
          new zone.moddev.mc.lib.gui.model.elements.ItemSlotElement(
              zone.moddev.mc.lib.gui.model.Common.empty(),
              0,
              zone.moddev.mc.lib.gui.model.SlotKind.NORMAL,
              zone.moddev.mc.lib.gui.model.elements.SlotElement.Appearance.standard(
                  zone.moddev.mc.lib.gui.model.SlotSize.NORMAL,
                  java.util.Optional.empty(),
                  java.util.Optional.empty())) {
            public List<Component> nativeTooltip(ItemStack stack, List<Component> vanilla) {
              stack.setCount(2);
              ((net.minecraft.network.chat.MutableComponent) vanilla.getFirst()).append(" edited");
              return switch (mode) {
                case 0 -> vanilla;
                case 1 -> List.of(Component.literal("replacement"));
                default -> List.of();
              };
            }
          };
      var slots = new zone.moddev.mc.lib.gui.runtime.slot.MenuSlots();
      var slot =
          new net.minecraft.world.inventory.Slot(
              new net.minecraft.world.SimpleContainer(
                  new ItemStack(net.minecraft.world.item.Items.DIAMOND)),
              0,
              0,
              0);
      slots.registrar(value -> value.index = 0).add(slot, element, element.role());
      List<Component> vanilla = List.of(Component.literal("vanilla"));
      var result =
          new NativeSlotRenderer().itemTooltip(slots, slot, element, vanilla, ignored -> parents);
      int nativeRows = mode == 2 ? 0 : 1;
      require(
          result.size() == nativeRows + parents.size(),
          "Native tooltip policy controls only its own rows");
      require(
          result.subList(nativeRows, result.size()).equals(parents),
          "All element and parent rows survive replacement/suppression");
      if (nativeRows != 0)
        require(
            result.getFirst().getString().equals(mode == 0 ? "vanilla edited" : "replacement"),
            "Native tooltip override applied");
      require(
          slot.getItem().getCount() == 1 && vanilla.getFirst().getString().equals("vanilla"),
          "Native hook receives detached inputs");
      ((net.minecraft.network.chat.MutableComponent) result.getLast()).append(" changed");
      require(
          !parents.getLast().getString().endsWith(" changed"),
          "Composed tooltip rows are detached");
    }
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
