package zone.moddev.mc.lib.gui.model;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;

/**
 * Client tooltip inputs without client-only classes or access to machine
 * storage.
 */
public interface TooltipContext {
    /** The originally hovered element, unchanged as parents contribute. */
    Element target();

    /** The element currently contributing rows. */
    Element element();

    /**
     * Immediate child on the path to the target; empty for the target itself.
     */
    Optional<Element> child();

    int mouseX();

    int mouseY();

    boolean shiftDown();

    boolean controlDown();

    boolean altDown();

    Object value(Element element, boolean maximum);

    default Object value() {
        return value(element(), false);
    }

    default Object maximum() {
        return value(element(), true);
    }

    Component text(Text text);

    /** Detached server-authored rows for the contributing element. */
    List<Component> serverLines();

    /**
     * Standard authored/translated/value rows; does not invoke behavior or
     * ancestor hooks.
     */
    void appendDefaults(List<Component> rows);
}
