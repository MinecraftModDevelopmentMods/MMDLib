package zone.moddev.mc.lib.api.redstone;

import java.util.List;

/**
 * Optional runtime-part capability: contributes named, independently selectable
 * redstone signals.
 */
public interface IRedstoneOutput {
    /**
     * Stable ordered descriptors, identical on server and client; signal
     * suppliers run only on the server.
     */
    List<RedstoneOutput> redstoneOutputs();
}
