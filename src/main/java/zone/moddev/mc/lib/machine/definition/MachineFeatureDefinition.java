package zone.moddev.mc.lib.machine.definition;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.DrawerSettings;

/** Resolved, inherited configuration of one named machine feature. */
public record MachineFeatureDefinition(String id, ResourceLocation type,
    boolean enabled, Map<String, String> options, DrawerSettings drawer) {
    public MachineFeatureDefinition {
        options = Map.copyOf(options);
    }

    public DrawerSettings drawerSettings() {
        return enabled ? drawer : DrawerSettings.HIDDEN;
    }
}
