package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.RedstoneLayout;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.runtime.RedstoneMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Per-face redstone setup using the same neighbour grid and frame as the
 * inventory drawer.
 */
public final class RedstoneDrawer extends Drawer {
    private final RedstoneMenu model;
    private final FaceConfigRenderer faces = new FaceConfigRenderer();
    private static final RelativeFace[] FACES = RelativeFace.values();
    private static final ResourceLocation DISABLED = ResourceLocation
        .parse("mmdlib:side_mode_disabled"),
        BETWEEN = ResourceLocation.parse("mmdlib:icon_redstone_between"),
        OUTSIDE = ResourceLocation.parse("mmdlib:icon_redstone_outside"),
        REVERT = ResourceLocation.parse("mmdlib:icon_redstone_revert"),
        REVERT_ON = ResourceLocation.parse("mmdlib:icon_redstone_revert_on"),
        RUNNING = ResourceLocation.parse("mmdlib:icon_redstone_running"),
        PAUSED = ResourceLocation.parse("mmdlib:icon_redstone_paused");
    private Language textLanguage;
    private Font textFont;
    private long infoRevision = -1;
    private int infoFace = -1, infoLow, infoHigh, infoSignal, nameSource = -2;
    private List<FormattedCharSequence> cachedInfo = List.of(),
        cachedName = List.of();
    private Component outputName = Component.empty();
    private Rect tab, body;
    private List<Component> tooltip = List.of();
    private boolean handled;
    private static final float INFO_SCALE = .75f, SIGNAL_SCALE = .625f;
    private int editorScroll;
    private int sliderHandle = -1, sliderFace, previewLow, previewHigh;
    private long sliderRevision;

    public RedstoneDrawer(MachineScreen screen,
        zone.moddev.mc.lib.gui.drawer.DrawerDefinition definition) {
        super(screen, definition,
            IconSprite.item(() -> new ItemStack(Items.REDSTONE)), 0xc78181);
        model = screen.getMenu().components()
            .get(BuiltinMenuComponents.REDSTONE);
    }

    void layout() {
        var panel = screen.panelBounds();
        tab = super.tabBounds();
        if (!model.open())
            return;
        int height = contentHeight();
        body = SideDrawerLayout.body(panel, screen.drawerBottom(),
            SideDrawerLayout.bodyX(edge(), tab, RedstoneLayout.WIDTH),
            panel.y() + AutomationLayout.TOP, RedstoneLayout.WIDTH, height,
            SideDrawerLayout.HeightLimit.SCREEN);
        editorScroll = Math.clamp(editorScroll, 0,
            Math.max(0, height - body.height()));
    }

    private Rect at(Rect rect) {
        return rect.offset(body.x(), body.y());
    }

    private Rect controlAt(Rect rect) {
        return at(rect).offset(0, -editorScroll);
    }

    private int low() {
        return sliderHandle >= 0 ? previewLow : model.lower(model.selected());
    }

    private int high() {
        return sliderHandle >= 0 ? previewHigh : model.upper(model.selected());
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.mmdlib.redstone." + key, args);
    }

    public void render(GuiGraphics g, int mx, int my) {
        layout();
        tooltip = List.of();
        drawFrame(g, tab, body, model.open());
        if (tab.contains(mx, my))
            tooltip = List.of(text("title"));
        if (!model.open())
            return;
        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        for (int face = 0; face < 6; face++) {
            var r = at(RedstoneLayout.face(face));
            var mode = model.mode(face);
            var neighbour = faces.render(g, r, screen.getMenu().blockEntity,
                model.control.world(face), mode == RedstoneControl.Mode.READ,
                mode == RedstoneControl.Mode.EMIT);
            if (mode == RedstoneControl.Mode.IGNORE) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.blitSprite(DISABLED, r.x() + 1, r.y() + 1, 7, 7);
                g.pose().popPose();
            }
            if (mode != RedstoneControl.Mode.IGNORE) {
                var font = Minecraft.getInstance().font;
                boolean input = mode == RedstoneControl.Mode.READ;
                String strength = Integer.toString(
                    input ? model.incoming(face) : model.outgoing(face));
                float x = r.right() - font.width(strength) * SIGNAL_SCALE - 2;
                int y = input ? r.bottom() - 6 : r.y() + 2;
                g.pose().pushPose();
                g.pose().translate(x, y, 210);
                g.pose().scale(SIGNAL_SCALE, SIGNAL_SCALE, 1);
                // A thin glyph outline keeps the small digits legible without a
                // background panel.
                g.drawString(font, strength, -1, 0, 0xff182018, false);
                g.drawString(font, strength, 1, 0, 0xff182018, false);
                g.drawString(font, strength, 0, -1, 0xff182018, false);
                g.drawString(font, strength, 0, 1, 0xff182018, false);
                g.drawString(font, strength, 0, 0,
                    input ? 0xffa6ff54 : 0xffffc36b, false);
                g.pose().popPose();
            }
            if (r.contains(mx, my))
                tooltip = List.of(
                    Component.translatable("screen.mmdlib.face."
                        + FACES[face].getSerializedName()),
                    neighbour,
                    text("mode." + mode.name().toLowerCase(Locale.ROOT)),
                    text("strength", model.incoming(face),
                        model.outgoing(face)),
                    text("face.tip"));
        }
        // Draw last and above the neighbour models, so the front face stays
        // clearly selected.
        var selectedFace = at(RedstoneLayout.face(model.selected()));
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        g.renderOutline(selectedFace.x() - 1, selectedFace.y() - 1,
            selectedFace.width() + 2, selectedFace.height() + 2, 0xffeeeeee);
        g.pose().popPose();
        renderControls(g, mx, my);
        renderInfo(g);
        g.disableScissor();
    }

    private int settingsHeight() {
        return switch (model.mode(model.selected())) {
            case READ -> 115;
            case EMIT -> 102;
            default -> 74;
        };
    }

    private Component explanation() {
        int face = model.selected();
        return switch (model.mode(face)) {
            case READ -> rangeExplanation().copy().append("\n")
                .append(text("info.input", model.incoming(face)));
            case EMIT -> {
                int group = model.source(face);
                var message = group >= 0
                    && group < model.control.outputs().size()
                        ? model.control.outputs().get(group).description()
                            .copy()
                            .append(model.inverted(face)
                                ? text("source.reverted").copy()
                                    .withStyle(ChatFormatting.DARK_RED)
                                : Component.empty())
                        : text("info.unconfigured").copy();
                yield message.append("\n")
                    .append(text("info.output", model.outgoing(face)));
            }
            default -> text("info.ignore");
        };
    }

    private Component rangeExplanation() {
        int lower = low(), upper = high();
        boolean outside = model.outside(model.selected());
        if (lower == upper)
            return text(outside ? "info.except" : "info.for", lower);
        if (lower == 0 && upper == 15)
            return text(outside ? "info.never" : "info.always");
        if (lower == 0)
            return text(outside ? "info.above" : "info.at_or_below", upper);
        if (upper == 15)
            return text(outside ? "info.below" : "info.at_or_above", lower);
        return text(outside ? "info.outside" : "info.between", lower, upper);
    }

    private void checkTextResources() {
        var language = Language.getInstance();
        var font = Minecraft.getInstance().font;
        if (language != textLanguage || font != textFont) {
            textLanguage = language;
            textFont = font;
            infoRevision = -1;
            nameSource = -2;
        }
    }

    private List<FormattedCharSequence> infoLines() {
        checkTextResources();
        int face = model.selected(), lower = low(), upper = high();
        int signal = switch (model.mode(face)) {
            case READ -> model.incoming(face);
            case EMIT -> model.outgoing(face);
            default -> 0;
        };
        if (infoRevision == model.revision() && infoFace == face
            && infoLow == lower && infoHigh == upper && infoSignal == signal)
            return cachedInfo;
        infoRevision = model.revision();
        infoFace = face;
        infoLow = lower;
        infoHigh = upper;
        infoSignal = signal;
        var font = textFont;
        var lines = new ArrayList<FormattedCharSequence>();
        lines.addAll(font.split(
            Component
                .translatable("screen.mmdlib.face."
                    + FACES[model.selected()].getSerializedName())
                .withStyle(ChatFormatting.BOLD),
            (int) ((RedstoneLayout.WIDTH - 18) / INFO_SCALE)));
        lines.addAll(font.split(explanation(),
            (int) ((RedstoneLayout.WIDTH - 18) / INFO_SCALE)));
        return cachedInfo = List.copyOf(lines);
    }

    private int contentHeight() {
        return settingsHeight() + infoLines().size() * 8 + 13;
    }

    private void renderInfo(GuiGraphics g) {
        if (body.height() <= RedstoneLayout.CONTROLS_TOP)
            return;
        var lines = infoLines();
        int x = body.x() + 5, y = body.y() + settingsHeight() - editorScroll;
        int width = RedstoneLayout.WIDTH - 10, height = lines.size() * 8 + 8;
        g.enableScissor(body.x(), body.y() + RedstoneLayout.CONTROLS_TOP,
            body.right(), body.bottom());
        g.fill(x, y, x + width, y + height, 0xffd4bd62);
        g.renderOutline(x, y, width, height, 0xff89752f);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(INFO_SCALE, INFO_SCALE, 1);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(Minecraft.getInstance().font, lines.get(i), 0,
                Math.round(i * 8 / INFO_SCALE), 0xff3b3219, false);
        g.pose().popPose();
        g.disableScissor();
    }

    private void renderControls(GuiGraphics g, int mx, int my) {
        if (body.height() <= RedstoneLayout.CONTROLS_TOP)
            return;
        g.enableScissor(body.x(), body.y() + RedstoneLayout.CONTROLS_TOP,
            body.right(), body.bottom());
        int face = model.selected();
        if (model.mode(face) == RedstoneControl.Mode.READ) {
            var toggle = controlAt(RedstoneLayout.intervalMode());
            flat(g, toggle, mx, my);
            g.renderOutline(toggle.x(), toggle.y(), toggle.width(),
                toggle.height(), 0xff715353);
            g.blitSprite(model.outside(face) ? OUTSIDE : BETWEEN,
                toggle.x() + (toggle.width() - 32) / 2, toggle.y() + 2, 32, 12);
            if (toggle.contains(mx, my))
                tooltip = List.of(
                    text(model.outside(face) ? "outside" : "between"),
                    text("interval.tip"));
            var bar = controlAt(RedstoneLayout.bar());
            for (int value = 0; value < 16; value++) {
                boolean selected = RedstoneControl.matches(value, low(), high(),
                    model.outside(face));
                g.fill(bar.x() + Math.round(value * bar.width() / 16f), bar.y(),
                    bar.x() + Math.round((value + 1) * bar.width() / 16f),
                    bar.bottom(), selected ? 0xff30b84c : 0xff654c4c);
            }
            handle(g, bar, low(), true);
            handle(g, bar, high(), false);
            if (controlAt(RedstoneLayout.range()).contains(mx, my))
                tooltip = List.of(text("range.tip"),
                    text("range.values", low(), high()));
        } else if (model.mode(face) == RedstoneControl.Mode.EMIT) {
            var revert = controlAt(RedstoneLayout.revert());
            flat(g, revert, mx, my);
            g.renderOutline(revert.x(), revert.y(), revert.width(),
                revert.height(),
                model.inverted(face) ? 0xffffdf75 : 0xff715353);
            g.blitSprite(model.inverted(face) ? REVERT_ON : REVERT,
                revert.x() + 2, revert.y() + 2, 14, 14);
            if (revert.contains(mx, my))
                tooltip = List.of(
                    text(model.inverted(face) ? "revert.on" : "revert.off"));
            var r = controlAt(RedstoneLayout.group());
            flat(g, r, mx, my);
            int index = model.source(face);
            var group = index >= 0 && index < model.control.outputs().size()
                ? model.control.outputs().get(index)
                : null;
            if (group != null && group.sprite().isPresent())
                g.blitSprite(group.sprite().get(), r.x() + 2, r.y() + 2, 14,
                    14);
            else
                g.fill(r.x() + 3, r.y() + 3, r.x() + 13, r.bottom() - 3,
                    group == null ? 0xff777777 : 0xff000000 | group.colour());
            checkTextResources();
            if (nameSource != index) {
                nameSource = index;
                outputName = group == null
                    ? text("choose_group")
                    : group.name();
                cachedName = textFont.split(outputName,
                    (int) ((r.width() - 20) / .625f));
            }
            var font = textFont;
            var nameLines = cachedName;
            g.pose().pushPose();
            g.pose().translate(r.x() + 17,
                r.y() + (nameLines.size() > 1 ? 3 : 6), 0);
            g.pose().scale(.625f, .625f, 1);
            for (int line = 0; line < Math.min(2, nameLines.size()); line++)
                g.drawString(font, nameLines.get(line), 0, line * 10,
                    0xff302820, false);
            g.pose().popPose();
            if (r.contains(mx, my))
                tooltip = List.of(outputName, text("control.tip"));
        }
        g.disableScissor();
    }

    private void handle(GuiGraphics g, Rect bar, int value, boolean lower) {
        int x = RedstoneLayout.signalX(bar, value);
        int y = lower ? bar.y() - 5 : bar.bottom() + 1;
        int colour = sliderHandle == (lower ? 0 : 1) ? 0xffffe395 : 0xffe8d6ac;
        g.fill(x - 2, y, x + 3, y + 4, colour);
        g.fill(x, lower ? y + 4 : y - 1, x + 1, lower ? y + 5 : y, colour);
        String valueText = Integer.toString(value);
        float width = Minecraft.getInstance().font.width(valueText)
            * INFO_SCALE;
        smallText(g, Component.literal(valueText), x - width / 2,
            lower ? bar.y() - 14 : bar.bottom() + 7, INFO_SCALE, 0xff302820);
    }

    private void flat(GuiGraphics g, Rect r, int mx, int my) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
            r.contains(mx, my) ? 0xffd4aaaa : 0xffbf9191);
    }

    private void smallText(GuiGraphics g, Component text, float x, float y,
        float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    public void tooltip(GuiGraphics g, int x, int y) {
        if (!tooltip.isEmpty())
            g.renderComponentTooltip(Minecraft.getInstance().font, tooltip, x,
                y);
    }

    public Rect tabBounds() {
        layout();
        return tab;
    }

    public boolean open() {
        return model.open();
    }

    public boolean pending() {
        return model.pending();
    }

    public void close() {
        if (open() && !pending())
            model.send(RedstoneMenu.TOGGLE, model.selected(), 0, 0);
    }

    public Selection selection() {
        return new Selection("", model.selected());
    }

    public boolean restore(Selection selection) {
        if (pending())
            return false;
        if (!open()) {
            model.send(RedstoneMenu.TOGGLE, 0, 0, 0);
            return false;
        }
        if (model.selected() != selection.offset())
            model.send(RedstoneMenu.SELECT, selection.offset(), 0, 0);
        activated();
        return true;
    }

    public void resized() {
        sliderHandle = -1;
        handled = false;
    }

    @Override
    protected void drawIcon(GuiGraphics g, Rect tab) {
        super.drawIcon(g, tab);
        if (model.watching()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.blitSprite(model.paused() ? PAUSED : RUNNING, tab.right() - 9,
                tab.bottom() - 9, 7, 7);
            g.pose().popPose();
        }
    }

    public List<Rect> exclusions() {
        layout();
        return model.open()
            ? List.of(SideDrawerLayout.rail(tab, body), body)
            : List.of(tab);
    }

    public boolean contains(double x, double y) {
        layout();
        return tab.contains(x, y) || model.open() && (body.contains(x, y)
            || SideDrawerLayout.rail(tab, body).contains(x, y));
    }

    private void send(int kind, int face, int column, int value) {
        if (model.send(kind, face, column, value)) {
            if (kind == RedstoneMenu.TOGGLE || kind == RedstoneMenu.SELECT)
                remember(kind != RedstoneMenu.TOGGLE || !model.open(),
                    new Selection("", face));
            if (kind == RedstoneMenu.TOGGLE && !model.open())
                activated();
        }
    }

    public boolean click(double x, double y, int button) {
        if (otherTab(x, y))
            return false;
        if (!contains(x, y))
            return false;
        if (button < 0 || button > 1 || model.pending()) {
            handled = true;
            return true;
        }
        if (tab.contains(x, y)) {
            send(RedstoneMenu.TOGGLE, model.selected(), 0, 0);
            handled = true;
            return true;
        }
        if (!model.open())
            return false;
        int face = model.selected();
        for (int i = 0; i < 6; i++)
            if (at(RedstoneLayout.face(i)).contains(x, y)) {
                if (i != face)
                    send(RedstoneMenu.SELECT, i, 0, 0);
                else
                    send(RedstoneMenu.MODE, face, 0,
                        Math.floorMod(
                            model.mode(face).ordinal() + (button == 0 ? 1 : -1),
                            3));
                handled = true;
                return true;
            }
        if (y < body.y() + RedstoneLayout.CONTROLS_TOP || y >= body.bottom()) {
            handled = true;
            return true;
        }
        if (model.mode(face) == RedstoneControl.Mode.READ) {
            if (controlAt(RedstoneLayout.intervalMode()).contains(x, y))
                send(RedstoneMenu.OUTSIDE, face, 0,
                    model.outside(face) ? 0 : 1);
            else if (button == 0
                && controlAt(RedstoneLayout.range()).contains(x, y)) {
                var bar = controlAt(RedstoneLayout.bar());
                previewLow = model.lower(face);
                previewHigh = model.upper(face);
                sliderHandle = y < bar.y()
                    ? 0
                    : y >= bar.bottom()
                        ? 1
                        : Math.abs(
                            x - RedstoneLayout.signalX(bar, previewLow)) <= Math
                                .abs(x
                                    - RedstoneLayout.signalX(bar, previewHigh))
                                        ? 0
                                        : 1;
                sliderFace = face;
                sliderRevision = model.revision();
                updateSlider(x);
            }
        } else if (model.mode(face) == RedstoneControl.Mode.EMIT
            && controlAt(RedstoneLayout.revert()).contains(x, y)) {
            send(RedstoneMenu.INVERT, face, 0, model.inverted(face) ? 0 : 1);
        } else if (model.mode(face) == RedstoneControl.Mode.EMIT
            && controlAt(RedstoneLayout.group()).contains(x, y)) {
            int count = model.control.outputs().size();
            if (count > 0)
                send(RedstoneMenu.SOURCE, face, 0, Math.floorMod(
                    model.source(face) + (button == 0 ? 1 : -1), count));
        }
        handled = true;
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (sliderHandle >= 0)
            return true;
        if (!contains(x, y) || !model.open())
            return false;
        editorScroll = Math.clamp(editorScroll + (amount < 0 ? 12 : -12), 0,
            Math.max(0, contentHeight() - body.height()));
        return true;
    }

    public boolean key(int key, int scan, int modifiers) {
        if (key == 256 && sliderHandle >= 0) {
            sliderHandle = -1;
            handled = false;
            return true;
        }
        if (key == 256 && model.open() && !model.pending()) {
            send(RedstoneMenu.TOGGLE, model.selected(), 0, 0);
            return true;
        }
        return false;
    }

    private void updateSlider(double x) {
        var bar = controlAt(RedstoneLayout.bar());
        int value = RedstoneLayout.signalAt(bar, x);
        if (sliderHandle == 0) {
            previewLow = value;
            previewHigh = Math.max(previewHigh, value);
        } else {
            previewHigh = value;
            previewLow = Math.min(previewLow, value);
        }
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        if (sliderHandle >= 0 && button == 0)
            updateSlider(x);
        return handled;
    }

    public boolean release(double x, double y, int button) {
        if (sliderHandle >= 0) {
            if (button != 0)
                return true;
            updateSlider(x);
            sliderHandle = -1;
            if (model.open() && model.selected() == sliderFace
                && model.revision() == sliderRevision
                && model.mode(sliderFace) == RedstoneControl.Mode.READ)
                send(RedstoneMenu.RANGE, sliderFace, 0,
                    previewLow * 16 + previewHigh);
        }
        boolean result = handled;
        handled = false;
        return result;
    }
}
