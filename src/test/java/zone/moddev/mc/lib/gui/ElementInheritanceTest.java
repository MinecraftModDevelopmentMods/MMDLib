package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.FilterControl;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.ElementTooltips;
import zone.moddev.mc.lib.gui.client.SlotBehavior;
import zone.moddev.mc.lib.gui.client.WidgetRenderer;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.gui.model.containers.ContextElement;
import zone.moddev.mc.lib.gui.model.containers.GroupElement;
import zone.moddev.mc.lib.gui.model.containers.InventoryControlsElement;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.RadioGroupElement;
import zone.moddev.mc.lib.gui.model.containers.SideTabElement;
import zone.moddev.mc.lib.gui.model.containers.SpacerElement;
import zone.moddev.mc.lib.gui.model.containers.ViewElement;
import zone.moddev.mc.lib.gui.model.elements.AnimatedSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.ButtonElement;
import zone.moddev.mc.lib.gui.model.elements.CheckboxElement;
import zone.moddev.mc.lib.gui.model.elements.CustomElement;
import zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.model.elements.GaugeElement;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.model.elements.MeterElement;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.model.elements.SpriteElement;

class ElementInheritanceTest {
    static class Tank extends FluidTankElement {
        Tank() {
            super(Common.empty(), 0, Orientation.VERTICAL, 54);
        }

        public double fillRatio(Object value, Object maximum) {
            return .25;
        }

        public Component displayText(Object value, boolean detailed) {
            return Component.literal((detailed ? "exact " : "shown ") + value);
        }
    }

    static class Inventory extends InventoryElement {
        Inventory(List<Element> slots) {
            super(Common.empty(), "test", 1, 0, slots);
        }
    }

    static class Slot extends ItemSlotElement {
        Slot() {
            super(
                Common.empty(),
                0,
                SlotKind.NORMAL,
                SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(),
                    Optional.empty()));
        }
    }

    static class Toggle extends FilterControl.Toggle {
        Toggle() {
            super("test", ResourceLocation.parse("test:icon"), Component.literal("test"));
        }
    }

    static class Widget extends CustomElement {
        Widget() {
            super(Common.empty(), ResourceLocation.parse("test:widget"), 10, 10, Map.of());
        }

        public double fillRatio(Object value, Object maximum) {
            return .75;
        }
    }

    @Test
    void subclassesInheritRegisteredRenderingAndBehavior() {
        var registry = ElementRenderers.builtins();
        assertSame(registry.require(FluidTankElement.class), registry.require(Tank.class));
        assertSame(registry.require(InventoryElement.class), registry.require(Inventory.class));
        assertSame(registry.require(SlotElement.class), registry.require(Slot.class));
        assertSame(registry.require(FilterControl.Toggle.class), registry.require(Toggle.class));
        assertInstanceOf(
            SlotBehavior.class,
            ElementBehaviors.builtins().create(new ElementBehaviorsTest.Access(new Slot())));
        assertTrue(
            ElementBehaviors.builtins()
                .create(new ElementBehaviorsTest.Access(new Toggle()))
                .focusable());
        var children = new ArrayList<Element>(List.of(new Slot()));
        var inventory = new Inventory(children);
        children.clear();
        assertEquals(1, inventory.children().size());
        assertThrows(UnsupportedOperationException.class, () -> inventory.children().clear());
        assertArrayEquals(new int[]{18, 18}, inventory.measure(TextMeasure.NONE));
    }

    @Test
    void renderingAndTooltipsUseElementOverrides() {
        var tank = new Tank();
        assertEquals(
            .25,
            new ElementRenderers.Context(null, tank, new Rect(0, 0, 18, 54), 80, 100, 0, 0, 0)
                .ratio());
        assertEquals(
            .75,
            new WidgetRenderer.Context(null, new Widget(), new Rect(0, 0, 10, 10), 80, 100, 0, 0, 0)
                .ratio());
        var common = TooltipCompositionTest.authored("{value} / {max}");
        var label = new LabelElement(common, new Text("test", Optional.empty())) {
            public Component displayText(Object value, boolean detailed) {
                return Component.literal("custom " + value);
            }
        };
        var rows = ElementTooltips.collect(
            label,
            new TooltipCompositionTest.Access(),
            e -> new ElementBehavior() {
            },
            0,
            0,
            true,
            false,
            false);
        assertEquals(
            List.of("server", "Detailed custom 7/custom 9"),
            rows.stream().map(Component::getString).toList());
    }

    @Test
    void numericDefaultsRetainPrecisionAndRejectUnavailableRatios() {
        Element e = () -> Common.empty();
        var huge = BigInteger.TEN.pow(400);
        assertEquals(.5, e.fillRatio(huge, huge.multiply(BigInteger.TWO)));
        assertEquals(huge.toString(), e.displayText(huge, true).getString());
        assertEquals("40 k", e.displayText(BigInteger.valueOf(40000), false).getString());
        assertEquals(0, e.fillRatio(Double.NaN, 100));
        assertEquals(0, e.fillRatio(null, 100));
        assertEquals(0, e.fillRatio(1, 0));
        assertEquals(0, e.fillRatio(-1, 100));
        assertEquals(1, e.fillRatio(101, 100));
        assertEquals(.5, e.fillRatio(new BigDecimal("1E1000"), new BigDecimal("2E1000")));
    }

    @Test
    void customValuesUseElementConversionWithoutARegistry() {
        record Pressure(double amount) {
        }
        var gauge = new GaugeElement(Common.empty(), Orientation.VERTICAL, 54,
            Optional.of(0xffeeeeee)) {
            public Object displayValue(Object value) {
                return value instanceof Pressure pressure ? pressure.amount() : value;
            }
        };
        assertEquals(.5, gauge.fillRatio(new Pressure(2.5), new Pressure(5)));
        assertEquals("2.5", gauge.displayText(new Pressure(2.5), false).getString());
    }

    @Test
    void subclassTypesParticipateInDefinitionFingerprints() {
        var base = new FluidTankElement(Common.empty(), 0, Orientation.VERTICAL, 54);
        var derived = new Tank();
        assertNotEquals(base, derived);
        var a = new Screen(
            ResourceLocation.parse("test:screen"),
            "test",
            new Text("test", Optional.empty()),
            "test.Host",
            false,
            List.of(base),
            List.of());
        var b = new Screen(
            a.id(), a.source(), a.title(), a.blockEntity(), false, List.of(derived), List.of());
        assertNotEquals(a.fingerprint(), b.fingerprint());
        assertEquals(
            b.fingerprint(),
            new Screen(
                a.id(),
                a.source(),
                a.title(),
                a.blockEntity(),
                false,
                List.of(new Tank()),
                List.of())
                .fingerprint());
    }

    @Test
    void allBuiltInElementImplementationsAllowInheritance() {
        for (var type : List.of(
            AnimatedSpriteElement.class,
            ButtonElement.class,
            CheckboxElement.class,
            CustomElement.class,
            EnergyGaugeElement.class,
            FluidTankElement.class,
            GaugeElement.class,
            GhostSlotElement.class,
            ItemSlotElement.class,
            LabelElement.class,
            MeterElement.class,
            PopoutElement.class,
            ProcessorButtonElement.class,
            ProgressElement.class,
            ProgressSpriteElement.class,
            RadioElement.class,
            SideConfigElement.class,
            SpriteElement.class,
            BoxElement.class,
            ColouredGroupElement.class,
            ContextElement.class,
            GroupElement.class,
            InventoryControlsElement.class,
            InventoryElement.class,
            PlayerInventoryElement.class,
            RadioGroupElement.class,
            SideTabElement.class,
            SpacerElement.class,
            ViewElement.class,
            FilterControl.Toggle.class,
            FilterControl.Text.class,
            FilterControl.Number.class,
            FilterControl.Samples.class,
            FilterControl.Box.class,
            FilterControl.Custom.class)) {
            assertFalse(type.isRecord(), type.getName());
            assertFalse(java.lang.reflect.Modifier.isFinal(type.getModifiers()), type.getName());
            assertTrue(type.getConstructors().length > 0, type.getName());
        }
    }
}
