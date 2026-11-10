package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStateView;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSync;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;
import zone.moddev.mc.lib.gui.runtime.slot.ReservationMenuComponent;
import zone.moddev.mc.lib.gui.runtime.slot.SlotAdapter;
import zone.moddev.mc.lib.gui.runtime.slot.SlotTransferContext;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

final class SlotPolicySmoke {
    private static class Policy implements SlotElement {
        private final SlotKind kind;
        private final Boolean overlay;
        private final Function<SlotTransferContext, List<Slot>> route;

        Policy(SlotKind kind, Boolean overlay, Function<SlotTransferContext, List<Slot>> route) {
            this.kind = kind;
            this.overlay = overlay;
            this.route = route;
        }

        public Common common() {
            return Common.empty();
        }

        public int index() {
            return 0;
        }

        public SlotKind kind() {
            return kind;
        }

        public Appearance appearance() {
            return Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());
        }

        public int[] itemPosition(Rect bounds) {
            return new int[]{bounds.x() + 4, bounds.y() + 6};
        }

        public boolean reservationSync(MenuSlots.Entry entry) {
            return overlay == null ? SlotElement.super.reservationSync(entry) : true;
        }

        public boolean reservationOverlay(MenuSlots.Entry entry) {
            return overlay == null ? SlotElement.super.reservationOverlay(entry) : overlay;
        }

        public List<Slot> quickMoveTargets(SlotTransferContext context) {
            return route == null
                ? SlotElement.super.quickMoveTargets(context)
                : route.apply(context);
        }
    }

    private static final class PlannedSlot extends Slot implements SlotAdapter {
        SlotPlan plan;

        PlannedSlot(SlotPlan plan) {
            super(new SimpleContainer(1), 0, 0, 0);
            this.plan = plan;
        }

        public SlotPlan plan() {
            return plan;
        }

        public void applyPlan(SlotPlan value) {
            plan = value;
        }
    }

    static void check(Player player) {
        var rect = new Rect(10, 20, 26, 26);
        for (var kind : List.of(SlotKind.NORMAL, SlotKind.GHOST)) {
            var element = new Policy(kind, null, null);
            var slot = element.createSlot(new ItemStackHandler(1), 0, rect, () -> true, () -> false,
                () -> {
                });
            require(
                slot.x == 14 && slot.y == 26,
                "Default adapter uses direct implementation positioning: " + kind);
        }
        ghostFactories();
        selection(player);
        adapters(player);
        overlays();
        routing(player);
        defaults(player);
    }

    private static final class Mirror extends zone.moddev.mc.lib.api.capabilities.PlannedItemHandler
        implements
            zone.moddev.mc.lib.api.capabilities.ReservationReceiver {
        Mirror() {
            super(1);
        }
    }

    private static class CustomItemAdapter extends ItemSlotElement.MenuSlot {
        CustomItemAdapter(Mirror mirror) {
            super(mirror, 0, 3, 5, () -> true, () -> false, () -> {
            }, false);
        }

        public int getMaxStackSize() {
            return 12;
        }
    }

    private static class CustomPlayerAdapter extends PlayerSlotElement.PlayerMenuSlot {
        CustomPlayerAdapter(Player player) {
            super(player.getInventory(), 39, 2, 4, () -> true);
        }

        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.DIAMOND_HELMET) && super.mayPlace(stack);
        }
    }

    private static void adapters(Player player) {
        var mirror = new Mirror();
        mirror.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        var slot = new CustomItemAdapter(mirror);
        slot.applyPlan(new SlotPlan(3, ItemStack.EMPTY));
        require(
            mirror.plan(0).take() == 3 && slot.getMaxStackSize() == 12,
            "Subclass adapter applies plans through storage contract");
        require(
            slot.remove(8).getCount() == 5 && mirror.getStackInSlot(0).getCount() == 3,
            "Subclass retains reservation extraction rules");
        slot.applyPlan(SlotPlan.NONE);
        require(slot.remove(8).getCount() == 3, "Empty plan restores extraction");
        var helmet = new CustomPlayerAdapter(player);
        require(
            helmet.mayPlace(new ItemStack(Items.DIAMOND_HELMET))
                && !helmet.mayPlace(new ItemStack(Items.IRON_HELMET)),
            "Player adapter can refine equipment rules");
        require(
            helmet.getMaxStackSize() == 1 && !helmet.mayPlace(new ItemStack(Items.STONE)),
            "Player subclass retains vanilla armour rules");
        var old = player.getInventory().getItem(39).copy();
        helmet.setByPlayer(new ItemStack(Items.DIAMOND_HELMET), old);
        require(
            player.getInventory().getItem(39).is(Items.DIAMOND_HELMET),
            "Inherited player setter equips item");
        helmet.setByPlayer(old, helmet.getItem());
    }

    private static void selection(Player player) {
        ItemStack[] stored = {ItemStack.EMPTY};
        var host = new zone.moddev.mc.lib.gui.runtime.ScreenHost() {
            public ItemStackHandler screenItems() {
                return new ItemStackHandler(0);
            }

            public boolean setScreenGhost(
                Player viewer, String inventory, int index, ItemStack stack) {
                require(
                    viewer == player && inventory.equals("custom") && index == 0,
                    "Selection address");
                stored[0] = stack;
                return true;
            }

            public ItemStack screenGhost(String inventory, int index) {
                return stored[0];
            }
        };
        var direct = new Policy(SlotKind.GHOST, null, null) {
            public ItemStack selection(int button, ItemStack stack) {
                stack.setCount(7);
                return stack;
            }
        };
        var derived = new GhostSlotElement(Common.empty(), 0, direct.appearance()) {
            public ItemStack selection(int button, ItemStack stack) {
                return stack.copyWithCount(3);
            }
        };
        var input = new ItemStack(Items.DIAMOND, 12);
        for (var element : List.of(direct, derived)) {
            require(element.accept(host, "custom", player, input), "Custom selection accepted");
            require(
                stored[0].getCount() == (element == direct ? 7 : 3),
                "Original element normalizes selection");
            require(input.getCount() == 12, "Selection cannot mutate the submitted stack");
            element.read(host, "custom").setCount(1);
            require(stored[0].getCount() != 1, "Ghost reads are detached");
        }
        var defaults = new Policy(SlotKind.GHOST, null, null);
        defaults.accept(host, "custom", player, input);
        require(stored[0].getCount() == 1, "Default selection normalizes to one");
        require(defaults.selection(1, input).isEmpty(), "Right click clears selection");
        defaults.accept(host, "custom", player, ItemStack.EMPTY);
        require(stored[0].isEmpty(), "Empty selection clears host state");
    }

    private static void ghostFactories() {
        var created = new ArrayList<Slot>();
        var element = new GhostSlotElement(
            Common.empty(),
            0,
            SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty())) {
            public Slot createSlot(Rect bounds, java.util.function.BooleanSupplier visible) {
                var result = super.createSlot(bounds, visible);
                created.add(result);
                return result;
            }

            public int[] itemPosition(Rect bounds) {
                return new int[]{bounds.x() + 3, bounds.y() + 5};
            }
        };
        var screen = new Screen(
            net.minecraft.resources.ResourceLocation.parse("test:ghost_factory"),
            "ghost",
            new Text("test", Optional.empty()),
            "test.Host",
            false,
            List.of(element),
            List.of());
        var layout = zone.moddev.mc.lib.gui.layout.ScreenLayout.of(
            screen, zone.moddev.mc.lib.gui.layout.TextMeasure.NONE);
        for (boolean client : new boolean[]{false, true}) {
            var session = new zone.moddev.mc.lib.gui.model.containers.InventoryElement.Session(
                screen, null, layout, client, e -> true, () -> false, () -> {
                });
            require(
                created.size() == (client ? 2 : 1) && session.slot(element) == created.getLast(),
                "Menu assembly dispatches to the original ghost factory on both sides");
            var xy = element.itemPosition(layout.bounds(element));
            require(
                session.slot(element).x == xy[0] && session.slot(element).y == xy[1],
                "Ghost factory retains subclass geometry");
        }
    }

    private static void overlays() {
        var slots = new MenuSlots();
        var off = new Policy(SlotKind.NORMAL, false, null);
        var on = new Policy(SlotKind.NORMAL, true, null);
        var plain = new Policy(SlotKind.NORMAL, null, null);
        var hidden = new PlannedSlot(new SlotPlan(2, ItemStack.EMPTY));
        var auxiliary = new PlannedSlot(new SlotPlan(1, ItemStack.EMPTY));
        add(slots, hidden, off, MenuSlots.Role.MACHINE);
        add(slots, auxiliary, on, MenuSlots.Role.AUXILIARY);
        add(
            slots,
            new Slot(new SimpleContainer(1), 0, 0, 0),
            new Policy(SlotKind.NORMAL, true, null),
            MenuSlots.Role.EQUIPMENT);
        require(
            !slots.entry(0).overlay() && slots.entry(1).overlay() && slots.entry(2).overlay(),
            "Overlay policy is independent of role and adapter");
        require(
            new MenuSlots.Entry(hidden, plain, MenuSlots.Role.MACHINE).overlay(),
            "Default machine overlay retained");
        require(
            !new MenuSlots.Entry(hidden, plain, MenuSlots.Role.AUXILIARY).overlay(),
            "Default auxiliary overlay retained");
        var snapshots = new HashMap<ComponentSync<?>, Object>();
        new ReservationMenuComponent(slots, false)
            .snapshot(
                new ComponentSnapshot() {
                    public <T> void put(ComponentSync<T> field, T value) {
                        snapshots.put(field, value);
                    }
                });
        var plans = (Map<?, ?>) snapshots.get(ReservationMenuComponent.PLANS);
        require(
            plans.size() == 2
                && ((SlotPlan) plans.get(0)).take() == 2
                && ((SlotPlan) plans.get(1)).take() == 1,
            "All adapters synchronize reservations even with hidden overlays or custom roles");
        hidden.plan = auxiliary.plan = SlotPlan.NONE;
        var receiver = new ReservationMenuComponent(slots, true);
        receiver.applied(
            new ComponentStateView() {
                @SuppressWarnings("unchecked")
                public <T> T get(ComponentSync<T> field) {
                    return (T) snapshots.get(field);
                }
            });
        require(
            hidden.plan.take() == 2 && auxiliary.plan.take() == 1,
            "Hidden reservations still apply; plain visual slots need no adapter");
        snapshots.put(ReservationMenuComponent.PLANS, Map.of());
        receiver.applied(
            new ComponentStateView() {
                @SuppressWarnings("unchecked")
                public <T> T get(ComponentSync<T> field) {
                    return (T) snapshots.get(field);
                }
            });
        require(
            hidden.plan.isNone() && auxiliary.plan.isNone(),
            "Complete empty snapshot clears all adapters");
    }

    private static void routing(Player player) {
        var slots = new MenuSlots();
        var source = new Slot(new SimpleContainer(1), 0, 0, 0);
        var foreign = new Slot(new SimpleContainer(1), 0, 0, 0);
        var inactive = new Slot(new SimpleContainer(1), 0, 0, 0) {
            public boolean isActive() {
                return false;
            }
        };
        var rejected = new Slot(new SimpleContainer(1), 0, 0, 0) {
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
        var first = new Slot(new SimpleContainer(1), 0, 0, 0);
        var second = new Slot(new SimpleContainer(1), 0, 0, 0) {
            public int getMaxStackSize() {
                return 2;
            }
        };
        var policy = new Policy(
            SlotKind.NORMAL,
            null,
            context -> {
                require(
                    context.source().slot() == source && context.entries().size() == 5,
                    "Source and registered entries available");
                context.stack().setCount(999);
                require(context.stack().getCount() == 5, "Routing stack is a defensive copy");
                return List.of(foreign, source, inactive, rejected, second, second, first);
            });
        add(slots, source, policy, MenuSlots.Role.EQUIPMENT);
        for (var target : List.of(inactive, rejected, first, second))
            add(slots, target, null, MenuSlots.Role.AUXILIARY);
        source.set(new ItemStack(Items.DIAMOND, 5));
        var visited = new ArrayList<Integer>();
        var moved = slots.quickMove(
            player,
            0,
            stack -> {
                throw new AssertionError("Custom routing need not evaluate machine targets");
            },
            mover(slots, visited));
        require(
            moved.getCount() == 5 && source.getItem().isEmpty(),
            "Custom routing moves the entire source");
        require(
            visited.equals(List.of(4, 3)),
            "Custom order retained; duplicates, foreign/self/hidden/rejected slots excluded");
        require(
            second.getItem().getCount() == 2 && first.getItem().getCount() == 3,
            "Custom destination order and capacities respected");
        require(
            foreign.getItem().isEmpty() && inactive.getItem().isEmpty()
                && rejected.getItem().isEmpty(),
            "Invalid targets unchanged");
        var disabled = new MenuSlots();
        var noMove = new Slot(new SimpleContainer(new ItemStack(Items.DIAMOND)), 0, 0, 0);
        add(
            disabled,
            noMove,
            new Policy(SlotKind.NORMAL, null, c -> List.of()),
            MenuSlots.Role.MACHINE);
        require(
            disabled
                .quickMove(player, 0, stack -> List.of(), mover(disabled, new ArrayList<>()))
                .isEmpty()
                && noMove.getItem().getCount() == 1,
            "Empty custom route refuses movement without item loss");
    }

    private static void defaults(Player player) {
        var slots = new MenuSlots();
        var source = new Slot(new SimpleContainer(new ItemStack(Items.DIAMOND)), 0, 0, 0);
        var a = new Slot(new SimpleContainer(1), 0, 0, 0);
        var b = new Slot(new SimpleContainer(1), 0, 0, 0);
        add(slots, source, new Policy(SlotKind.NORMAL, null, null), MenuSlots.Role.MACHINE);
        add(slots, a, null, MenuSlots.Role.PLAYER_STORAGE);
        add(slots, b, null, MenuSlots.Role.PLAYER_STORAGE);
        slots.quickMove(
            player,
            0,
            stack -> {
                throw new AssertionError("Machine source uses player storage");
            },
            mover(slots, new ArrayList<>()));
        require(
            a.getItem().isEmpty() && b.getItem().is(Items.DIAMOND),
            "Default player storage order remains reversed");
        slots.quickMove(
            player,
            2,
            stack -> {
                stack.setCount(64);
                return List.of(source);
            },
            mover(slots, new ArrayList<>()));
        require(
            source.getItem().getCount() == 1 && b.getItem().isEmpty(),
            "Elementless player source uses machine destinations with copied stack");
        a.set(new ItemStack(Items.DIAMOND, 1));
        slots.quickMove(player, 0, stack -> List.of(), mover(slots, new ArrayList<>()));
        require(
            a.getItem().getCount() == 2 && b.getItem().isEmpty(),
            "Merge pass precedes empty slots despite reversed candidate order");
    }

    private static MenuSlots.Move mover(MenuSlots slots, List<Integer> visited) {
        return (stack, start, end, reverse) -> {
            require(end == start + 1 && !reverse,
                "Shared executor moves individual eligible targets");
            visited.add(start);
            var target = slots.entry(start).slot();
            int current = target.getItem().getCount();
            int amount = Math.min(
                stack.getCount(),
                Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize()) - current);
            if (amount <= 0)
                return false;
            target.set(stack.copyWithCount(current + amount));
            stack.shrink(amount);
            return true;
        };
    }

    private static void add(MenuSlots slots, Slot slot, SlotElement element, MenuSlots.Role role) {
        slots.registrar(value -> value.index = slots.entries().size()).add(slot, element, role);
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
