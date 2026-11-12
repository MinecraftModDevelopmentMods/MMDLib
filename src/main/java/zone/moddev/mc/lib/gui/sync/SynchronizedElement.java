package zone.moddev.mc.lib.gui.sync;

import java.util.List;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * Optional capability for elements with state beyond their ordinary
 * value/maximum pair.
 */
public interface SynchronizedElement extends Element {
    List<ElementSync<?>> synchronizedValues();
}
