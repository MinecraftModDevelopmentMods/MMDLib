package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenValidation;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation;

class ElementValidationTest {
    private record ClaimElement(Common common, String domain, String key) implements Element {
        public void validateScreen(ScreenValidationContext context) {
            context.unique(ResourceLocation.parse(domain), key, "Duplicate custom binding: " + key);
        }
    }

    private record ModSlot(Common common, int index) implements SlotElement {
        public SlotKind kind() {
            return SlotKind.NORMAL;
        }

        public Appearance appearance() {
            return Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());
        }
    }

    private static Screen screen(boolean playerInventory, Element... elements) {
        var screen = new Screen(
            ResourceLocation.parse("test:validation"),
            "validation.java",
            new Text("test", Optional.empty()),
            "net.minecraft.world.level.block.entity.BlockEntity",
            playerInventory,
            List.of(elements),
            List.of());
        var origins = new IdentityHashMap<Common, String>();
        screen.forEach(element -> origins.put(element.common(), "inherited.xml"));
        return new Screen(
            screen.id(),
            screen.source(),
            screen.title(),
            screen.blockEntity(),
            screen.playerInventory(),
            screen.body(),
            screen.tabs(),
            origins);
    }

    private static void validate(Screen screen) {
        ScreenValidation.check(screen, WidgetTypes.GLOBAL);
    }

    @Test
    void customClaimsAreNamespacedAndIsolatedPerPass() {
        var first = new ClaimElement(Common.empty(), "example:binding", "one");
        var otherDomain = new ClaimElement(Common.empty(), "another:binding", "one");
        var otherKey = new ClaimElement(Common.empty(), "example:binding", "two");
        var valid = screen(false, first, otherDomain, otherKey);
        assertDoesNotThrow(() -> validate(valid));
        assertDoesNotThrow(() -> validate(valid));
        var duplicate = new ClaimElement(Common.empty(), "example:binding", "one");
        var failure = assertThrows(ScreenException.class,
            () -> validate(screen(false, first, duplicate)));
        assertTrue(failure.getMessage().contains("inherited.xml"));
        assertTrue(failure.getMessage().contains("Duplicate custom binding: one"));
    }

    @Test
    void slotClaimsUseNamedInventoryAndApplyToModSlots() {
        var first = new ModSlot(Common.empty(), 0);
        var second = new ModSlot(Common.empty(), 0);
        var input = new InventoryElement(Common.empty(), "input", 1, 0, List.of(first));
        var output = new InventoryElement(Common.empty(), "output", 1, 0, List.of(second));
        assertDoesNotThrow(() -> validate(screen(false, input, output)));
        var repeated = new InventoryElement(Common.empty(), "input", 1, 0, List.of(second));
        assertTrue(
            assertThrows(ScreenException.class, () -> validate(screen(false, input, repeated)))
                .getMessage()
                .contains("Slot 0 is placed twice"));
    }

    @Test
    void playerInventoryOwnsUniquenessAndScreenCompatibility() {
        var inventory = new PlayerInventoryElement(Common.empty());
        assertDoesNotThrow(() -> validate(screen(true, inventory)));
        assertTrue(
            assertThrows(
                ScreenException.class,
                () -> validate(screen(true, inventory, new PlayerInventoryElement(Common.empty()))))
                .getMessage()
                .contains("may appear only once"));
        assertTrue(
            assertThrows(ScreenException.class, () -> validate(screen(false, inventory)))
                .getMessage()
                .contains("inventory=\"none\""));
    }

    private static ScreenHost host() {
        var storage = (IItemHandlerModifiable) Proxy.newProxyInstance(
            ElementValidationTest.class.getClassLoader(),
            new Class<?>[]{IItemHandlerModifiable.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getSlots"))
                    return 1;
                throw new AssertionError("Validation must not access items: " + method.getName());
            });
        return new ScreenHost() {
            public IItemHandlerModifiable screenItems() {
                return storage;
            }

            public Map<String, ? extends IItemHandlerModifiable> screenInventories() {
                return Map.of("input", storage);
            }
        };
    }

    @Test
    void hostValidationUsesScreenContextAndRetainsSourceErrors() {
        var slot = new ModSlot(Common.empty(), 1);
        var inventory = new InventoryElement(Common.empty(), "input", 1, 0, List.of(slot));
        var failure = assertThrows(
            ScreenException.class,
            () -> ScreenInventoryValidation.validate(screen(false, inventory), host()));
        assertTrue(failure.getMessage().contains("inherited.xml"));
        assertTrue(failure.getMessage().contains("inventory input outside storage of size 1"));
    }

    @Test
    void existingScreenAndHostHooksStillRun() {
        var called = new ArrayList<String>();
        var element = new Element() {
            public Common common() {
                return Common.empty();
            }

            public void validateScreen(Screen screen) {
                called.add("screen");
            }

            public void validateHost(ScreenHost host) {
                called.add("host");
            }
        };
        var screen = screen(false, element);
        validate(screen);
        ScreenInventoryValidation.validate(screen, host());
        assertEquals(List.of("screen", "host"), called);
    }

    @Test
    void customHostHooksReceiveSharedContextWithoutSlotInterfaces() {
        var visited = new ArrayList<Screen>();
        var element = new Element() {
            private final Common common = Common.empty();

            public Common common() {
                return common;
            }

            public void validateHost(ScreenValidationContext context, ScreenHost host) {
                visited.add(context.screen());
                context.unique(
                    ResourceLocation.parse("example:host_binding"), "one", "Repeated host binding");
            }
        };
        var single = screen(false, element);
        ScreenInventoryValidation.validate(single, host());
        ScreenInventoryValidation.validate(single, host());
        assertEquals(List.of(single, single), visited);
        assertTrue(
            assertThrows(
                ScreenException.class,
                () -> ScreenInventoryValidation.validate(screen(false, element, element), host()))
                .getMessage()
                .contains("Repeated host binding"));
    }
}
