package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;

import zone.moddev.mc.lib.gui.client.NativeSlotRenderer;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.*;
import zone.moddev.mc.lib.gui.runtime.slot.*;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Real native drawing verifies policy opt-in/out independently of reservation support and role. */
final class SlotPolicyClientSmoke {
  private static int phase, frames;
  private static MenuSlots.Entry[] entries;
  private static final int[] overlays = new int[3];
  private static final int[] nativeDraws = new int[3];
  private static final int[] highlights = new int[3];
  private static final String[] vanillaCounts = new String[3];
  private static final NativeSlotRenderer renderer = new NativeSlotRenderer();

  private static class Policy extends ItemSlotElement {
    final int fixture;

    Policy(int fixture) {
      super(
          Common.empty(),
          fixture,
          SlotKind.NORMAL,
          Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty()));
      this.fixture = fixture;
    }

    public boolean renderNative() {
      return fixture != 0;
    }

    public Rect nativeBounds(Rect vanilla) {
      return fixture == 1
          ? new Rect(vanilla.x() + 3, vanilla.y() + 2, 24, 12)
          : super.nativeBounds(vanilla);
    }

    public Rect hitBounds(Rect vanilla) {
      return fixture == 2
          ? new Rect(vanilla.x() - 2, vanilla.y() + 1, 20, 10)
          : super.hitBounds(vanilla);
    }

    public Rect highlightBounds(Rect vanilla) {
      highlights[fixture]++;
      return fixture == 2
          ? new Rect(vanilla.x() - 1, vanilla.y() - 1, 18, 18)
          : super.highlightBounds(vanilla);
    }

    public boolean reservationOverlay(MenuSlots.Entry entry) {
      return fixture != 1;
    }

    public void renderOverlay(
        OverlayPainter painter, ItemStack displayed, SlotPlan plan, String count) {
      overlays[fixture]++;
      super.renderOverlay(painter, displayed, plan, count);
      if (fixture == 2) painter.quantity("custom", false);
    }
  }

  private static class Planned extends Slot implements SlotAdapter {
    Planned(int x) {
      super(new SimpleContainer(new ItemStack(Items.DIAMOND, 5)), 0, x, 24);
    }

    public SlotPlan plan() {
      return new SlotPlan(2, ItemStack.EMPTY);
    }
  }

  static boolean run() {
    if (phase == 0) {
      entries =
          new MenuSlots.Entry[] {
            new MenuSlots.Entry(new Planned(12), new Policy(0), MenuSlots.Role.AUXILIARY),
            new MenuSlots.Entry(new Planned(72), new Policy(1), MenuSlots.Role.MACHINE),
            new MenuSlots.Entry(
                new Slot(new SimpleContainer(new ItemStack(Items.EMERALD, 3)), 0, 132, 24),
                new Policy(2),
                MenuSlots.Role.EQUIPMENT)
          };
      var slots = new MenuSlots();
      var registrar = slots.registrar(slot -> slot.index = slots.entries().size());
      for (var entry : entries) registrar.add(entry.slot(), entry.element(), entry.role());
      for (var entry : entries) {
        var slot = entry.slot();
        var vanilla = new Rect(slot.x, slot.y, 16, 16);
        require(
            renderer
                .hitBounds(slots, slot.x, slot.y, 16, 16)
                .equals(entry.element().hitBounds(vanilla)),
            "Native hit bridge honors element geometry");
      }
      require(
          renderer.hitBounds(slots, 12, 24, 40, 9).equals(new Rect(12, 24, 40, 9)),
          "Non-slot hit tests retain vanilla bounds");
      var ghost = new GhostSlotElement(Common.empty(), 0, entries[0].element().appearance());
      renderer.render(
          null,
          entries[0].slot(),
          new MenuSlots.Entry(entries[0].slot(), ghost, MenuSlots.Role.MACHINE),
          () -> {
            throw new AssertionError("Default ghost draws native contents");
          });
      var customGhost =
          new GhostSlotElement(Common.empty(), 0, ghost.appearance()) {
            public boolean renderNative() {
              return true;
            }
          };
      int[] draws = {0};
      renderer.render(
          null,
          entries[0].slot(),
          new MenuSlots.Entry(entries[0].slot(), customGhost, MenuSlots.Role.MACHINE),
          () -> draws[0]++);
      require(draws[0] == 1, "Ghost subclasses can opt into native drawing");
      phase++;
      return false;
    }
    if (phase == 1) {
      if (frames == 0) return false;
      require(
          overlays[0] > 0 && overlays[1] == 0 && overlays[2] > 0,
          "Slot overlay policy controls actual rendering");
      require(
          "".equals(vanillaCounts[0])
              && "5".equals(vanillaCounts[1])
              && "".equals(vanillaCounts[2]),
          "Disabled overlays retain vanilla quantity rendering");
      require(
          nativeDraws[0] == 0 && nativeDraws[1] > 0 && nativeDraws[2] > 0,
          "Native visibility is independent of slot kind and overlays");
      require(highlights[1] > 0 && highlights[2] > 0, "Native bridge requests highlight geometry");
      var mc = Minecraft.getInstance();
      Screenshot.grab(
          mc.gameDirectory, "slot-overlay-policies.png", mc.getMainRenderTarget(), message -> {});
      entries = null;
      phase++;
    }
    return true;
  }

  static void render(GuiGraphics graphics) {
    if (entries == null) return;
    var font = Minecraft.getInstance().font;
    String[] labels = {"Aux", "Hidden", "Custom"};
    for (int i = 0; i < entries.length; i++) {
      final int index = i;
      var entry = entries[i];
      var slot = entry.slot();
      graphics.fill(slot.x - 2, 8, slot.x + 58, 44, 0xee303030);
      graphics.drawString(font, labels[i], slot.x - 2, 10, 0xffffff);
      renderer.contents(
          graphics,
          font,
          slot,
          entry,
          slot.getItem(),
          Integer.toString(slot.getItem().getCount()),
          count -> {
            vanillaCounts[index] = count;
            graphics.renderItem(slot.getItem(), slot.x, slot.y);
            graphics.renderItemDecorations(font, slot.getItem(), slot.x, slot.y, count);
          });
    }
    graphics.fill(8, 48, 190, 106, 0xee303030);
    String[] nativeLabels = {"No native", "Wide", "Hit area"};
    for (int i = 0; i < entries.length; i++) {
      final int index = i;
      var element = entries[i].element();
      var original = entries[i].slot();
      var slot = new Slot(original.container, 0, original.x, 72);
      var entry = new MenuSlots.Entry(slot, element, entries[i].role());
      graphics.drawString(font, nativeLabels[i], slot.x - 2, 54, 0xffffff);
      var before = new org.joml.Matrix4f(graphics.pose().last().pose());
      var target = element.nativeBounds(new Rect(slot.x, slot.y, 16, 16));
      renderer.render(
          graphics,
          slot,
          entry,
          () -> {
            nativeDraws[index]++;
            var actual =
                graphics
                    .pose()
                    .last()
                    .pose()
                    .transformPosition(slot.x, slot.y, 0, new org.joml.Vector3f());
            var expected =
                before.transformPosition(target.x(), target.y(), 0, new org.joml.Vector3f());
            require(actual.distance(expected) < .01f, "Native drawing applies element offset");
            var edge =
                graphics
                    .pose()
                    .last()
                    .pose()
                    .transformPosition(slot.x + 16, slot.y + 16, 0, new org.joml.Vector3f());
            var expectedEdge =
                before.transformPosition(
                    target.right(), target.bottom(), 0, new org.joml.Vector3f());
            require(
                edge.distance(expectedEdge) < .01f,
                "Native drawing applies independent width/height");
            graphics.renderItem(slot.getItem(), slot.x, slot.y);
          });
      require(before.equals(graphics.pose().last().pose()), "Native renderer restores pose");
      if (i != 0)
        renderer.highlight(
            graphics,
            slot,
            entry,
            0x66ffffff,
            () -> {
              throw new AssertionError("Custom highlight delegated vanilla geometry");
            });
      if (i == 2) {
        var hit = element.hitBounds(new Rect(slot.x, slot.y, 16, 16));
        graphics.renderOutline(hit.x(), hit.y(), hit.width(), hit.height(), 0xff00ff00);
      }
    }
    frames++;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
