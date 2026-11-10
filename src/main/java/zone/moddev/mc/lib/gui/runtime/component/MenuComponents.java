package zone.moddev.mc.lib.gui.runtime.component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** Generic lifecycle, slot contributions and server drawer arbitration. */
public final class MenuComponents {
    private final MachineMenu menu;
    private final ComponentStates state;
    private final Map<MenuComponentKey<?>, MenuComponent> values;

    MenuComponents(MachineMenu menu,
        Map<MenuComponentKey<?>, MenuComponent> values, ComponentStates state) {
        this.menu = menu;
        this.state = state;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public <T extends MenuComponent> T get(MenuComponentKey<T> key) {
        return key.type().cast(values.get(key));
    }

    public <T extends MenuComponent> T require(MenuComponentKey<T> key) {
        return Objects.requireNonNull(get(key),
            "Menu component is unavailable: " + key.id());
    }

    public List<MenuComponent> entries() {
        return List.copyOf(values.values());
    }

    public <T> T requireCapability(Class<T> type) {
        var found = values.values().stream().filter(type::isInstance)
            .map(type::cast).toList();
        if (found.size() != 1)
            throw new IllegalArgumentException(
                "Expected one menu capability: " + type.getName());
        return found.getFirst();
    }

    private List<MenuSlotContributor> contributors() {
        return values.values().stream()
            .filter(MenuSlotContributor.class::isInstance)
            .map(MenuSlotContributor.class::cast).toList();
    }

    public void installSlots(MenuSlots.Registrar slots, ScreenLayout layout) {
        contributors().forEach(c -> c.addSlots(slots, layout));
        contributors().forEach(c -> c.finishSlots(slots, layout));
    }

    public void position(ScreenLayout layout) {
        contributors().forEach(c -> c.position(layout));
    }

    public List<Slot> destinations(ItemStack stack) {
        return contributors().stream()
            .sorted(
                Comparator.comparingInt(MenuSlotContributor::transferPriority))
            .flatMap(c -> c.destinations(stack).stream()).distinct().toList();
    }

    public void tickClient() {
        values.values().forEach(MenuComponent::tickClient);
    }

    public void beforeBroadcast() {
        values.values().forEach(MenuComponent::beforeBroadcast);
    }

    public void broadcast() {
        values.values().forEach(MenuComponent::broadcast);
    }

    public void beforeInteraction() {
        values.values().forEach(MenuComponent::beforeInteraction);
    }

    public void afterInteraction() {
        values.values().forEach(MenuComponent::afterInteraction);
    }

    public void removed() {
        values.values().forEach(MenuComponent::removed);
    }

    public boolean pendingInventory() {
        return values.values().stream()
            .anyMatch(MenuComponent::pendingInventory);
    }

    public <T> T state(MenuComponent component, ComponentSync<T> field) {
        return state.get(component, field);
    }

    public Map<Integer, Object> snapshot() {
        return state.sample(values.values());
    }

    public void apply(Map<Integer, Object> delta) {
        state.apply(delta);
    }

    /** Call only after the component authorizes a server-side open request. */
    public void activate(DrawerSession source) {
        if (menu.blockEntity.getLevel().isClientSide)
            throw new IllegalStateException(
                "Drawer arbitration is server-only");
        DrawerSessions.activate(source,
            values.values().stream().filter(DrawerSession.class::isInstance)
                .map(DrawerSession.class::cast).toList(),
            menu.layout().drawers());
    }
}
