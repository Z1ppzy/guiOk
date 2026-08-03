package dev.z1ppzy.guiok;

import java.util.Locale;

public final class NumberFormatter {
    private NumberFormatter() {
    }

    public static String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000.0) {
            return scaled(value / 1_000_000_000.0, "B");
        }
        if (absolute >= 1_000_000.0) {
            return scaled(value / 1_000_000.0, "M");
        }
        if (absolute >= 1_000.0) {
            return scaled(value / 1_000.0, "K");
        }
        return scaled(value, "");
    }

    private static String scaled(double value, String suffix) {
        String pattern = Math.abs(value) >= 100 || value == Math.rint(value) ? "%.0f" : "%.1f";
        return String.format(Locale.ROOT, pattern + "%s", value, suffix);
    }
}
