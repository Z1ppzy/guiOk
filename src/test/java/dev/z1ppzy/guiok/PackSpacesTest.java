/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackSpacesTest {
    @Test
    void zeroCostsNothing() {
        assertEquals("", PackSpaces.of(0));
    }

    @Test
    void singleStepsAreOneCharacter() {
        assertEquals("\ue303", PackSpaces.of(8));
        assertEquals("\ue313", PackSpaces.of(-8));
        assertEquals("\ue308", PackSpaces.of(256));
    }

    /** 169 = 128 + 32 + 8 + 1, largest step first, so the sequence stays short. */
    @Test
    void offsetsComposeFromPowersOfTwo() {
        assertEquals("\ue317\ue315\ue313\ue310", PackSpaces.of(-169));
        assertEquals(4, PackSpaces.of(169).length());
    }

    @Test
    void everyOffsetInRangeIsExpressible() {
        for (int pixels = -PackSpaces.LIMIT; pixels <= PackSpaces.LIMIT; pixels++) {
            String composed = PackSpaces.of(pixels);
            assertTrue(pixels == 0 || !composed.isEmpty());
        }
    }

    /**
     * Clipping an offset would move a picture somewhere else on the screen rather than
     * report the mistake, so the limit is a failure and not a maximum.
     */
    @Test
    void offsetsBeyondTheRangeFail() {
        assertThrows(IllegalArgumentException.class, () -> PackSpaces.of(PackSpaces.LIMIT + 1));
        assertThrows(IllegalArgumentException.class, () -> PackSpaces.of(-PackSpaces.LIMIT - 1));
    }

    @Test
    void advancesCoverBothDirections() {
        assertEquals(18, PackSpaces.advances().size());
        assertEquals(8, PackSpaces.advances().get("\ue303"));
        assertEquals(-8, PackSpaces.advances().get("\ue313"));
    }
}
