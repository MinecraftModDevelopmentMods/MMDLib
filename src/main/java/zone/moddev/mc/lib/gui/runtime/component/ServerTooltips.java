package zone.moddev.mc.lib.gui.runtime.component;

import java.util.List;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * Read-only synchronized tooltip rows. Element hooks own their meaning and
 * composition.
 */
public interface ServerTooltips extends MenuComponent {
    List<Component> lines(Element element);
}
