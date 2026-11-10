package zone.moddev.mc.lib.gui.sync;

import zone.moddev.mc.lib.gui.model.Element;

/** Read-only typed values, available to layout, rendering and client behavior. */
public interface ElementStateView {
    <T> T get(Element element, ElementSync<T> field);

    ElementStateView DEFAULTS = new ElementStateView() {
        public <T> T get(Element element, ElementSync<T> field) {
            return field.initial(element);
        }
    };
}
