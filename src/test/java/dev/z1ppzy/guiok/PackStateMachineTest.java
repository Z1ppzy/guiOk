package dev.z1ppzy.guiok;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
