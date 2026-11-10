package zone.moddev.mc.companion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.companion.CompanionWidgets;
import zone.moddev.mc.lib.gui.client.WidgetAccess;
import zone.moddev.mc.lib.gui.client.WidgetInstance;
import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Vanilla text editing supplies selection, cursor movement and clipboard
 * shortcuts.
 */
final class NameEditor implements WidgetInstance {
    private final WidgetAccess access;
    private final EditBox field;
    private boolean dirty, syncing;
    private String status = "";
    private int selectionAnchor;
    private boolean selecting;

    NameEditor(WidgetAccess access) {
        this.access = access;
        field = new EditBox(Minecraft.getInstance().font,
            access.bounds().width(), 18, WidgetDrawing.text("name"));
        field.setMaxLength(32);
        field.setResponder(value -> {
            if (!syncing) {
                dirty = true;
                status = "";
            }
        });
    }

    private void position() {
        var r = access.bounds();
        field.setX(r.x());
        field.setY(r.y());
        field.setWidth(r.width());
    }

    private Rect applyBounds() {
        var r = access.bounds();
        return new Rect(r.x(), r.y() + 22, 44, 18);
    }

    private Rect cancelBounds() {
        var r = access.bounds();
        return new Rect(r.x() + 48, r.y() + 22, 44, 18);
    }

    private void reset() {
        if (access.value() instanceof Component value) {
            syncing = true;
            if (!field.getValue().equals(value.getString()))
                field.setValue(value.getString());
            syncing = false;
            dirty = false;
        }
        status = "";
    }

    @Override
    public void render(Context context) {
        position();
        if (!dirty && !access.pending())
            reset();
        field.setEditable(!access.pending());
        field.render(context.graphics(), context.mouseX(), context.mouseY(),
            context.partialTick());
        WidgetDrawing.button(context.graphics(), applyBounds(), "apply", false,
            !access.pending());
        WidgetDrawing.button(context.graphics(), cancelBounds(), "cancel",
            false, !access.pending());
        String message = access.pending() ? "waiting" : status;
        if (!message.isEmpty())
            context.graphics().drawString(Minecraft.getInstance().font,
                WidgetDrawing.text(message), access.bounds().x() + 96,
                access.bounds().y() + 27, 0xff883333, false);
    }

    private void apply() {
        if (access.pending())
            return;
        if (field.getValue().isBlank()) {
            status = "name_required";
            return;
        }
        access.submit(CompanionWidgets.RENAME, field.getValue());
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        field.setFocused(focused);
    }

    @Override
    public Click mouseClicked(double x, double y, int button) {
        position();
        if (button != 0)
            return Click.CONSUME;
        if (applyBounds().contains(x, y))
            apply();
        else if (cancelBounds().contains(x, y)) {
            if (!access.pending())
                reset();
        } else if (!access.pending() && field.mouseClicked(x, y, button)) {
            selectionAnchor = field.getCursorPosition();
            selecting = true;
            return Click.CAPTURE;
        }
        return Click.CONSUME;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) {
            if (!dirty || access.pending())
                return false;
            reset();
            return true;
        }
        if (key == 257 || key == 335) {
            apply();
            return true;
        }
        return !access.pending() && field.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return !access.pending() && field.charTyped(character, modifiers);
    }

    @Override
    public void resized() {
        position();
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx,
        double dy) {
        if (selecting) {
            field.onClick(x, y);
            field.setHighlightPos(selectionAnchor);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = selecting;
        selecting = false;
        return handled;
    }

    @Override
    public void cancelInteraction() {
        selecting = false;
    }

    @Override
    public void visibilityChanged(boolean visible) {
        if (!visible)
            reset();
    }

    @Override
    public void actionResult(boolean accepted) {
        if (accepted)
            reset();
        else {
            dirty = true;
            status = "unconfirmed";
        }
    }
}
