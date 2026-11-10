package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.sync.*;

/** Non-button controls and a non-context destination use the ordinary typed action lifecycle. */
final class NavigationClientSmoke {
  private static int phase;
  private static volatile boolean reset;
  private static final ElementSync<Integer> SELECTED =
      ElementSync.field(
              ResourceLocation.parse("mmdlib_companion:navigation_selection"),
              StreamCodec.of(
                  (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
              5,
              element -> 0)
          .validate((element, value) -> value == 0 || value == 1)
          .affectsLayout()
          .build();

  private record ModControl(Common common, ViewRef target) implements Element {
    public Optional<Navigation> navigation() {
      return Optional.of(new Navigation(target));
    }

    public int[] measure(TextMeasure text) {
      return new int[] {100, 18};
    }
  }

  private record ModDestination(Common common) implements NavigableElement, SynchronizedElement {
    public List<ElementSync<?>> synchronizedValues() {
      return List.of(SELECTED);
    }

    public String defaultSelection() {
      return "home";
    }

    public boolean hasSelection(String selection) {
      return Set.of("home", "details").contains(selection);
    }

    public String selected(ElementStateView state) {
      return state.get(this, SELECTED) == 0 ? "home" : "details";
    }

    public boolean select(ElementStates state, String selection) {
      return hasSelection(selection) && state.set(this, SELECTED, selection.equals("home") ? 0 : 1);
    }

    public int[] measure(TextMeasure text) {
      return new int[] {100, 18};
    }
  }

  private static Common common(String id) {
    var e = Common.empty();
    return new Common(
        Optional.of(id),
        e.value(),
        e.max(),
        e.tooltip(),
        e.tooltipShift(),
        e.clicks(),
        e.onHover(),
        e.hoverShows(),
        e.jeiRecipes(),
        0,
        0,
        0);
  }

  static void install() {
    ElementRenderers.GLOBAL.register(
        ModControl.class,
        (e, c) -> {
          var r = c.bounds();
          c.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xff406080);
          c.graphics()
              .drawString(c.access().font(), e.target().context(), r.x() + 2, r.y() + 4, 0xffffff);
        });
    ElementRenderers.GLOBAL.register(
        ModDestination.class,
        (e, c) -> {
          var r = c.bounds();
          c.graphics()
              .drawString(
                  c.access().font(),
                  e.selected(c.access().state()),
                  r.x() + 2,
                  r.y() + 4,
                  0xffffff);
        });
    var id = ResourceLocation.parse("mmdlib_companion:crusher");
    var original = Screens.get(id).orElseThrow().screen();
    var body = new ArrayList<>(original.body());
    body.add(
        new BoxElement(
            Common.empty(),
            false,
            0,
            4,
            Align.START,
            List.of(
                new ModControl(
                    common("navigation_custom"), new ViewRef("navigation_destination", "details")),
                new ModControl(common("navigation_builtin"), new ViewRef("panel", "settings")),
                new ModDestination(common("navigation_destination")))));
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
    screens.put(id, ScreenBinder.bind(screen, NavigationClientSmoke.class.getClassLoader()));
    Screens.install(screens);
  }

  static boolean run(MachineScreen screen) {
    if (phase > 10) return true;
    var menu = screen.getMenu();
    var custom = menu.bound.screen().find("navigation_custom").orElseThrow();
    var builtin = menu.bound.screen().find("navigation_builtin").orElseThrow();
    var destination =
        (NavigableElement) menu.bound.screen().find("navigation_destination").orElseThrow();
    if (menu.elementPending(custom) || menu.elementPending(builtin)) return false;
    var selected = destination.selected(menu.elementStates());
    switch (phase) {
      case 0 -> {
        click(screen, custom);
        require(
            destination.selected(menu.elementStates()).equals("details"),
            "Custom control predicts selection");
      }
      case 1 -> {
        require(selected.equals("details"), "Server accepts custom navigation destination");
        click(screen, custom);
      }
      case 2 -> {
        require(selected.equals("home"), "Selecting the same target toggles to its default");
        click(screen, custom);
      }
      case 3 -> {
        require(selected.equals("details"), "Custom selection confirmed before restoration");
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(
            () -> {
              var serverMenu =
                  (MachineMenu) server.getPlayerList().getPlayers().getFirst().containerMenu;
              serverMenu.navigate("navigation_destination", "home");
              serverMenu.broadcastChanges();
              reset = true;
            });
      }
      case 4 -> {
        if (!reset || !selected.equals("home")) return false;
        screen.removed();
        screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        require(
            menu.elementPending(custom), "Remembered custom destination uses its custom control");
        require(
            destination.selected(menu.elementStates()).equals("home"),
            "Restoration waits for server confirmation");
      }
      case 5 -> {
        require(selected.equals("details"), "Restored custom selection confirmed by server");
        click(screen, custom);
      }
      case 6 -> {
        require(selected.equals("home"), "Custom destination reset before rejected action");
        var packet =
            menu.prepareElementAction(
                    custom, Navigation.NAVIGATE, new ElementActions.Click(0, 0, 1))
                .orElseThrow();
        menu.navigate("navigation_destination", "details");
        PacketDistributor.sendToServer(packet);
      }
      case 7 -> {
        require(
            selected.equals("home"), "Rejected custom navigation restores authoritative selection");
        menu.prepareElementAction(custom, Navigation.NAVIGATE, new ElementActions.Click(0, 0, 0))
            .orElseThrow();
        menu.navigate("navigation_destination", "details");
        for (int i = 0; i < 100; i++) menu.tickClientActions();
      }
      case 8 -> {
        require(selected.equals("home"), "Custom navigation timeout restores confirmed selection");
        click(screen, builtin);
      }
      case 9 -> {
        require(
            menu.views().get("panel").equals("settings"),
            "Custom control navigates a built-in context");
        click(screen, builtin);
      }
      default -> {
        require(
            menu.views().get("panel").equals("machine"),
            "Custom control toggles built-in context back");
        phase = 11;
        return true;
      }
    }
    phase++;
    return false;
  }

  private static void click(MachineScreen screen, Element element) {
    var r = screen.bounds(element);
    require(
        screen.mouseClicked(r.x() + 3, r.y() + 3, 0), "Custom navigation control consumes click");
    screen.mouseReleased(r.x() + 3, r.y() + 3, 0);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
