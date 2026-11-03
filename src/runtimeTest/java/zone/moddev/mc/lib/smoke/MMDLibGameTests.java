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
  private static final String EMPTY = "empty";

  /** Inside the structure, away from its edges. */
  private static final BlockPos CENTER = new BlockPos(2, 1, 2);

  private MMDLibGameTests() {}

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
    ClientMenuItemsSmoke.check();
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
  public static void crusherEndToEnd(GameTestHelper helper) {
    CrusherSmoke.endToEnd(
        helper.getLevel(), helper.absolutePos(CENTER), helper.makeMockServerPlayerInLevel());
    helper.succeed();
  }
}
