package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementAccess;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementRenderAccess;
import zone.moddev.mc.lib.gui.client.OptionSelection;
import zone.moddev.mc.lib.gui.client.RadioBehavior;
import zone.moddev.mc.lib.gui.client.TextControlRenderer;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.RadioGroupElement;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

class RadioSelectionTest {
    static RadioElement option() {
        return new RadioElement(Common.empty(),
            new Text("test", Optional.empty()));
    }

    static class Access implements ElementAccess {
        final Element element;
        Object value = BigInteger.ONE;
        boolean accept = true;
        Element sent;
        Object payload;
        ElementAction<?> action;
        ElementRenderAccess presentation;

        Access(Element element) {
            this.element = element;
        }

        public Element element() {
            return element;
        }

        public Rect bounds() {
            return new Rect(0, 0, 40, 10);
        }

        public Object value() {
            return value;
        }

        public Object maximum() {
            return null;
        }

        public ElementRenderAccess presentation() {
            return presentation;
        }

        public List<Element> elements() {
            return List.of(element);
        }

        public ResourceLocation screenId() {
            return ResourceLocation.parse("test:radio");
        }

        public boolean pending(Element element) {
            return !accept;
        }

        public int modifiers() {
            return 5;
        }

        public <T> boolean submit(Element e, ElementAction<T> a, T v) {
            sent = e;
            action = a;
            payload = v;
            return accept;
        }

        public void navigate(String context, String view) {
        }

        public ItemStack carried() {
            throw new AssertionError();
        }

        public SlotPlan reservation(SlotElement e) {
            return null;
        }

        public boolean showRecipes() {
            return false;
        }
    }

    @Test
    void groupOwnsIdentitySelectionAndActivationWithoutPrediction() {
        var first = option();
        var second = option();
        var outsider = option();
        assertEquals(first, second);
        var group = new RadioGroupElement(Common.empty(), Optional.empty(),
            List.of(first, second));
        var access = new Access(group);
        var behavior = ElementBehaviors.builtins().create(access);
        var selection = assertInstanceOf(OptionSelection.class, behavior);
        assertFalse(selection.selected(first));
        assertTrue(selection.selected(second));
        assertFalse(selection.selected(outsider));
        assertEquals(ElementBehavior.Click.PASS,
            selection.activate(outsider, 0, 0));
        assertEquals(ElementBehavior.Click.PASS,
            selection.activate(first, 2, 0));
        assertNull(access.sent);
        access.accept = false;
        assertEquals(ElementBehavior.Click.CONSUME,
            selection.activate(first, 1, 5));
        assertSame(group, access.sent);
        assertSame(ElementActions.CLICK, access.action);
        assertEquals(new ElementActions.Click(1, 5, 0), access.payload);
        assertTrue(selection.selected(second));
        access.value = BigInteger.ZERO;
        assertTrue(selection.selected(first));
        assertFalse(selection.selected(second));
    }

    @Test
    void customParentCapabilityControlsDrawingAndInput() {
        Element parent = () -> Common.empty();
        var radio = option();
        var access = new Access(radio);
        var activation = new ArrayList<Integer>();
        var selection = new OptionSelection() {
            public boolean selected(Element option) {
                assertSame(radio, option);
                return true;
            }

            public ElementBehavior.Click activate(Element option, int button,
                int modifiers) {
                assertSame(radio, option);
                activation.add(button);
                activation.add(modifiers);
                return ElementBehavior.Click.CONSUME;
            }
        };
        access.presentation = new ElementRenderAccess() {
            public Font font() {
                return null;
            }

            public Rect viewport() {
                return access.bounds();
            }

            public Optional<Element> parent(Element e) {
                return Optional.of(parent);
            }

            public <T> Optional<T> capability(Element e, Class<T> type) {
                assertSame(parent, e);
                return type == OptionSelection.class
                    ? Optional.of(type.cast(selection))
                    : Optional.empty();
            }
        };
        assertTrue(TextControlRenderer.selected(radio, access.presentation));
        assertEquals(ElementBehavior.Click.CONSUME,
            new RadioBehavior(radio, access).mouseClicked(1, 1, 0));
        assertEquals(List.of(0, 5), activation);
        assertNull(access.sent);
    }

    @Test
    void standaloneOptionsAndExplicitCallbacksKeepTheirOwnBehavior() {
        var c = Common.empty();
        var clicks = new Common.Clicks(
            Optional.of(ResourceLocation.parse("test:choose")),
            Optional.empty(), Optional.empty(), Optional.empty());
        var common = new Common(c.id(), c.value(), c.max(), c.tooltip(),
            c.tooltipShift(), clicks, c.onHover(), c.hoverShows(),
            c.jeiRecipes(), 0, 0, 0);
        var radio = new RadioElement(common,
            new Text("test", Optional.empty()));
        var access = new Access(radio);
        access.presentation = new ElementRenderAccess() {
            public Font font() {
                return null;
            }

            public Rect viewport() {
                return access.bounds();
            }
        };
        assertFalse(TextControlRenderer.selected(radio, access.presentation));
        assertEquals(ElementBehavior.Click.CONSUME,
            new RadioBehavior(radio, access).mouseClicked(1, 1, 0));
        assertSame(radio, access.sent);
        assertEquals(new ElementActions.Click(0, 5, 0), access.payload);
        var standalone = option();
        var standaloneAccess = new Access(standalone);
        standaloneAccess.presentation = access.presentation;
        assertEquals(ElementBehavior.Click.PASS,
            new RadioBehavior(standalone, standaloneAccess).mouseClicked(1, 1,
                0));
        assertNull(standaloneAccess.sent);
    }
}
