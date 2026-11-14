package zone.moddev.mc.lib.gui.model.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.gui.ElementInteractions;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.GaugeFill;
import zone.moddev.mc.lib.gui.model.Interaction;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.ResourceBinding;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * A complete tank UI component: binding, values, tooltip and cursor-container
 * interaction. All storage access runs on the server. The client draws its
 * snapshots with the glass gauge renderer.
 */
public class FluidTankElement implements Element {
    private final Common common;
    private final int tank;
    private final Orientation orientation;
    private final int length;

    public static final ResourceLocation INTERACTION_ID = ResourceLocation
        .fromNamespaceAndPath("mmdlib", "fluid-container");

    public FluidTankElement(Common common, int tank, Orientation orientation,
        int length) {
        Objects.requireNonNull(common);
        Objects.requireNonNull(orientation);
        if (tank < 0)
            throw new IllegalArgumentException(
                "Tank index must be nonnegative");
        if (length != FILL && length < 8)
            throw new IllegalArgumentException(
                "Tank length must be at least 8");

        this.common = common;
        this.tank = tank;
        this.orientation = orientation;
        this.length = length;
    }

    public boolean fills() {
        return length == FILL;
    }

    /**
     * Selects visual contents independently of display values and fill ratio;
     * subclasses may override.
     */
    public GaugeFill fill(Object value) {
        return value instanceof FluidStack fluid
            ? GaugeFill.fluid(fluid)
            : GaugeFill.solid(0x777777);
    }

    @Override
    public Object displayValue(Object value) {
        return value instanceof FluidStack fluid ? fluid.getAmount() : value;
    }

    @Override
    public void validate() {
        if (common.value().isPresent() || common.max().isPresent()
            || common.interaction().isPresent())
            throw new IllegalArgumentException(
                "A fluid tank owns its values and interaction; use only its tank binding");
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public Optional<ResourceBinding> resourceBinding() {
        return Optional.of(binding(tank));
    }

    @Override
    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
        var storage = storage(host, tank);
        return Optional
            .of(new ElementSnapshot(storage.getFluidInTank(tank).copy(),
                storage.getTankCapacity(tank)));
    }

    @Override
    public boolean interact(ScreenClick click) {
        return transfer(click, tank);
    }

    /**
     * The same transfer behavior can be attached to a label, button or another
     * element.
     */
    public static Interaction containerInteraction(int tank) {
        return new Interaction(INTERACTION_ID,
            Integer.toString(binding(tank).index()));
    }

    /**
     * Registration adapter only; the behavior remains owned by this component.
     */
    public static ElementInteractions.Type containerInteractionType() {
        return new ElementInteractions.Type() {
            public void validate(String target) {
                binding(Integer.parseInt(target));
            }

            public Optional<ResourceBinding> resourceBinding(String target) {
                return Optional.of(binding(Integer.parseInt(target)));
            }

            public boolean click(ScreenClick click, String target) {
                return transfer(click, Integer.parseInt(target));
            }
        };
    }

    private static ResourceBinding binding(int tank) {
        return new ResourceBinding("fluids", tank);
    }

    private static FluidStorage storage(ScreenHost host, int tank) {
        var storage = host.screenFluids();
        if (storage == null || tank < 0 || tank >= storage.getTanks())
            throw new IllegalArgumentException("Unknown screen tank: " + tank);
        return storage;
    }

    private static boolean transfer(ScreenClick click, int index) {
        if (!(click.menu().blockEntity instanceof ScreenHost host))
            return false;
        var carried = click.menu().getCarried();
        if (carried.isEmpty() || FluidUtil.getFluidHandler(carried).isEmpty())
            return false;
        var tank = playerView(storage(host, index), index);
        var inventory = click.player()
            .getCapability(Capabilities.ItemHandler.ENTITY);
        if (inventory == null)
            return true;
        var result = click.button() == 0
            ? FluidUtil.tryEmptyContainerAndStow(carried, tank, inventory,
                Integer.MAX_VALUE, click.player(), true)
            : FluidUtil.tryFillContainerAndStow(carried, tank, inventory,
                Integer.MAX_VALUE, click.player(), true);
        if (result.isSuccess())
            click.menu().setCarried(result.getResult());
        return true;
    }

    /**
     * Single-tank player access; storage still enforces its filters and
     * reservation plans.
     */
    private static IFluidHandler playerView(FluidStorage storage, int index) {
        return new IFluidHandler() {
            public int getTanks() {
                return 1;
            }

            public FluidStack getFluidInTank(int tank) {
                return tank == 0
                    ? storage.getFluidInTank(index)
                    : FluidStack.EMPTY;
            }

            public int getTankCapacity(int tank) {
                return tank == 0 ? storage.getTankCapacity(index) : 0;
            }

            public boolean isFluidValid(int tank, FluidStack stack) {
                return tank == 0 && storage.isFluidValid(index, stack);
            }

            public int fill(FluidStack stack, FluidAction action) {
                return isFluidValid(0, stack)
                    ? storage.tank(index).fill(stack, action)
                    : 0;
            }

            public FluidStack drain(FluidStack stack, FluidAction action) {
                return storage.tank(index).drain(stack, action);
            }

            public FluidStack drain(int amount, FluidAction action) {
                return storage.tank(index).drain(amount, action);
            }
        };
    }

    @Override
    public List<Component> tooltip(ScreenHost host, ItemStack carried) {
        var storage = storage(host, tank);
        var current = storage.getFluidInTank(tank);
        Component name = host.screenTankName(tank).orElseGet(
            () -> Component.translatable("screen.mmdlib.tank.name", tank + 1));
        var accepted = BuiltInRegistries.FLUID.stream()
            .filter(fluid -> fluid != Fluids.EMPTY
                && fluid.isSource(fluid.defaultFluidState()))
            .map(fluid -> new FluidStack(fluid, 1))
            .filter(fluid -> storage.isFluidValid(tank, fluid))
            .map(FluidStack::getHoverName).toList();
        var lines = new ArrayList<Component>();
        lines.add(name.copy().withStyle(ChatFormatting.GOLD));
        lines.add(current.isEmpty()
            ? Component.translatable("screen.mmdlib.tank.empty")
                .withStyle(ChatFormatting.GRAY)
            : Component.translatable("screen.mmdlib.tank.contents",
                current.getAmount(), current.getHoverName()));
        var names = Component.empty();
        for (var acceptedName : accepted) {
            if (!names.getSiblings().isEmpty())
                names.append(", ");
            names.append(acceptedName.copy());
        }
        lines.add(Component.translatable("screen.mmdlib.tank.accepts",
            accepted.isEmpty()
                ? Component.translatable("screen.mmdlib.tank.none")
                : names)
            .withStyle(ChatFormatting.BLUE));
        if (!carried.isEmpty())
            FluidUtil.getFluidHandler(carried.copy()).ifPresent(handler -> {
                Component container = carried.getItem() instanceof BucketItem
                    ? Component.translatable(Items.BUCKET.getDescriptionId())
                    : carried.getHoverName();
                var seen = new ArrayList<FluidStack>();
                for (int i = 0; i < handler.getTanks(); i++) {
                    var fluid = handler.getFluidInTank(i);
                    if (fluid.isEmpty()
                        || seen.stream().anyMatch(old -> FluidStack
                            .isSameFluidSameComponents(old, fluid)))
                        continue;
                    seen.add(fluid.copy());
                    boolean allowed = storage.isFluidValid(tank, fluid)
                        && (current.isEmpty() || FluidStack
                            .isSameFluidSameComponents(current, fluid))
                        && !handler.drain(fluid.copy(),
                            IFluidHandler.FluidAction.SIMULATE).isEmpty();
                    lines.add(Component
                        .translatable(
                            allowed
                                ? "screen.mmdlib.tank.container_accepts"
                                : "screen.mmdlib.tank.container_rejects",
                            fluid.getHoverName(), container)
                        .withStyle(allowed
                            ? ChatFormatting.BLUE
                            : ChatFormatting.RED));
                }
            });
        return List.copyOf(lines);
    }

    public int[] measure(TextMeasure text) {
        return ElementLayouts.oriented(orientation, 18, fills() ? 8 : length);
    }

    public boolean stretchX() {
        return fills();
    }

    public boolean stretchY() {
        return fills();
    }

    public Optional<Orientation> fillOrientation() {
        return fills() ? Optional.of(orientation()) : Optional.empty();
    }

    public Common common() {
        return common;
    }

    public int tank() {
        return tank;
    }

    public Orientation orientation() {
        return orientation;
    }

    public int length() {
        return length;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (FluidTankElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(tank, other.tank)
            && java.util.Objects.equals(orientation, other.orientation)
            && java.util.Objects.equals(length, other.length);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(tank);
        hash = 31 * hash + java.util.Objects.hashCode(orientation);
        hash = 31 * hash + java.util.Objects.hashCode(length);
        return hash;
    }

    @Override
    public String toString() {
        return "FluidTankElement[common=" + common + ", tank=" + tank
            + ", orientation=" + orientation + ", length=" + length + "]";
    }
}
