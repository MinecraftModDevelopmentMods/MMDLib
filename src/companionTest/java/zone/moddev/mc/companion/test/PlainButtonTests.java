package zone.moddev.mc.companion.test;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Navigation;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class PlainButtonTests {
    @GameTest(template = "empty")
    public static void ordinaryBlockEntitiesAcceptLegacyAndTypedButtonActions(
        GameTestHelper h) throws Exception {
        var id = ResourceLocation.parse("minecraft:furnace");
        var xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test"
                block-entity="net.minecraft.world.level.block.entity.FurnaceBlockEntity" inventory="none">
              <vbox>
                <button id="next" text="Next" tooltip="Next" shows="pages:details"/>
                <context id="pages">
                  <view id="home"><label text="Home"/></view>
                  <view id="details"><label text="Details"/></view>
                </context>
              </vbox>
            </screen>
            """;
        var screen = ScreenReader.read(id, "plain-buttons.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var previous = Screens.all();
        var screens = new HashMap<>(previous);
        screens.put(id,
            ScreenBinder.bind(screen, PlainButtonTests.class.getClassLoader()));
        Screens.install(screens);
        var player = h.makeMockServerPlayerInLevel();
        try {
            var pos = h.absolutePos(new BlockPos(2, 2, 2));
            h.getLevel().setBlock(pos, Blocks.FURNACE.defaultBlockState(), 3);
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
            var menu = new MachineMenu(70, player.getInventory(),
                h.getLevel().getBlockEntity(pos));
            player.containerMenu = menu;
            var denied = new zone.moddev.mc.lib.gui.model.Element() {
                public zone.moddev.mc.lib.gui.model.Common common() {
                    return zone.moddev.mc.lib.gui.model.Common.empty();
                }
                public boolean serverEnabled(
                    zone.moddev.mc.lib.gui.runtime.ScreenHost host,
                    net.minecraft.world.entity.player.Player viewer) {
                    return false;
                }
            };
            var invoked = new java.util.concurrent.atomic.AtomicBoolean();
            var guarded = new zone.moddev.mc.lib.gui.ElementAction<>(
                ResourceLocation.parse("test:guarded"),
                ElementActions.Click.CODEC, 8, (context, click) -> {
                    invoked.set(true);
                    return true;
                });
            var context = new zone.moddev.mc.lib.gui.ElementAction.Context(
                player, menu, denied);
            var click = new ElementActions.Click(0, 0, 0);
            h.assertTrue(
                !guarded.execute(context, click)
                    && !guarded.dispatch(context,
                        guarded.encode(player.registryAccess(), click))
                    && !invoked.get(),
                "Non-button elements own authorization in both dispatch paths");
            var button = screen.find("next").orElseThrow();
            menu.action(player,
                new ScreenNetwork.Action(70, menu.index(button), 1, 0, 0, 0));
            h.assertTrue(menu.views().get("pages").equals("details"),
                "Legacy buttons work without ScreenHost");
            var result = menu.widgetAction(player,
                new ScreenNetwork.Widget(70, menu.sessionId(),
                    menu.index(button), 1, Navigation.NAVIGATE.id(),
                    Navigation.NAVIGATE.encode(player.registryAccess(),
                        new ElementActions.Click(0, 0, 0))));
            h.assertTrue(
                result != null && result.accepted()
                    && menu.views().get("pages").equals("home"),
                "Typed buttons work without ScreenHost");
        } finally {
            player.containerMenu = player.inventoryMenu;
            Screens.install(previous);
        }
        h.succeed();
    }
}
