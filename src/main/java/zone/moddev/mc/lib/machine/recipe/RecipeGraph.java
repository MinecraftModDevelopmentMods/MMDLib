package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A bounded, deterministic graph of single-material transformations. Build from
 * a recipe snapshot, cache in the owning catalog and rebuild on reload.
 * Contains no machine state and never scans recipes during processing.
 */
public final class RecipeGraph {
    public record Operation(ResourceLocation id, ResourceLocation kind,
        int inputCount, ItemStack output, MachineRecipe recipe) {
        public Operation {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(recipe);
            output = output.copy();
            if (inputCount < 1 || output.isEmpty() || recipe.time() < 1)
                throw new IllegalArgumentException("Invalid recipe operation");
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    public record Edge(int from, int to, Operation operation) {
    }

    public record Limits(int nodes, int edges, int depth) {
        public Limits {
            if (nodes < 1 || nodes > 4096 || edges < 1 || edges > 16384
                || depth < 1 || depth > 63)
                throw new IllegalArgumentException(
                    "Invalid recipe graph limits");
        }

        public static Limits defaults() {
            return new Limits(512, 2048, 32);
        }
    }

    private final List<ItemStack> nodes;
    private final List<Edge> edges;
    private final List<Edge> parents;
    private final boolean truncated;

    private RecipeGraph(List<ItemStack> nodes, List<Edge> edges,
        List<Edge> parents, boolean truncated) {
        this.nodes = nodes.stream().map(ItemStack::copy).toList();
        this.edges = List.copyOf(edges);
        // Root has no parent.
        this.parents = java.util.Collections
            .unmodifiableList(new ArrayList<>(parents));
        this.truncated = truncated;
    }

    /**
     * The resolver returns admitted operations for a concrete source stack.
     * Each node is expanded once; cycles remain edges without recursive work.
     * Prefer shortest paths, then recipe ID order. Callers must also bound
     * resolver work for their recipe catalog.
     */
    public static RecipeGraph discover(ItemStack root,
        Function<ItemStack, List<Operation>> resolver, Limits limits) {
        if (root.isEmpty())
            throw new IllegalArgumentException("Empty recipe root");
        Objects.requireNonNull(resolver);
        Objects.requireNonNull(limits);
        var nodes = new ArrayList<ItemStack>();
        var edges = new ArrayList<Edge>();
        var parents = new ArrayList<Edge>();
        var depths = new ArrayList<Integer>();
        nodes.add(root.copyWithCount(1));
        parents.add(null);
        depths.add(0);
        boolean truncated = false;
        outer : for (int from = 0; from < nodes.size(); from++) {
            var operations = resolver
                .apply(nodes.get(from).copy()).stream().sorted(Comparator
                    .comparing(Operation::id).thenComparing(Operation::kind))
                .toList();
            if (depths.get(from) == limits.depth()) {
                truncated |= !operations.isEmpty();
                continue;
            }
            var seen = new java.util.HashSet<ResourceLocation>();
            for (var operation : operations) {
                if (!seen.add(operation.id()))
                    throw new IllegalArgumentException(
                        "Duplicate operation ID: " + operation.id());
                if (edges.size() == limits.edges()) {
                    truncated = true;
                    break outer;
                }
                int to = indexOf(nodes, operation.output());
                if (to < 0 && nodes.size() == limits.nodes()) {
                    truncated = true;
                    continue;
                }
                boolean fresh = to < 0;
                if (fresh)
                    to = nodes.size();
                var edge = new Edge(from, to, operation);
                edges.add(edge);
                if (fresh) {
                    nodes.add(operation.output().copyWithCount(1));
                    parents.add(edge);
                    depths.add(depths.get(from) + 1);
                }
            }
        }
        return new RecipeGraph(nodes, edges, parents, truncated);
    }

    private static int indexOf(List<ItemStack> nodes, ItemStack item) {
        for (int i = 0; i < nodes.size(); i++)
            if (ItemStack.isSameItemSameComponents(nodes.get(i), item))
                return i;
        return -1;
    }

    public int size() {
        return nodes.size();
    }

    public int indexOf(ItemStack item) {
        return indexOf(nodes, item);
    }

    public ItemStack item(int node) {
        return nodes.get(node).copy();
    }

    public List<Edge> edges() {
        return edges;
    }

    public boolean truncated() {
        return truncated;
    }

    public List<Operation> pathTo(int node) {
        nodes.get(node);
        var path = new ArrayList<Operation>();
        while (node != 0) {
            var parent = parents.get(node);
            path.add(parent.operation());
            node = parent.from();
        }
        java.util.Collections.reverse(path);
        return List.copyOf(path);
    }

    /**
     * One graph per owner/source; a new recipe or policy snapshot invalidates
     * it.
     */
    public static final class Cache {
        private Object revision;
        private RecipeGraph graph;
        private Limits bounds;

        public RecipeGraph get(Object snapshot, ItemStack root,
            Function<ItemStack, List<Operation>> resolver, Limits limits) {
            Objects.requireNonNull(snapshot);
            if (graph == null || revision != snapshot || !limits.equals(bounds)
                || !ItemStack.isSameItemSameComponents(graph.item(0), root)) {
                graph = discover(root, resolver, limits);
                revision = snapshot;
                bounds = limits;
            }
            return graph;
        }

        public void invalidate() {
            revision = null;
            graph = null;
            bounds = null;
        }
    }
}
