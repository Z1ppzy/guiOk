/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuiOkItemServiceTest {
    @Test
    void splitsAnAmountIntoFullStacksPlusRemainder() {
        assertArrayEquals(new int[] {64, 64, 12}, GuiOkItemService.stackSizes(140, 64));
        assertArrayEquals(new int[] {64}, GuiOkItemService.stackSizes(64, 64));
        assertArrayEquals(new int[] {1}, GuiOkItemService.stackSizes(1, 64));
        assertArrayEquals(new int[] {64, 1}, GuiOkItemService.stackSizes(65, 64));
    }

    @Test
    void handlesItemsThatDoNotStack() {
        assertArrayEquals(new int[] {1, 1, 1}, GuiOkItemService.stackSizes(3, 1));
    }

    @Test
    void alwaysHandsOutExactlyTheRequestedAmount() {
        for (int maxStackSize : new int[] {1, 16, 64, 99}) {
            for (int amount = 1; amount <= GuiOkItemService.MAX_GIVE_AMOUNT; amount += 37) {
                int[] sizes = GuiOkItemService.stackSizes(amount, maxStackSize);
                int total = 0;
                for (int size : sizes) {
                    assertTrue(size >= 1 && size <= maxStackSize, "Bad stack size " + size);
                    total += size;
                }
                assertEquals(amount, total, "Lost items splitting " + amount + '/' + maxStackSize);
            }
        }
    }

    /**
     * The bound keeps a caller from asking for Integer.MAX_VALUE items, which would allocate
     * tens of millions of stacks before the inventory hands almost all of them straight back.
     */
    @Test
    void boundsAllocationForTheLargestAllowedRequest() {
        assertEquals(100, GuiOkItemService.stackSizes(GuiOkItemService.MAX_GIVE_AMOUNT, 64).length);
        assertTrue(GuiOkItemService.MAX_GIVE_AMOUNT > 0);
    }
}
