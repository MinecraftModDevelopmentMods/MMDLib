package zone.moddev.mc.lib.machine.neoforge.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * Preserves JSON models, occupied overrides and transforms while adding a
 * declarative entity overlay.
 */
public final class EntityDisplayItemModel
    extends
        BakedModelWrapper<BakedModel> {
    private final Map<BakedModel, EntityDisplayItemModel> models;
    private final ItemOverrides overrides;

    public EntityDisplayItemModel(BakedModel original) {
        this(original, new IdentityHashMap<>());
    }

    private EntityDisplayItemModel(BakedModel original,
        Map<BakedModel, EntityDisplayItemModel> models) {
        super(original);
        this.models = models;
        models.put(original, this);
        overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack,
                ClientLevel level, LivingEntity entity, int seed) {
                var resolved = original.getOverrides().resolve(original, stack,
                    level, entity, seed);
                return wrap(resolved == null ? original : resolved);
            }
        };
    }

    private EntityDisplayItemModel wrap(BakedModel model) {
        var wrapped = models.get(model);
        return wrapped == null
            ? new EntityDisplayItemModel(model, models)
            : wrapped;
    }

    public BakedModel geometry() {
        return originalModel;
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose,
        boolean leftHand) {
        return wrap(originalModel.applyTransform(context, pose, leftHand));
    }
}
