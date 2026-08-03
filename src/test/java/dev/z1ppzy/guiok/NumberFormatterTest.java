package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumberFormatterTest {
    @Test
    void keepsSmallBalancesReadable() {
        assertEquals("0", NumberFormatter.compact(0));
        assertEquals("12.5", NumberFormatter.compact(12.5));
        assertEquals("999", NumberFormatter.compact(999));
    }

    @Test
    void compactsLargeBalancesWithoutLocaleDependentComma() {
        assertEquals("1.5K", NumberFormatter.compact(1_500));
        assertEquals("12.3M", NumberFormatter.compact(12_300_000));
        assertEquals("-2.5B", NumberFormatter.compact(-2_500_000_000.0));
    }
}
