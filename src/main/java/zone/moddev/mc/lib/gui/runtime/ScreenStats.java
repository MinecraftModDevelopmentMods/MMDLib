package zone.moddev.mc.lib.gui.runtime;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.stats.Statistics;
import zone.moddev.mc.lib.api.stats.StatsFormatContext;
import zone.moddev.mc.lib.api.stats.StatsProvider;
import zone.moddev.mc.lib.api.stats.StatsSamplingContext;
import zone.moddev.mc.lib.api.stats.StatsTimescale;

/** Server snapshots from the public provider contract; no concrete storage/processor knowledge. */
final class ScreenStats {

    // Menus own the sampler; weak keys AND values prevent provider closures retaining machines.
    private static final Map<ScreenHost, java.lang.ref.WeakReference<ScreenStats>> SHARED = new WeakHashMap<>();

    static ScreenStats forHost(ScreenHost host) {
        var reference = SHARED.get(host);
        var shared = reference == null ? null : reference.get();
        if (shared == null) {
            shared = new ScreenStats();
            SHARED.put(host, new java.lang.ref.WeakReference<>(shared));
        }
        return shared;
    }

    private record Sampled(Class<?> type, Object key, Statistics.Sample sample) {
    }

    private record Part(StatsProvider provider, Statistics section) {
    }

    private final Map<StatsProvider, Map<String, Sampled>> samples = new IdentityHashMap<>();
    private List<StatsPage> snapshot = List.of();
    private long lastTick = Long.MIN_VALUE;

    List<StatsPage> read(ScreenHost host, long tick) {
        if (lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < 20)
            return snapshot;
        return readParts(
            host == null ? List.of() : host.screenStats(),
            host == null ? StatsTimescale.DEFAULTS : host.screenStatsTimescales(),
            tick);
    }

    List<StatsPage> readParts(List<? extends StatsProvider> providers, long tick) {
        return readParts(providers, StatsTimescale.DEFAULTS, tick);
    }

    List<StatsPage> readParts(
        List<? extends StatsProvider> providers, List<StatsTimescale> timescales, long tick) {
        timescales = StatsTimescale.checked(timescales);
        if (tick < lastTick)
            samples.clear();
        lastTick = tick;
        var seen = Collections.newSetFromMap(new IdentityHashMap<StatsProvider, Boolean>());
        var parts = new ArrayList<Part>();
        var demands = new IdentityHashMap<Object, BigInteger>();
        for (var provider : providers) {
            if (!seen.add(provider))
                continue;
            var section = provider.statistics();
            parts.add(new Part(provider, section));
            for (var use : section.maximumUses())
                demands.merge(use.resource(), use.perTick(), BigInteger::add);
        }
        var context = new StatsSamplingContext(tick, demands);
        for (var part : parts) {
            var rows = samples.computeIfAbsent(part.provider(), ignored -> new HashMap<>());
            var currentIds = new HashSet<String>();
            for (var row : part.section().rows()) {
                currentIds.add(row.id());
                var value = row.value();
                var key = Objects.requireNonNull(value.samplingKey(), "Statistic sampling key");
                var old = rows.get(row.id());
                var previous = old != null && old.type() == value.getClass()
                    && Objects.equals(old.key(), key)
                        ? old.sample()
                        : null;
                var sample = Objects.requireNonNull(value.sample(context, previous),
                    "Statistic sample");
                rows.put(row.id(), new Sampled(value.getClass(), key, sample));
            }
            rows.keySet().retainAll(currentIds);
        }
        samples.keySet().retainAll(seen);
        var result = new ArrayList<StatsPage>();
        for (var timescale : timescales) {
            var lines = new ArrayList<Component>();
            for (var part : parts) {
                var section = part.section();
                var visible = section.rows().stream()
                    .filter(
                        row -> row.demandResource() == null
                            || demands.getOrDefault(row.demandResource(), BigInteger.ZERO)
                                .signum() > 0)
                    .toList();
                if (visible.isEmpty())
                    continue;
                if (!lines.isEmpty())
                    lines.add(Component.empty());
                if (!section.title().equals(Component.empty()))
                    lines.add(section.title().copy().withStyle(ChatFormatting.BOLD));
                for (var row : visible) {
                    var line = row.label().copy().append(": ");
                    line.append(
                        samples
                            .get(part.provider())
                            .get(row.id())
                            .sample()
                            .format(
                                new StatsFormatContext(
                                    timescale, row.unit(), row.decimals(), row.perTimescale())));
                    lines.add(line);
                }
            }
            if (lines.isEmpty())
                lines.add(Statistics.label("empty"));
            result.add(new StatsPage(timescale.id(), timescale.label(), lines));
        }
        return snapshot = List.copyOf(result);
    }
}
