package zone.moddev.mc.lib.gui.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.sync.ElementStateValue;
import zone.moddev.mc.lib.gui.sync.ElementStates;

/**
 * Per-menu typed action lifecycle. The menu supplies authorization and
 * authoritative snapshots.
 */
final class ElementActionSession {
    private static final int TIMEOUT_TICKS = 100, MAX_ACTIONS_PER_TICK = 8;
    private final MachineMenu menu;
    private final long sessionId;
    private final RegistryAccess registries;
    private final Map<Integer, Object> values;
    private final ElementStates elementStates;
    private int nextRequest, lastRequest;
    private long actionTick = Long.MIN_VALUE;
    private int actionsThisTick;
    private final Map<Integer, Integer> pending = new HashMap<>();
    private final Map<Integer, ScreenNetwork.WidgetResult> results = new HashMap<>();
    private final Set<Integer> refreshKeys = new HashSet<>();
    private final Map<Integer, Long> sentAt = new HashMap<>();
    private long clientTicks;
    private boolean closed;

    ElementActionSession(MachineMenu menu, long sessionId,
        RegistryAccess registries, Map<Integer, Object> values,
        ElementStates elementStates) {
        this.menu = menu;
        this.sessionId = sessionId;
        this.registries = registries;
        this.values = values;
        this.elementStates = elementStates;
    }

    long id() {
        return sessionId;
    }

    record Snapshot(Map<Integer, Object> values,
        Map<Integer, ElementStateValue> states) {
    }

    public <T> Optional<ScreenNetwork.Widget> prepare(Element element,
        ElementAction<T> action, T value) {
        int index = menu.findIndex(element);
        if (closed || index < 0 || !menu.visible(element)
            || (element instanceof zone.moddev.mc.lib.gui.model.elements.ButtonControl button
                && !button.enabled(menu.elementStates()))
            || pending.containsKey(index) || results.containsKey(index)
            || nextRequest == Integer.MAX_VALUE)
            return Optional.empty();
        if (element.action(action.id()).orElse(null) != action)
            throw new IllegalArgumentException(
                "Action is not registered for " + element.getClass().getName());
        byte[] data;
        try {
            data = action.encode(registries, value);
        } catch (RuntimeException failure) {
            zone.moddev.mc.lib.MMDLib.LOGGER.error(
                "Could not encode element action: {}", action.id(), failure);
            return Optional.empty();
        }
        int request = ++nextRequest;
        pending.put(index, request);
        sentAt.put(index, clientTicks);
        return Optional.of(new ScreenNetwork.Widget(menu.containerId, sessionId,
            index, request, action.id(), data));
    }

    public boolean pending(Element element) {
        return pending.containsKey(menu.findIndex(element));
    }

    public void receive(ScreenNetwork.WidgetResult result) {
        if (closed || result.menu() != menu.containerId
            || result.session() != sessionId
            || !Objects.equals(pending.get(result.element()), result.request()))
            return;
        pending.remove(result.element());
        sentAt.remove(result.element());
        values.put(result.element() * 2, result.value());
        values.put(result.element() * 2 + 1, result.maximum());
        menu.apply(new HashMap<Integer, Object>(result.states()));
        results.put(result.element(), result);
    }

    public ScreenNetwork.WidgetResult takeResult(Element element) {
        return results.remove(menu.findIndex(element));
    }

    /**
     * Client-side timeout prevents an unconfirmed edit from permanently
     * disabling a control.
     */
    public void tickClient() {
        if (closed)
            return;
        clientTicks++;
        for (var entry : new HashMap<>(sentAt).entrySet())
            if (clientTicks - entry.getValue() >= TIMEOUT_TICKS) {
                int index = entry.getKey();
                receive(
                    new ScreenNetwork.WidgetResult(menu.containerId, sessionId,
                        index, pending.get(index), false, values.get(index * 2),
                        values.get(index * 2 + 1), elementStates.sent(values)));
            }
    }

    public ScreenNetwork.WidgetResult handle(ServerPlayer player,
        ScreenNetwork.Widget packet) {
        if (closed || !menu.authorizesElementAction(player)
            || packet.menu() != menu.containerId
            || packet.session() != sessionId || packet.request() <= lastRequest
            || packet.element() >= menu.elements.size())
            return null;
        long tick = player.level().getGameTime();
        if (actionTick != tick) {
            actionTick = tick;
            actionsThisTick = 0;
        }
        lastRequest = packet.request();
        var element = menu.elements.get(packet.element());
        if (++actionsThisTick > MAX_ACTIONS_PER_TICK)
            // Use the last sent snapshot: rate-limited requests must not invoke
            // codecs or getters.
            return result(packet, false, values, elementStates.snapshot());
        boolean accepted = false;
        var action = element.action(packet.action());
        if (menu.visible(element) && action.isPresent()) {
            try {
                accepted = action.get().dispatch(
                    new ElementAction.Context(player, menu, element),
                    packet.data());
            } catch (RuntimeException failure) {
                zone.moddev.mc.lib.MMDLib.LOGGER.error(
                    "Element action failed: {}", packet.action(), failure);
            }
        }
        var snapshot = menu.snapshotAfterElementAction(accepted);
        return result(packet, accepted, snapshot.values(), snapshot.states());
    }

    private ScreenNetwork.WidgetResult result(ScreenNetwork.Widget packet,
        boolean accepted, Map<Integer, Object> snapshot,
        Map<Integer, ElementStateValue> state) {
        var result = new ScreenNetwork.WidgetResult(menu.containerId, sessionId,
            packet.element(), packet.request(), accepted,
            snapshot.get(packet.element() * 2),
            snapshot.get(packet.element() * 2 + 1), state);
        // The acknowledgment changes the client's values too. Include it in the
        // delta baseline so
        // a later return to the pre-action value is sent even if no regular
        // broadcast saw the edit.
        values.put(packet.element() * 2, result.value());
        values.put(packet.element() * 2 + 1, result.maximum());
        // A client may already have timed out and ignored this response. Also
        // send the next normal
        // snapshot for these keys so its confirmed values converge
        // independently of
        // acknowledgments.
        refreshKeys.add(packet.element() * 2);
        refreshKeys.add(packet.element() * 2 + 1);
        values.putAll(state);
        refreshKeys.addAll(state.keySet());
        return result;
    }

    /**
     * Force the next broadcast to repair clients that timed out before an
     * acknowledgment arrived.
     */
    void appendCorrections(Map<Integer, Object> snapshot,
        Map<Integer, Object> delta) {
        for (int key : refreshKeys)
            delta.put(key, snapshot.get(key));
        refreshKeys.clear();
    }

    void close() {
        closed = true;
        pending.clear();
        results.clear();
        sentAt.clear();
        refreshKeys.clear();
    }
}
