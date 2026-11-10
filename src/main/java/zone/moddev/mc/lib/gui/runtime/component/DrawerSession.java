package zone.moddev.mc.lib.gui.runtime.component;

import net.minecraft.resources.ResourceLocation;

/** Server-authoritative drawer state. Its registered type determines the exclusion edge. */
public interface DrawerSession extends MenuComponent {
    ResourceLocation drawerType();

    void closeServer();
}
