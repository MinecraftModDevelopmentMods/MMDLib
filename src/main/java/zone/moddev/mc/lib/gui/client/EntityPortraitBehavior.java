package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.gui.model.elements.EntityPortraitElement;

/**
 * Per-open-screen preview cache; never inserts an entity into a level or ticks
 * it.
 */
public final class EntityPortraitBehavior extends DefaultElementBehavior {
    private final EntityPreviewCache<LivingEntity> previews = new EntityPreviewCache<>(
        LivingEntity.class, 1);

    public EntityPortraitBehavior(EntityPortraitElement element,
        ElementAccess access) {
        super(access);
    }

    private LivingEntity preview(EntitySnapshot next) {
        return previews.get(Minecraft.getInstance().level, next);
    }

    public void removed() {
        previews.clear();
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
