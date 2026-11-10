package zone.moddev.mc.companion.test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.client.ColouredGroupBehavior;
import zone.moddev.mc.lib.gui.client.DefaultElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementAccess;
import zone.moddev.mc.lib.gui.client.ElementBehaviors;
import zone.moddev.mc.lib.gui.client.ElementHighlight;
import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.client.GroupLabelProvider;
import zone.moddev.mc.lib.gui.client.HighlightPresentation;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.gui.model.containers.GroupElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerArmourElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerOffhandElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerSlotsElement;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Real inline and drawer callers consume mod-owned labels/highlights through presentation services.
 */
final class GroupPresentationClientSmoke {
    private static int phase, labelReads;
    private static int customEmphasis, beforeAssociationClick, inventoryWait;
    private static final Set<String> renderedPlayerParts = new HashSet<>();

    private static class ModArmour extends PlayerArmourElement {
        ModArmour(Common common) {
            super(common, ModPlayerSlot::new);
        }
    }

    private static class ModPlayerSlot
        extends
            zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement {
        ModPlayerSlot(int index) {
            super(Common.empty(), index, appearance(index));
        }

        private static Appearance appearance(int index) {
            var a = player(index).appearance();
            return new Appearance(
                a.size(),
                Optional.of(0x88bbcc),
                a.icon(),
                a.background(),
                a.ghostAlpha(),
                a.iconAtlas(),
                a.itemSize());
        }
    }

    private static final Set<ElementHighlight.Kind> customRequests = EnumSet
        .noneOf(ElementHighlight.Kind.class);
    private static final Set<ElementHighlight.Kind> colouredRequests = EnumSet
        .noneOf(ElementHighlight.Kind.class);

    private record ModGroup(Common common) implements Element {
        public List<String> associatedGroups(Screen screen, List<ElementGroup> groups) {
            var ids = new ArrayList<String>();
            ids.add("tank_3");
            ids.addAll(Element.super.associatedGroups(screen, groups));
            return List.copyOf(ids);
        }

        public int[] measure(TextMeasure text) {
            return new int[]{44, 22};
        }
    }

    private static final class ModBehavior extends DefaultElementBehavior
        implements
            GroupLabelProvider,
            HighlightPresentation {
        ModBehavior(ModGroup element, ElementAccess access) {
            super(access);
        }

        public Optional<Text> groupLabel() {
            labelReads++;
            return Optional.of(text("Mod tank"));
        }

        public void highlight(GuiGraphics graphics, Rect bounds, ElementHighlight request) {
            customRequests.add(request.kind());
            if (request.kind() == ElementHighlight.Kind.EMPHASIS)
                customEmphasis++;
            if (!bounds.equals(access.bounds()))
                throw new AssertionError("Highlight lost element bounds");
            var r = request.outline(bounds);
            graphics.renderOutline(r.x(), r.y(), r.width(), r.height(), 0xffff55cc);
            graphics.fill(r.x(), r.y(), r.x() + 4, r.y() + 4, 0xff55ffff);
        }
    }

    private static Text text(String value) {
        return new Text("test.group." + value.replace(' ', '_'), Optional.of(value));
    }

    private static Common common(String id, String tooltip) {
        var e = Common.empty();
        return new Common(
            Optional.of(id),
            e.value(),
            e.max(),
            Optional.of(text(tooltip)),
            e.tooltipShift(),
            e.clicks(),
            e.onHover(),
            e.hoverShows(),
            e.jeiRecipes(),
            0,
            0,
            0);
    }

    static void install() {
        ElementRenderers.GLOBAL.replace(
            PlayerSlotsElement.class,
            (element, context) -> renderedPlayerParts.add(element.part()));
        ElementBehaviors.GLOBAL.register(ModGroup.class, ModBehavior::new);
        ElementRenderers.GLOBAL.register(
            ModGroup.class,
            (e, c) -> {
                var r = c.bounds();
                c.graphics().fill(r.x(), r.y(), r.right(), r.bottom(), 0xff304050);
                c.graphics().drawString(c.access().font(), "Mod", r.x() + 4, r.y() + 7, 0xffffff);
            });
        ElementBehaviors.GLOBAL.replace(
            ColouredGroupElement.class,
            (element, access) -> new ColouredGroupBehavior(element, access) {
                public void highlight(GuiGraphics graphics, Rect bounds, ElementHighlight request) {
                    colouredRequests.add(request.kind());
                    super.highlight(graphics, bounds, request);
                }
            });
        var id = ResourceLocation.parse("mmdlib_companion:logistics");
        var original = Screens.get(id).orElseThrow().screen();
        var body = new ArrayList<Element>();
        body.add(
            new BoxElement(
                Common.empty(),
                false,
                0,
                6,
                Align.START,
                List.of(
                    new ModGroup(common("tank_1", "Wrong label")),
                    new GroupElement(
                        common("labelled_group", "Wrong label"),
                        Optional.of(text("Panel")),
                        Optional.empty(),
                        3,
                        List.of(new LabelElement(Common.empty(), text("Body")))),
                    new ColouredGroupElement(
                        common("tank_2", "Coloured"),
                        0xff55ff88,
                        new LabelElement(Common.empty(), text("Owned"))),
                    new LabelElement(common("tank_3", "Plain"), text("Plain")),
                    new SideConfigElement(
                        common("group_selector", "Select"),
                        List.of("tank_1", "labelled_group", "tank_2", "tank_3")))));
        body.add(
            new BoxElement(
                Common.empty(),
                false,
                0,
                6,
                Align.START,
                List.of(
                    new ModArmour(common("player_armour", "Armour")),
                    new PlayerInventoryElement(common("player_items", "Inventory"),
                        ModPlayerSlot::new),
                    new PlayerOffhandElement(
                        common("player_offhand", "Offhand"), ModPlayerSlot::new))));
        var screen = new zone.moddev.mc.lib.gui.model.Screen(
            original.id(),
            original.source(),
            original.title(),
            original.blockEntity(),
            original.playerInventory(),
            body,
            original.tabs());
        var screens = new HashMap<>(Screens.all());
        screens.put(id,
            ScreenBinder.bind(screen, GroupPresentationClientSmoke.class.getClassLoader()));
        Screens.install(screens);
    }

    static boolean run(MachineScreen screen) {
        var menu = screen.getMenu();
        var automation = menu.components().require(BuiltinMenuComponents.AUTOMATION);
        if (automation.pending())
            return false;
        var selector = menu.bound.screen().find("group_selector").orElseThrow();
        switch (phase) {
            case 0 -> {
                require(
                    labelReads > 0 && customRequests.contains(ElementHighlight.Kind.SELECTION),
                    "Inline side config reads the mod caption and invokes its highlight capability");
                require(
                    customRequests.contains(ElementHighlight.Kind.DECORATION),
                    "Drawer identification reaches the custom highlight capability");
                assertLabel(screen, "tank_1", "Mod tank");
                assertLabel(screen, "labelled_group", "Panel");
                assertLabel(screen, "tank_2", "Coloured");
                assertLabel(screen, "tank_3", "Plain");
                select(automation, "tank_1");
            }
            case 1 -> {
                require(
                    customRequests.contains(ElementHighlight.Kind.EMPHASIS),
                    "Opened drawer emphasizes the mod group through the same capability");
                capture("groups-custom.png");
                click(screen, selector);
            }
            case 2 -> {
                click(screen, selector);
                select(automation, "tank_2");
            }
            case 3 -> {
                require(
                    colouredRequests.containsAll(EnumSet.allOf(ElementHighlight.Kind.class)),
                    "Coloured wrapper handles decoration, emphasis and inline selection through its"
                        + " contract");
                capture("groups-coloured.png");
                click(screen, selector);
                select(automation, "tank_3");
            }
            case 4 -> {
                capture("groups-fallback.png");
                automation.send("close", -1, -1, "", 0, "");
            }
            case 5 -> {
                beforeAssociationClick = customEmphasis;
                var bar = screen.layout.region(PlayerInventoryElement.TOOLBAR).orElseThrow();
                require(
                    screen.mouseClicked(screen.guiLeft() + bar.x() + 3,
                        screen.guiTop() + bar.y() + 3, 0),
                    "Generic colour bar accepts selection");
                screen.mouseReleased(screen.guiLeft() + bar.x() + 3, screen.guiTop() + bar.y() + 3,
                    0);
            }
            case 6 -> {
                require(
                    automation.open()
                        && automation.inventory.group(automation.selected()).id().equals("tank_3"),
                    "Mod-owned association orders the bar and selects the original server group");
                require(
                    customEmphasis > beforeAssociationClick,
                    "Mod-owned association also controls the selected group's highlight targets");
                require(
                    renderedPlayerParts.containsAll(List.of("storage", "armour", "offhand")),
                    "All three player sections render through the registered inherited renderer");
                for (var slot : menu.playerSlotElements()) {
                    var nativeSlot = menu.slot(slot);
                    var bounds = screen.bounds(slot);
                    require(
                        nativeSlot.x + screen.guiLeft() == bounds.x() + 1
                            && nativeSlot.y + screen.guiTop() == bounds.y() + 1,
                        "Native slot follows ordinary element bounds: " + slot.index());
                }
                capture("groups-associations.png");
                automation.send("close", -1, -1, "", 0, "");
                var mc = Minecraft.getInstance();
                var id = mc.player.getUUID();
                mc.getSingleplayerServer()
                    .execute(
                        () -> {
                            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                            player
                                .getInventory()
                                .setItem(
                                    40,
                                    new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.SHIELD));
                            player
                                .getInventory()
                                .setItem(
                                    39,
                                    new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.DIAMOND_HELMET));
                            player.containerMenu.broadcastChanges();
                        });
            }
            case 7 -> {
                if (!Minecraft.getInstance().player
                    .getInventory()
                    .getItem(40)
                    .is(net.minecraft.world.item.Items.SHIELD)) {
                    require(++inventoryWait < 40, "Player inventory update timed out");
                    return false;
                }
                click(
                    screen,
                    menu.playerSlotElements().stream()
                        .filter(slot -> slot.index() == 40)
                        .findFirst()
                        .orElseThrow());
            }
            case 8 -> {
                require(
                    menu.getCarried().is(net.minecraft.world.item.Items.SHIELD),
                    "Offhand element click picks up its native item");
                click(
                    screen,
                    menu.playerSlotElements().stream()
                        .filter(slot -> slot.index() == 40)
                        .findFirst()
                        .orElseThrow());
            }
            case 9 -> {
                require(
                    menu.getCarried().isEmpty()
                        && Minecraft.getInstance().player
                            .getInventory()
                            .getItem(40)
                            .is(net.minecraft.world.item.Items.SHIELD),
                    "Offhand element click returns its native item");
                capture("player-sections.png");
            }
            default -> {
                return true;
            }
        }
        phase++;
        return false;
    }

    private static void assertLabel(MachineScreen screen, String id, String expected) {
        var access = screen.presentation();
        require(
            access
                .text(access.groupLabel(access.find(id).orElseThrow()).orElseThrow())
                .getString()
                .equals(expected),
            "Unexpected presentation caption for " + id);
    }

    private static void select(zone.moddev.mc.lib.gui.runtime.AutomationMenu automation,
        String id) {
        for (int i = 0; i < automation.inventory.groups().size(); i++)
            if (automation.inventory.group(i).id().equals(id)) {
                require(automation.send("select", i, -1, "", 0, ""), "Select group " + id);
                return;
            }
        throw new AssertionError("Missing resource group " + id);
    }

    private static void click(MachineScreen screen, Element element) {
        var r = screen.bounds(element);
        require(screen.mouseClicked(r.x() + 3, r.y() + 3, 0),
            "Inline group selector accepts click");
        screen.mouseReleased(r.x() + 3, r.y() + 3, 0);
    }

    private static void capture(String filename) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> {
        });
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
