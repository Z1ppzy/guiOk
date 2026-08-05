/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.Locale;

public final class NumberFormatter {
    /** Rendered instead of a number whenever the value is missing or not finite. */
    public static final String UNKNOWN = "—";

    /**
     * A unit is chosen by what the value will look like once rounded, not by its raw
     * magnitude. {@link #scaled} prints three significant digits at most, so anything from
     * 999.5 upwards already reads as "1000" — switching a unit only at a round 1000 would
     * render 999.95 as "1000" instead of "1.0K", and 999 999.4 as "1000K" instead of "1.0M".
     */
    private static final double UNIT_STEP = 1_000.0;
    private static final double ROUNDS_UP_TO_NEXT_UNIT = 999.5;

    private NumberFormatter() {
    }

    public static String compact(double value) {
        if (!Double.isFinite(value)) {
            return UNKNOWN;
        }
        double absolute = Math.abs(value);
        if (absolute >= ROUNDS_UP_TO_NEXT_UNIT * UNIT_STEP * UNIT_STEP) {
            return scaled(value / (UNIT_STEP * UNIT_STEP * UNIT_STEP), "B");
        }
        if (absolute >= ROUNDS_UP_TO_NEXT_UNIT * UNIT_STEP) {
            return scaled(value / (UNIT_STEP * UNIT_STEP), "M");
        }
        if (absolute >= ROUNDS_UP_TO_NEXT_UNIT) {
            return scaled(value / UNIT_STEP, "K");
        }
        return scaled(value, "");
    }

    private static String scaled(double value, String suffix) {
        String pattern = Math.abs(value) >= 100 || value == Math.rint(value) ? "%.0f" : "%.1f";
        return String.format(Locale.ROOT, pattern + "%s", value, suffix);
    }
}
