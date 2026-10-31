# Machine module

> **Status: stage 1 is built** (internals without GUI, and phase 1: ingredients, groups,
> transactions, matching); see [stage 1](stage-1.md). Everything else here is design. Today's other
> machine support is described in
> [machines](../machines.md), [capability wrappers](../capabilities.md) and
> [GUI screens](../gui-screens.md).

A planned layer on top of MMDLib's storages and screens: machine ingredients and recipes that
cover items, fluids and energy, transactions, a processing loop, and later machines defined
entirely in JSON. It rebuilds the idea of Tesla-Core-Lib's unfinished `net.modcrafters.mclib`
and targets NeoForge 1.21.1, NeoForge 26.1 and Fabric 26.3.

Read in this order:

| Page | What it covers |
|---|---|
| [Comparison](comparison.md) | Tesla-Core-Lib and MMDLib side by side as the code stands; what is worth taking from each |
| [Rebuild plan](rebuild-plan.md) | Decisions, the core/vanilla/loader layering, core interfaces, per-build differences, Stonecutter layout, phases 1–8 |
| [Recipes](recipes.md) | How ingredients, groups, matching, the processor and transactions work, with a crusher end to end |
| [Data-only machines](data-only-machines.md) | Ports, targets, actions, the plan → check → commit → apply cycle, four complete JSON machines, phases 9–14 |
| [Machine UI](machine-ui.md) | The screen pieces those machines need, face config with face cards, internals (parts in sockets that change stats), phases 15–18 |
| [Stage 1](stage-1.md) | **Built.** The Java API that exists now: internals and part files, ingredients, groups, transactions, `RecipeMatcher`, and what is still missing |
| [Stage 1 plan](stage-1-plan.md) | Step-by-step implementation plan for the first build: internals without GUI, and phase 1 (ingredients, groups, transactions, matcher) |
