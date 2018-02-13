package com.mcmoddev.lib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilsTest {
    private static final String ORIGINAL = "Cats are cool";

    @Test
    void insert_before_start() {
        assertEquals(
            "All Cats are cool",
            StringUtils.insert(ORIGINAL, -5, "All ")
        );
    }

    @Test
    void insert_at_start() {
        assertEquals(
            "All Cats are cool",
            StringUtils.insert(ORIGINAL, 0, "All ")
        );
    }

    @Test
    void insert_in_middle() {
        assertEquals(
            "Cats are the cool",
            StringUtils.insert(ORIGINAL, "Cats are ".length(), "the ")
        );
    }

    @Test
    void insert_at_end() {
        assertEquals(
            "Cats are cooler than dogs",
            StringUtils.insert(ORIGINAL, ORIGINAL.length(), "er than dogs")
        );
    }

    @Test
    void insert_after_end() {
        assertEquals(
            "Cats are cooler than dogs",
            StringUtils.insert(ORIGINAL, ORIGINAL.length() + 5, "er than dogs"));
    }

    @Test
    void remove_before_start_1() {
        assertEquals(
            "Cats are cool",
            StringUtils.remove(ORIGINAL, -10, 5)
        );
    }

    @Test
    void remove_before_start_2() {
        assertEquals(
            "are cool",
            StringUtils.remove(ORIGINAL, -5, "Cats ".length() + 5)
        );
    }

    @Test
    void remove_nothing_from_start() {
        assertEquals(
            "Cats are cool",
            StringUtils.remove(ORIGINAL, 0, 0)
        );
    }

    @Test
    void remove_from_start() {
        assertEquals(
            "are cool",
            StringUtils.remove(ORIGINAL, 0, "Cats ".length())
        );
    }

    @Test
    void remove_from_end() {
        assertEquals(
            "Cats are",
            StringUtils.remove(ORIGINAL, "Cats are".length(), 5)
        );
    }

    @Test
    void remove_beyond_end() {
        assertEquals(
            "Cats are",
            StringUtils.remove(ORIGINAL, "Cats are".length(), 40)
        );
    }

    @Test
    void remove_after_end_1() {
        assertEquals(
            "Cats are cool",
            StringUtils.remove(ORIGINAL, ORIGINAL.length(), 10)
        );
    }

    @Test
    void remove_after_end_2() {
        assertEquals(
            "Cats are cool",
            StringUtils.remove(ORIGINAL, ORIGINAL.length() + 5, 10)
        );
    }
}