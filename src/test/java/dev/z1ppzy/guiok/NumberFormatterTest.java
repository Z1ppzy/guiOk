/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void switchesUnitExactlyAtEachThreshold() {
        assertEquals("1K", NumberFormatter.compact(1_000));
        assertEquals("1M", NumberFormatter.compact(1_000_000));
        assertEquals("1B", NumberFormatter.compact(1_000_000_000));
    }

    @Test
    void keepsTheSignOnDebt() {
        assertEquals("-7", NumberFormatter.compact(-7));
        assertEquals("-1.5K", NumberFormatter.compact(-1_500));
    }

    /**
     * A value that rounds up to the next unit must carry that unit: "1000" and "1000K" are
     * both a unit behind what they mean.
     */
    @Test
    void promotesTheUnitWhenRoundingReachesIt() {
        assertEquals("999", NumberFormatter.compact(999.4));
        assertEquals("1.0K", NumberFormatter.compact(999.5));
        assertEquals("1.0K", NumberFormatter.compact(999.95));
        assertEquals("999K", NumberFormatter.compact(999_499));
        assertEquals("1.0M", NumberFormatter.compact(999_500));
        assertEquals("999M", NumberFormatter.compact(999_499_999));
        assertEquals("1.0B", NumberFormatter.compact(999_500_000));
    }

    @Test
    void promotesTheUnitForDebtTheSameWay() {
        assertEquals("-999", NumberFormatter.compact(-999.4));
        assertEquals("-1.0K", NumberFormatter.compact(-999.95));
        assertEquals("-1.0M", NumberFormatter.compact(-999_500));
    }

    /** Whatever unit is picked, the rendered number never reads as a full next unit. */
    @Test
    void neverRendersAValueThatHasOutgrownItsUnit() {
        for (double value = 0; value < 2_000_000_000; value = value * 1.0009 + 0.7) {
            String rendered = NumberFormatter.compact(value);
            String digits = rendered.replaceAll("[KMB]$", "");
            assertTrue(
                    Double.parseDouble(digits) < 1_000,
                    "Rendered " + value + " as " + rendered + ", which has outgrown its unit");
        }
    }

    /** A misbehaving Vault provider must not paint "InfinityB" across every sidebar. */
    @Test
    void replacesNonFiniteBalancesWithThePlaceholder() {
        assertEquals(NumberFormatter.UNKNOWN, NumberFormatter.compact(Double.NaN));
        assertEquals(NumberFormatter.UNKNOWN, NumberFormatter.compact(Double.POSITIVE_INFINITY));
        assertEquals(NumberFormatter.UNKNOWN, NumberFormatter.compact(Double.NEGATIVE_INFINITY));
    }
}
