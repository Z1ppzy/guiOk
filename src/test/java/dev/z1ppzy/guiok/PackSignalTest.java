/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.junit.jupiter.api.Test;

class PackSignalTest {
    /**
     * Guards the enum against Adventure: the pack callback maps status names by value, so a
     * status added upstream would silently stop driving the sidebar. When this fails, add the
     * missing constant to {@link PackSignal} and give it a transition in
     * {@link PackStateMachine}.
     */
    @Test
    void everyAdventureStatusHasASignal() {
        for (ResourcePackStatus status : ResourcePackStatus.values()) {
            assertTrue(
                    PackSignal.from(status.name()).isPresent(),
                    "No PackSignal for Adventure status " + status.name());
        }
        assertEquals(ResourcePackStatus.values().length, PackSignal.values().length);
    }

    @Test
    void everySignalDrivesTheStateMachine() {
        for (PackSignal signal : PackSignal.values()) {
            assertTrue(
                    PackStateMachine.transition(PackState.REQUESTED, signal) != PackState.REQUESTED,
                    "Signal " + signal + " leaves a requested pack stuck");
        }
    }

    @Test
    void unknownStatusIsIgnoredInsteadOfThrowing() {
        assertEquals(Optional.empty(), PackSignal.from("FUTURE_ADVENTURE_STATUS"));
        assertEquals(Optional.empty(), PackSignal.from(""));
        assertEquals(Optional.empty(), PackSignal.from(null));
        assertEquals(Optional.empty(), PackSignal.from("accepted"));
    }

    @Test
    void knownStatusResolvesToTheMatchingSignal() {
        assertEquals(
                Optional.of(PackSignal.SUCCESSFULLY_LOADED),
                PackSignal.from(ResourcePackStatus.SUCCESSFULLY_LOADED.name()));
        assertEquals(
                Optional.of(PackSignal.DECLINED),
                PackSignal.from(ResourcePackStatus.DECLINED.name()));
    }
}
