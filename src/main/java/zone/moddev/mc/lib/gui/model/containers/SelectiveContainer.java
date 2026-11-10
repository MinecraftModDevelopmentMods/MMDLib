package zone.moddev.mc.lib.gui.model.containers;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Optional child-visibility policy, evaluated identically by client and server. */
public interface SelectiveContainer extends Element {
    boolean childVisible(Element child, ElementStateView state);
}
