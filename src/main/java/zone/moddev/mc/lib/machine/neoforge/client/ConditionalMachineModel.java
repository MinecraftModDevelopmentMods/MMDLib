package zone.moddev.mc.lib.machine.neoforge.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.machine.neoforge.MachineVisualState;
import zone.moddev.mc.lib.machine.presentation.EmissiveMask;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;

/**
 * Static, independently conditional parts baked with the block's normal facing
 * transform.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConditionalMachineModel
    implements IUnbakedGeometry<ConditionalMachineModel> {
    private record Part(String id, BlockModel model,
        VisualCondition condition) {
    }
    private record FaceTexture(RelativeFace face, String texture,
        VisualCondition condition, String emissiveMask) {
    }
    private final BlockModel base;
    private final List<Part> parts;
    private final List<FaceTexture> faceTextures;

    private ConditionalMachineModel(BlockModel base, List<Part> parts,
        List<FaceTexture> faceTextures) {
        this.base = base;
        this.parts = List.copyOf(parts);
        this.faceTextures = List.copyOf(faceTextures);
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<ConditionalMachineModel> loader = ConditionalMachineModel::read;
        event.register(ResourceLocation.parse("mmdlib:conditional_machine"),
            loader);
    }

    private static ConditionalMachineModel read(JsonObject json,
        JsonDeserializationContext context) {
        try {
            var defaults = new HashMap<String, Object>();
            for (var entry : json.getAsJsonObject("variables").entrySet()) {
                var value = entry.getValue().getAsJsonPrimitive();
                Object fallback = value.isBoolean()
                    ? value.getAsBoolean()
                    : value.isNumber()
                        ? value.getAsDouble()
                        : value.getAsString();
                VisualVariables.Kind.of(fallback);
                defaults.put(entry.getKey(), fallback);
            }
            var parts = new ArrayList<Part>();
            var ids = new HashSet<String>();
            ids.add("base");
            for (var element : json.has("parts")
                ? json.getAsJsonArray("parts")
                : new com.google.gson.JsonArray()) {
                var part = element.getAsJsonObject();
                String id = part.get("id").getAsString();
                if (!ids.add(id))
                    throw new JsonParseException(
                        "Duplicate or reserved model part " + id);
                parts.add(new Part(id,
                    context.deserialize(part.get("model"), BlockModel.class),
                    VisualCondition.compile(part.get("when").getAsString(),
                        defaults)));
            }
            var faceTextures = new ArrayList<FaceTexture>();
            if (json.has("face_textures"))
                for (var element : json.getAsJsonArray("face_textures")) {
                    var rule = element.getAsJsonObject();
                    faceTextures.add(new FaceTexture(
                        RelativeFace.valueOf(rule.get("face").getAsString()
                            .toUpperCase(java.util.Locale.ROOT)),
                        rule.get("texture").getAsString(),
                        VisualCondition.compile(rule.get("when").getAsString(),
                            defaults),
                        rule.has("emissive_mask")
                            ? rule.get("emissive_mask").getAsString()
                            : null));
                }
            return new ConditionalMachineModel(
                context.deserialize(json.get("base"), BlockModel.class), parts,
                faceTextures);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new JsonParseException(
                "Invalid conditional machine model: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> models,
        IGeometryBakingContext context) {
        base.resolveParents(models);
        parts.forEach(part -> part.model().resolveParents(models));
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
        Function<Material, TextureAtlasSprite> sprites, ModelState state,
        ItemOverrides overrides) {
        if (!context.getRootTransform().isIdentity())
            state = UnbakedGeometryHelper.composeRootTransformIntoModelState(
                state, context.getRootTransform());
        var baseModel = base.bake(baker, base, sprites, state, true);
        var children = ImmutableMap.<String, BakedModel>builder();
        var textureRules = new ArrayList<FaceTextureModel.Rule>();
        for (var rule : faceTextures) {
            var face = Direction.rotate(state.getRotation().getMatrix(),
                rule.face().toWorld(Direction.NORTH));
            var mask = rule.emissiveMask() == null
                ? null
                : readMask(
                    sprites.apply(context.getMaterial(rule.emissiveMask())));
            textureRules.add(new FaceTextureModel.Rule(face, rule.condition(),
                sprites.apply(context.getMaterial(rule.texture())), mask));
        }
        children.put("base",
            textureRules.isEmpty()
                ? baseModel
                : new FaceTextureModel(baseModel, textureRules));
        for (var part : parts)
            children.put(part.id(), new ConditionalPart(
                part.model().bake(baker, part.model(), sprites, state, true),
                part.condition()));
        // Inventory items intentionally show the base appearance, independent
        // of world state.
        return new CompositeModel.Baked(context.isGui3d(),
            context.useBlockLight(), context.useAmbientOcclusion(),
            baseModel.getParticleIcon(), context.getTransforms(), overrides,
            children.build(), ImmutableList.of(baseModel));
    }

    private static EmissiveMask readMask(TextureAtlasSprite sprite) {
        if (sprite.contents().name().equals(
            net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
                .getLocation()))
            throw new JsonParseException("Missing emissive mask texture");
        var contents = sprite.contents();
        var image = contents.getOriginalImage();
        if (image.getWidth() != contents.width()
            || image.getHeight() != contents.height())
            throw new JsonParseException(
                "Emissive masks must be static single-frame textures: "
                    + contents.name());
        return EmissiveMask.read(contents.width(), contents.height(),
            (x, y) -> {
                int rgba = image.getPixelRGBA(x, y);
                int brightness = Math.max(rgba & 255,
                    Math.max((rgba >>> 8) & 255, (rgba >>> 16) & 255));
                return ((rgba >>> 24) & 255) >= 128 && brightness >= 128
                    ? 1
                    : 0;
            });
    }

    private static final class ConditionalPart
        extends
            BakedModelWrapper<BakedModel> {
        private final VisualCondition condition;

        private ConditionalPart(BakedModel model, VisualCondition condition) {
            super(model);
            this.condition = condition;
        }

        private boolean visible(ModelData data) {
            Map<String, Object> values = data.get(MachineVisualState.VARIABLES);
            return condition.test(values == null ? Map.of() : values);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
            BlockState state, ModelData data) {
            var child = originalModel.getModelData(level, pos, state, data);
            var values = data.get(MachineVisualState.VARIABLES);
            return values == null
                ? child
                : child.derive().with(MachineVisualState.VARIABLES, values)
                    .build();
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side,
            RandomSource random, ModelData data, RenderType type) {
            return visible(data)
                ? originalModel.getQuads(state, side, random, data, type)
                : List.of();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state,
            RandomSource random, ModelData data) {
            return visible(data)
                ? originalModel.getRenderTypes(state, random, data)
                : ChunkRenderTypeSet.none();
        }
    }
}
