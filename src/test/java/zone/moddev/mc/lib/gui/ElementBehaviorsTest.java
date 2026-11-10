package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementAccess;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementInput;
import zone.moddev.mc.lib.gui.client.ElementRenderAccess;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.model.ValueRef;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Mod types outside the implementation package exercise the public contracts. */
class ElementBehaviorsTest {
    interface Family extends Element {
    }

    interface Specific extends Family {
    }

    interface Other extends Element {
    }

    record ModElement(Common common) implements Specific, Other {
        public int[] measure(TextMeasure text) {
            return new int[]{20, 10};
        }
    }

    static class State implements ElementBehavior {
        int drags, releases, cancels, initializations;

        public boolean focusable() {
            return true;
        }

        public Click mouseClicked(double x, double y, int button) {
            return Click.CAPTURE;
        }

        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            drags++;
            return true;
        }

        public boolean mouseReleased(double x, double y, int button) {
            releases++;
            return true;
        }

        public void cancelInteraction() {
            cancels++;
        }

        public void initialized() {
            initializations++;
        }
    }

    record Access(Element element) implements ElementAccess {
        public Rect bounds() {
            return new Rect(0, 0, 20, 10);
        }

        public Object value() {
            return null;
        }

        public Object maximum() {
            return null;
        }

        public ElementRenderAccess presentation() {
            return null;
        }

        public List<Element> elements() {
            return List.of(element);
        }

        public ResourceLocation screenId() {
            return ResourceLocation.parse("test:behaviors");
        }

        public boolean pending(Element e) {
            return false;
        }

        public <T> boolean submit(Element e, ElementAction<T> a, T v) {
            return false;
        }

        public void navigate(String context, String view) {
        }

        public ItemStack carried() {
            throw new UnsupportedOperationException();
        }

        public SlotPlan reservation(SlotElement e) {
            return null;
        }

        public boolean showRecipes() {
            return false;
        }
    }

    @Test
    void factoriesResolveSpecificTypesAndRejectAmbiguityAndDuplicateRegistration() {
        var registry = new ElementBehaviors();
        var access = new Access(new ModElement(Common.empty()));
        registry.register(Family.class, (e, a) -> new State());
        registry.register(Specific.class, (e, a) -> new DefaultElementBehavior(a));
        assertInstanceOf(DefaultElementBehavior.class, registry.create(access));
        registry.register(Other.class, (e, a) -> new State());
        assertThrows(IllegalArgumentException.class, () -> registry.create(access));
        registry.register(ModElement.class, (e, a) -> new State());
        assertInstanceOf(State.class, registry.create(access));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(ModElement.class, (e, a) -> new State()));
        registry.replace(ModElement.class, (e, a) -> new DefaultElementBehavior(a));
        assertInstanceOf(DefaultElementBehavior.class, registry.create(access));
    }

    @Test
    void stateIsPerOpenScreenAndCaptureSurvivesMovingOutsideButNotHiding() {
        var registry = new ElementBehaviors();
        registry.register(ModElement.class, (e, a) -> new State());
        var element = new ModElement(Common.empty());
        var state = (State) registry.create(new Access(element));
        assertNotSame(state, registry.create(new Access(element)));
        boolean[] visible = {true};
        var input = new ElementInput<>(
            List.of(element), e -> state, e -> visible[0], e -> new Rect(0, 0, 20, 10));
        input.initialized();
        input.update();
        assertEquals(1, state.initializations);
        assertTrue(input.click(element, 2, 2, 0));
        assertTrue(input.drag(200, 200, 0, 5, 5));
        assertEquals(1, state.drags);
        visible[0] = false;
        input.update();
        assertEquals(1, state.cancels);
        assertNull(input.focused());
        assertTrue(input.release(200, 200, 0));
        assertEquals(0, state.releases);
    }

    @Test
    void adaptersCannotHideReusedMutableState() {
        var first = new ModElement(Common.empty());
        var second = new ModElement(Common.empty());
        var shared = new Object();
        assertThrows(
            IllegalArgumentException.class,
            () -> new ElementInput<>(
                List.of(first, second),
                e -> new State() {
                    public Object stateIdentity() {
                        return shared;
                    }
                },
                e -> true,
                e -> new Rect(0, 0, 20, 10)));
    }

    @Test
    void actionsUseBoundedCodecsWithoutRequiringCustomElements() {
        var action = new ElementAction<Integer>(
            ResourceLocation.parse("test:choose"),
            StreamCodec.of(
                RegistryFriendlyByteBuf::writeVarInt, RegistryFriendlyByteBuf::readVarInt),
            5,
            (context, value) -> value == 7);
        var encoded = action.encode(RegistryAccess.EMPTY, 7);
        assertEquals(7, action.decode(RegistryAccess.EMPTY, encoded));
        assertThrows(
            IllegalArgumentException.class,
            () -> action.decode(RegistryAccess.EMPTY, Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(
            IllegalArgumentException.class, () -> action.decode(RegistryAccess.EMPTY, new byte[6]));
        assertThrows(IllegalArgumentException.class, () -> new ElementActions.Click(0, 0, -1));
    }

    record TypedElement(Common common) implements Element {
        public int[] measure(TextMeasure text) {
            return new int[]{20, 10};
        }

        public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
            return Set.of(ValueTypes.WHOLE);
        }

        public void validate() {
            Element.super.validate();
            ValueContract.require(this, "Mod counter", false);
        }
    }

    private Common binding(String getter) {
        return new Common(
            Optional.of("mod"),
            Optional.ofNullable(getter).map(ValueRef::parse),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Common.Clicks.NONE,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            0,
            0,
            23);
    }

    private Screen screen(Element element) {
        return new Screen(
            ResourceLocation.parse("test:contracts"),
            "mod.java",
            new Text("test", Optional.empty()),
            ScreenLoaderTest.Press.class.getName(),
            false,
            List.of(element),
            List.of());
    }

    @Test
    void modValueContractsApplyToJavaScreensWithSourceDiagnostics() {
        var loader = getClass().getClassLoader();
        var valid = new TypedElement(binding("#getProgress"));
        assertEquals(
            ValueTypes.WHOLE, ScreenBinder.bind(screen(valid), loader).values().get(valid).kind());
        var wrong = assertThrows(
            ScreenException.class,
            () -> ScreenBinder.bind(screen(new TypedElement(binding("#isAutoEject"))), loader));
        assertTrue(wrong.getMessage().contains("mod.java:23:"));
        assertTrue(wrong.getMessage().contains("whole number"));
        var missing = assertThrows(
            ScreenException.class,
            () -> ScreenBinder.bind(screen(new TypedElement(binding(null))), loader));
        assertTrue(missing.getMessage().contains("Mod counter needs a value getter"));
    }
}
