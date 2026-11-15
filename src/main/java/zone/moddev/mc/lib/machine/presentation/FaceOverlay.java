package zone.moddev.mc.lib.machine.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;

/** One frame of a vertical texture strip on a rectangular face region. */
public record FaceOverlay(RelativeFace face, ResourceLocation texture, double x,
    double y, double width, double height, int frame, int frames,
    boolean emissive) {
    public FaceOverlay {
        if (face == null || texture == null || !Double.isFinite(x)
            || !Double.isFinite(y) || !Double.isFinite(width)
            || !Double.isFinite(height) || x < 0 || y < 0 || width <= 0
            || height <= 0 || x + width > 16 || y + height > 16)
            throw new IllegalArgumentException(
                "Overlay region must fit within a 16 by 16 face");
        if (frames < 1 || frames > 32 || frame < 0 || frame >= frames)
            throw new IllegalArgumentException(
                "Overlay requires 1 to 32 frames and a valid frame index");
    }

    /**
     * Pixel coordinates measured from the top left when looking at the face.
     */
    public JsonObject model() {
        var direction = face.toWorld(Direction.NORTH);
        double left = x, right = x + width, top = 16 - y, bottom = top - height;
        double[] from, to;
        switch (direction) {
            case NORTH -> {
                from = new double[]{16 - right, bottom, -0.01};
                to = new double[]{16 - left, top, -0.01};
            }
            case SOUTH -> {
                from = new double[]{left, bottom, 16.01};
                to = new double[]{right, top, 16.01};
            }
            case WEST -> {
                from = new double[]{-0.01, bottom, left};
                to = new double[]{-0.01, top, right};
            }
            case EAST -> {
                from = new double[]{16.01, bottom, 16 - right};
                to = new double[]{16.01, top, 16 - left};
            }
            case UP -> {
                from = new double[]{left, 16.01, y};
                to = new double[]{right, 16.01, y + height};
            }
            case DOWN -> {
                from = new double[]{left, -0.01, 16 - y - height};
                to = new double[]{right, -0.01, 16 - y};
            }
            default -> throw new IllegalStateException();
        }
        var model = new JsonObject();
        model.addProperty("render_type", "minecraft:cutout");
        var textures = new JsonObject();
        textures.addProperty("overlay", texture.toString());
        textures.addProperty("particle", texture.toString());
        model.add("textures", textures);
        var element = new JsonObject();
        element.add("from", array(from));
        element.add("to", array(to));
        element.addProperty("shade", !emissive);
        var surface = new JsonObject();
        surface.addProperty("texture", "#overlay");
        surface.add("uv",
            array(0, 16.0 * frame / frames, 16, 16.0 * (frame + 1) / frames));
        var faces = new JsonObject();
        faces.add(direction.getName(), surface);
        element.add("faces", faces);
        var elements = new JsonArray();
        elements.add(element);
        model.add("elements", elements);
        return model;
    }

    private static JsonArray array(double... values) {
        var result = new JsonArray();
        for (double value : values)
            result.add(value);
        return result;
    }
}
