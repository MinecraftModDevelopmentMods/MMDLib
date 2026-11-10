package zone.moddev.mc.lib.gui.model.containers;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;

/** Optional named selection used by navigation controls and compatibility menu helpers. */
public interface NavigableElement extends Element {
    /** Named destination used when a control toggles its selected view off. */
    String defaultSelection();

    /**
     * Whether a declared destination belongs to this container. Must not depend on client state.
     */
    boolean hasSelection(String selection);

    String selected(ElementStateView state);

    boolean select(ElementStates state, String selection);
}
