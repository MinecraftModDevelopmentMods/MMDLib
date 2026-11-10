package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Text;

/** Optional human-readable label for a behavior's current value. */
public interface ValueLabelProvider {
    Optional<Text> valueLabel();
}
