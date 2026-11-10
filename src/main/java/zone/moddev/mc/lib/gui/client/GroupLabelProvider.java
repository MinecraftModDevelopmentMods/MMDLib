package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Text;

/** Optional group caption supplied by an element's registered client behavior. */
public interface GroupLabelProvider {
    Optional<Text> groupLabel();
}
