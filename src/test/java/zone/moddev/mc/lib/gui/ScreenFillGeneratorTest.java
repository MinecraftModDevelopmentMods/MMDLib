package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenFillGenerator;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.InventoryScope;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.definition.MachineScreenGenerators;

class ScreenFillGeneratorTest {
    @TempDir
    Path directory;

    @Test
    void multipleTankGaugesBindSeparateIndexesAndReplaceableFill() throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(
            directory.resolve("machine/furnace.xml"),
            Files
                .readString(
                    Path.of("src/companion/resources/mmdlib/machine/simple_lava_furnace.xml"))
                .replace("<tank id=\"fuel\"", "<tanks count=\"2\" id=\"fuel\""));
        var sources = List.of(
            new SourceRoot(
                "mmdlib", Path.of("src/main/resources/mmdlib"), getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
        var machines = MachineDefinitions.load(sources);
        var id = ResourceLocation.parse("test:furnace");
        var screen = ScreenLoader.load(
            sources,
            Map.of(id, machines.get(id).screen()),
            (key, node) -> MachineScreenGenerators.generate(machines.get(key), node))
            .get(id)
            .screen();
        var highlights = MachineScreenGenerators.compileHighlights(machines.get(id), screen);
        assertEquals(List.of("tank_0", "tank_1"), highlights.get("fuel"));
        assertTrue(screen.find("energy").isEmpty());
        for (int index = 0; index < 2; index++) {
            var tank = screen.find("tank_" + index).orElseThrow();
            assertInstanceOf(zone.moddev.mc.lib.gui.model.elements.FluidTankElement.class, tank);
            assertEquals(
                new zone.moddev.mc.lib.gui.model.ResourceBinding("fluids", index),
                tank.resourceBinding().orElseThrow());
            assertTrue(tank.common().value().isEmpty());
            assertTrue(tank.common().max().isEmpty());
        }
        var layout = ScreenLayout.of(screen, TextMeasure.NONE);
        var tank = layout.bounds(screen.find("tank_0").orElseThrow());
        var input = layout.bounds(screen.find("slot_0").orElseThrow());
        var output = layout.bounds(screen.find("slot_1").orElseThrow());
        assertEquals(7 + tank.x() - 6, input.x() + output.right(), 1);
        assertTrue(layout.bounds(screen.find("tank_1").orElseThrow()).x() >= tank.right());
    }

    private void write(String name, String attrs, String body) throws Exception {
        Files.createDirectories(directory.resolve("screen"));
        Files.writeString(
            directory.resolve("screen/" + name + ".xml"),
            "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" " + attrs + ">" + body
                + "</screen>");
    }

    @Test
    void defaultsOnlyRunWhenNoFillOverridesThemIncludingEmptyFills() throws Exception {
        write(
            "base",
            "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
            "<vbox><label text=\"test.header\"/><placeholder name=\"body\""
                + " generator=\"test:default\"/></vbox>");
        write("default", "extends=\"test:base\" title=\"test.default\"", "");
        write(
            "manual",
            "extends=\"test:base\" title=\"test.manual\"",
            "<fill name=\"body\"><slot id=\"manual\" index=\"2\"/></fill>");
        write("empty", "extends=\"test:base\" title=\"test.empty\"", "<fill name=\"body\"/>");
        write(
            "middle",
            "abstract=\"true\" extends=\"test:base\"",
            "<fill name=\"body\" generator=\"test:replacement\" columns=\"2\"/>");
        write("leaf", "extends=\"test:middle\" title=\"test.leaf\"", "");
        var calls = new ArrayList<String>();
        var screens = ScreenLoader.load(
            List.of(new SourceRoot("test", directory, getClass().getClassLoader())),
            Map.of(),
            (id, node) -> {
                calls.add(id + "=" + node.attributes().get("generator"));
                return List.of(
                    new Node(
                        "slot",
                        Map.of("id", "generated", "index", "0"),
                        List.of(),
                        node.source(),
                        node.line()));
            });
        assertEquals(List.of("test:default=test:default", "test:leaf=test:replacement"), calls);
        assertTrue(
            screens.get(ResourceLocation.parse("test:empty")).screen().find("generated").isEmpty());
        assertTrue(
            screens.get(ResourceLocation.parse("test:manual")).screen().find("manual").isPresent());
        assertInstanceOf(
            SlotElement.class,
            screens
                .get(ResourceLocation.parse("test:default"))
                .screen()
                .find("generated")
                .orElseThrow());
    }

    @Test
    void realSimpleCrusherGeneratesSlotsAndInheritsCommonScreen() {
        var sources = List.of(
            new SourceRoot(
                "mmdlib", Path.of("src/main/resources/mmdlib"), getClass().getClassLoader()),
            new SourceRoot(
                "mmdlib_companion",
                Path.of("src/companion/resources/mmdlib"),
                getClass().getClassLoader()));
        var machines = MachineDefinitions.load(sources);
        var id = ResourceLocation.parse("mmdlib_companion:simple_crusher");
        // Only load library screens; the other companion screens bind companion-only classes.
        var screens = ScreenLoader.load(
            List.of(sources.getFirst()),
            Map.of(id, machines.get(id).screen()),
            (key, node) -> MachineScreenGenerators.generate(machines.get(key), node));
        var screen = screens.get(id).screen();
        assertDoesNotThrow(() -> MachineScreenGenerators.validate(machines.get(id), screen));
        var highlights = MachineScreenGenerators.compileHighlights(machines.get(id), screen);
        assertEquals(List.of("inventory_input"), highlights.get("input"));
        assertEquals(List.of("inventory_output"), highlights.get("output"));
        assertEquals(List.of("energy"), highlights.get("energy"));
        class Scope extends BoxElement implements InventoryScope {
            Scope(List<Element> children) {
                super(Common.empty(), true, 0, 0, Align.START, children);
            }

            public String inventory() {
                return "input";
            }
        }
        var scopedSlot = screen.find("slot_0").orElseThrow();
        var wrapper = new BoxElement(Common.empty(), true, 0, 0, Align.START, List.of(scopedSlot));
        var outline = new ColouredGroupElement(
            screen.find("inventory_input").orElseThrow().common(),
            0xffffff,
            new Scope(List.of(wrapper)));
        var custom = new Screen(
            screen.id(),
            screen.source(),
            screen.title(),
            screen.blockEntity(),
            false,
            List.of(outline),
            List.of());
        assertEquals(
            List.of("inventory_input"),
            MachineScreenGenerators.compileHighlights(machines.get(id), custom).get("input"));
        var invalidScoped = new ItemSlotElement(
            Common.empty(), 1, SlotKind.NORMAL, ((SlotElement) scopedSlot).appearance());
        var invalidScope = new Screen(
            screen.id(),
            screen.source(),
            screen.title(),
            screen.blockEntity(),
            false,
            List.of(new Scope(List.of(invalidScoped))),
            List.of());
        assertThrows(
            ScreenException.class,
            () -> MachineScreenGenerators.validate(machines.get(id), invalidScope));
        int[] validations = {0};
        var customPolicy = new ItemSlotElement(
            scopedSlot.common(), 999, SlotKind.NORMAL, ((SlotElement) scopedSlot).appearance()) {
            public void validateResources(ResourceValidationContext context) {
                validations[0]++;
                assertEquals(1, context.inventories().get("input"));
                assertEquals(2, context.inventories().get("output"));
                assertFalse(context.bindings().get("processors").isEmpty());
            }

            public List<String> highlightedGroups(Screen screen, List<ElementGroup> groups) {
                return List.of("output", "output", "absent");
            }
        };
        var customPolicyScreen = new Screen(
            screen.id(),
            screen.source(),
            screen.title(),
            screen.blockEntity(),
            false,
            List.of(customPolicy),
            List.of());
        var customHighlights = MachineScreenGenerators.compileHighlights(machines.get(id),
            customPolicyScreen);
        assertEquals(1, validations[0]);
        assertEquals(List.of(scopedSlot.common().id().orElseThrow()),
            customHighlights.get("output"));
        assertTrue(customHighlights.get("input").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> highlights.put("other", List.of()));
        assertThrows(UnsupportedOperationException.class,
            () -> highlights.get("input").add("other"));
        assertEquals(
            List.of(0, 0, 1),
            screen.elements().stream()
                .filter(e -> e instanceof SlotElement slot && !slot.playerInventory())
                .map(e -> ((SlotElement) e).index())
                .toList());
        assertTrue(screen.find("energy").isPresent());
        assertTrue(screen.find("info").isPresent());
        assertTrue(screen.find("generated_progress").isPresent());
        var progress = screen.find("generated_progress").orElseThrow();
        assertEquals(List.of(machines.get(id).recipeType().id()), progress.common().jeiRecipes());
        var layout = ScreenLayout.of(screen, TextMeasure.NONE);
        var input = layout.bounds(screen.find("slot_0").orElseThrow());
        var output = layout.bounds(screen.find("slot_2").orElseThrow());
        var energy = layout.bounds(screen.find("energy").orElseThrow());
        // Center the entire processing cluster in the space before the fixed energy gauge.
        assertEquals(7 + energy.x() - 6, input.x() + output.right(), 1);
        assertEquals(energy.y() * 2 + energy.height(), input.y() * 2 + input.height(), 1);
        assertEquals(input.y(), output.y());
        assertEquals(
            input.y() * 2 + input.height(),
            layout.bounds(progress).y() * 2 + layout.bounds(progress).height(),
            1);
        var processorNodes = MachineScreenGenerators.generate(
            machines.get(id),
            new Node(
                "fill",
                Map.of("name", "processor", "generator", "mmdlib:processors"),
                List.of(),
                "test.xml",
                1));
        assertEquals(
            machines.get(id).recipeType().id().toString(),
            processorNodes.getFirst().attributes().get("jei-recipes"));
        var invalid = ScreenLoader.load(
            List.of(sources.getFirst()),
            Map.of(id, machines.get(id).screen()),
            (key, node) -> node.attributes().get("generator").equals("mmdlib:tanks")
                ? List.of()
                : List.of(
                    new Node(
                        "slot", Map.of("index", "99"), List.of(), node.source(), node.line())));
        assertThrows(
            IllegalArgumentException.class,
            () -> MachineScreenGenerators.validate(machines.get(id), invalid.get(id).screen()));
    }

    @Test
    void literalTemplateAttributesRejectTyposBeforeExpansion() throws Exception {
        var sources = List.of(new SourceRoot("test", directory, getClass().getClassLoader()));
        for (var attribute : List.of("colums=\"2\"", "generatr=\"test:fill\"")) {
            write(
                "base",
                "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
                "<placeholder name=\"body\" " + attribute + "/>");
            write("child", "extends=\"test:base\" title=\"test.child\"", "<fill name=\"body\"/>");
            assertTrue(
                assertThrows(ScreenException.class, () -> ScreenLoader.load(sources))
                    .getMessage()
                    .contains("Unknown attribute"));
            write(
                "base",
                "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
                "<placeholder name=\"body\"/>");
            write(
                "child",
                "extends=\"test:base\" title=\"test.child\"",
                "<fill name=\"body\" " + attribute + "/>");
            assertTrue(
                assertThrows(ScreenException.class, () -> ScreenLoader.load(sources))
                    .getMessage()
                    .contains("Unknown attribute"));
        }
        var invalid = new Node(
            "screen",
            Map.of("title", "test", "block-entity", "test.Host"),
            List.of(
                new Node(
                    "placeholder",
                    Map.of("name", "body", "colums", "2"),
                    List.of(),
                    "machine.xml",
                    7)),
            "machine.xml",
            1);
        var error = assertThrows(
            ScreenException.class,
            () -> ScreenLoader.read(
                Map.of(ResourceLocation.parse("test:embedded"), invalid),
                new WidgetTypes(),
                ScreenFillGenerator.UNAVAILABLE));
        assertTrue(error.getMessage().contains("machine.xml:7: Unknown attribute colums"));
    }

    @Test
    void rejectsGeneratorWithLiteralChildren() throws Exception {
        write(
            "base",
            "abstract=\"true\"",
            "<placeholder name=\"body\" generator=\"test:default\"><label"
                + " text=\"test.no\"/></placeholder>");
        write(
            "child",
            "extends=\"test:base\" title=\"test.child\""
                + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
            "");
        assertThrows(
            IllegalArgumentException.class,
            () -> ScreenLoader.load(
                List.of(new SourceRoot("test", directory, getClass().getClassLoader()))));
    }
}
