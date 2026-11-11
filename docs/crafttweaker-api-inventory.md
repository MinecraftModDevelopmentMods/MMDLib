# CraftTweaker API inventory and runtime support

Updated for phases 2–4 on top of MMDLib `1862428`. Source support and runtime test evidence are distinguished below. All script names are under `mods.mmdlib`.

## Declared API and inheritance

Members are listed on their declaring type. A child also exposes its listed parent members. `get` and `set` denote property access; constructors use the script type name. Unannotated Java helpers are excluded.

### `BlockBuilder`

Source: [ScriptBlockBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockBuilder.java). Inherits `BlockProperties`.

- constructor `BlockBuilder(String name)`
- constructor `BlockBuilder(String name, String module)`
- constructor `BlockBuilder(String name, BlockModuleReference module)`
- get `reference` — `BlockReference getReference()`
- get `name` — `String getName()`
- get `module` — `String getModule()`
- method `BlockBuilder baseBlockFrom(BlockReference block)`
- get `baseBlock` — `String getBaseBlock()`
- set `baseBlock` — `void setBaseBlock(String baseBlock)`
- get `blockSetType` — `String getBlockSetType()`
- set `blockSetType` — `void setBlockSetType(String blockSetType)`
- set `blockEntityData` — `void setBlockEntityData(IData data)`
- method `void build()`

### `BlockContext`

Source: [ScriptBlockContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockContext.java).

- get `client` — `boolean isClient()`
- get `x` — `int getX()`
- get `y` — `int getY()`
- get `z` — `int getZ()`
- get `facing` — `String getFacing()`
- method `int input(String side)`
- get `output` — `int getOutput()`
- set `output` — `void setOutput(int power)`

### `BlockEventContext`

Source: [ScriptBlockEventContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockEventContext.java). Inherits `BlockContext`.

- get `entity` — `Entity getEntity()`

### `BlockEventHandler`

Source: [ScriptBlockEventHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockEventHandler.java).

- method `void handle(BlockEventContext context)`

### `BlockModule`

Source: [ScriptBlockModule](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockModule.java). Inherits `BlockProperties`.

- constructor `BlockModule(String name)`
- constructor `BlockModule(String name, String basedOn)`
- constructor `BlockModule(String name, BlockModuleReference basedOn)`
- get `reference` — `BlockModuleReference getReference()`
- method `BlockModule copy(String name)`
- get `name` — `String getName()`
- get `factory` — `String getFactory()`
- set `factory` — `void setFactory(String factory)`
- get `materialDefaults` — `boolean getMaterialDefaults()`
- set `materialDefaults` — `void setMaterialDefaults(boolean materialDefaults)`
- method `void build()`

### `BlockModuleReference`

Source: [ScriptBlockModuleReference](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockModuleReference.java).

- constructor `BlockModuleReference(String id)`
- get `id` — `String getId()`

### `BlockProperties`

Source: [ScriptBlockProperties](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockProperties.java). Inherits `DefinitionProperties`.

- method `BlockProperties addTag(BlockTagReference tag)`
- method `BlockProperties destroyTime(float value)`
- method `BlockProperties explosionResistance(float value)`
- method `BlockProperties strength(float value)`
- method `BlockProperties strength(float hardness, float resistance)`
- method `BlockProperties noCollisions()`
- method `BlockProperties noOcclusion()`
- method `BlockProperties requiresToolToDrop()`
- method `BlockProperties requiresToolToDrop(boolean value)`
- method `BlockProperties noCorrespondingItem()`
- method `BlockProperties noDrops()`
- method `BlockProperties dropsNormally()`
- method `BlockProperties dropsSelf()`
- method `BlockProperties dropsFrom(String table)`
- get `hardness` — `float getHardness()`
- set `hardness` — `void setHardness(float hardness)`
- get `blastResistance` — `float getBlastResistance()`
- set `blastResistance` — `void setBlastResistance(float blastResistance)`
- get `friction` — `float getFriction()`
- set `friction` — `void setFriction(float friction)`
- get `speedFactor` — `float getSpeedFactor()`
- set `speedFactor` — `void setSpeedFactor(float speedFactor)`
- get `jumpFactor` — `float getJumpFactor()`
- set `jumpFactor` — `void setJumpFactor(float jumpFactor)`
- get `lightLevel` — `int getLightLevel()`
- set `lightLevel` — `void setLightLevel(int lightLevel)`
- get `requiresCorrectTool` — `boolean getRequiresCorrectTool()`
- set `requiresCorrectTool` — `void setRequiresCorrectTool(boolean requiresCorrectTool)`
- get `collision` — `boolean getCollision()`
- set `collision` — `void setCollision(boolean collision)`
- get `occlusion` — `boolean getOcclusion()`
- set `occlusion` — `void setOcclusion(boolean occlusion)`
- get `randomTicks` — `boolean getRandomTicks()`
- set `randomTicks` — `void setRandomTicks(boolean randomTicks)`
- get `replaceable` — `boolean getReplaceable()`
- set `replaceable` — `void setReplaceable(boolean replaceable)`
- get `sound` — `String getSound()`
- set `sound` — `void setSound(String sound)`
- get `mapColor` — `String getMapColor()`
- set `mapColor` — `void setMapColor(String mapColor)`
- get `hasItem` — `boolean getHasItem()`
- set `hasItem` — `void setHasItem(boolean hasItem)`
- get `redstoneInput` — `boolean getRedstoneInput()`
- set `redstoneInput` — `void setRedstoneInput(boolean redstoneInput)`
- get `redstoneOutput` — `boolean getRedstoneOutput()`
- set `redstoneOutput` — `void setRedstoneOutput(boolean redstoneOutput)`
- get `strongPower` — `boolean getStrongPower()`
- set `strongPower` — `void setStrongPower(boolean strongPower)`
- get `directional` — `boolean getDirectional()`
- set `directional` — `void setDirectional(boolean directional)`
- get `redstoneDelay` — `int getRedstoneDelay()`
- set `redstoneDelay` — `void setRedstoneDelay(int redstoneDelay)`
- get `comparator` — `String getComparator()`
- set `comparator` — `void setComparator(String comparator)`
- get `outputFaces` — `String[] getOutputFaces()`
- method `BlockProperties addOutputFaces(String... faces)`
- method `BlockProperties removeOutputFaces(String... faces)`
- get `inputFaces` — `String[] getInputFaces()`
- method `BlockProperties addInputFaces(String... faces)`
- method `BlockProperties removeInputFaces(String... faces)`
- method `BlockProperties onRedstoneChanged(RedstoneHandler handler)`
- method `BlockProperties onRightClick(ClickHandler handler)`
- method `BlockProperties onBroken(BreakHandler handler)`
- method `BlockProperties onPlaced(BlockEventHandler handler)`
- method `BlockProperties onSteppedOn(BlockEventHandler handler)`
- method `BlockProperties onRandomTick(BlockEventHandler handler)`

### `BlockReference`

Source: [ScriptBlockReference](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockReference.java).

- constructor `BlockReference(String id)`
- get `id` — `String getId()`

### `BlockTagReference`

Source: [ScriptBlockTagReference](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBlockTagReference.java).

- constructor `BlockTagReference(String id)`
- get `id` — `String getId()`

### `BreakContext`

Source: [ScriptBreakContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBreakContext.java).

- get `player` — `Player getPlayer()`
- get `tool` — `IItemStack getTool()`
- get `block` — `String getBlock()`
- get `x` — `int getX()`
- get `y` — `int getY()`
- get `z` — `int getZ()`
- get `face` — `String getFace()`
- method `void cancel()`
- method `boolean mine(int x, int y, int z)`

### `BreakHandler`

Source: [ScriptBreakHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptBreakHandler.java).

- method `void handle(BreakContext context)`

### `ClickContext`

Source: [ScriptClickContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptClickContext.java). Inherits `BlockContext`.

- get `player` — `Player getPlayer()`
- get `sneaking` — `boolean isSneaking()`
- get `face` — `String getFace()`

### `ClickHandler`

Source: [ScriptClickHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptClickHandler.java).

- method `void handle(ClickContext context)`

### `CreativeTabBuilder`

Source: [ScriptCreativeTabBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptCreativeTabBuilder.java).

- constructor `CreativeTabBuilder(String name)`
- static method `CreativeTabBuilder edit(String id)`
- static method `boolean exists(String id)`
- static method `String[] declared()`
- get `name` — `String getName()`
- get `title` — `String getTitle()`
- set `title` — `void setTitle(String value)`
- get `titleFallback` — `String getTitleFallback()`
- set `titleFallback` — `void setTitleFallback(String value)`
- get `icon` — `String getIcon()`
- set `icon` — `void setIcon(String value)`
- get `searchBar` — `boolean getSearchBar()`
- set `searchBar` — `void setSearchBar(boolean value)`
- get `before` — `String[] getBefore()`
- set `before` — `void setBefore(String[] tabs)`
- get `after` — `String[] getAfter()`
- set `after` — `void setAfter(String[] tabs)`
- get `contents` — `String[] getContents()`
- method `CreativeTabBuilder addItems(String... items)`
- method `CreativeTabBuilder addTags(String... tags)`
- method `CreativeTabBuilder addMaterials(String... materials)`
- method `CreativeTabBuilder removeItems(String... items)`
- method `CreativeTabBuilder removeTags(String... tags)`
- method `CreativeTabBuilder removeMaterials(String... materials)`
- method `CreativeTabBuilder clearContents()`
- method `void build()`

### `DefinitionProperties`

Source: [ScriptDefinitionProperties](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptDefinitionProperties.java).

- get `displayName` — `String getDisplayName()`
- set `displayName` — `void setDisplayName(String name)`
- method `void build()`
- get `factoryData` — `IData getFactoryData()`
- set `factoryData` — `void setFactoryData(IData data)`
- get `tags` — `String[] getTags()`
- method `DefinitionProperties addTags(String... tags)`
- method `DefinitionProperties removeTags(String... tags)`
- get `events` — `String[] getEvents()`
- method `DefinitionProperties addEvent(String event, String callback)`
- method `DefinitionProperties removeEvents(String... events)`

### `HitContext`

Source: [ScriptHitContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptHitContext.java).

- get `attacker` — `LivingEntity getAttacker()`
- get `target` — `LivingEntity getTarget()`
- get `stack` — `IItemStack getStack()`
- get `damage` — `float getDamage()`

### `HitHandler`

Source: [ScriptHitHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptHitHandler.java).

- method `void handle(HitContext context)`

### `ItemBuilder`

Source: [ScriptItemBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemBuilder.java). Inherits `ItemProperties`.

- constructor `ItemBuilder(String name)`
- constructor `ItemBuilder(String name, String module)`
- constructor `ItemBuilder(String name, ItemModuleReference module)`
- get `reference` — `ItemReference getReference()`
- get `name` — `String getName()`
- get `module` — `String getModule()`
- method `void build()`

### `ItemClickContext`

Source: [ScriptItemClickContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemClickContext.java).

- get `player` — `Player getPlayer()`
- get `client` — `boolean isClient()`
- get `stack` — `IItemStack getStack()`
- get `target` — `String getTarget()`
- get `entity` — `Entity getEntity()`
- get `x` — `int getX()`
- get `y` — `int getY()`
- get `z` — `int getZ()`
- get `face` — `String getFace()`

### `ItemClickHandler`

Source: [ScriptItemClickHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemClickHandler.java).

- method `void handle(ItemClickContext context)`

### `ItemModule`

Source: [ScriptItemModule](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemModule.java). Inherits `ItemProperties`.

- constructor `ItemModule(String name)`
- constructor `ItemModule(String name, String basedOn)`
- constructor `ItemModule(String name, ItemModuleReference basedOn)`
- get `reference` — `ItemModuleReference getReference()`
- method `ItemModule copy(String name)`
- get `name` — `String getName()`
- get `factory` — `String getFactory()`
- set `factory` — `void setFactory(String factory)`
- method `void build()`

### `ItemModuleReference`

Source: [ScriptItemModuleReference](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemModuleReference.java).

- constructor `ItemModuleReference(String id)`
- get `id` — `String getId()`

### `ItemProperties`

Source: [ScriptItemProperties](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemProperties.java). Inherits `DefinitionProperties`.

- get `useDuration` — `int getUseDuration()`
- set `useDuration` — `void setUseDuration(int useDuration)`
- get `useAnimation` — `String getUseAnimation()`
- set `useAnimation` — `void setUseAnimation(String useAnimation)`
- method `ItemProperties onUseStart(ItemUseHandler handler)`
- method `ItemProperties onUseTick(ItemUseHandler handler)`
- method `ItemProperties onUseStop(ItemUseHandler handler)`
- method `ItemProperties onUseFinish(ItemUseHandler handler)`
- method `ItemProperties onHit(HitHandler handler)`
- method `ItemProperties onBreakBlock(BreakHandler handler)`
- method `ItemProperties onRightClick(ItemClickHandler handler)`
- get `stackSize` — `int getStackSize()`
- set `stackSize` — `void setStackSize(int stackSize)`
- get `durability` — `int getDurability()`
- set `durability` — `void setDurability(int durability)`
- get `fireResistant` — `boolean getFireResistant()`
- set `fireResistant` — `void setFireResistant(boolean fireResistant)`
- get `container` — `String getContainer()`
- set `container` — `void setContainer(String container)`
- method `ItemProperties configureTool(ToolSettings settings)`
- method `ItemProperties remainder(ItemReference item)`
- method `ItemProperties stacksTo(int size)`
- method `ItemProperties craftRemainder(String item)`
- method `ItemProperties craftRemainder(ItemReference item)`

### `ItemReference`

Source: [ScriptItemReference](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemReference.java).

- constructor `ItemReference(String id)`
- get `id` — `String getId()`

### `ItemUseContext`

Source: [ScriptItemUseContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemUseContext.java).

- get `phase` — `String getPhase()`
- get `entity` — `LivingEntity getEntity()`
- get `client` — `boolean isClient()`
- get `stack` — `IItemStack getStack()`
- get `duration` — `int getDuration()`
- set `duration` — `void setDuration(int duration)`
- get `result` — `IItemStack getResult()`
- set `result` — `void setResult(IItemStack result)`
- method `void cancel()`

### `ItemUseHandler`

Source: [ScriptItemUseHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptItemUseHandler.java).

- method `void handle(ItemUseContext context)`

### `MachineCycleContext`

Source: [ScriptMachineCycleContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptMachineCycleContext.java).

- get `machine` — `BlockEntity getMachine()`
- get `level` — `Level getLevel()`
- get `pos` — `BlockPos getPos()`
- get `machineId` — `String getMachineId()`
- get `recipe` — `String getRecipe()`
- get `time` — `int getTime()`

### `MachineCycleHandler`

Source: [ScriptMachineCycleHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptMachineCycleHandler.java).

- method `void handle(MachineCycleContext cycle)`

### `MachineHandlers`

Source: [ScriptMachineHandlers](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptMachineHandlers.java).

- static method `String onStart(String name, MachineCycleHandler handler)`
- static method `String onFinish(String name, MachineCycleHandler handler)`
- static method `boolean exists(String id)`

### `MaterialBuilder`

Source: [ScriptMaterialBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptMaterialBuilder.java).

- constructor `MaterialBuilder(String name)`
- get `name` — `String getName()`
- get `type` — `String getType()`
- set `type` — `void setType(String type)`
- get `color` — `int getColor()`
- set `color` — `void setColor(int color)`
- get `hardness` — `double getHardness()`
- set `hardness` — `void setHardness(double hardness)`
- get `strength` — `double getStrength()`
- set `strength` — `void setStrength(double strength)`
- get `magic` — `double getMagic()`
- set `magic` — `void setMagic(double magic)`
- get `damage` — `double getDamage()`
- set `damage` — `void setDamage(double damage)`
- get `hasFluid` — `boolean getHasFluid()`
- set `hasFluid` — `void setHasFluid(boolean hasFluid)`
- get `fluid` — `String getFluid()`
- set `fluid` — `void setFluid(String fluid)`
- get `items` — `String[] getItems()`
- method `MaterialBuilder addItems(String... names)`
- method `MaterialBuilder removeItems(String... names)`
- get `blocks` — `String[] getBlocks()`
- method `MaterialBuilder addBlocks(String... names)`
- method `MaterialBuilder removeBlocks(String... names)`
- get `harvestTags` — `String[] getHarvestTags()`
- method `MaterialBuilder addHarvestTags(String... tags)`
- method `MaterialBuilder removeHarvestTags(String... tags)`
- method `MaterialBuilder addSpecial(String key, double value)`
- method `MaterialBuilder removeSpecial(String... keys)`
- method `void build()`
- get `repairItem` — `String getRepairItem()`
- set `repairItem` — `void setRepairItem(String value)`
- get `incorrectBlocks` — `String getIncorrectBlocks()`
- set `incorrectBlocks` — `void setIncorrectBlocks(String value)`
- get `baseBlock` — `String getBaseBlock()`
- set `baseBlock` — `void setBaseBlock(String value)`
- get `blockSetType` — `String getBlockSetType()`
- set `blockSetType` — `void setBlockSetType(String value)`
- get `blockSound` — `String getBlockSound()`
- set `blockSound` — `void setBlockSound(String value)`
- get `mapColor` — `String getMapColor()`
- set `mapColor` — `void setMapColor(String value)`
- method `MaterialBuilder toolDurability(int value)`
- method `MaterialBuilder toolEfficiency(double value)`
- method `MaterialBuilder attackDamage(double value)`
- method `MaterialBuilder enchantability(int value)`
- method `MaterialBuilder repairWith(ItemReference item)`
- method `MaterialBuilder baseBlockFrom(BlockReference block)`
- method `MaterialBuilder incorrectBlocksTag(BlockTagReference tag)`
- method `MaterialBuilder addHarvestTag(BlockTagReference tag)`
- method `MaterialBuilder addItemModule(ItemModuleReference module)`
- method `MaterialBuilder addBlockModule(BlockModuleReference module)`
- get `displayName` — `String getDisplayName()`
- set `displayName` — `void setDisplayName(String value)`

### `RedstoneContext`

Source: [ScriptRedstoneContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptRedstoneContext.java). Inherits `BlockContext`.

- get `power` — `int getPower()`
- get `previous` — `int getPrevious()`
- get `powered` — `boolean isPowered()`

### `RedstoneHandler`

Source: [ScriptRedstoneHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptRedstoneHandler.java).

- method `void handle(RedstoneContext context)`

### `ScreenClickContext`

Source: [ScriptScreenClickContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenClickContext.java).

- get `player` — `Player getPlayer()`
- get `blockEntity` — `BlockEntity getBlockEntity()`
- get `level` — `Level getLevel()`
- get `pos` — `BlockPos getPos()`
- get `screen` — `String getScreen()`
- get `element` — `String getElement()`
- get `button` — `int getButton()`
- get `shift` — `boolean getShift()`
- get `control` — `boolean getControl()`
- get `alt` — `boolean getAlt()`
- get `option` — `int getOption()`

### `ScreenClickHandler`

Source: [ScriptScreenClickHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenClickHandler.java).

- method `void handle(ScreenClickContext click)`

### `ScreenHandlers`

Source: [ScriptScreenHandlers](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenHandlers.java).

- static method `String onClick(String name, ScreenClickHandler handler)`
- static method `String onHover(String name, ScreenHoverHandler handler)`
- static method `boolean exists(String id)`

### `ScreenDecimalValue`

Source: [ScriptScreenDecimalValue](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenDecimalValue.java).

- method `double read(BlockEntity blockEntity)`

### `ScreenFlagValue`

Source: [ScriptScreenFlagValue](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenFlagValue.java).

- method `boolean read(BlockEntity blockEntity)`

### `ScreenHoverContext`

Source: [ScriptScreenHoverContext](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenHoverContext.java).

- get `blockEntity` — `BlockEntity getBlockEntity()`
- get `level` — `Level getLevel()`
- get `pos` — `BlockPos getPos()`
- get `screen` — `String getScreen()`
- get `element` — `String getElement()`
- get `entered` — `boolean getEntered()`

### `ScreenHoverHandler`

Source: [ScriptScreenHoverHandler](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenHoverHandler.java).

- method `void handle(ScreenHoverContext hover)`

### `ScreenTextValue`

Source: [ScriptScreenTextValue](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenTextValue.java).

- method `String read(BlockEntity blockEntity)`

### `ScreenValues`

Source: [ScriptScreenValues](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenValues.java).

- static method `String whole(String name, ScreenWholeValue function)`
- static method `String decimal(String name, ScreenDecimalValue function)`
- static method `String flag(String name, ScreenFlagValue function)`
- static method `String text(String name, ScreenTextValue function)`
- static method `boolean exists(String id)`

### `ScreenWholeValue`

Source: [ScriptScreenWholeValue](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptScreenWholeValue.java).

- method `long read(BlockEntity blockEntity)`

### `SoundBuilder`

Source: [ScriptSoundBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptSoundBuilder.java).

- constructor `SoundBuilder(String name)`
- static method `SoundBuilder edit(String id)`
- static method `boolean exists(String id)`
- static method `String[] declared()`
- get `name` — `String getName()`
- get `range` — `float getRange()`
- set `range` — `void setRange(float value)`
- method `void build()`

### `SoundTypeBuilder`

Source: [ScriptSoundTypeBuilder](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptSoundTypeBuilder.java).

- constructor `SoundTypeBuilder(String name)`
- static method `SoundTypeBuilder edit(String id)`
- static method `boolean exists(String id)`
- static method `String[] declared()`
- get `name` — `String getName()`
- get `base` — `String getBase()`
- set `base` — `void setBase(String value)`
- get `breakSound` — `String getBreakSound()`
- set `breakSound` — `void setBreakSound(String value)`
- get `stepSound` — `String getStepSound()`
- set `stepSound` — `void setStepSound(String value)`
- get `placeSound` — `String getPlaceSound()`
- set `placeSound` — `void setPlaceSound(String value)`
- get `hitSound` — `String getHitSound()`
- set `hitSound` — `void setHitSound(String value)`
- get `fallSound` — `String getFallSound()`
- set `fallSound` — `void setFallSound(String value)`
- get `volume` — `float getVolume()`
- set `volume` — `void setVolume(float value)`
- get `pitch` — `float getPitch()`
- set `pitch` — `void setPitch(float value)`
- method `void build()`

### `ToolSettings`

Source: [ScriptToolSettings](../src/main/java/zone/moddev/mc/lib/compat/CraftTweaker/ScriptToolSettings.java).

- constructor `ToolSettings()`
- set `mineable` — `void setMineable(String tag)`
- set `fastOn` — `void setFastOn(String tag)`
- set `attackDamage` — `void setAttackDamage(float value)`
- set `attackSpeed` — `void setAttackSpeed(float value)`
- method `ToolSettings abilities(String... names)`
- method `ToolSettings durability(double factor, int minimum, String stat)`
- method `ToolSettings speed(double factor, double minimum, String stat)`
- method `ToolSettings mineableTag(BlockTagReference tag)`
- method `ToolSettings fastOnTag(BlockTagReference tag)`

## Definition-to-runtime map

Paths below are relative to `src/main/java/zone/moddev/mc/lib/`.

| Exposed surface | Definition/planning path | Runtime or resource consumer |
| --- | --- | --- |
| Material identity, type, color, hardness, strength, magic, damage, special stats | `ScriptMaterialBuilder.build` -> `MaterialProperties` / `MaterialFactory` | `common/runtime/MaterialStats.java`, `MaterialToolTier.java`, standard item/block factories; color also feeds script texture generation. Type is classification metadata, not a new Java implementation. Arbitrary special keys only affect behavior if a factory reads them. |
| Material item/block selection, fluid selection, harvest tags | `MaterialSelection` -> `MaterialFactory` | `common/runtime/RuntimeContentRegistrar.java` queues selected content; `ScriptMaterialResources.data` emits tags. Projectile selection follows its item. |
| Standalone block/item identity and selected module | `ScriptBlockBuilder` / `ScriptItemBuilder` -> `StandaloneBlock` / `StandaloneItem` | Same definition planning and runtime registrar as file definitions. Standalone items use default material runtime settings, not configurable independent tiers. |
| Module factory, block materialDefaults, copied module settings | `ScriptBlockModule` / `ScriptItemModule` -> `DataModules` and public module registration | `MaterialFactory` plans selected parts; `StandardImplementations` or an addon implementation creates objects. `materialDefaults` controls material-stat application to block templates. |
| Common block properties including sound/mapColor | `BlockTemplate` -> `MMDBlock.Properties` | `common/runtime/RuntimeProperties.block`; individual factory choices can further specialize vanilla properties. |
| Block baseBlock / blockSetType | Standalone `MaterialRuntimeSettings` | `RuntimeContentRegistrar` block context and the stairs/door/trapdoor/button/plate factories that use these values. These are not universal behavior overrides. |
| hasItem | `BlockTemplate` -> `MMDBlock` | Registrar queues a block item only when present in the planned block definition; resources follow the plan. |
| blockEntityData | Standalone block factory/block-entity configuration | Existing block-entity planning/creation path; supplying data does not create a new machine implementation or script-defined screen. |
| Item stackSize, durability, fireResistant, container | `ItemTemplate` -> `MMDItem` | `RuntimeProperties.item`; standard factories can derive/override durability from the material tier. |
| Item useDuration / useAnimation | `ItemTemplate.DelayedUse` -> `MMDItem.useTime/useAnim` | `StandardImplementations` basic_item factory passes values to `StandardItems.Fuel` / `Basic`, which implement timed use. Other factories keep their native use behavior; these fields do not override bows or make every item usable. |
| factoryData | Module/standalone `FactoryData` | Selected Java factory's decoder. Unknown or unsupported settings do not automatically gain runtime behavior. |
| addTags/removeTags | Block template tags or script item-tag collection | `ScriptMaterialResources.data` emits generated tag entries. |
| addEvent/removeEvents and onXxx functions | `ScriptDefinitionProperties` registers callback IDs and event bindings | `common/runtime/ItemEventBridge.java`, `BlockHooks.java`, `RedstoneBlock.java`, and `api/MaterialEventContexts.java`; see callback table below. |
| Redstone properties and input/output faces | Existing `factory_data.redstone` schema | `RedstoneBlock.Settings` validates; `RedstoneBlock` implements signals, scheduling, facing, and comparator behavior. Explicit redstone configuration requires factory `mmdlib:block`. |
| BlockContext.input/output/facing and coordinates | Wrappers over material event contexts | Reads current block state/signals; output changes are server-only, clamped to 0–15, and require an output-capable RedstoneBlock. |
| Generated models/names/textures/loot | Planned script content plus module template ancestry | `ScriptMaterialResources` -> `MaterialPacks`; templates and `scripts/mmdlibdata` supply resources. No recipes are generated by this integration. |

## Callback contracts and context limits

Every handler interface exposes `handle(context)` as listed above. Script functions
are retained as callbacks; they are not rerun to rebuild content during gameplay.

| Builder callbacks | Contract / runtime dispatcher | Side and writable results |
| --- | --- | --- |
| Item onRightClick | tool-right-click / ItemEventBridge | Both sides; observational callback alongside native use, no script cancel/result setter. Target-specific fields must be checked against target. |
| Item onUseStart/Tick/Stop/Finish | item-use-start/tick/stop/finish / ItemEventBridge | Both sides; duration writable at start, cancel at start/tick, result at finish. Requires an item with actual timed-use behavior. |
| Item onHit | item-hit / ItemEventBridge | Server, after melee damage; damage is read-only. |
| Item onBreakBlock, block onBroken | item-break-block then block-broken / ItemEventBridge | Server before player break; cancel supported. BreakContext.mine uses guarded player destruction, protection checks, harvest eligibility, drops, experience and tool wear. |
| Block onPlaced | block-placed / ItemEventBridge | Server; entity may be absent. |
| Block onSteppedOn | block-stepped-on / BlockHooks | Both sides; needs mmdlib:block. |
| Block onRandomTick | block-random-tick / BlockHooks | Server; enables randomTicks and needs mmdlib:block. |
| Block onRightClick | block-right-click / ItemEventBridge | Both sides; consumes click. Output mutation only has an effect on a compatible redstone block. |
| Block onRedstoneChanged | redstone-changed / RedstoneBlock | Server; input is enabled automatically; configured delay and face selection apply. |

World mutation in callbacks that run on both sides must be guarded with `client`.
BreakContext and HitContext are server-only and do not expose a client property.
BlockEventContext.entity and ItemClickContext.entity can be null. ItemClickContext
coordinates are zero and face is empty for targets other than a block. Exceptions
from script callback execution are logged by the binding wrapper.

## Capability classification for parity work

| Status | Capability | Evidence / consequence |
| --- | --- | --- |
| Script-exposed and runtime-supported | Common block/item properties, selected shapes, materials, modules, callbacks, redstone, resource generation | Consumer map above. Factory-specific limits still apply. |
| Script-exposed with limited consumers | useDuration/useAnimation, arbitrary factoryData and special stats | Only the documented consuming factory gives these values behavior. |
| Script-exposed in phase 2 | Material repairItem, incorrectBlocks, baseBlock, blockSetType, blockSound, mapColor | `MaterialRuntimeSettings` and registrar/factory context consumers; MaterialBuilder now exposes these settings while preserving defaults. |
| Runtime-supported through an existing generic script map | Tool stat overrides and generic tool settings; basic-item burn time | `MaterialStats`, `StandardItems.ToolSettings`, `StandardImplementations`; addSpecial/factoryData already reach them. Named stat methods and ToolSettings now wrap these existing paths; per-tool durability scaling is limited by TieredItem overriding durability. |
| Schema-only in current standard runtime | food reference, lore, declared toolActions, general attributeModifiers, delayed-use onComplete/useFinishMode, creativeStacks | Stored in ItemTemplate/MMDItem; no standard runtime consumer found. Native tool attributes and generic tool abilities are separate implemented paths. |
| Unavailable as a configurable definition capability | Item rarity/feature flags; advanced block predicates, piston reaction, instrument, offsets, solidity flags | Current definition/runtime property path does not represent and apply them. |
| Unavailable as a public script registration path | Custom sound events/types, independent tool tiers, user-defined creative tabs | Existing material tab/armor/tier machinery is not a general script builder API. |
| Script-exposed in phase 3 | Typed ID references and module snapshot copies | New wrappers unwrap into existing consumers; copy rebinds locally owned callbacks with separate handler maps. |
| Script-exposed in phase 4 | Fluent builder aliases, displayName, default/self/empty/referenced loot | Aliases use existing setters. Immutable ScriptResourceOptions metadata feeds the generated pack; external resources keep priority. |
| Unavailable script conveniences | Arbitrary game-object property cloning | Module configuration copying does not clone registered game objects. |

## Startup and copying rules

`MMDLibCraftTweakerPlugin` registers loader `mmdlib`, inheriting `crafttweaker` types.
`CraftTweakerInit` installs a definition source; `ScriptDefinitions.load` executes it
before registry events. Module build registers a catalog module immediately so later
script statements and material files can use it. Material and standalone build queue
content for normal planning. Building outside the startup run fails. A startup script
error is logged and definitions built before that failure can remain; this is not a
transactional all-or-nothing script loader.

New content IDs are restricted to `mmdlib:`. Selecting an existing module/factory
from another namespace is distinct from creating content in that namespace. Scripts
and client resources must be distributed to both sides. `/reload` does not rerun
content definitions; changing them requires a restart. Registry-dependent recipe
operations belong in ordinary CraftTweaker scripts.

Constructing a module from another module copies its configuration; it does not
clone a registered vanilla/modded object, track future edits to its source, or create
a Java subclass. Existing event bindings may refer to shared callback IDs: copying
bindings is not a deep copy of executable closures. Material selection of a module
uses its template and factory under the existing material-default rules.

## Next-phase gates

Each proposed addition in [the parity plan](crafttweaker-parity-plan.md) must target
an existing consumer in this audit. Phase 2 exposes runtime settings; phase 3 now wraps IDs
and copies supported module configurations. Phase 4 resource controls operate through
ScriptMaterialResources/MaterialPacks. A schema-only field needs a runtime change
and remains deferred under this plan. New registry types and event contracts also
remain deferred. This audit establishes source support only; it does not certify
new script compilation, server execution, client rendering, or packaging results.

## Phase 2 verification

`./gradlew --offline check` passed with 185 unit tests, 83 MMDLib server GameTests,
52 companion GameTests, release/companion packaging checks, and static-asset
validation. The new script fixture is `src/runtimeTest/scripts/mmdlib_tools.zs`;
`ScriptHookSmoke.toolSettings` checks the registered material tool and runtime
settings. No client rendering or launch verification was performed for phase 2.

The GameTest also exposed an existing runtime limit: vanilla TieredItem calls
`properties.durability(tier.getUses())`, replacing the generic tool factory's scaled
durability. The test records actual material-tier durability; codec/unit coverage
only proves that helper scaling configuration is represented correctly. Speed
scaling has a separate runtime assertion. No core fix is included.

## Phase 3 verification

`./gradlew --offline check` passed with 187 unit tests, 84 MMDLib server GameTests,
52 companion GameTests, packaging checks, and static-asset validation. The new
`mmdlib_references.zs` fixture verifies item/block module constructors using typed
references, independently edited property/tag snapshots, separately rebound item
and block callbacks, same-run tag inheritance, and a forward item remainder
reference resolved by the existing registrar. Unit tests check unregistered IDs
without registry lookup and malformed ID rejection. Wrong-kind rejection follows
from the distinct exposed parameter types; no runtime registry-kind probing was
added. No client smoke test was performed in this phase.

## Phase 4 verification

The phase 4 tests cover fluent/property definition equivalence and matching
validation failures, generated material/module name expansion, empty and nested
loot JSON, and rejected direct self-reference/self-drop-without-item requests.
`ScriptHookSmoke.generatedLoot` performs actual server player block breaks for
normal/self/no-drop/referenced tables, itemless blocks, and a folder-pack override
that replaces generated noDrops with a diamond drop. Existing resource tests retain
shape-aware double-slab loot coverage. English-name resource content is unit-tested;
client rendering and localization display have not been smoke-tested.

Final `./gradlew --offline check` passed: 190 unit tests, 85 MMDLib GameTests,
52 companion GameTests, packaging and static-asset validation.

## Phase 5 final verification

Normal `./gradlew --offline check build` passed (190 unit tests, 85 MMDLib and
52 companion GameTests, packaging and static assets). Real integrated-client
launches passed with CraftTweaker installed and absent. Generated English names,
models, textures, and four horizontal front-face rotations passed assertions;
world and item-gallery captures were visually inspected. Release JAR inspection
confirmed no smoke content remained after restoring normal main outputs.
See [the verification report](crafttweaker-verification.md). The existing core
per-tool durability-scaling limitation remains deferred.

## Shared crushing recipe integration

The startup content inventory above is separate from the ordinary recipe API in
`compat/CraftTweaker/crusher`. `mods.mmdlib.crusher.CrushingRecipeManager` implements
CraftTweaker's IRecipeManager for `mmdlib:crushing` and adds the typed
`addRecipe(string name, IItemStack output, IIngredient input)` method.
`ExpandCrushingRecipe` registers `mods.mmdlib.crusher.CrushingRecipe` as the native
query-result type. Standard removals, queries and JSON additions are inherited.
See [shared crushing recipe scripting](crafttweaker-crusher.md).
