package zone.moddev.mc.lib.machine.inventory;

import java.util.Set;

/** Save keys owned by machine persistence/sync rather than consumer storage. */
public final class MachineStorageKeys {
    public static final Set<String> RESERVED = Set.of("automation", "features",
        "redstone_control", "redstone_mode", "id", "components",
        "mmdlib_features", "mmdlib_machine");

    private MachineStorageKeys() {
    }
}
