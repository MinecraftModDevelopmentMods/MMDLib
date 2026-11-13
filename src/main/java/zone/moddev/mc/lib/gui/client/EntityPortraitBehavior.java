package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import zone.moddev.mc.lib.gui.model.EntityPortraitData;
import zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement;

/**
 * Per-open-screen preview cache; never inserts an entity into a level or ticks
 * it.
 */
public final class EntityPortraitBehavior extends DefaultElementBehavior {
    private ClientLevel level;
    private EntityPortraitData data;
    private LivingEntity preview;

    public EntityPortraitBehavior(EntityPortraitElement element,
        ElementAccess access) {
        super(access);
    }

    private LivingEntity preview(EntityPortraitData next) {
        var currentLevel = Minecraft.getInstance().level;
        if (level == currentLevel && next.equals(data))
            return preview;
        level = currentLevel;
        data = next;
        preview = null;
        if (level == null || data.isEmpty())
            return null;
        try {
            var tag = data.data();
            // Only this entity is needed for a portrait. No passenger tree or
            // world lookup.
            tag.remove("Passengers");
            var entity = EntityType.by(tag).map(type -> type.create(level))
                .orElse(null);
            if (entity instanceof LivingEntity living) {
                living.load(tag);
                living.setPos(0, 0, 0);
                if (Float.isFinite(living.getBbHeight())
                    && Float.isFinite(living.getBbWidth())
                    && living.getBbHeight() > 0 && living.getBbWidth() > 0)
                    preview = living;
            }
        } catch (RuntimeException malformed) {
            // Missing modded types or malformed display data leave the
            // placeholder visible.
            preview = null;
        }
        return preview;
    }

    public void removed() {
        preview = null;
        data = null;
        level = null;
    }

    public static void render(EntityPortraitElement element,
        ElementRenderers.Context c) {
        var bounds = c.bounds();
        c.graphics().fill(bounds.x(), bounds.y(), bounds.right(),
            bounds.bottom(), 0xff30343c);
        var behavior = c.access()
            .capability(element, EntityPortraitBehavior.class).orElse(null);
        var entity = behavior == null
            ? null
            : behavior.preview(
                c.access().state().get(element, EntityPortraitElement.ENTITY));
        if (entity != null) {
            int scale = Math.max(1,
                (int) (Math.min(bounds.width(), bounds.height()) / Math.max(2,
                    Math.max(entity.getBbHeight(), entity.getBbWidth())
                        * 1.5)));
            InventoryScreen.renderEntityInInventoryFollowsMouse(c.graphics(),
                bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), scale,
                .05f, c.mouseX(), c.mouseY(), entity);
        } else
            element.emptyText().ifPresent(
                text -> c.graphics().drawCenteredString(c.access().font(),
                    c.access().text(text), bounds.x() + bounds.width() / 2,
                    bounds.y() + bounds.height() / 2, 0xffd9b84e));
    }
}
