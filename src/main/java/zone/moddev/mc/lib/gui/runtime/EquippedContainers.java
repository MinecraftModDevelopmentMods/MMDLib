package zone.moddev.mc.lib.gui.runtime;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

/** Equipment discovery boundary. Storage access uses only the standard item capability. */
public final class EquippedContainers {
  private EquippedContainers() {}

  /** The live equipped stack, its stable equipment address, and its current capability. */
  public record Source(
      String key,
      ItemStack stack,
      IItemHandler items,
      Presentation presentation,
      String selectionKey,
      ContainerPolicy policy) {
    public Source {
      java.util.Objects.requireNonNull(policy);
    }

    public Source(
        String key,
        ItemStack stack,
        IItemHandler items,
        Presentation presentation,
        String selectionKey) {
      this(key, stack, items, presentation, selectionKey, ContainerPolicy.STORAGE);
    }

    public Presentation presentation() {
      var settings = policy.settings();
      return (presentation == null ? Presentation.container(stack) : presentation)
          .withPolicy(settings)
          .withGestureRows(policy.instructions(settings));
    }

    public Source(String key, ItemStack stack, IItemHandler items) {
      this(key, stack, items, null, key);
    }
  }

  /** Provider-authored chrome. All components are detached on both input and output. */
  public record Presentation(
      Component title,
      Optional<ResourceLocation> icon,
      int colour,
      List<Component> headings,
      List<Component> tabRows,
      List<Component> openRows,
      List<Component> headerRows,
      ContainerPolicy.Settings policy,
      List<Component> gestureRows) {
    public Presentation(
        Component title,
        Optional<ResourceLocation> icon,
        int colour,
        List<Component> headings,
        List<Component> tabRows,
        List<Component> openRows,
        List<Component> headerRows,
        ContainerPolicy.Settings policy) {
      this(
          title,
          icon,
          colour,
          headings,
          tabRows,
          openRows,
          headerRows,
          policy,
          ContainerPolicy.defaultInstructions(policy));
    }

    public Presentation withGestureRows(List<Component> rows) {
      return new Presentation(
          title, icon, colour, headings, tabRows, openRows, headerRows, policy, rows);
    }

    public List<Component> gestureRows() {
      return copyRows(gestureRows);
    }

    public Presentation(
        Component title,
        Optional<ResourceLocation> icon,
        int colour,
        List<Component> headings,
        List<Component> tabRows,
        List<Component> openRows,
        List<Component> headerRows) {
      this(
          title,
          icon,
          colour,
          headings,
          tabRows,
          openRows,
          headerRows,
          ContainerPolicy.STORAGE.settings());
    }

    public Presentation withPolicy(ContainerPolicy.Settings policy) {
      return new Presentation(title, icon, colour, headings, tabRows, openRows, headerRows, policy);
    }

    public Presentation {
      title = title.copy();
      icon = java.util.Objects.requireNonNull(icon);
      headings = copyRows(headings);
      tabRows = copyRows(tabRows);
      openRows = copyRows(openRows);
      headerRows = copyRows(headerRows);
      gestureRows = copyRows(gestureRows);
      java.util.Objects.requireNonNull(policy);
    }

    public Component title() {
      return title.copy();
    }

    public List<Component> headings() {
      return copyRows(headings);
    }

    public List<Component> tabRows() {
      return copyRows(tabRows);
    }

    public List<Component> openRows() {
      return copyRows(openRows);
    }

    public List<Component> headerRows() {
      return copyRows(headerRows);
    }

    public static Presentation container(ItemStack stack) {
      return new Presentation(
          stack.getHoverName(),
          Optional.empty(),
          0xc5aa7b,
          List.of(),
          List.of(Component.translatable("screen.mmdlib.container.toggle")),
          List.of(Component.translatable("screen.mmdlib.container.store")),
          List.of());
    }
  }

  private static List<Component> copyRows(List<Component> rows) {
    if (rows.size() > 16) throw new IllegalArgumentException("Too many container tooltip rows");
    return rows.stream().map(Component::copy).map(Component.class::cast).toList();
  }

  /** Public slot presentation, independent of the optional Curios classes. */
  public record SlotInfo(
      String identifier,
      int index,
      boolean cosmetic,
      boolean available,
      ResourceLocation icon,
      Component label,
      List<Component> tooltip,
      Component placeholder) {
    public SlotInfo(
        String identifier, int index, boolean cosmetic, boolean available, ResourceLocation icon) {
      this(
          identifier,
          index,
          cosmetic,
          available,
          icon,
          Component.literal(identifier),
          List.of(Component.literal(identifier)),
          Component.literal("-"));
    }

    public Component label() {
      return label.copy();
    }

    public List<Component> tooltip() {
      return copyRows(tooltip);
    }

    public Component placeholder() {
      return placeholder.copy();
    }

    public SlotInfo {
      label = label.copy();
      tooltip = copyRows(tooltip);
      placeholder = placeholder.copy();
      if (identifier.isBlank()
          || identifier.length() > 128
          || index < 0
          || index >= ContainerDrawerMenu.MAX_SLOTS)
        throw new IllegalArgumentException("Invalid equipment slot");
    }
  }

  public interface DescribedHandler extends IItemHandler {
    SlotInfo describe(int slot);

    default boolean quickInsert(int slot) {
      return !describe(slot).cosmetic();
    }
  }

  /** One equipment scan shared by both drawers. Handler contents remain live. */
  public record Snapshot(List<Source> containers, List<Source> equipment) {
    public Snapshot {
      containers = List.copyOf(containers);
      equipment = List.copyOf(equipment);
    }
  }

  /** Menu-local cache, discarded with its viewer; mutations always invalidate it. */
  public static final class Discovery {
    private final java.util.function.LongSupplier clock;
    private final java.util.function.Supplier<Snapshot> scan;
    private long tick = Long.MIN_VALUE;
    private Snapshot snapshot;

    public Discovery(Player player) {
      this(player.level()::getGameTime, scanner(player));
    }

    Discovery(java.util.function.LongSupplier clock, java.util.function.Supplier<Snapshot> scan) {
      this.clock = clock;
      this.scan = scan;
    }

    public Snapshot get() {
      long now = clock.getAsLong();
      if (snapshot == null || tick != now) {
        snapshot = scan.get();
        tick = now;
      }
      return snapshot;
    }

    public void invalidate() {
      snapshot = null;
    }

    private static java.util.function.Supplier<Snapshot> scanner(Player player) {
      var identity = new ItemStack(net.minecraft.world.item.Items.ENDER_EYE);
      return () -> discover(player, identity);
    }
  }

  private static Snapshot discover(Player player, ItemStack identity) {
    // Kept outside the optional adapter so a server without Curios never loads its API.
    if (!ModList.get().isLoaded("curios")) return new Snapshot(List.of(), List.of());
    return zone.moddev.mc.lib.compat.curios.CuriosEquipment.discover(player, identity);
  }

  public static List<Source> equipment(Player player, ItemStack identity) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosEquipment.find(player, identity);
  }

  public static List<Source> find(Player player) {
    if (!ModList.get().isLoaded("curios")) return List.of();
    return zone.moddev.mc.lib.compat.curios.CuriosContainers.find(player);
  }
}
