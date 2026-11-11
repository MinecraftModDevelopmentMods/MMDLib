#loader mmdlib
import mods.mmdlib.ScreenHandlers;

// A click handler screen XML can name as on-click="mmdlib:test/script_click".
// It tags the player with what it saw, for the game test to check.
ScreenHandlers.onClick("test/script_click", (click) => {
    click.player.addTag("mmdlib_click_" + click.button + (click.shift ? "_shift" : ""));
    click.player.addTag("mmdlib_screen_" + click.screen);
});

// A hover handler, named as on-hover="mmdlib:test/script_hover".
ScreenHandlers.onHover("test/script_hover", (hover) => {
    if (hover.entered) {
        println("Hovering " + hover.element);
    }
});
