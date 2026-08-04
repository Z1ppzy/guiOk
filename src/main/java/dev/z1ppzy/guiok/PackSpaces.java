/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zero-width characters that move the text cursor by an exact number of pixels.
 *
 * <p>A bitmap glyph can only ever be drawn where the text already is. A negative space
 * can move the cursor back, which is the whole trick behind a picture in a GUI title:
 * the title starts eight pixels inside the window, so a plate that has to cover the
 * window edge is preceded by a step back and followed by a step forward, and the title
 * text lands in its usual place on top of it.
 *
 * <p>The pack registers powers of two from one to 256 pixels in both directions, so any
 * offset in that range is a short sequence of them. Registered in the default font as
 * well as in {@code guiok:hud}, because a container title has no way to choose a font.
 */
public final class PackSpaces {
    /** Largest offset the pack can express, in pixels, in either direction. */
    public static final int LIMIT = 511;

    private static final int[] STEPS = {256, 128, 64, 32, 16, 8, 4, 2, 1};
    private static final Map<Integer, String> POSITIVE = steps(0xe300);
    private static final Map<Integer, String> NEGATIVE = steps(0xe310);

    private PackSpaces() {
    }

    private static Map<Integer, String> steps(int firstCodePoint) {
        Map<Integer, String> steps = new LinkedHashMap<>();
        int[] widths = {1, 2, 4, 8, 16, 32, 64, 128, 256};
        for (int index = 0; index < widths.length; index++) {
            steps.put(widths[index], String.valueOf((char) (firstCodePoint + index)));
        }
        return Map.copyOf(steps);
    }

    /**
     * A string that shifts the cursor by {@code pixels}, negative to the left.
     *
     * @throws IllegalArgumentException when the offset is beyond {@link #LIMIT}; a
     *     silently clipped offset would misplace the picture rather than report the
     *     mistake, and a GUI drawn half a window off is nobody's intent
     */
    public static String of(int pixels) {
        if (pixels < -LIMIT || pixels > LIMIT) {
            throw new IllegalArgumentException(
                    "Space offset " + pixels + " is beyond ±" + LIMIT + " pixels");
        }
        if (pixels == 0) {
            return "";
        }
        Map<Integer, String> steps = pixels > 0 ? POSITIVE : NEGATIVE;
        int remaining = Math.abs(pixels);
        StringBuilder composed = new StringBuilder();
        for (int step : STEPS) {
            while (remaining >= step) {
                composed.append(steps.get(step));
                remaining -= step;
            }
        }
        return composed.toString();
    }

    /** The advances the resource pack must declare, for the build to verify against. */
    public static Map<String, Integer> advances() {
        Map<String, Integer> advances = new LinkedHashMap<>();
        POSITIVE.forEach((pixels, glyph) -> advances.put(glyph, pixels));
        NEGATIVE.forEach((pixels, glyph) -> advances.put(glyph, -pixels));
        return Map.copyOf(advances);
    }
}
