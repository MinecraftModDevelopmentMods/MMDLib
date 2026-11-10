package zone.moddev.mc.lib.gui.client;

import java.util.List;

/**
 * One element's state for one open screen. Factories must return fresh instances. Coordinates are
 * absolute GUI pixels. Only a captured press receives dragging and release, even outside its
 * bounds. Hidden widgets lose focus and capture; resize preserves the instance and focus but
 * cancels capture without committing.
 */
public interface ElementBehavior {
    enum Click {
        PASS, CONSUME, CAPTURE
    }

    default boolean focusable() {
        return false;
    }

    default void setFocused(boolean focused) {
    }

    default Click mouseClicked(double x, double y, int button) {
        return Click.PASS;
    }

    default boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return false;
    }

    default boolean mouseReleased(double x, double y, int button) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double dx, double dy) {
        return false;
    }

    default boolean keyPressed(int key, int scan, int modifiers) {
        return false;
    }

    default boolean keyReleased(int key, int scan, int modifiers) {
        return false;
    }

    default boolean charTyped(char character, int modifiers) {
        return false;
    }

    default void tick() {
    }

    default void resized() {
    }

    default void visibilityChanged(boolean visible) {
    }

    default void cancelInteraction() {
    }

    /** Values have already been updated. False also covers an unconfirmed edit after timeout. */
    default void actionResult(boolean accepted) {
    }

    default void removed() {
    }

    default void initialized() {
    }

    /** Identity of mutable state, including through compatibility adapters. */
    default Object stateIdentity() {
        return this;
    }

    default void mouseMoved(double x, double y) {
    }

    /** Enter/leave transitions for the topmost visible hit target; leave precedes removal. */
    default void hoverChanged(boolean hovered) {
    }

    /** Unfocused keyboard shortcuts (for example, a context's Escape/back). */
    default boolean shortcut(int key, int scan, int modifiers) {
        return false;
    }

    default List<net.minecraft.network.chat.Component> tooltip(int x, int y) {
        return List.of();
    }

    /** Client-local additions after the element's own rows. Retains existing tooltip overrides. */
    default void appendTooltip(
        zone.moddev.mc.lib.gui.model.TooltipContext context,
        List<net.minecraft.network.chat.Component> rows) {
        rows.addAll(tooltip(context.mouseX(), context.mouseY()));
    }

    /** Optional client-local additions for a hovered descendant. */
    default void appendChildTooltip(
        zone.moddev.mc.lib.gui.model.TooltipContext context,
        List<net.minecraft.network.chat.Component> rows) {
    }
}
