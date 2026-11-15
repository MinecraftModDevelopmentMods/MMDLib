package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.neoforge.MachineResourcePack;
import zone.moddev.mc.lib.machine.presentation.FaceOverlay;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

class FaceOverlayTest {
    @TempDir
    Path directory;

    private zone.moddev.mc.lib.machine.definition.MachineDefinition load(
        String settings) throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/overlay.xml"),
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="mmdlib:base_powered_machine" name="Overlay">
                  <appearance preset="cube-front" front="test:block/front" side="test:block/side" top="test:block/top">
                    <face-overlay id="lights" face="front" texture="test:block/lights" %s/>
                    <face-overlay id="other" face="front" texture="test:block/other" x="0" y="0" width="2" height="2"/>
                  </appearance>
                  <recipe-type id="test:crushing"/>
                  <processor id="crusher" recipe-type="test:crushing"/>
                </machine>
                """
                .formatted(settings));
        return MachineDefinitions
            .load(List.of(
                new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                    getClass().getClassLoader()),
                new SourceRoot("test", directory, getClass().getClassLoader())))
            .get(ResourceLocation.parse("test:overlay"));
    }

    @Test
    void framesCoexistAndOnlyCoverTheirOwnRegion() throws Exception {
        var definition = load(
            "x='5' y='13.5' width='6' height='1' frames='4' variable='$processors.crusher.progress' emissive='true'");
        assertEquals(5, definition.modelParts().size());
        assertTrue(definition.faceTextures().isEmpty());
        for (double value : new double[]{-2, 0, 0.9, 1, 1.9, 2, 3, 9}) {
            int selected = (int) Math.max(0, Math.min(3, value));
            for (int frame = 0; frame < 4; frame++)
                assertEquals(frame == selected,
                    VisualCondition
                        .compile(definition.modelParts().get(frame).when(),
                            definition.visualDefaults())
                        .test(Map.of("processors.crusher.progress", value)));
        }
        var assets = MachineResourcePack.build(PackType.CLIENT_RESOURCES,
            Map.of(definition.id(), definition));
        var model = com.google.gson.JsonParser.parseString(new String(
            assets
                .get(ResourceLocation.parse("test:models/block/overlay.json")),
            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var part = model.getAsJsonArray("parts").get(2).getAsJsonObject();
        assertTrue(part.get("emissive").getAsBoolean());
        var child = part.getAsJsonObject("model");
        assertEquals("minecraft:cutout",
            child.get("render_type").getAsString());
        var element = child.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals("[5.0,1.5,-0.01]", element.get("from").toString());
        assertEquals("[11.0,2.5,-0.01]", element.get("to").toString());
        var face = element.getAsJsonObject("faces").getAsJsonObject("north");
        assertEquals("[0.0,8.0,16.0,12.0]", face.get("uv").toString());
        assertFalse(face.has("cullface"));
    }

    @Test
    void validatesBoundsAndNumericBindings() {
        for (String settings : List.of("x='NaN' y='0' width='1' height='1'",
            "x='15' y='0' width='2' height='1'",
            "x='0' y='0' width='1' height='1' frames='33' variable='$processors.crusher.progress'",
            "x='0' y='0' width='1' height='1' frames='4'",
            "x='0' y='0' width='1' height='1' frames='4' variable='$processors.crusher.running'",
            "x='0' y='0' width='1' height='1' frames='4' variable='$processors.crusher.missing'"))
            assertThrows(IllegalArgumentException.class, () -> load(settings));
    }

    @Test
    void supportsEveryRelativeFace() {
        for (var side : RelativeFace.values()) {
            var overlay = new FaceOverlay(side,
                ResourceLocation.parse("test:block/lights"), 2, 3, 4, 5, 0, 1,
                false);
            var element = overlay.model().getAsJsonArray("elements").get(0)
                .getAsJsonObject();
            assertTrue(element.getAsJsonObject("faces").has(
                side.toWorld(net.minecraft.core.Direction.NORTH).getName()));
        }
    }
}
