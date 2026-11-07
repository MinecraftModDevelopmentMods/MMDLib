package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * MMDLib's server game tests, run by {@code ./gradlew runGameTestServer}. Each one runs a check in
 * an empty 5×5×5 structure ({@code mmdlib:empty}) and passes when the check returns; a thrown error
 * fails it with that error's message. The server exits with the number of failed tests.
 */
@GameTestHolder("mmdlib")
@PrefixGameTestTemplate(false)
public final class MMDLibGameTests {
  @GameTest(template = "empty")
  public static void inventoryScreenReferences(GameTestHelper helper) throws Exception {
    InventoryArchitectureSmoke.screenValidation(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void inventoryItemMigration(GameTestHelper helper) {
    InventoryArchitectureSmoke.itemMigration(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void inventoryFluidMigration(GameTestHelper helper) {
    InventoryArchitectureSmoke.fluidMigration(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void inventoryGroupBindings(GameTestHelper helper) {
    InventoryArchitectureSmoke.bindings(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void inventoryRecipeAdmission(GameTestHelper helper) {
    InventoryArchitectureSmoke.recipeFilters(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void processorIdleSearchBackoff(GameTestHelper helper) {
    ProcessorSmoke.idleSearchBackoff(helper.getLevel());
    helper.succeed();
  }

  private static final String EMPTY = "empty";

  /** Inside the structure, away from its edges. */
  private static final BlockPos CENTER = new BlockPos(2, 1, 2);

  private MMDLibGameTests() {}

  @GameTest(template = EMPTY)
  public static void scriptGeneratedLoot(GameTestHelper helper) {
    ScriptHookSmoke.generatedLoot(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void scriptReferencesAndCopies(GameTestHelper helper) {
    ScriptHookSmoke.referencesAndCopies(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void scriptToolSettings(GameTestHelper helper) {
    ScriptHookSmoke.toolSettings(helper);
  }

  @GameTest(template = EMPTY)
  public static void equippedContainer(GameTestHelper helper) {
    ContainerDrawerSmoke.check(
        helper.getLevel(),
        helper.absolutePos(CENTER),
        helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void playerEquipment(GameTestHelper helper) {
    PlayerEquipmentSmoke.check(
        helper.getLevel(),
        helper.absolutePos(CENTER),
        helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL),
        helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void contentRegistered(GameTestHelper helper) {
    ContentSmoke.registered();
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void blocksPlace(GameTestHelper helper) {
    ContentSmoke.blocksPlace(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void ammunitionRecovers(GameTestHelper helper) {
    ContentSmoke.ammunitionRecovers(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void bucketsPlaceAndRecover(GameTestHelper helper) {
    ContentSmoke.bucketsPlaceAndRecover(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crackhammerCrushesDrops(GameTestHelper helper) {
    ContentSmoke.crackhammerCrushesDrops(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void toolsAndArmor(GameTestHelper helper) throws Exception {
    ContentSmoke.toolsAndArmor();
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void itemUseHooks(GameTestHelper helper) {
    HookSmoke.use(helper);
  }

  @GameTest(template = EMPTY)
  public static void itemHitHook(GameTestHelper helper) {
    HookSmoke.hit(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void miningHook(GameTestHelper helper) {
    HookSmoke.mining(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void blockHooks(GameTestHelper helper) {
    HookSmoke.block(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void scriptItemHooks(GameTestHelper helper) {
    ScriptHookSmoke.item(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void scriptHammer(GameTestHelper helper) {
    ScriptHookSmoke.hammer(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void scriptBlockHooks(GameTestHelper helper) {
    ScriptHookSmoke.block(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void redstoneGate(GameTestHelper helper) {
    RedstoneSmoke.gate(helper, CENTER);
  }

  @GameTest(template = EMPTY)
  public static void capabilities(GameTestHelper helper) {
    CapabilitySmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void standaloneMachine(GameTestHelper helper) {
    MachineSmoke.check(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void clientMenuItems(GameTestHelper helper) {
    ClientMenuItemsSmoke.check(helper.getLevel().registryAccess());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machinePartsLoad(GameTestHelper helper) {
    MachinePartsLoaderSmoke.check();
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineInternals(GameTestHelper helper) {
    MachineInternalsSmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineInventory(GameTestHelper helper) {
    MachineInventorySmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void slotPlanRules(GameTestHelper helper) {
    SlotPlanSmoke.check();
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void reservedStorage(GameTestHelper helper) {
    ReservedStorageSmoke.check(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void reservedGroups(GameTestHelper helper) {
    ReservedGroupSmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipes(GameTestHelper helper) {
    MachineRecipeSmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void cyclePlan(GameTestHelper helper) {
    CyclePlanSmoke.check(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorWorkedRun(GameTestHelper helper) {
    ProcessorSmoke.workedRun(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorHopperCantJam(GameTestHelper helper) {
    ProcessorSmoke.hopperCantJam(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorTwoLanes(GameTestHelper helper) {
    ProcessorSmoke.twoLanes(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorChanceKeepsNothing(GameTestHelper helper) {
    ProcessorSmoke.chanceKeepsNothing(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorInputRemoved(GameTestHelper helper) {
    ProcessorSmoke.inputRemoved(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorBlockedByOutput(GameTestHelper helper) {
    ProcessorSmoke.blockedByOutput(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPartialStallIsQuiet(GameTestHelper helper) {
    ProcessorSmoke.partialStallIsQuiet(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorNoPowerNeeded(GameTestHelper helper) {
    ProcessorSmoke.noPowerNeeded(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorReloadResumes(GameTestHelper helper) {
    ProcessorSmoke.reloadResumes(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorRecipeChanged(GameTestHelper helper) {
    ProcessorSmoke.recipeChanged(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPlanCantHold(GameTestHelper helper) {
    ProcessorSmoke.planCantHold(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPartialPlanCancels(GameTestHelper helper) {
    ProcessorSmoke.partialPlanCancels(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorLoadedTwice(GameTestHelper helper) {
    ProcessorSmoke.loadedTwice(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorLanesAcrossInterruptReloadAndRecipeChange(GameTestHelper helper) {
    ProcessorSmoke.lanesAcrossInterruptReloadAndRecipeChange(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorLanesRestoreInEitherOrder(GameTestHelper helper) {
    ProcessorSmoke.lanesRestoreInEitherOrder(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorInterruptFrees(GameTestHelper helper) {
    ProcessorSmoke.interruptFrees(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorInterruptWorksWhileStalled(GameTestHelper helper) {
    ProcessorSmoke.interruptWorksWhileStalled(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorInterruptClearsBlocked(GameTestHelper helper) {
    ProcessorSmoke.interruptClearsBlocked(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorResumeKeepsProgress(GameTestHelper helper) {
    ProcessorSmoke.resumeKeepsProgress(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorResumeWithoutPower(GameTestHelper helper) {
    ProcessorSmoke.resumeWithoutPower(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorResumeResets(GameTestHelper helper) {
    ProcessorSmoke.resumeResets(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorResumeAfterRedefinition(GameTestHelper helper) {
    ProcessorSmoke.resumeAfterRedefinition(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPausedStaysPaused(GameTestHelper helper) {
    ProcessorSmoke.pausedStaysPaused(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPausedSavesItsPlace(GameTestHelper helper) {
    ProcessorSmoke.pausedSavesItsPlace(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorIdleDoesNotMarkChanged(GameTestHelper helper) {
    ProcessorSmoke.idleDoesNotMarkChanged(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorSyncIsThrottled(GameTestHelper helper) {
    ProcessorSmoke.syncIsThrottled(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorLastChangeStillArrives(GameTestHelper helper) {
    ProcessorSmoke.lastChangeStillArrives(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorNoSyncingFeatureNoSend(GameTestHelper helper) {
    ProcessorSmoke.noSyncingFeatureNoSend(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorSendsOnStatusChange(GameTestHelper helper) {
    ProcessorSmoke.processorSendsOnStatusChange(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorConsecutiveCyclesStayRunning(GameTestHelper helper) {
    ProcessorSmoke.consecutiveCyclesStayRunning(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorConsecutiveCyclesOfDifferentTimes(GameTestHelper helper) {
    ProcessorSmoke.consecutiveCyclesOfDifferentTimes(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorUnknownStatusInASave(GameTestHelper helper) {
    ProcessorSmoke.unknownStatusInASave(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorPendingMarkOnAFinishTick(GameTestHelper helper) {
    ProcessorSmoke.pendingMarkOnAFinishTick(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorSiblingMarkOnAFinishTick(GameTestHelper helper) {
    ProcessorSmoke.siblingMarkOnAFinishTick(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorUpdateTagInTheGap(GameTestHelper helper) {
    ProcessorSmoke.updateTagInTheGap(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void processorSaveInTheGap(GameTestHelper helper) {
    ProcessorSmoke.saveInTheGap(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipeTypeRegistered(GameTestHelper helper) {
    MachineRecipeTypeSmoke.registered();
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipesLoad(GameTestHelper helper) {
    MachineRecipeTypeSmoke.recipesLoad(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipeLookup(GameTestHelper helper) {
    MachineRecipeTypeSmoke.lookupFinds(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipeExplain(GameTestHelper helper) {
    MachineRecipeTypeSmoke.explainPicksTheClosest(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipeNetworkCodec(GameTestHelper helper) {
    MachineRecipeTypeSmoke.streamCodecRoundTrips(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void machineRecipeBuilder(GameTestHelper helper) {
    MachineRecipeTypeSmoke.builderMatchesFile(helper.getLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherMenuRows(GameTestHelper helper) {
    CrusherSmoke.menuRows(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherWrappedReservations(GameTestHelper helper) {
    CrusherSmoke.wrappedReservations(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherShiftClickShortfalls(GameTestHelper helper) {
    CrusherSmoke.shiftClickShortfalls(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherClearableDropsNothing(GameTestHelper helper) {
    CrusherSmoke.clearableDropsNothing(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherClientRows(GameTestHelper helper) {
    CrusherSmoke.clientRows(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherEndToEnd(GameTestHelper helper) {
    CrusherSmoke.endToEnd(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }

  @GameTest(template = EMPTY)
  public static void crusherSyncRoundTrip(GameTestHelper helper) {
    CrusherSmoke.syncRoundTrip(helper.getLevel(), helper.absolutePos(CENTER));
    helper.succeed();
  }
}
