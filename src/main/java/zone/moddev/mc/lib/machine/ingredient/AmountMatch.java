package zone.moddev.mc.lib.machine.ingredient;

/**
 * How an amount held compares with an amount required, read from the recipe's
 * side. Recipes use {@link #AT_LEAST}; the others are for code asking narrower
 * questions.
 */
public enum AmountMatch {
    /** Held equals required. */
    EXACT,
    /** Held is at least required. */
    AT_LEAST,
    /** Only the kind matters; any amount, even none. */
    ANY_AMOUNT;

    /** Whether {@code available} satisfies {@code required} under this rule. */
    public boolean test(long required, long available) {
        return switch (this) {
            case EXACT -> available == required;
            case AT_LEAST -> available >= required;
            case ANY_AMOUNT -> true;
        };
    }
}
