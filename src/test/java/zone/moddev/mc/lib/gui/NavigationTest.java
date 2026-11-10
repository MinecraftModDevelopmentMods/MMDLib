package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.gui.Font;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementAccess;
import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementRenderAccess;
import zone.moddev.mc.lib.gui.client.NavigableBehavior;
import zone.moddev.mc.lib.gui.client.NavigationMemory;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.load.ScreenValidation;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Navigation;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ViewRef;
import zone.moddev.mc.lib.gui.model.containers.NavigableElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

class NavigationTest {
    private static final ElementSync<Integer> SELECTION = ElementSync.field(
        ResourceLocation.parse("test:navigation"),
        StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
        5,
        element -> 0)
        .validate((element, value) -> value == 0 || value == 1)
        .build();

    private record Destination(Common common) implements NavigableElement, SynchronizedElement {
        public List<ElementSync<?>> synchronizedValues() {
            return List.of(SELECTION);
        }

        public String defaultSelection() {
            return "home";
        }

        public boolean hasSelection(String selection) {
            return Set.of("home", "details").contains(selection);
        }

        public String selected(ElementStateView state) {
            return state.get(this, SELECTION) == 0 ? "home" : "details";
        }

        public boolean select(ElementStates state, String selection) {
            return hasSelection(selection)
                && state.set(this, SELECTION, selection.equals("home") ? 0 : 1);
        }
    }

    private record Control(Common common, ViewRef target) implements Element {
        public Optional<Navigation> navigation() {
            return Optional.of(new Navigation(target));
        }
    }

    private static Common common(String id) {
        var empty = Common.empty();
        return new Common(
            Optional.of(id),
            empty.value(),
            empty.max(),
            empty.tooltip(),
            empty.tooltipShift(),
            empty.clicks(),
            empty.onHover(),
            empty.hoverShows(),
            empty.jeiRecipes(),
            0,
            0,
            7);
    }

    private static class Fixture {
        final Destination destination = new Destination(common("destination"));
        final Control control = new Control(common("control"),
            new ViewRef("destination", "details"));
        final Screen screen;
        final ElementStates state;
        final List<Element> sent = new ArrayList<>();
        final Set<Element> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean accept = true;

        Fixture(String name, Element... extra) {
            var elements = new ArrayList<Element>(List.of(destination));
            elements.addAll(List.of(extra));
            elements.add(control);
            screen = new Screen(
                ResourceLocation.parse("test:" + name),
                "navigation.xml",
                new Text("test", Optional.empty()),
                "net.minecraft.world.level.block.entity.BlockEntity",
                false,
                elements,
                List.of());
            state = new ElementStates(elements, RegistryAccess.EMPTY, () -> {
            });
        }

        ElementAccess access(Element element) {
            return new ElementAccess() {
                public Element element() {
                    return element;
                }

                public Rect bounds() {
                    return new Rect(0, 0, 20, 20);
                }

                public Object value() {
                    return null;
                }

                public Object maximum() {
                    return null;
                }

                public List<Element> elements() {
                    return screen.elements();
                }

                public ResourceLocation screenId() {
                    return screen.id();
                }

                public boolean pending(Element target) {
                    return false;
                }

                public int modifiers() {
                    return 0;
                }

                public <T> boolean submit(Element target, ElementAction<T> action, T value) {
                    assertSame(Navigation.NAVIGATE, action);
                    assertSame(action, target.action(action.id()).orElseThrow());
                    if (!accept)
                        return false;
                    sent.add(target);
                    return true;
                }

                public void navigate(String id, String selection) {
                    ((NavigableElement) screen.find(id).orElseThrow()).select(state, selection);
                }

                public ItemStack carried() {
                    throw new UnsupportedOperationException();
                }

                public SlotPlan reservation(SlotElement slot) {
                    throw new UnsupportedOperationException();
                }

                public boolean showRecipes() {
                    return false;
                }

                public ElementRenderAccess presentation() {
                    return new ElementRenderAccess() {
                        public Font font() {
                            return null;
                        }

                        public Rect viewport() {
                            return new Rect(0, 0, 20, 20);
                        }

                        public Optional<Element> find(String id) {
                            return screen.find(id);
                        }

                        public boolean visible(Element target) {
                            return !hidden.contains(target);
                        }

                        public ElementStateView state() {
                            return state;
                        }
                    };
                }
            };
        }
    }

    @Test
    void plainControlsValidateAndToggleCustomDestinations() {
        var f = new Fixture("navigation_contract");
        ScreenValidation.check(f.screen, new WidgetTypes());
        assertSame(Navigation.NAVIGATE, f.control.action(Navigation.NAVIGATE.id()).orElseThrow());
        var binding = f.control.navigation().orElseThrow();
        assertTrue(binding.select(f.screen::find, f.state));
        assertEquals("details", f.destination.selected(f.state));
        assertTrue(binding.select(f.screen::find, f.state));
        assertEquals("home", f.destination.selected(f.state));
        var missing = new Navigation(new ViewRef("missing", "details"));
        assertThrows(IllegalArgumentException.class, () -> missing.validate(f.screen));
        assertFalse(missing.select(f.screen::find, f.state));
        var badView = new Navigation(new ViewRef("destination", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> badView.validate(f.screen));
        assertFalse(badView.select(f.screen::find, f.state));
        assertEquals("home", f.destination.selected(f.state));
    }

    @Test
    void defaultBehaviorPredictsAndRemembersOnlyCorrectedStateAfterRejection() {
        var f = new Fixture("navigation_prediction");
        var behavior = ElementBehaviors.builtins().create(f.access(f.control));
        f.accept = false;
        behavior.mouseClicked(1, 1, 0);
        assertEquals("home", f.destination.selected(f.state));
        f.accept = true;
        var authoritative = new HashMap<Integer, Object>(f.state.snapshot());
        assertEquals(ElementBehavior.Click.CONSUME, behavior.mouseClicked(1, 1, 0));
        assertEquals("details", f.destination.selected(f.state));
        assertEquals(List.of(f.control), f.sent);
        f.state.apply(authoritative);
        behavior.actionResult(false);
        var reopened = new Fixture("navigation_prediction");
        ElementBehaviors.builtins().create(reopened.access(reopened.destination)).initialized();
        assertTrue(reopened.sent.isEmpty(), "Rejected prediction must not be restored");
    }

    @Test
    void restorationUsesVisibleCustomControlsWithoutLocalPrediction() {
        var f = new Fixture("navigation_restore");
        var behavior = ElementBehaviors.builtins().create(f.access(f.control));
        behavior.mouseClicked(1, 1, 0);
        behavior.actionResult(true);
        var hidden = new Control(common("hidden"), new ViewRef("destination", "details"));
        var reopened = new Fixture("navigation_restore", hidden);
        reopened.hidden.add(hidden);
        var destinationBehavior = ElementBehaviors.builtins()
            .create(reopened.access(reopened.destination));
        assertInstanceOf(NavigableBehavior.class, destinationBehavior);
        destinationBehavior.initialized();
        destinationBehavior.initialized();
        assertEquals(List.of(reopened.control), reopened.sent);
        assertEquals("home", reopened.destination.selected(reopened.state));
        reopened.hidden.add(reopened.control);
        reopened.sent.clear();
        NavigationMemory.restore(reopened.access(reopened.destination), reopened.destination);
        assertTrue(reopened.sent.isEmpty(), "Hidden controls cannot restore a view");
    }
}
