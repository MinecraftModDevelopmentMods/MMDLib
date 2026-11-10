package zone.moddev.mc.companion.test;

import java.util.*;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.value.*;

/** Element-owned popout presentation using inherited production drawing and drawer behavior. */
final class PopoutClientSmoke {
  private static int stage, attempts;
  private static boolean compact;

  private static class CustomPopout extends PopoutElement {
    CustomPopout(PopoutElement original) {
      super(
          withoutGetter(original.common()),
          original.kind(),
          original.icon(),
          original.edge(),
          original.text());
    }

    public int colour() {
      return 0x699fc4;
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
      return Set.of(ValueTypes.WHOLE);
    }

    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
      return Optional.of(new ElementSnapshot(7, null));
    }

    public List<Component> content(Object value, Function<Text, Component> resolveText) {
      var rows = new ArrayList<Component>();
      rows.add(Component.literal("Load: " + value).withStyle(ChatFormatting.BOLD));
      if (!compact)
        for (int i = 0; i < 60; i++)
          rows.add(
              Component.literal(
                  "Paragraph " + i + ": this long line must wrap inside the popout body."));
      return rows;
    }
  }

  private static Common withoutGetter(Common c) {
    return new Common(
        c.id(),
        Optional.empty(),
        c.max(),
        c.tooltip(),
        c.tooltipShift(),
        c.clicks(),
        c.onHover(),
        c.hoverShows(),
        c.jeiRecipes(),
        c.offsetX(),
        c.offsetY(),
        c.line(),
        c.interaction());
  }

  static void install() {
    var id = ResourceLocation.parse("mmdlib_companion:crusher");
    var old = Screens.get(id).orElseThrow().screen();
    var tabs = new ArrayList<>(old.tabs());
    var info = (PopoutElement) old.find("info").orElseThrow();
    tabs.set(tabs.indexOf(info), new CustomPopout(info));
    var model =
        new Screen(
            old.id(),
            old.source(),
            old.title(),
            old.blockEntity(),
            old.playerInventory(),
            old.body(),
            tabs,
            old.origins(),
            old.implicitElements());
    var screens = new HashMap<>(Screens.all());
    screens.put(id, ScreenBinder.bind(model, PopoutClientSmoke.class.getClassLoader()));
    Screens.install(screens);
  }

  static boolean run(MachineScreen screen) {
    if (++attempts > 40) throw new AssertionError("Popout smoke stuck at " + stage);
    var element = screen.getMenu().bound.screen().find("info").orElseThrow();
    var tab = screen.bounds(element);
    var drawer =
        screen.drawers().entries().stream()
            .filter(d -> d.definition().element().orElse(null) == element)
            .findFirst()
            .orElseThrow();
    if (stage == 0) {
      if (!java.math.BigInteger.valueOf(7).equals(screen.getMenu().value(element, false)))
        return false;
      if (drawer.colour() != 0x699fc4) throw new AssertionError("Element colour was ignored");
      drawer.close();
      screen.mouseClicked(tab.x() + 5, tab.y() + 5, 0);
    } else if (stage == 1) {
      var presentation =
          screen
              .presentation()
              .drawerPresentation(element, PopoutRenderer.Presentation.class)
              .orElseThrow();
      var first = new StringBuilder();
      presentation
          .lines()
          .getFirst()
          .accept(
              (index, style, codepoint) -> {
                if (!style.isBold()) throw new AssertionError("Content style was discarded");
                first.appendCodePoint(codepoint);
                return true;
              });
      if (!first.toString().equals("Load: 7") || presentation.lines().size() <= 61)
        throw new AssertionError("Custom numeric content was not consumed and wrapped");
      capture("popout-custom-content.png");
      var body = presentation.body();
      if (!screen.mouseScrolled(body.x() + 8, body.y() + 8, 0, -5))
        throw new AssertionError("Popout did not accept scrolling");
    } else if (stage == 2) {
      var presentation =
          screen
              .presentation()
              .drawerPresentation(element, PopoutRenderer.Presentation.class)
              .orElseThrow();
      if (presentation.scroll() <= 0) throw new AssertionError("Popout did not scroll");
      compact = true;
    } else if (stage == 3) {
      var presentation =
          screen
              .presentation()
              .drawerPresentation(element, PopoutRenderer.Presentation.class)
              .orElseThrow();
      if (presentation.scroll() != 0 || presentation.lines().size() != 1)
        throw new AssertionError("Popout scroll did not clamp after content shrank");
      capture("popout-custom-compact.png");
      screen.mouseClicked(tab.x() + 5, tab.y() + 5, 0);
    } else {
      if (screen
          .presentation()
          .drawerPresentation(element, PopoutRenderer.Presentation.class)
          .isPresent()) throw new AssertionError("Popout did not close");
      return true;
    }
    stage++;
    return false;
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
  }
}
