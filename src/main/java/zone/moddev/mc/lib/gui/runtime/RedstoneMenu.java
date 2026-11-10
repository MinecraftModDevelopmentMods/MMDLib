package zone.moddev.mc.lib.gui.runtime;

import java.util.Arrays;

import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.lib.api.redstone.RedstoneControl;

/**
 * Per-viewer face selection and authoritative built-in redstone configuration.
 */
public final class RedstoneMenu
    implements zone.moddev.mc.lib.gui.runtime.component.DrawerSession {
    public net.minecraft.resources.ResourceLocation drawerType() {
        return zone.moddev.mc.lib.gui.drawer.BuiltinDrawers.REDSTONE;
    }

    public void tickClient() {
        tick();
    }

    public static final int TOGGLE = 0, SELECT = 1, MODE = 2, OUTSIDE = 3,
        RANGE = 4, SOURCE = 5, SYNC = 6, INVERT = 7;
    public final RedstoneControl control;
    private static final RedstoneControl.Mode[] MODES = RedstoneControl.Mode
        .values();
    private final int[] snapshotFaces = new int[48];
    private final MachineMenu menu;
    private final Player owner;
    private final boolean client;
    private int selected;
    private final MenuRequests.Client requests = new MenuRequests.Client(100);
    private final MenuRequests.Server received = new MenuRequests.Server(16);
    private boolean open;
    private RedstoneNetwork.State state, sent;

    public RedstoneMenu(MachineMenu menu, Player owner, RedstoneControl control,
        boolean client) {
        this.menu = menu;
        this.owner = owner;
        this.control = control;
        this.client = client;
    }

    public boolean open() {
        return open;
    }

    /**
     * Whether any face is configured to gate processing from an input signal.
     */
    public boolean watching() {
        for (int face = 0; face < 6; face++)
            if (mode(face) == RedstoneControl.Mode.READ)
                return true;
        return false;
    }

    /**
     * Authoritative resolved gate, also synchronized while the drawer is
     * closed.
     */
    public boolean paused() {
        return client ? state != null && state.paused() : control.paused();
    }

    public boolean conflicts(boolean manuallyPaused) {
        return watching() && manuallyPaused != paused();
    }

    public int selected() {
        return selected;
    }

    public boolean pending() {
        return requests.pending();
    }

    public long revision() {
        return client
            ? state == null ? 0 : state.revision()
            : control.revision();
    }

    public RedstoneControl.Mode mode(int face) {
        return client
            ? MODES[state == null ? 0 : state.faces()[face * 8]]
            : control.mode(face);
    }

    public boolean outside(int face) {
        return client
            ? state != null && state.faces()[face * 8 + 1] == 1
            : control.outside(face);
    }

    public int incoming(int face) {
        return client
            ? state == null ? 0 : state.faces()[face * 8 + 2]
            : control.incoming(face);
    }

    public int outgoing(int face) {
        return client
            ? state == null ? 0 : state.faces()[face * 8 + 3]
            : control.outgoing(face);
    }

    public boolean inverted(int face) {
        return client
            ? state != null && state.faces()[face * 8 + 7] == 1
            : control.inverted(face);
    }

    public int lower(int face) {
        return client
            ? state == null ? 1 : state.faces()[face * 8 + 4]
            : control.lower(face);
    }

    public int upper(int face) {
        return client
            ? state == null ? 15 : state.faces()[face * 8 + 5]
            : control.upper(face);
    }

    public int source(int face) {
        return client
            ? state == null ? -1 : state.faces()[face * 8 + 6] - 1
            : control.source(face);
    }

    public void closeServer() {
        open = false;
    }

    public boolean send(int kind, int face, int column, int value) {
        int request = requests.begin();
        if (request == 0)
            return false;
        PacketDistributor.sendToServer(
            new RedstoneNetwork.Action(menu.containerId, menu.sessionId(),
                request, revision(), kind, face, column, value));
        return true;
    }

    public void tick() {
        if (requests.tick())
            send(SYNC, selected, 0, 0);
    }

    public void action(Player player, RedstoneNetwork.Action p) {
        if (client || player != owner || player.containerMenu != menu
            || !menu.stillValid(player) || p.menu() != menu.containerId
            || p.session() != menu.sessionId()
            || !received.receive(p.request()))
            return;
        if (received.allow(player.level().getGameTime())) {
            if (p.kind() == TOGGLE) {
                open = !open;
                if (open)
                    menu.components().activate(this);
            } else if (open) {
                if (p.kind() == SELECT)
                    selected = p.face();
                else if (p.face() == selected && p.revision() == revision())
                    switch (p.kind()) {
                        case MODE -> {
                            if (p.value() < 3)
                                control.mode(selected, MODES[p.value()]);
                        }
                        case OUTSIDE -> {
                            if (p.value() < 2
                                && mode(selected) == RedstoneControl.Mode.READ)
                                control.outside(selected, p.value() == 1);
                        }
                        case RANGE -> {
                            if (p.value() <= 255
                                && mode(selected) == RedstoneControl.Mode.READ)
                                control.range(selected, p.value() / 16,
                                    p.value() % 16);
                        }
                        case SOURCE -> {
                            if (mode(selected) == RedstoneControl.Mode.EMIT)
                                control.source(selected, p.value());
                        }
                        case INVERT -> {
                            if (mode(selected) == RedstoneControl.Mode.EMIT
                                && p.value() < 2)
                                control.inverted(selected, p.value() == 1);
                        }
                        default -> {
                        }
                    }
            }
        }
        menu.broadcastChanges();
    }

    public void receive(RedstoneNetwork.State p) {
        if (p.menu() != menu.containerId || p.session() != menu.sessionId()
            || state != null && p.revision() < state.revision())
            return;
        state = p;
        open = p.open();
        selected = p.selected();
        requests.acknowledgeThrough(p.acknowledged());
    }

    public void broadcast() {
        if (client
            || !(owner instanceof net.minecraft.server.level.ServerPlayer player)
            || !player.connection.hasChannel(RedstoneNetwork.State.TYPE))
            return;
        int[] faces = snapshotFaces;
        for (int i = 0; i < 6; i++) {
            faces[i * 8] = mode(i).ordinal();
            faces[i * 8 + 1] = outside(i) ? 1 : 0;
            faces[i * 8 + 2] = incoming(i);
            faces[i * 8 + 3] = outgoing(i);
            faces[i * 8 + 4] = lower(i);
            faces[i * 8 + 5] = upper(i);
            faces[i * 8 + 6] = source(i) + 1;
            faces[i * 8 + 7] = inverted(i) ? 1 : 0;
        }
        if (sent == null || sent.acknowledged() != received.acknowledged()
            || sent.revision() != revision() || sent.open() != open
            || sent.selected() != selected || sent.paused() != control.paused()
            || !Arrays.equals(sent.faces(), faces)) {
            // State owns a clone, so the scratch buffer cannot mutate an
            // in-flight packet.
            var next = new RedstoneNetwork.State(menu.containerId,
                menu.sessionId(), received.acknowledged(), revision(), open,
                selected, control.paused(), faces);
            PacketDistributor.sendToPlayer(player, next);
            sent = next;
        }
    }
}
