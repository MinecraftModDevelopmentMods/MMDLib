package zone.moddev.mc.lib.smoke;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;

import zone.moddev.mc.lib.client.jei.JeiIngredientTypes;
import zone.moddev.mc.lib.gui.*;
import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.*;

/**
 * A non-slot mod element accepts a fluid through the real registered JEI handler and typed action.
 */
final class GuiIngredientDropSmoke {
  static final ElementSync<Integer> AMOUNT =
      ElementSync.field(
              ResourceLocation.parse("mmdlib_smoke:dropped_fluid"),
              StreamCodec.of((b, v) -> b.writeInt(v), b -> b.readInt()),
              4,
              e -> 0)
          .build();
  static final ElementAction<FluidStack> SELECT =
      new ElementAction<>(
          ResourceLocation.parse("mmdlib_smoke:select_fluid"),
          FluidStack.OPTIONAL_STREAM_CODEC,
          4096,
          (c, v) -> {
            if (v.isEmpty() || !v.is(Fluids.WATER)) return false;
            c.menu().elementStates().set(c.element(), AMOUNT, v.getAmount());
            return true;
          });

  record Display(Common common) implements SynchronizedElement {
    public int[] measure(TextMeasure text) {
      return new int[] {180, 14};
    }

    public List<ElementSync<?>> synchronizedValues() {
      return List.of(AMOUNT);
    }

    public List<? extends ElementAction<?>> actions() {
      return List.of(SELECT);
    }
  }

  static final class DropBehavior extends DefaultElementBehavior implements IngredientDropHandler {
    DropBehavior(ElementAccess access) {
      super(access);
    }

    public <T> List<Target<T>> targets(IngredientType<T> type, T ingredient) {
      if (!type.equals(IngredientTypes.FLUID) || access.pending(access.element())) return List.of();
      return List.of(
          new Target<>(
              "fluid",
              access.bounds(),
              value -> access.submit(SELECT, FluidStack.class.cast(value))));
    }

    public void actionResult(boolean ok) {
      if (ok) accepted++;
      else rejected++;
    }
  }

  private static final ElementAction<ItemStack> CUSTOM_SELECTION =
      new ElementAction<>(
          ResourceLocation.parse("mmdlib_smoke:custom_selection"),
          ItemStack.OPTIONAL_STREAM_CODEC,
          4096,
          (context, stack) ->
              !stack.is(Items.STONE) && GhostSlotElement.SELECT_GHOST.execute(context, stack));

  private static class CustomGhost extends GhostSlotElement {
    private final int backing;

    CustomGhost(Common common, int backing, Appearance appearance) {
      super(common, 127, appearance);
      this.backing = backing;
    }

    public Optional<ElementAction<ItemStack>> selectionAction() {
      return Optional.of(CUSTOM_SELECTION);
    }

    public boolean acceptsSelection(int button, ItemStack stack) {
      return !stack.is(Items.STONE);
    }

    public Optional<ItemStack> selectionIngredient(ResourceLocation type, Object ingredient) {
      if (type.equals(IngredientTypes.FLUID.id()) && ingredient instanceof FluidStack fluid)
        return fluid.is(Fluids.WATER)
            ? Optional.of(new ItemStack(Items.WATER_BUCKET))
            : Optional.empty();
      return super.selectionIngredient(type, ingredient);
    }

    public ItemStack read(ScreenHost host, String inventory) {
      return host.screenGhost(backing).copy();
    }

    public boolean accept(
        ScreenHost host,
        String inventory,
        net.minecraft.world.entity.player.Player player,
        ItemStack stack) {
      return host.setScreenGhost(player, backing, selection(0, stack));
    }
  }

  private static int phase, accepted, rejected, frames, captureFrame = -1;

  static void rendered() {
    frames++;
  }

  private static java.util.function.Consumer<FluidStack> stale;

  static void install() {
    // Exercise public mod mappings and duplicate rejection without depending on global built-ins.
    var mapping = new JeiIngredientTypes();
    IIngredientType<String> source = () -> String.class;
    var type =
        new IngredientType<>(ResourceLocation.parse("mmdlib_smoke:token"), String.class, v -> v);
    require(mapping.find(source).isEmpty(), "Unmapped JEI type was accepted");
    mapping.register(source, type);
    require(mapping.find(source).orElseThrow() == type, "Custom JEI mapping was lost");
    try {
      mapping.register(source, type);
      throw new AssertionError("Duplicate mapping accepted");
    } catch (IllegalArgumentException expected) {
    }
    ElementBehaviors.GLOBAL.register(Display.class, (e, a) -> new DropBehavior(a));
    ElementRenderers.GLOBAL.register(
        Display.class,
        (e, c) ->
            c.graphics()
                .drawString(
                    c.access().font(),
                    "Mod fluid drop: " + c.access().state().get(e, AMOUNT) + " mB",
                    c.bounds().x() + 4,
                    c.bounds().y() + 2,
                    0x404040,
                    false));
    var old = Screens.get(GuiRuntimeChecks.ID).orElseThrow().screen();
    var c = Common.empty();
    var common =
        new Common(
            Optional.of("fluid_drop"),
            c.value(),
            c.max(),
            c.tooltip(),
            c.tooltipShift(),
            c.clicks(),
            c.onHover(),
            c.hoverShows(),
            c.jeiRecipes(),
            0,
            0,
            0);
    var body = new ArrayList<>(old.body());
    body.add(new Display(common));
    var originalGhost = (GhostSlotElement) old.find("ghost").orElseThrow();
    var ghostCommon =
        new Common(
            Optional.of("custom_ghost"),
            c.value(),
            c.max(),
            c.tooltip(),
            c.tooltipShift(),
            c.clicks(),
            c.onHover(),
            c.hoverShows(),
            c.jeiRecipes(),
            0,
            0,
            0);
    body.add(new CustomGhost(ghostCommon, originalGhost.index(), originalGhost.appearance()));
    var screen =
        new Screen(
            old.id(),
            old.source(),
            old.title(),
            old.blockEntity(),
            old.playerInventory(),
            body,
            old.tabs());
    var screens = new HashMap<>(Screens.all());
    screens.put(old.id(), ScreenBinder.bind(screen, GuiIngredientDropSmoke.class.getClassLoader()));
    Screens.install(screens);
  }

  static boolean tick(MachineScreen screen) throws Exception {
    var menu = screen.getMenu();
    var element = menu.bound.screen().find("fluid_drop").orElseThrow();
    if (menu.elementPending(element)) return false;
    switch (phase) {
      case 0 -> {
        var water = new FluidStack(Fluids.WATER, 1234);
        var target =
            GuiJeiSmoke.ingredientTarget(screen, "fluid_drop", NeoForgeTypes.FLUID_STACK, water)
                .orElseThrow();
        require(
            GuiJeiSmoke.ingredientTarget(screen, "ghost", NeoForgeTypes.FLUID_STACK, water)
                .isEmpty(),
            "Ghost item slot accepted fluid");
        require(
            GuiJeiSmoke.ingredientTarget(
                    screen,
                    "fluid_drop",
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                    new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.GOLD_INGOT))
                .isEmpty(),
            "Fluid-only element accepted an item");
        target.accept(water);
        require(menu.elementPending(element), "Fluid drop did not use typed submission");
        require(water.getAmount() == 1234, "Drop consumed the viewer's fluid");
        stale = target;
      }
      case 1 -> {
        if (accepted == 0) return false;
        require(
            menu.elementStates().get(element, AMOUNT) == 1234,
            "Fluid selection did not synchronize");
        var lava = new FluidStack(Fluids.LAVA, 1000);
        GuiJeiSmoke.ingredientTarget(screen, "fluid_drop", NeoForgeTypes.FLUID_STACK, lava)
            .orElseThrow()
            .accept(lava);
      }
      case 2 -> {
        if (rejected == 0) return false;
        require(
            menu.elementStates().get(element, AMOUNT) == 1234,
            "Rejected fluid changed authoritative state");
        var custom = menu.bound.screen().find("custom_ghost").orElseThrow();
        require(
            custom.action(CUSTOM_SELECTION.id()).orElseThrow() == CUSTOM_SELECTION,
            "Selection override participates in ordinary action declaration");
        var gold = new ItemStack(Items.GOLD_INGOT, 32);
        var target =
            GuiJeiSmoke.ingredientTarget(
                    screen, "custom_ghost", mezz.jei.api.constants.VanillaTypes.ITEM_STACK, gold)
                .orElseThrow();
        target.accept(gold);
        require(menu.elementPending(custom), "Custom ghost drop uses typed pending session");
        require(gold.getCount() == 32, "Custom selection preserves viewer stack");
        target.accept(new ItemStack(Items.DIAMOND));
      }
      case 3 -> {
        var custom = (CustomGhost) menu.bound.screen().find("custom_ghost").orElseThrow();
        if (menu.elementPending(custom) || !menu.slot(custom).getItem().is(Items.GOLD_INGOT))
          return false;
        require(
            GuiJeiSmoke.ingredientTarget(
                    screen,
                    "custom_ghost",
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                    new ItemStack(Items.STONE))
                .isEmpty(),
            "Element-owned selection eligibility filters drops");
        var bounds = screen.bounds(custom);
        screen.mouseClicked(bounds.x() + 8, bounds.y() + 8, 1);
        screen.mouseReleased(bounds.x() + 8, bounds.y() + 8, 1);
        require(menu.elementPending(custom), "Custom ghost click uses selected action");
      }
      case 4 -> {
        var custom = (CustomGhost) menu.bound.screen().find("custom_ghost").orElseThrow();
        if (menu.elementPending(custom) || !menu.slot(custom).getItem().isEmpty()) return false;
        GuiJeiSmoke.ingredientTarget(
                screen,
                "custom_ghost",
                NeoForgeTypes.FLUID_STACK,
                new FluidStack(Fluids.WATER, 1000))
            .orElseThrow()
            .accept(new FluidStack(Fluids.WATER, 1000));
        require(
            menu.elementPending(custom), "Element-owned ingredient conversion uses same action");
      }
      case 5 -> {
        var custom = (CustomGhost) menu.bound.screen().find("custom_ghost").orElseThrow();
        if (menu.elementPending(custom) || !menu.slot(custom).getItem().is(Items.WATER_BUCKET))
          return false;
        if (captureFrame < 0) captureFrame = frames;
        if (frames <= captureFrame) return false;
        var mc = Minecraft.getInstance();
        Screenshot.grab(
            mc.gameDirectory, "ingredient-drop.png", mc.getMainRenderTarget(), ignored -> {});
        mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        stale.accept(new FluidStack(Fluids.WATER, 50));
        require(!menu.elementPending(element), "Closed-screen target sent an action");
        return true;
      }
      default -> {
        return true;
      }
    }
    phase++;
    return false;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
