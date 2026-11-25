package zone.moddev.mc.lib.gui.drawer;

import java.util.List;

/**
 * Optional presentation of an installed machine feature. Register its drawers'
 * client factories separately. Definitions must agree on both sides.
 */
public interface MachineFeatureDrawers {
    String key();

    List<DrawerDefinition> drawers(DrawerRegistry.Context context);
}
