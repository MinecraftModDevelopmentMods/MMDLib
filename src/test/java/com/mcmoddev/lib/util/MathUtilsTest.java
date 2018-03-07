package com.mcmoddev.lib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathUtilsTest {
    @Test
    void clampi_before() {
        assertEquals(
            10,
            MathUtils.clampi(5, 10, 20)
        );
    }

    @Test
    void clampi_after() {
        assertEquals(
            20,
            MathUtils.clampi(25, 10, 20)
        );
    }

    @Test
    void clampi_middle() {
        assertEquals(
            15,
            MathUtils.clampi(15, 10, 20)
        );
    }

    @Test
    void clampi_start() {
        assertEquals(
            10,
            MathUtils.clampi(10, 10, 20)
        );
    }

    @Test
    void clampi_end() {
        assertEquals(
            20,
            MathUtils.clampi(20, 10, 20)
        );
    }

    @Test
    void clampi_reverse_before() {
        assertEquals(
            10,
            MathUtils.clampi(5, 20, 10)
        );
    }

    @Test
    void clampi_reverse_after() {
        assertEquals(
            20,
            MathUtils.clampi(25, 20, 10)
        );
    }
}