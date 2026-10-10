package combatant.client.util.block.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MiningTargetLockTest {
    @Test void candidateCannotReplaceLockedTargetDuringAlignmentOrBreaking() {
        MiningTargetLock<String> lock = new MiningTargetLock<>();
        assertEquals(MiningTargetLock.Phase.IDLE, lock.phase());
        assertTrue(lock.acquire("stone@0,64,0"));
        assertEquals(MiningTargetLock.Phase.ALIGNING, lock.phase());
        assertFalse(lock.acquire("stone@1,64,0"));
        assertEquals("stone@0,64,0", lock.target());
        lock.markBreaking("stone@1,64,0");
        assertEquals(MiningTargetLock.Phase.ALIGNING, lock.phase());
        lock.markBreaking("stone@0,64,0");
        assertEquals(MiningTargetLock.Phase.BREAKING, lock.phase());
        assertFalse(lock.acquire("stone@1,64,0"));
        assertEquals("stone@0,64,0", lock.target());
        lock.release();
        assertEquals(MiningTargetLock.Phase.IDLE, lock.phase());
        assertTrue(lock.acquire("stone@1,64,0"));
    }

    @Test void invalidCandidatesNeverAcquireAndReleaseIsIdempotent() {
        MiningTargetLock<String> lock = new MiningTargetLock<>();
        assertFalse(lock.acquire(null));
        lock.markBreaking("ghost");
        assertFalse(lock.isLocked());
        lock.release();
        lock.release();
        assertEquals(MiningTargetLock.Phase.IDLE, lock.phase());
    }
}
