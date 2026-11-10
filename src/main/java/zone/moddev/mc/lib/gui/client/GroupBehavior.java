package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.GroupElement;

/** A panel supplies its caption to any UI that presents it as a group. */
public class GroupBehavior extends DefaultElementBehavior implements GroupLabelProvider {
    private final GroupElement group;

    public GroupBehavior(GroupElement group, ElementAccess access) {
        super(access);
        this.group = group;
    }

    public Optional<Text> groupLabel() {
        return group.label();
    }
}
