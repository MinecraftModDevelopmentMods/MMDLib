package zone.moddev.mc.lib.machine.ingredient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmountMatchTest {
  @Test
  void exactNeedsTheSameAmount() {
    assertTrue(AmountMatch.EXACT.test(5, 5));
    assertFalse(AmountMatch.EXACT.test(5, 6));
  }

  @Test
  void atLeastAcceptsMore() {
    assertTrue(AmountMatch.AT_LEAST.test(5, 6));
    assertTrue(AmountMatch.AT_LEAST.test(5, 5));
    assertFalse(AmountMatch.AT_LEAST.test(5, 4));
  }

  @Test
  void anyAmountAcceptsNothing() {
    assertTrue(AmountMatch.ANY_AMOUNT.test(5, 0));
  }

  @Test
  void amountsAbove2To31() {
    assertTrue(AmountMatch.AT_LEAST.test(3_000_000_000L, 3_000_000_001L));
    assertFalse(AmountMatch.AT_LEAST.test(3_000_000_000L, 2_999_999_999L));
  }
}
