package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementRenderAccess;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.OptionSelection;
import zone.moddev.mc.lib.gui.client.TextControlRenderer;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.RadioGroupElement;
import zone.moddev.mc.lib.gui.model.elements.AnimatedSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.ButtonControl;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.model.elements.SpriteElement;

/** External-package classes exercise the same extension API available to mods. */
class ElementRenderersTest {
    interface Family extends Element {
    }

    interface Specialized extends Family {
    }

    interface Other extends Element {
    }

    record ModElement(Common common) implements Specialized, Other {
        public int[] measure(TextMeasure text) {
            return new int[]{20, 10};
        }
    }

    record ModSlot(Common common, int index, Appearance appearance) implements SlotElement {
        public SlotKind kind() {
            return SlotKind.NORMAL;
        }
    }

    record ModButton(Common common) implements ButtonControl {
        public boolean flat() {
            return true;
        }

        public Optional<Text> text() {
            return Optional.empty();
        }

        public Optional<ResourceLocation> iconFor(Object value) {
            return Optional.empty();
        }

        public Optional<Text> labelFor(Object value) {
            return Optional.empty();
        }
    }

    private ElementRenderers.Context context(Element element) {
        return new ElementRenderers.Context(null, element, new Rect(0, 0, 20, 10), null, null, 0, 0,
            0);
    }

    @Test
    void mostSpecificFamilyAndExactOverrideAreIndependentOfRegistrationOrder() {
        for (boolean reverse : new boolean[]{false, true}) {
            var registry = new ElementRenderers();
            var seen = new ArrayList<String>();
            var element = new ModElement(Common.empty());
            if (reverse)
                registry.register(Specialized.class, (e, c) -> seen.add("specific"));
            registry.register(Family.class, (e, c) -> seen.add("family"));
            if (!reverse)
                registry.register(Specialized.class, (e, c) -> seen.add("specific"));
            registry.require(ModElement.class).accept(context(element));
            registry.register(ModElement.class, (e, c) -> seen.add("exact"));
            registry.require(ModElement.class).accept(context(element));
            assertEquals(List.of("specific", "exact"), seen);
        }
    }

    @Test
    void ambiguityRequiresAnExplicitResolution() {
        var registry = new ElementRenderers();
        registry.register(Family.class, (e, c) -> {
        });
        registry.register(Other.class, (e, c) -> {
        });
        var error = assertThrows(IllegalArgumentException.class,
            () -> registry.require(ModElement.class));
        assertTrue(error.getMessage().contains("Ambiguous"));
        assertTrue(error.getMessage().contains(Family.class.getName()));
        assertTrue(error.getMessage().contains(Other.class.getName()));
        registry.register(ModElement.class, (e, c) -> {
        });
        assertDoesNotThrow(() -> registry.require(ModElement.class));
    }

    @Test
    void replacementInvalidatesFamilyCacheAndDuplicateRegistrationsFail() {
        var registry = new ElementRenderers();
        var element = new ModElement(Common.empty());
        var seen = new ArrayList<Integer>();
        assertThrows(IllegalArgumentException.class, () -> registry.require(ModElement.class));
        registry.register(Family.class, (e, c) -> seen.add(1));
        registry.require(ModElement.class).accept(context(element));
        assertThrows(
            IllegalArgumentException.class, () -> registry.register(Family.class, (e, c) -> {
            }));
        registry.replace(Family.class, (e, c) -> seen.add(2));
        registry.require(ModElement.class).accept(context(element));
        assertEquals(List.of(1, 2), seen);
        assertThrows(
            IllegalArgumentException.class, () -> registry.replace(ModElement.class, (e, c) -> {
            }));
    }

    @Test
    void optionalPassesResolveIndependentlyAndInvalidateCachedMisses() {
        var registry = new ElementRenderers();
        var element = new ModElement(Common.empty());
        var seen = new ArrayList<String>();
        registry.register(Family.class, (e, c) -> seen.add("background"));
        for (var pass : ElementRenderers.Pass.values())
            if (pass != ElementRenderers.Pass.BACKGROUND)
                registry.render(pass, context(element)); // Missing optional passes do not touch
                                                         // graphics.
        registry.registerForeground(Family.class, (e, c) -> seen.add("foreground"));
        registry.registerOverlay(ModElement.class, (e, c) -> seen.add("overlay"));
        registry.registerTop(Family.class, (e, c) -> seen.add("top"));
        for (var pass : ElementRenderers.Pass.values())
            registry.require(pass, ModElement.class).accept(context(element));
        assertEquals(List.of("background", "overlay", "foreground", "top"), seen);
    }

    @Test
    void builtinsShareRenderingWithModSlotsButtonsAndSpriteSubclasses() {
        var registry = ElementRenderers.builtins();
        assertSame(registry.require(SlotElement.class), registry.require(ModSlot.class));
        assertSame(registry.require(ButtonControl.class), registry.require(ModButton.class));
        assertSame(
            registry.require(ButtonControl.class), registry.require(ProcessorButtonElement.class));
        assertSame(
            registry.require(SpriteElement.class), registry.require(AnimatedSpriteElement.class));
        assertSame(
            registry.require(SpriteElement.class), registry.require(ProgressSpriteElement.class));
        registry.register(ModSlot.class, (e, c) -> {
        });
        assertNotSame(registry.require(SlotElement.class), registry.require(ModSlot.class));
    }

    @Test
    void unregisteredElementsFailDuringScreenValidation() {
        var element = new ModElement(Common.empty());
        var registry = ElementRenderers.builtins();
        var screen = new Screen(
            ResourceLocation.parse("test:renderers"),
            "test",
            new Text("test", Optional.empty()),
            "test.Host",
            false,
            List.of(element),
            List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.validate(screen));
        registry.register(ModElement.class, (e, c) -> {
        });
        assertDoesNotThrow(() -> registry.validate(screen));
    }

    @Test
    void validationUsesResolvedRegistrationsAndOriginalElementSource() {
        var element = new ModElement(Common.empty());
        var screen = new Screen(
            ResourceLocation.parse("test:validation"),
            "derived.xml",
            new Text("test", Optional.empty()),
            "test.Host",
            false,
            List.of(element),
            List.of(),
            Map.of(element.common(), "base.xml"));
        var registry = new ElementRenderers();
        var seen = new ArrayList<String>();
        registry.register(Family.class, validator("base", seen));
        registry.registerTop(Family.class, validator("top", seen));
        registry.validate(screen);
        assertEquals(List.of("base", "top"), seen);
        seen.clear();
        registry.register(ModElement.class, validator("exact", seen));
        registry.validate(screen);
        assertEquals(List.of("exact", "top"), seen);
        registry.replace(
            ElementRenderers.Pass.TOP,
            Family.class,
            ElementRenderers.Clip.ELEMENT,
            new ElementRenderers.Renderer<Family>() {
                public void render(Family e, ElementRenderers.Context c) {
                }

                public void validate(Family e) {
                    throw new IllegalArgumentException("Missing mod asset");
                }
            });
        var error = assertThrows(
            zone.moddev.mc.lib.gui.load.ScreenException.class, () -> registry.validate(screen));
        assertEquals("base.xml: Missing mod asset", error.getMessage());
    }

    @Test
    void fragmentTraversalValidatesOptionalPassesWithSourceAndLine() {
        var c = Common.empty();
        var common = new Common(
            c.id(),
            c.value(),
            c.max(),
            c.tooltip(),
            c.tooltipShift(),
            c.clicks(),
            c.onHover(),
            c.hoverShows(),
            c.jeiRecipes(),
            0,
            0,
            17);
        var element = new ModElement(common);
        var registry = new ElementRenderers();
        var seen = new ArrayList<String>();
        registry.register(Family.class, validator("background", seen));
        registry.registerOverlay(Family.class, validator("left", seen));
        registry.registerOverlay(Other.class, validator("right", seen));
        var error = assertThrows(
            zone.moddev.mc.lib.gui.load.ScreenException.class,
            () -> registry.validate(List.of(element), e -> "fragment.xml"));
        assertTrue(
            error.getMessage()
                .startsWith("fragment.xml:17: Ambiguous element renderers for OVERLAY"));
        registry.registerOverlay(ModElement.class, validator("exact overlay", seen));
        seen.clear();
        registry.validate(List.of(element), e -> "fragment.xml");
        assertEquals(List.of("background", "exact overlay"), seen);
    }

    private static <E extends Element> ElementRenderers.Renderer<E> validator(
        String name, List<String> seen) {
        return new ElementRenderers.Renderer<>() {
            public void render(E e, ElementRenderers.Context c) {
            }

            public void validate(E e) {
                seen.add(name);
            }
        };
    }

    @Test
    void radioRendererUsesParentCapability() {
        var a = new RadioElement(Common.empty(), new Text("test", Optional.empty()));
        var b = new RadioElement(Common.empty(), new Text("test", Optional.empty()));
        assertEquals(a, b);
        var group = new RadioGroupElement(Common.empty(), Optional.empty(), List.of(a, b));
        var access = new ElementRenderAccess() {
            public net.minecraft.client.gui.Font font() {
                return null;
            }

            public Rect viewport() {
                return new Rect(0, 0, 100, 100);
            }

            public Optional<Element> parent(Element element) {
                return Optional.of(group);
            }

            public Object value(Element element, boolean maximum) {
                return java.math.BigInteger.ONE;
            }

            public <T> Optional<T> capability(Element element, Class<T> type) {
                return Optional.of(
                    type.cast(
                        new OptionSelection() {
                            public boolean selected(Element option) {
                                return option == b;
                            }

                            public ElementBehavior.Click activate(
                                Element option, int button, int modifiers) {
                                throw new AssertionError("Rendering must not activate an option");
                            }
                        }));
            }
        };
        assertFalse(TextControlRenderer.selected(a, access));
        assertTrue(TextControlRenderer.selected(b, access));
    }
}
