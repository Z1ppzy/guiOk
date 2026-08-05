/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackStateMachineTest {
    @Test
    void followsSuccessfulClientLifecycle() {
        PackState state = PackState.REQUESTED;
        state = PackStateMachine.transition(state, PackSignal.ACCEPTED);
        assertEquals(PackState.ACCEPTED, state);
        state = PackStateMachine.transition(state, PackSignal.DOWNLOADED);
        assertEquals(PackState.DOWNLOADED, state);
        state = PackStateMachine.transition(state, PackSignal.SUCCESSFULLY_LOADED);
        assertEquals(PackState.APPLIED, state);
        assertTrue(state.usesPackedTitle());
    }

    @Test
    void terminalFailureCannotBeForgedIntoSuccessByLateStatus() {
        PackState state = PackStateMachine.transition(PackState.REQUESTED, PackSignal.DECLINED);
        state = PackStateMachine.transition(state, PackSignal.SUCCESSFULLY_LOADED);

        assertEquals(PackState.DECLINED, state);
        assertTrue(state.failed());
    }

    @Test
    void discardedAppliedPackFallsBack() {
        PackState state = PackStateMachine.transition(PackState.APPLIED, PackSignal.DISCARDED);

        assertEquals(PackState.DISCARDED, state);
        assertTrue(state.failed());
    }

    /**
     * Geyser answers a required pack with the full success sequence on the client's behalf, so
     * a Bedrock player would otherwise be talked into the packed title their client cannot draw.
     */
    @Test
    void aBedrockClientStaysBedrockWhateverTheStatusClaims() {
        PackState state = PackState.BEDROCK;
        for (PackSignal signal : PackSignal.values()) {
            state = PackStateMachine.transition(state, signal);
            assertEquals(PackState.BEDROCK, state, "flipped by " + signal);
        }
        assertFalse(state.usesPackedTitle());
        assertFalse(state.failed(), "a client that cannot use packs has not failed at anything");
    }

    @Test
    void everyDownloadFailureMapsToFailed() {
        assertEquals(
                PackState.FAILED,
                PackStateMachine.transition(PackState.REQUESTED, PackSignal.INVALID_URL));
        assertEquals(
                PackState.FAILED,
                PackStateMachine.transition(PackState.REQUESTED, PackSignal.FAILED_DOWNLOAD));
        assertEquals(
                PackState.FAILED,
                PackStateMachine.transition(PackState.DOWNLOADED, PackSignal.FAILED_RELOAD));
    }
}
