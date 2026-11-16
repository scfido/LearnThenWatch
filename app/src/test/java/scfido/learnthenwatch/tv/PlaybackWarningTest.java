package scfido.learnthenwatch.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackWarningTest {
    @Test public void warnsOnlyDuringFinalThirtySeconds() {
        assertFalse(PlaybackTransitionOverlay.shouldWarn(30001));
        assertTrue(PlaybackTransitionOverlay.shouldWarn(30000));
        assertTrue(PlaybackTransitionOverlay.shouldWarn(10000));
        assertTrue(PlaybackTransitionOverlay.shouldWarn(1));
        assertFalse(PlaybackTransitionOverlay.shouldWarn(0));
        assertFalse(PlaybackTransitionOverlay.shouldWarn(-1));
    }
    @Test public void dailyLimitCanTriggerWarningBeforeWalletRunsOut() {
        assertEquals(9000, PlaybackTransitionOverlay.remaining(600000, 9000));
        assertEquals(8000, PlaybackTransitionOverlay.remaining(8000, 600000));
        assertEquals(0, PlaybackTransitionOverlay.remaining(600000, 0));
        assertEquals(0, PlaybackTransitionOverlay.remaining(-1000, 600000));
    }
}
