package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.model.elements.*;
import zone.moddev.mc.lib.gui.sync.*;

/** A focused real-screen fixture for external element types and the shared drawing passes. */
final class ElementRendererClientSmoke {
  private static final List<ElementRenderers.Pass> passes = new ArrayList<>();
  private static final Set<String> widgets = new HashSet<>();
  private static int playerSlots, machineSlots, playerOverlays, popoutBodies, phase;

  record ModElement(Common common) implements SynchronizedElement {
    public List<ElementSync<?>> synchronizedValues() {
      return List.of(ElementBehaviorClientSmoke.PROBE_STATE);
    }

    public List<? extends zone.moddev.mc.lib.gui.ElementAction<?>> actions() {
      return List.of(ElementBehaviorClientSmoke.PROBE);
    }

    public int[] measure(TextMeasure text) {
      return new int[] {30, 14};
    }
  }

  private record ModButton(Common common) implements ButtonControl {
    public boolean flat() {
      return true;
    }

    public Optional<Text> text() {
      return Optional.empty();
    }

    public Optional<ResourceLocation> iconFor(Object value) {
      return Optional.of(ResourceLocation.parse("mmdlib:icon_info"));
    }

    public Optional<Text> labelFor(Object value) {
      return Optional.empty();
    }
  }

  private static Text text(String label) {
    return new Text(
        "renderer.test." + label.toLowerCase(Locale.ROOT).replace(' ', '_'), Optional.of(label));
  }

  private static Common common(String id, String value, String maximum) {
    var empty = Common.empty();
    return new Common(
        Optional.of(id),
        Optional.ofNullable(value).map(ValueRef::parse),
        Optional.ofNullable(maximum).map(ValueRef::parse),
        Optional.empty(),
        Optional.empty(),
        empty.clicks(),
        Optional.empty(),
        Optional.empty(),
        List.of(),
        0,
        0,
        0);
  }

  static void install() {
    var registry = ElementRenderers.GLOBAL;
    registry.register(
        ModElement.class,
        (e, c) -> {
          passes.clear();
          passes.add(ElementRenderers.Pass.BACKGROUND);
          var r = c.bounds();
          c.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xff228855);
          if (c.access().find("renderer_probe").orElseThrow() != e
              || !c.access().bounds(e).equals(r))
            throw new AssertionError("Mod presentation services lost element identity");
        });
    registry.registerOverlay(
        ModElement.class,
        (e, c) -> {
          passes.add(ElementRenderers.Pass.OVERLAY);
          var r = c.bounds();
          c.graphics().renderOutline(r.x(), r.y(), r.width(), r.height(), 0xffffaa44);
        });
    registry.registerForeground(
        ModElement.class, (e, c) -> passes.add(ElementRenderers.Pass.FOREGROUND));
    registry.registerTop(ModElement.class, (e, c) -> passes.add(ElementRenderers.Pass.TOP));
    var sharedSlots = registry.require(SlotElement.class);
    registry.register(
        ItemSlotElement.class,
        (e, c) -> {
          sharedSlots.accept(c);
          if (c.access().parent(e).isEmpty() && e.common().id().isEmpty()) playerSlots++;
          else machineSlots++;
        });
    registry.registerOverlay(
        ItemSlotElement.class,
        (e, c) -> {
          if (c.access().parent(e).isEmpty() && e.common().id().isEmpty()) {
            if (!c.access().visible(e) || !c.access().bounds(e).equals(c.bounds()))
              throw new AssertionError(
                  "Implicit player slot has no presentation bounds/visibility");
            playerOverlays++;
          }
        });
    var sharedWidgets = registry.require(CustomElement.class);
    registry.replace(
        CustomElement.class,
        (e, c) -> {
          widgets.add(e.common().id().orElse(""));
          sharedWidgets.accept(c);
        });
    registry.replace(
        ElementRenderers.Pass.FOREGROUND,
        PopoutElement.class,
        ElementRenderers.Clip.VIEWPORT,
        (e, c) -> {
          if (c.access().drawerPresentation(e, PopoutRenderer.Presentation.class).isPresent())
            popoutBodies++;
          PopoutRenderer.body(e, c);
        });
    var id = ResourceLocation.parse("mmdlib_companion:crusher");
    var original = Screens.get(id).orElseThrow().screen();
    var radio =
        new RadioGroupElement(
            common("renderer_radios", "#getReservePercent", null),
            Optional.of(text("Modes")),
            List.of(
                new RadioElement(Common.empty(), text("One")),
                new RadioElement(Common.empty(), text("Two"))));
    var column =
        new BoxElement(
            Common.empty(),
            true,
            0,
            3,
            Align.START,
            List.of(
                new ModElement(common("renderer_probe", null, null)),
                new ModButton(Common.empty()),
                new CheckboxElement(common("renderer_checkbox", "#hasTank", null), text("Tank")),
                new MeterElement(
                    common("renderer_meter", "#getProgress", "#getTime"),
                    Orientation.HORIZONTAL,
                    10,
                    60,
                    0xff55cc88,
                    3,
                    1,
                    Optional.empty())));
    var row =
        new BoxElement(
            Common.empty(),
            false,
            0,
            8,
            Align.START,
            List.of(
                column,
                radio,
                new SideConfigElement(
                    common("renderer_sides", null, null), List.of("input", "output"))));
    var group =
        new GroupElement(
            common("renderer_group", null, null),
            Optional.of(text("Renderer extensions")),
            Optional.empty(),
            4,
            List.of(row));
    var body = new ArrayList<>(original.body());
    body.add(Math.max(0, body.size() - 1), group);
    var screen =
        new zone.moddev.mc.lib.gui.model.Screen(
            original.id(),
            original.source(),
            original.title(),
            original.blockEntity(),
            original.playerInventory(),
            body,
            original.tabs());
    var screens = new HashMap<>(Screens.all());
    screens.put(id, ScreenBinder.bind(screen, ElementRendererClientSmoke.class.getClassLoader()));
    Screens.install(screens);
  }

  static boolean run(MachineScreen screen) {
    var radio =
        (RadioGroupElement) screen.getMenu().bound.screen().find("renderer_radios").orElseThrow();
    var selected = screen.getMenu().value(radio, false);
    for (int i = 0; i < radio.options().size(); i++)
      if (TextControlRenderer.selected(radio.options().get(i), screen.presentation())
          != java.math.BigInteger.valueOf(i).equals(selected))
        throw new AssertionError("Radio drawing disagrees with synchronized group selection");
    if (!passes.equals(List.of(ElementRenderers.Pass.values())))
      throw new AssertionError("Wrong element drawing passes: " + passes);
    if (playerSlots == 0 || machineSlots == 0 || playerOverlays == 0)
      throw new AssertionError("Slot override did not render both player and machine slots");
    if (!widgets.contains("progress")) throw new AssertionError("Custom widget stopped rendering");
    if (phase == 0) {
      capture("renderer-elements.png");
      click(screen, "info");
      phase++;
      return false;
    }
    if (phase == 1) {
      if (popoutBodies == 0) throw new AssertionError("Popout foreground renderer did not run");
      capture("renderer-popout.png");
      click(screen, "info");
      click(screen, "open_settings");
      phase++;
      return false;
    }
    if (!screen.getMenu().views().get("panel").equals("settings")) return false;
    if (!widgets.contains("name_editor") || !widgets.contains("reserve_slider"))
      throw new AssertionError("Context widgets did not render");
    capture("renderer-context.png");
    return true;
  }

  private static void click(MachineScreen screen, String id) {
    var r = screen.bounds(screen.getMenu().bound.screen().find(id).orElseThrow());
    if (!screen.mouseClicked(r.x() + r.width() / 2., r.y() + r.height() / 2., 0))
      throw new AssertionError("Click not consumed: " + id);
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
  }
}
