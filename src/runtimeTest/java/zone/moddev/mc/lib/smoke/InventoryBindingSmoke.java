package zone.moddev.mc.lib.smoke;

import java.util.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import net.neoforged.neoforge.items.*;

import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.model.elements.*;
import zone.moddev.mc.lib.gui.runtime.*;

final class InventoryBindingSmoke {
  private static final SlotElement.Appearance APPEARANCE =
      SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());

  private static class Host implements ScreenHost {
    final ItemStackHandler physical = new ItemStackHandler(2);
    int bindings;
    final List<String> scopes = new ArrayList<>();
    boolean duplicatePhysical, duplicateAddress;

    public IItemHandlerModifiable screenItems() {
      throw new AssertionError("Assembly/validation must use binding hooks");
    }

    public int screenLegacySlotCount() {
      return 0;
    }

    public InventoryBinding screenInventoryBinding(String inventory, int slot, boolean standalone) {
      bindings++;
      require(!standalone, "Custom scoped slot must not use legacy binding");
      scopes.add(inventory);
      int physicalSlot = duplicatePhysical || slot == 100 ? 0 : 1;
      return new InventoryBinding(
          physical,
          physicalSlot,
          slot == 100 ? -1 : 4,
          List.of(
              new ItemInventories.Address("route", duplicateAddress ? 0 : physicalSlot),
              new ItemInventories.Address(inventory, slot)));
    }

    public ItemStack screenSlotHint(String inventory, int slot) {
      require(inventory.equals("nested") && slot == 200, "Default hint uses nearest scope");
      return new ItemStack(Items.DIAMOND);
    }

    public List<ItemInventories.Address> screenItemDestinations(ItemStack stack) {
      return List.of(
          new ItemInventories.Address("route", 1), new ItemInventories.Address("route", 0));
    }
  }

  private static class Scope extends BoxElement implements InventoryScope {
    private final String inventory;

    Scope(String inventory, List<Element> children) {
      super(Common.empty(), true, 0, 0, Align.START, children);
      this.inventory = inventory;
    }

    public String inventory() {
      return inventory;
    }
  }

  static void check(net.minecraft.world.entity.player.Player player) {
    var supplied = new ItemStack(Items.EMERALD);
    var first =
        new ItemSlotElement(Common.empty(), 100, SlotKind.NORMAL, APPEARANCE) {
          public ItemStack hint(ScreenHost host, String inventory) {
            require("custom".equals(inventory), "Custom hint receives enclosing inventory");
            return supplied;
          }
        };
    var second = new ItemSlotElement(Common.empty(), 200, SlotKind.NORMAL, APPEARANCE);
    var customPlayer =
        new PlayerSlotElement(Common.empty(), 9, APPEARANCE) {
          public ItemStack hint(ScreenHost host, String inventory) {
            require("player".equals(inventory), "Player hint receives player scope");
            return new ItemStack(Items.GOLD_INGOT);
          }
        };
    var view =
        new Scope(
            "custom",
            List.of(
                new BoxElement(Common.empty(), true, 0, 0, Align.START, List.of(first)),
                new Scope("nested", List.of(second))));
    var screen =
        new Screen(
            ResourceLocation.parse("test:binding"),
            "binding",
            new Text("test", Optional.empty()),
            "test.Host",
            false,
            List.of(view, customPlayer),
            List.of());
    var host = new Host();
    first.validateInventory(screen, host);
    second.validateInventory(screen, host);
    require(
        host.scopes.equals(List.of("custom", "nested")),
        "Validation resolves custom nested scopes");
    host.bindings = 0;
    host.scopes.clear();
    var session = session(screen, host, false);
    require(host.bindings == 2, "Resolve each binding once per assembly");
    require(host.scopes.equals(List.of("custom", "nested")), "Assembly shares validation scopes");
    session.playerSlots(
        session.slots().size(), player.getInventory(), ScreenLayout.of(screen, TextMeasure.NONE));
    var hint = session.hint(first);
    require(hint.is(Items.EMERALD), "Element owns hint selection");
    hint.setCount(42);
    require(supplied.getCount() == 1, "Session copies custom hint values");
    require(session.hint(second).is(Items.DIAMOND), "Default hint retains host lookup");
    require(session.hint(customPlayer).is(Items.GOLD_INGOT), "Player slot hints are overridable");
    require(
        session.index(second) == 4 && session.index(first) == 5,
        "Reserve later legacy indexes before automatic numbering");
    require(session.legacyIndex(4) == 4, "Custom legacy mapping");
    require(session.index(new ItemInventories.Address("route", 0)) == 5, "Custom alias routing");
    require(
        session.destinations(new ItemStack(Items.DIAMOND)).equals(List.of(4, 5)),
        "Custom destination ordering");
    session.slot(first).set(new ItemStack(Items.DIAMOND, 3));
    session.slot(second).set(new ItemStack(Items.EMERALD, 2));
    require(
        host.physical.getStackInSlot(0).getCount() == 3
            && host.physical.getStackInSlot(1).is(Items.EMERALD),
        "Logical indexes resolve to physical slots");
    var client = session(screen, host, true);
    var a = (net.neoforged.neoforge.items.SlotItemHandler) client.slot(first);
    var b = (net.neoforged.neoforge.items.SlotItemHandler) client.slot(second);
    require(
        a.getItemHandler() == b.getItemHandler(), "Physical handler identity shares client mirror");
    a.set(new ItemStack(Items.STONE, 8));
    require(
        host.physical.getStackInSlot(0).is(Items.DIAMOND),
        "Client mirror does not mutate host storage");
    host.duplicatePhysical = true;
    expectInvalid(() -> session(screen, host, false), "physical inventory slot");
    host.duplicatePhysical = false;
    host.duplicateAddress = true;
    expectInvalid(() -> session(screen, host, false), "inventory address");
    expectInvalid(() -> new InventoryBinding(host.physical, 2, -1, List.of()), "Physical slot");
  }

  private static InventoryElement.Session session(Screen screen, ScreenHost host, boolean client) {
    return new InventoryElement.Session(
        screen,
        host,
        ScreenLayout.of(screen, TextMeasure.NONE),
        client,
        e -> true,
        () -> false,
        () -> {});
  }

  private static void expectInvalid(Runnable action, String message) {
    try {
      action.run();
      throw new AssertionError("Accepted invalid binding");
    } catch (IllegalArgumentException expected) {
      require(expected.getMessage().contains(message), expected.getMessage());
    }
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
