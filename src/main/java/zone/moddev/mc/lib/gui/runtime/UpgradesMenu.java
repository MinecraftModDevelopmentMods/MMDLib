package zone.moddev.mc.lib.gui.runtime;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement;
import zone.moddev.mc.lib.gui.runtime.component.DrawerSession;

/**
 * Participates in server-side drawer exclusion; ordinary inventory elements own
 * the slots.
 */
public final class UpgradesMenu implements DrawerSession {
    private final MachineMenu menu;
    private final UpgradesDrawerElement element;
    public UpgradesMenu(MachineMenu menu, UpgradesDrawerElement element) {
        this.menu = menu;
        this.element = element;
    }
    public UpgradesDrawerElement element() {
        return element;
    }
    public ResourceLocation drawerType() {
        return BuiltinDrawers.UPGRADES;
    }
    public void open(boolean open) {
        if (open)
            menu.components().activate(this);
        menu.elementStates().set(element, UpgradesDrawerElement.OPEN, open);
    }
    public void closeServer() {
        open(false);
    }
    public boolean pendingInventory() {
        return menu.blockEntity.getLevel().isClientSide
            && menu.elementPending(element);
    }
}
