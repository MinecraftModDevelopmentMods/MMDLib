package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.ResourceBinding;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;

class ElementInteractionTest {
    /** Has storage access, but intentionally no tank value/capacity getters. */
    public static class Host extends ScreenLoaderTest.Press
        implements zone.moddev.mc.lib.gui.runtime.ScreenHost {
        public Host(
            net.minecraft.world.level.block.entity.BlockEntityType<?> type,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
            super(type, pos, state);
        }

        public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
            return null;
        }
    }

    private Screen read(String body) throws Exception {
        String xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test.screen\""
            + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\">"
            + body + "</screen>";
        return ScreenReader.read(ResourceLocation.parse("test:interactions"),
            "interactions.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void tankShorthandCreatesTheSameSelfContainedElement() throws Exception {
        var gauge = read("<gauge tank=\"2\" length=\"3s\"/>").body().getFirst();
        assertInstanceOf(FluidTankElement.class, gauge);
        assertEquals(gauge,
            read("<fluid-tank tank=\"2\" length=\"3s\"/>").body().getFirst());
        assertEquals(new ResourceBinding("fluids", 2),
            gauge.resourceBinding().orElseThrow());
        assertTrue(gauge.interactive());
        assertTrue(gauge.common().value().isEmpty());
        assertTrue(gauge.common().max().isEmpty());
        assertFalse(gauge.common().clicks().any());
    }

    @Test
    void sameInteractionCanBeAttachedToNonGaugeElementsAndAffectsFingerprint()
        throws Exception {
        var first = read(
            "<label text=\"test.fill\" interaction=\"mmdlib:fluid-container\""
                + " interaction-target=\"0\"/>");
        var second = read(
            "<label text=\"test.fill\" interaction=\"mmdlib:fluid-container\""
                + " interaction-target=\"1\"/>");
        assertEquals(FluidTankElement.containerInteraction(0),
            first.body().getFirst().interaction().orElseThrow());
        assertNotEquals(first.fingerprint(), second.fingerprint());
        assertTrue(read(
            "<gauge length=\"3s\" value=\"#getFluid\" max=\"#getCapacity\"/>")
            .body().getFirst().interaction().isEmpty());
    }

    @Test
    void javaConstructedTankBindsWithoutGetterMethodsAndRequiresAHost()
        throws Exception {
        var source = read("<fluid-tank tank=\"0\" length=\"3s\"/>");
        var element = new FluidTankElement(source.body().getFirst().common(), 0,
            zone.moddev.mc.lib.gui.model.Orientation.VERTICAL, 54);
        var screen = new Screen(source.id(), source.source(), source.title(),
            Host.class.getName(), true, java.util.List.of(element),
            java.util.List.of());
        var bound = zone.moddev.mc.lib.gui.load.ScreenBinder.bind(screen,
            getClass().getClassLoader());
        assertTrue(bound.values().isEmpty());
        assertTrue(bound.maxima().isEmpty());
        assertThrows(ScreenException.class,
            () -> zone.moddev.mc.lib.gui.load.ScreenBinder.bind(source,
                getClass().getClassLoader()));
    }

    @Test
    void tankUsesGaugeLayoutAndFillConstraints() throws Exception {
        var screen = read(
            "<hbox><fluid-tank id=\"tank\" tank=\"0\" length=\"fill\"/><slot index=\"0\"/></hbox>");
        var layout = zone.moddev.mc.lib.gui.layout.ScreenLayout.of(screen,
            zone.moddev.mc.lib.gui.layout.TextMeasure.NONE);
        var bounds = layout.bounds(screen.find("tank").orElseThrow());
        assertEquals(18, bounds.width());
        assertEquals(18, bounds.height());
        assertThrows(ScreenException.class,
            () -> read("<fluid-tank tank=\"0\" length=\"fill\"/>"));
        var horizontal = read(
            "<fluid-tank tank=\"0\" length=\"3s\" orientation=\"horizontal\"/>");
        var horizontalBounds = zone.moddev.mc.lib.gui.layout.ScreenLayout
            .of(horizontal, zone.moddev.mc.lib.gui.layout.TextMeasure.NONE)
            .bounds(horizontal.body().getFirst());
        assertEquals(54, horizontalBounds.width());
        assertEquals(18, horizontalBounds.height());
    }

    @Test
    void rejectsUnknownBehaviorsInvalidTargetsAndConflictingShorthand() {
        for (var body : new String[]{
            "<label text=\"test.x\" interaction=\"test:missing\"/>",
            "<label text=\"test.x\" interaction-target=\"0\"/>",
            "<label text=\"test.x\" interaction=\"mmdlib:fluid-container\""
                + " interaction-target=\"-1\"/>",
            "<label text=\"test.x\" interaction=\"mmdlib:fluid-container\""
                + " interaction-target=\"oops\"/>",
            "<gauge tank=\"0\" length=\"3s\" interaction=\"mmdlib:fluid-container\"/>",
            "<fluid-tank tank=\"0\" length=\"3s\" value=\"#getFluid\"/>",
            "<fluid-tank tank=\"0\" length=\"3s\" max=\"#getCapacity\"/>",
            "<slot index=\"0\" interaction=\"mmdlib:fluid-container\" interaction-target=\"0\"/>"})
            assertThrows(ScreenException.class, () -> read(body), body);
    }
}
