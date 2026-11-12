package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.RecipeSelectorElement;

/** One ghost slot, with a transient strip of recipe icons when opened. */
public final class RecipeSelectorBehavior
    implements ElementBehavior, IngredientDropHandler {
    private static final GhostItems GHOSTS = new GhostItems();
    private final RecipeSelectorElement element;
    private final ElementAccess access;

    public RecipeSelectorBehavior(RecipeSelectorElement element,
        ElementAccess access) {
        this.element = element;
        this.access = access;
    }

    private CompoundTag view() {
        return access.presentation().state().get(element,
            RecipeSelectorElement.VIEW);
    }
    private boolean send(String operation, String id, int page) {
        return !access.pending(element)
            && access.submit(RecipeSelectorElement.EDIT,
                new RecipeSelectorElement.Request(operation, id, page));
    }
    private boolean item(ItemStack stack) {
        return !stack.isEmpty() && send("item",
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 0);
    }

    public Optional<Rect> overlayBounds() {
        var state = view();
        return state.getBoolean("open")
            ? Optional.of(chooserBounds(access.bounds(),
                access.presentation().viewport(), state))
            : Optional.empty();
    }

    public void dismissOverlay() {
        send("close", "", 0);
    }

    /** Shared geometry for drawing, input and client smoke checks. */
    public static Rect chooserBounds(Rect slot, Rect viewport,
        CompoundTag state) {
        int count = Math.max(1, state.getList("rows", Tag.TAG_COMPOUND).size());
        int width = count * 18 + 4 + (state.getInt("pages") > 1 ? 24 : 0);
        int x = Math.clamp(slot.x(), viewport.x(),
            Math.max(viewport.x(), viewport.right() - width));
        int y = slot.bottom() + 2;
        if (y + 22 > viewport.bottom())
            y = Math.max(viewport.y(), slot.y() - 24);
        return new Rect(x, y, width, 22);
    }

    private static int choiceIndex(Rect popup, CompoundTag state, double x,
        double y) {
        int start = popup.x() + 2 + (state.getInt("pages") > 1 ? 12 : 0);
        int count = state.getList("rows", Tag.TAG_COMPOUND).size();
        return y >= popup.y() + 2 && y < popup.bottom() - 2 && x >= start
            && x < start + count * 18 ? (int) (x - start) / 18 : -1;
    }

    public Click mouseClicked(double x, double y, int button) {
        if (button != 0 && button != 1)
            return Click.PASS;
        var state = view();
        if (button == 1) {
            send("select", "", 0);
        } else if (access.bounds().contains(x, y)) {
            if (!access.carried().isEmpty())
                item(access.carried().copy());
            else
                send(state.getBoolean("open") ? "close" : "browse", "", 0);
        } else if (state.getBoolean("open")) {
            var popup = overlayBounds().orElseThrow();
            int index = choiceIndex(popup, state, x, y);
            if (index >= 0) {
                send("select", state.getList("rows", Tag.TAG_COMPOUND)
                    .getCompound(index).getString("id"), 0);
            } else if (state.getInt("pages") > 1) {
                int page = state.getInt("page") + (x < popup.x() + 14 ? -1 : 1);
                if (page >= 0 && page < state.getInt("pages"))
                    send("page", "", page);
            }
        }
        return Click.CONSUME;
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        var state = view();
        if (!state.getBoolean("open") || dy == 0)
            return false;
        int page = state.getInt("page") + (dy > 0 ? -1 : 1);
        if (page >= 0 && page < state.getInt("pages"))
            send("page", "", page);
        return true;
    }

    public boolean shortcut(int key, int scan, int modifiers) {
        return key == 256 && view().getBoolean("open") && send("close", "", 0);
    }

    public <T> List<Target<T>> targets(IngredientType<T> type, T ingredient) {
        if (access.pending(element) || !(ingredient instanceof ItemStack stack)
            || stack.isEmpty())
            return List.of();
        return List.of(new Target<>("recipe", access.bounds(),
            value -> value instanceof ItemStack item && item(item)));
    }

    private static ItemStack icon(CompoundTag row) {
        var level = Minecraft.getInstance().level;
        return level == null
            ? ItemStack.EMPTY
            : ItemStack.parseOptional(level.registryAccess(),
                row.getCompound("icon"));
    }
    private static Component name(CompoundTag row) {
        var icon = icon(row);
        return icon.isEmpty()
            ? Component.literal(row.getString("id"))
            : icon.getHoverName();
    }
    private static Component text(String key, Object... values) {
        return Component.translatable("screen.mmdlib.recipe_selector." + key,
            values);
    }
    private static Component reason(CompoundTag row) {
        return text(
            "reason."
                + row.getString("reason").toLowerCase(java.util.Locale.ROOT),
            row.getString("group"));
    }

    public List<Component> tooltip(int x, int y) {
        var state = view();
        var row = state.getCompound("chosen");
        if (state.getBoolean("open")) {
            int index = choiceIndex(overlayBounds().orElseThrow(), state, x, y);
            if (index >= 0)
                row = state.getList("rows", Tag.TAG_COMPOUND)
                    .getCompound(index);
        }
        var result = new ArrayList<Component>();
        result.add(text("produce"));
        if (row.isEmpty())
            result.add(text(
                state.getString("selected").isEmpty() ? "none" : "missing"));
        if (!row.isEmpty()) {
            result.add(name(row));
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown())
                result.add(Component.literal(row.getString("id")));
            result.add(text("cost", row.getLong("energy"), row.getInt("time")));
            for (var cost : row.getList("costs", Tag.TAG_STRING))
                result.add(Component.literal(cost.getAsString()));
            result.add(reason(row));
        }
        if (!state.getString("active").isEmpty()
            && !state.getString("active").equals(state.getString("selected"))) {
            var working = state.getCompound("working");
            result.add(text("finishing",
                working.isEmpty() ? state.getString("active") : name(working)));
            result.add(text("queued_help"));
        }
        result.add(text("help"));
        return result;
    }

    public static void render(RecipeSelectorElement element,
        ElementRenderers.Context c) {
        var state = c.access().state().get(element, RecipeSelectorElement.VIEW);
        var r = c.bounds();
        c.graphics().blitSprite(
            net.minecraft.resources.ResourceLocation.parse("mmdlib:slot"),
            r.x(), r.y(), 18, 18);
        var stack = icon(state.getCompound("chosen"));
        if (!stack.isEmpty())
            GHOSTS.render(c.graphics(), stack, r.x() + 1, r.y() + 1, .50f);
    }

    public static void renderChooser(RecipeSelectorElement element,
        ElementRenderers.Context c) {
        var state = c.access().state().get(element, RecipeSelectorElement.VIEW);
        if (!state.getBoolean("open"))
            return;
        var popup = chooserBounds(c.bounds(), c.access().viewport(), state);
        var graphics = c.graphics();
        var rows = state.getList("rows", Tag.TAG_COMPOUND);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        graphics.fill(popup.x(), popup.y(), popup.right(), popup.bottom(),
            0xff404b50);
        int start = popup.x() + 2 + (state.getInt("pages") > 1 ? 12 : 0);
        for (int i = 0; i < rows.size(); i++) {
            int x = start + i * 18;
            graphics.blitSprite(
                net.minecraft.resources.ResourceLocation.parse("mmdlib:slot"),
                x, popup.y() + 2, 18, 18);
            graphics.renderItem(icon(rows.getCompound(i)), x + 1,
                popup.y() + 3);
            if (choiceIndex(popup, state, c.mouseX(), c.mouseY()) == i)
                graphics.fill(x, popup.y() + 2, x + 18, popup.bottom() - 2,
                    0x4080e3ed);
        }
        if (state.getInt("pages") > 1) {
            graphics.drawString(c.access().font(), "<", popup.x() + 3,
                popup.y() + 7, 0xff80e3ed, false);
            graphics.drawString(c.access().font(), ">", popup.right() - 9,
                popup.y() + 7, 0xff80e3ed, false);
        }
        graphics.pose().popPose();
    }
}
