package zone.moddev.mc.companion;

import java.util.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.*;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.IoMode;

/** Companion content demonstrates capabilities: the library does not register concrete addons. */
public final class CompanionAddons {
  private static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(Registries.ITEM, CompanionMod.ID);
  public static final DeferredHolder<Item, Item> RECOVERY_CAPSULE =
      ITEMS.register("recovery_capsule", RecoveryCapsuleItem::new);
  private static final Map<DeferredHolder<Item, Item>, InventoryAddon> ADDONS =
      new LinkedHashMap<>();
  private static final Map<DeferredHolder<Item, Item>, InventoryFilter> FILTERS =
      new LinkedHashMap<>();
  public static final FilterWidgetType<Boolean> TAG_MODE =
      FilterWidgetType.register(
          CompanionMod.id("tag_mode"),
          com.mojang.serialization.Codec.BOOL,
          (config, deny) -> {
            config.putBoolean("deny", deny);
            return true;
          });
  public static final DeferredHolder<Item, Item> PULLER =
      addon("mechanical_puller", ResourceType.ITEMS, IoMode.INPUT, 8, 20, 4, true);
  public static final DeferredHolder<Item, Item> PUSHER =
      addon("mechanical_pusher", ResourceType.ITEMS, IoMode.OUTPUT, 8, 20, 4, true);
  public static final DeferredHolder<Item, Item> INPUT_PUMP =
      addon("input_pump", ResourceType.FLUIDS, IoMode.INPUT, 250, 10, 1, true);
  public static final DeferredHolder<Item, Item> OUTPUT_PUMP =
      addon("output_pump", ResourceType.FLUIDS, IoMode.OUTPUT, 250, 10, 1, true);
  public static final DeferredHolder<Item, Item> IMPORT_RELAY =
      addon("import_relay", ResourceType.ENERGY, IoMode.INPUT, 1000, 5, 0, false);
  public static final DeferredHolder<Item, Item> EXPORT_RELAY =
      addon("export_relay", ResourceType.ENERGY, IoMode.OUTPUT, 1000, 5, 0, false);
  public static final DeferredHolder<Item, Item> ITEM_FILTER =
      filter("item_filter", ResourceType.ITEMS, 9, false);
  public static final DeferredHolder<Item, Item> LARGE_FILTER =
      filter("large_item_filter", ResourceType.ITEMS, 36, false);
  public static final DeferredHolder<Item, Item> TAG_FILTER =
      filter("tag_filter", ResourceType.ITEMS, 0, true);
  public static final DeferredHolder<Item, Item> FLUID_FILTER =
      filter("fluid_filter", ResourceType.FLUIDS, 6, false);

  private static DeferredHolder<Item, Item> addon(
      String name,
      ResourceType<?> type,
      IoMode direction,
      int batch,
      int interval,
      long cost,
      boolean filters) {
    var item = ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(1)));
    var spec =
        new AddonSpec(CompanionMod.id(name), type, direction, batch, interval, cost, filters);
    ADDONS.put(item, stack -> spec);
    return item;
  }

  private static DeferredHolder<Item, Item> filter(
      String name, ResourceType<?> type, int size, boolean tag) {
    var item = ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(1)));
    FILTERS.put(
        item,
        new InventoryFilter() {
          public ResourceType<?> resource(ItemStack stack) {
            return type;
          }

          public ResourceLocation editorType(ItemStack stack) {
            return CompanionMod.id(name);
          }

          public FilterControl controls(ItemStack stack) {
            return new FilterControl.Box(
                "root",
                false,
                List.of(
                    tag
                        ? new FilterControl.Custom("deny", TAG_MODE, 14)
                        : new FilterControl.Toggle(
                            "deny",
                            ResourceLocation.parse("mmdlib:icon_sides"),
                            Component.translatable("screen.mmdlib_companion.filter.deny")),
                    tag
                        ? new FilterControl.Text(
                            "tag",
                            128,
                            Component.translatable("screen.mmdlib_companion.filter.tag"))
                        : new FilterControl.Samples("samples", size, type)));
          }

          public boolean validate(
              ItemStack stack,
              net.minecraft.nbt.CompoundTag config,
              HolderLookup.Provider registries) {
            if (!tag || config.getString("tag").isEmpty()) return true;
            var id = ResourceLocation.tryParse(config.getString("tag"));
            return id != null
                && registries
                    .lookupOrThrow(Registries.ITEM)
                    .get(TagKey.create(Registries.ITEM, id))
                    .isPresent();
          }

          public boolean matches(
              ItemStack stack, Object candidate, HolderLookup.Provider registries) {
            var config = configuration(stack);
            boolean match = false;
            if (tag && candidate instanceof ItemStack item) {
              var id = ResourceLocation.tryParse(config.getString("tag"));
              match = id != null && item.is(TagKey.create(Registries.ITEM, id));
            } else {
              String id =
                  candidate instanceof ItemStack item
                      ? BuiltInRegistries.ITEM.getKey(item.getItem()).toString()
                      : candidate instanceof FluidStack fluid
                          ? BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()
                          : "";
              var samples = config.getList("samples", net.minecraft.nbt.Tag.TAG_STRING);
              for (int i = 0; i < samples.size(); i++)
                if (samples.getString(i).equals(id)) {
                  match = true;
                  break;
                }
            }
            return config.getBoolean("deny") ? !match : match;
          }
        });
    return item;
  }

  public static List<DeferredHolder<Item, Item>> items() {
    var result = new ArrayList<>(ADDONS.keySet());
    result.addAll(FILTERS.keySet());
    return List.copyOf(result);
  }

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
  }

  public static void capabilities(RegisterCapabilitiesEvent event) {
    ADDONS.forEach(
        (item, provider) ->
            event.registerItem(AddonCapabilities.ADDON, (stack, context) -> provider, item.get()));
    FILTERS.forEach(
        (item, provider) ->
            event.registerItem(AddonCapabilities.FILTER, (stack, context) -> provider, item.get()));
  }
}
