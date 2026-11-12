package zone.moddev.mc.lib.machine.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InputClaimsTest {
    @Test
    void broadClaimMovesOffTheOnlyEntryALaterClaimAccepts() {
        var claims = new InputClaims(new long[]{1, 1});
        assertEquals(0, claims.claim(1, any -> true));
        assertEquals(0, claims.claim(1, entry -> entry == 0));
        assertEquals(1, claims.claimed(0));
        assertEquals(1, claims.claimed(1));
        assertEquals(0, claims.claimed(0, 0));
        assertEquals(1, claims.claimed(0, 1));
        assertEquals(1, claims.claimed(1, 0));
        assertEquals(0, claims.claimed(1, 1));
    }

    @Test
    void orderOfTheClaimsDoesntMatter() {
        var claims = new InputClaims(new long[]{1, 1});
        assertEquals(0, claims.claim(1, entry -> entry == 0));
        assertEquals(0, claims.claim(1, any -> true));
        assertEquals(1, claims.claimed(0));
        assertEquals(1, claims.claimed(1));
    }

    @Test
    void movesFollowAChain() {
        var claims = new InputClaims(new long[]{1, 1, 1});
        assertEquals(0, claims.claim(1, entry -> entry != 1));
        assertEquals(0, claims.claim(1, entry -> entry != 2));
        assertEquals(0, claims.claim(1, entry -> entry == 0));
        assertEquals(1, claims.claimed(0));
        assertEquals(1, claims.claimed(1));
        assertEquals(1, claims.claimed(2));
    }

    @Test
    void partOfAStackMovesAndTheRestStays() {
        var claims = new InputClaims(new long[]{3, 3});
        assertEquals(0, claims.claim(4, any -> true));
        assertEquals(0, claims.claim(2, entry -> entry == 0));
        assertEquals(3, claims.claimed(0));
        assertEquals(3, claims.claimed(1));
    }

    @Test
    void nothingCountsTwiceAndAShortClaimReportsWhatsMissing() {
        var claims = new InputClaims(new long[]{3});
        assertEquals(0, claims.claim(2, any -> true));
        assertEquals(1, claims.claim(2, any -> true));
        assertEquals(3, claims.claimed(0));
    }

    @Test
    void claimsOnTheSameSingleEntryCantBothBeMet() {
        var claims = new InputClaims(new long[]{1, 1});
        assertEquals(0, claims.claim(1, entry -> entry == 0));
        assertEquals(1, claims.claim(1, entry -> entry == 0));
        assertEquals(1, claims.claimed(0));
        assertEquals(0, claims.claimed(1));
    }

    // So an ingredient never tests an empty stack.
    @Test
    void emptyEntriesAreNotAskedAbout() {
        var claims = new InputClaims(new long[]{0, 2});
        assertEquals(0, claims.claim(1, entry -> {
            assertEquals(1, entry);
            return true;
        }));
    }

    // Energy amounts don't fit an int.
    @Test
    void amountsLargerThanAnInt() {
        var claims = new InputClaims(new long[]{3_000_000_000L});
        assertEquals(0, claims.claim(2_999_999_999L, any -> true));
        assertEquals(1, claims.claim(2, any -> true));
    }
}
