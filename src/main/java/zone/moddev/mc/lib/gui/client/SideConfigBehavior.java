package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;

/**
 * Local group selection, face hit testing and tooltips for the inline side
 * control.
 */
public class SideConfigBehavior extends DefaultElementBehavior
    implements SideConfigPresentation {
    private final SideConfigElement config;
    private int group;

    public SideConfigBehavior(SideConfigElement config, ElementAccess access) {
        super(access);
        this.config = config;
    }

    public int selection() {
        return group;
    }

    public int sideMode(int group, int face) {
        return access.presentation().state()
            .get(config, SideConfigElement.MODES).get(group * 6 + face);
    }

    protected Click click(double x, double y, int button) {
        var bounds = access.bounds();
        if (y < bounds.y() + 20) {
            group = (group + 1) % config.groups().size();
            return Click.CONSUME;
        }
        for (int face = 0; face < 6; face++)
            if (config.face(bounds, face).contains(x, y)) {
                access.click(SideConfigElement.CYCLE_FACE, button,
                    group * 6 + face);
                return Click.CONSUME;
            }
        return Click.PASS;
    }

    public List<Component> tooltip(int x, int y) {
        for (int face = 0; face < 6; face++)
            if (config.face(access.bounds(), face).contains(x, y))
                return List.of(
                    Component.translatable("screen.mmdlib.face."
                        + RelativeFace.values()[face].getSerializedName()),
                    Component.translatable("screen.mmdlib.mode."
                        + IoMode.values()[sideMode(group, face)]
                            .getSerializedName()));
        return List.of();
    }
}
