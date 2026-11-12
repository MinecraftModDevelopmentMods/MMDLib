package zone.moddev.mc.lib.client.jei;

/** Geometry shared by recipe slots and extras, independent of the client renderer. */
record MachineRecipeLayout(int ghosts, int inputs, int outputs) {
    static final int CELL = 18, COLUMNS = 3, ARROW = 24, GAP = 4, SEPARATOR = 12;

    /** Reserve each section's largest footprint so recipes in one category share columns. */
    MachineRecipeLayout merge(MachineRecipeLayout other) {
        return new MachineRecipeLayout(Math.max(ghosts, other.ghosts),
            Math.max(inputs, other.inputs), Math.max(outputs, other.outputs));
    }

    static int columns(int count) {
        return Math.min(COLUMNS, count);
    }

    static int rows(int count) {
        return Math.ceilDiv(count, COLUMNS);
    }

    int rows() {
        return Math.max(1, Math.max(rows(ghosts), Math.max(rows(inputs), rows(outputs))));
    }

    boolean separator() {
        return ghosts > 0 && inputs > 0;
    }

    int inputX() {
        return columns(ghosts) * CELL + (separator() ? SEPARATOR : 0);
    }

    int arrowX() {
        return inputX() + columns(inputs) * CELL + GAP;
    }

    int outputX() {
        return arrowX() + ARROW + GAP;
    }

    int width() {
        return outputX() + columns(outputs) * CELL;
    }

    /** Whole buckets, rounded upward; use long arithmetic even for int-sized fluid amounts. */
    static long fluidCapacity(int amount) {
        return Math.max(1000L, Math.ceilDiv((long) amount, 1000L) * 1000L);
    }
}
