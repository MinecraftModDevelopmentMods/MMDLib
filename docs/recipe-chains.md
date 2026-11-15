# Recipe chains

`RecipeCycle`, `RecipeChain` and `RecipeGraph` support machines that select a
material path and run its stages concurrently. They are Java runtime helpers;
the consuming mod supplies its processor/controller, recipe policy, storage,
save/sync integration and screen. They do not register a machine or recipe type.

## Execute a selected recipe

`machine.feature.RecipeCycle` executes a supplied `MachineRecipe` against a
`MachineInventory`. The ordinary `RecipeProcessor` uses this same runner while
retaining its automatic lookup, selection, pause and saved-state format.

- `start(id, recipe)` reserves inputs and output space. It neither advances
  time nor spends FE, and refuses to replace active work. Admission can succeed
  without power; automatic `RecipeProcessor` still applies its usual matcher.
- `advance(random)` advances at most one tick, transactionally charging that
  tick's share of every energy input. Results distinguish idle, running,
  energy-stalled, completion-blocked and completed work.
- A blocked completion retains paid progress and retries without charging it
  again. `CyclePlan` remains responsible for reservations, atomic consumption,
  output insertion and rollback.
- `state()` / `State.CODEC` persist the active plan and progress. Load storage
  first, then `restore(state)`. Restoring twice replaces this runner's claims.
  A failed restore returns false, leaves this runner idle and preserves items.
- `release()` releases claims while keeping progress. A caller using
  `resume(id, recipe, paidProgress, previousTime)` must retain the recipe ID and
  previous duration itself. A changed duration resets paid progress. `reset()`
  also clears progress. Neither is appropriate for a finish-before-switch policy.

Use the runner only on the owning server thread. Do not call `restore` on a
client mirror: it installs authoritative storage reservations.

## Bind a path

`machine.feature.RecipeChain` accepts 1–64 `Step` bindings. Stage zero generates
an item without item inputs; its recipe can require fluids and energy. Every
later stage consumes one ingredient, possibly several copies, from the previous
stage. Every stage produces one deterministic item stack into one output slot.
Chance products, multiple item ingredients and item catalysts are outside this
coordinator's scope.

Recipe item keys are `input` and `output`. Other keys can map shared energy or
fluids. Create each physical stage output group **once**, then reuse that exact
`ItemGroup` instance as the next stage's input. Distinct stage outputs must not
overlap physically, even through different group wrappers. These bindings and
their reservations are exclusive to the chain.

For example, given two valid recipes (`generation` and `grinding`) and their
IDs, a host binds its resources as follows:

```java
var items = new ItemStorage(2).role(Role.OUTPUT, 0, 1);
var generated = StorageGroups.items(items, 0);
var ground = StorageGroups.items(items, 1);
var power = StorageGroups.energy(energy);
var sourceInventory = MachineInventory.builder()
    .group("output", generated)
    .group("energy", power)
    .group("water", StorageGroups.fluids(fluids, 0))
    .group("lava", StorageGroups.fluids(fluids, 1))
    .build();
var grindingInventory = MachineInventory.builder()
    .group("input", generated)
    .group("output", ground)
    .group("energy", power)
    .build();
var chain = new RecipeChain(List.of(
    new RecipeChain.Step(generationId, generation, sourceInventory),
    new RecipeChain.Step(grindingId, grinding, grindingInventory)));
items.pendingClaims(chain::pendingTake);
chain.target(1, chain.step(1).product().getMaxStackSize());
```

The types above are `api.capabilities.ItemStorage` / `Role`,
`machine.neoforge.StorageGroups`, `machine.inventory.MachineInventory` and
`machine.feature.RecipeChain`. The host owns `energy` and the two-tank `fluids`
storage. Recipe group names must match the bindings.

Call `chain.tick(random)` once per server tick and mark the host changed when it
returns true. Settings changes also need the host's usual save/sync notification.
`cycle(index)` exposes a detached state snapshot; `result(index)` is the last
attempted advancement/admission result, not a promise that the stage advanced
on the current tick.

Stages active at tick entry advance before new work is admitted. With enough
FE, they all advance in the same tick. The first stage rotates between funded
passes. An energy-stalled stage keeps first turn and ends the advancement pass
until its charge can be funded, so cheaper stages cannot keep spending that
charge. Configure the energy buffer to hold each stage's largest per-tick share.

## Stock and extraction

`target(index, amount)` takes a number from zero through the product's normal
stack size. A mod can map None / One / Stack to 0 / 1 / maximum. Targets are
refill goals, not protected minimum reserves. Zero-stock intermediates still
supply downstream demand. Planning works backward through the path, counting
active outputs and subtracting active consumption to avoid double-ordering.
A host can call `bufferLimit(index, amount)` to cap a stage's planned stock even
when downstream demand is larger. For One Run, set both target and buffer limit
to the following step's input count (the terminal stage uses its own output
batch). Default limits are a normal stack, preserving ordinary concurrent bulk
planning. Buffer limits are host policy: reapply them after binding/restoring the
chain, alongside the host's saved mode. Existing stock and active work are never
removed. Limits must allow at least the next step's input batch; full output
batches may overshoot the limit. As a downstream cycle reserves the ready batch,
a replacement can run concurrently.

A stage whose output cannot accept a complete batch does not propagate unmet
stock demand upstream. This prevents a blocked/incompatible intermediate from
filling every preceding None stage. Independent stock targets still apply on
those stages, and active cycles keep their reservations and finish normally.
Removing the blockage allows downstream demand to propagate again.

Full recipe batches are retained. A target of one pane can produce sixteen.
If sixteen panes cannot fit in the destination slot, the batch waits; there is
no overflow deletion or additional hidden buffer. Inputs and products larger
than a normal stack are unsupported in this one-slot model.

Output slots use three availability quantities:

1. Actual `CyclePlan` claims protect active work.
2. `pendingTake(stage)` protects up to the next demanded downstream batch,
   including a partial batch while it accumulates.
3. Everything else remains extractable, including stock below its refill target.

Bind the pure pending-claim projection to **physical** `ItemStorage` owners with
`pendingClaims`. In the example, local slot and stage indexes coincide. For
combined/named inventories, explicitly map each owner's local slots to stage
indexes and return zero for unrelated slots. Use a callback referencing the
host's current chain when bindings can change. Pending claims apply only to
`Role.OUTPUT` slots.

External extraction, hopper views and menu `SlotPlan` snapshots see the same
combined protection. Simulation changes neither items nor reservations. Internal
`StorageGroups` use `ItemStore.processingPlan`, `freeForProcessing` and
`extractForProcessing`, which respect active claims but can consume the pending
batch. Storage subclass extraction limits and rollback behavior still apply.
Internal insertion capacity uses actual reservations, not pending demand.

Pending protection releases immediately when demand disappears, downstream
output space is unavailable, a pause/host hold applies, or draining starts.
Power loss alone retains it so the batch can resume. It also releases when the
next cycle takes over with an actual reservation. No pending claim is persisted.

## Pause, switching and loading

`paused(true)` and `held(true)` stop advancement and admission while preserving
active work and its claims. The former is persisted; the latter is transient
host/redstone state. This is deliberately different from ordinary
`RecipeProcessor.pause()`, which retains its existing release-and-resume behavior.

For a route switch, the host validates and saves a pending selection, then calls
`drain()`. This stops every new old-route cycle, including generation, and
releases pending batch protection. Keep ticking until `drained()` is true.
Pause, power loss or blocked completion may delay it. `cancelDrain()` resumes
admission without replacing any active plan.

Once drained, the host preserves accessible leftovers and changes bindings only
when that can be done without losing or relabeling items. The library does not
move leftovers, choose recovery slots or apply the pending route.

Persist `RecipeChain.State` alongside storage and the host's source, active
bindings, pending route and recovery metadata. Reconstruct the same bindings,
load storage, then call `restore`. It validates route IDs, targets and shape;
false means at least one active reservation could not be restored. Other valid
cycles can remain active. The host must handle that recovery result explicitly.
Retain binding descriptions needed to drain saved work if a recipe disappears;
the chain state is not a recipe catalog. Current recipe lookup is not needed to
finish an already-restored `CyclePlan`.

For a `StoredProcessor`, decode registry-dependent bindings and restore claims in
`MachineFeature.restoreResources(registries)`. The defined-machine lifecycle calls
this hook after both storage and feature state have loaded, before automation can
extract anything on the first tick. `FeatureHost` forwards it to every feature.
The ordinary `restore` callback has no registry provider and must not install
claims against the old inventory. Override `reset()` to call `chain.reset()` and
release any previous runner claims before reloading or destroying the host.
Reinstall pending-claim projections during resource restoration as well.

Use state snapshots for the host's bounded client presentation data, alongside
the existing menu reservation synchronization. Recompute host hold after load.

## Discover transformations

`machine.recipe.RecipeGraph.discover(root, resolver, limits)` expands concrete
item-and-component identities. The resolver returns admitted `Operation` values
with recipe ID, operation kind, input count, output and executable recipe.
Each identity is expanded once; cycles remain edges. `pathTo(node)` chooses a
shortest path, breaking ties by recipe ID and operation kind. Alternative edges
remain available through `edges()`. Duplicate IDs for one source are rejected.

Default bounds are 512 nodes, 2,048 edges and depth 32. `truncated()` reports
incomplete discovery. The resolver must also bound/index its own catalog work.
Discovery belongs outside processing ticks.

`RecipeGraph.Cache` caches one source per owner and recipe/policy snapshot.
Use a fresh snapshot identity or call `invalidate()` on recipe **and tag**
reload, or when admission/cost policy changes. Changing source components or
limits also rebuilds. This helper does not subscribe to loader reload events.
Before starting a client-selected route, the server must resolve its exact IDs
against the current admitted graph. On reload, drain an invalid active route
before rebuilding it; changing the cache alone does not stop an existing chain.

`machine.neoforge.RecipeOperations` supplies conservative adapters:

- `smelting(holder, source, level, cost)` supports ordinary vanilla smelting.
- `crafting(...)` supports ordinary shaped/shapeless recipes whose occupied
  cells all accept the same source. It preserves empty shaped cells and batch
  quantities, such as six glass producing sixteen panes.
- Both exclude special subclasses, remainder-bearing recipes and batches that
  cannot fit one normal stack. Their generated ingredients bind exact source
  components. Sources that cannot be represented by the standard strict
  component ingredient are excluded.
- `machine(id, kind, recipe, source)` admits an existing deterministic
  single-item transformation with optional energy. It retains its ingredient
  and recipe semantics; extra fluids/materials, catalysts and chance outputs
  are excluded. A chain additionally checks its concrete parent product.

The mod decides allowed operations and supplies positive time/FE costs for
vanilla adapters. The graph is independent of the NeoForge adapter and can use
other explicitly supported operation resolvers.
