package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Shares what a group's slots or tanks hold between a recipe's ingredients.
 * Each new claim may move an earlier claim onto another entry it also accepts,
 * so a claim only falls short when no way of sharing could meet every claim so
 * far, and the order of the ingredients doesn't matter.
 */
public final class InputClaims {
    private final long[] held;
    private final long[] free;
    private final List<boolean[]> accepts = new ArrayList<>();
    private final List<long[]> claims = new ArrayList<>();

    /**
     * @param held
     *            what each entry holds
     */
    public InputClaims(long[] held) {
        this.held = held.clone();
        free = held.clone();
    }

    /**
     * Claims up to {@code need} from entries {@code matches} accepts.
     *
     * @return what's still needed; 0 when the claim is met
     */
    public long claim(long need, IntPredicate matches) {
        var accepted = new boolean[held.length];
        for (int entry = 0; entry < held.length; entry++)
            accepted[entry] = held[entry] > 0 && matches.test(entry);
        accepts.add(accepted);
        claims.add(new long[held.length]);
        int part = claims.size() - 1;
        while (need > 0) {
            long found = find(part, need, new boolean[held.length]);
            if (found == 0)
                break;
            need -= found;
        }
        return need;
    }

    /** The total claimed from {@code entry}. */
    public long claimed(int entry) {
        return held[entry] - free[entry];
    }

    /**
     * Finds up to {@code amount} for {@code part} on one entry, from what's
     * free there and by moving other parts' claims elsewhere; returns how much
     * it found.
     */
    private long find(int part, long amount, boolean[] visited) {
        for (int entry = 0; entry < held.length; entry++) {
            if (!accepts.get(part)[entry] || visited[entry])
                continue;
            visited[entry] = true;
            long found = Math.min(free[entry], amount);
            free[entry] -= found;
            for (int other = 0; other < claims.size()
                && found < amount; other++) {
                long claimed = claims.get(other)[entry];
                if (other == part || claimed == 0)
                    continue;
                long moved = find(other, Math.min(amount - found, claimed),
                    visited);
                claims.get(other)[entry] -= moved;
                found += moved;
            }
            if (found > 0) {
                claims.get(part)[entry] += found;
                return found;
            }
        }
        return 0;
    }
}
