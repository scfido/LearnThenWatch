package scfido.learnthenwatch.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViewingVolumeFadeTest {
    private static final class Device implements ViewingVolumeFade.Access {
        int level = 12, writes;
        boolean muted, supported = true, failSave;
        ViewingVolumeFade.RestorePoint point;
        public ViewingVolumeFade.State current() { return new ViewingVolumeFade.State(level, muted); }
        public boolean supported() { return supported; }
        public ViewingVolumeFade.RestorePoint pending() { return point; }
        public void save(ViewingVolumeFade.RestorePoint next) {
            if (failSave) throw new IllegalStateException("storage failure");
            point = next;
        }
        public void clear() { point = null; }
        public void setLevel(int value) {
            assertNotNull("every system write needs a durable recovery point", point);
            writes++; level = value; muted = value == 0;
        }
        public void unmute() { muted = false; }
    }
    @Test public void finalThreeSecondsFadeAndRestoreOriginal() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(30000, true); f.update(3001, true); assertEquals(0, d.writes);
        f.update(3000, true); f.update(2250, true); assertEquals(9, d.level);
        f.update(1500, true); assertEquals(6, d.level);
        f.update(750, true); assertEquals(3, d.level);
        f.update(0, true); assertEquals(0, d.level);
        f.cancel(); assertEquals(12, d.level); assertFalse(d.muted); assertNull(d.point);
        int writes = d.writes; f.cancel(); assertEquals(writes, d.writes);
    }
    @Test public void pauseRestoresAndResumeKeepsTheOriginalBaseline() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(2000, true); assertEquals(8, d.level);
        f.update(2000, false); assertEquals(12, d.level);
        f.update(1500, true); assertEquals(6, d.level);
        f.cancel(); assertEquals(12, d.level);
    }
    @Test public void manualVolumeOrMuteCancelsFadeWithoutOverwritingUser() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(1500, true); d.level = 7;
        f.update(1000, true); f.update(100, true); f.cancel();
        assertEquals(7, d.level); assertNull(d.point);
        f.update(30000, true); f.update(1500, true); d.level = 0; d.muted = true;
        f.update(1000, true); f.cancel(); assertTrue(d.muted); assertEquals(0, d.level);
    }
    @Test public void originallyMutedZeroAndUnsupportedDevicesAreUntouched() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        d.muted = true; f.update(1000, true); f.cancel(); assertEquals(0, d.writes); assertTrue(d.muted);
        d.muted = false; d.level = 0; f.update(1000, true); assertEquals(0, d.writes);
        d.level = 12; d.supported = false; f.update(1000, true); assertEquals(0, d.writes);
    }
    @Test public void processRestartRecoversBothSidesOfInterruptedSystemWrite() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(2000, true); f.update(1000, true);
        new ViewingVolumeFade(d).restore(); assertEquals(12, d.level); assertNull(d.point);
        d.point = new ViewingVolumeFade.RestorePoint(12, new ViewingVolumeFade.State(8, false), 4);
        d.level = 8; new ViewingVolumeFade(d).restore(); assertEquals(12, d.level);
        d.point = new ViewingVolumeFade.RestorePoint(12, new ViewingVolumeFade.State(4, false), 0);
        d.level = 0; d.muted = true; new ViewingVolumeFade(d).restore();
        assertEquals(12, d.level); assertFalse(d.muted);
    }
    @Test public void pendingRecoveryDoesNotOverwriteLaterManualVolume() {
        Device d = new Device(); new ViewingVolumeFade(d).update(1500, true);
        d.level = 10; new ViewingVolumeFade(d).restore(); assertEquals(10, d.level); assertNull(d.point);
    }
    @Test public void watchdogRestoresAndDoesNotRestartTheSameFade() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(1500, true); f.timeout(); assertEquals(12, d.level);
        f.update(500, true); assertEquals(12, d.level);
        f.update(30000, true); f.update(1500, true); assertEquals(6, d.level);
    }
    @Test public void storageFailureNeverChangesVolume() {
        Device d = new Device(); d.failSave = true;
        try { new ViewingVolumeFade(d).update(1500, true); fail("storage failure expected"); }
        catch (IllegalStateException expected) { assertEquals(0, d.writes); assertEquals(12, d.level); }
    }
    @Test public void transientWindowPausePreservesManualOverrideBeforeFirstWrite() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(3000, true); d.level = 15;
        f.restore(); f.update(1000, true); assertEquals(15, d.level); assertNull(d.point);
    }
    @Test public void transientWindowPauseDoesNotRestartAfterManualOverride() {
        Device d = new Device(); ViewingVolumeFade f = new ViewingVolumeFade(d);
        f.update(1500, true); d.level = 10;
        f.restore(); f.update(500, true); assertEquals(10, d.level); assertNull(d.point);
    }
}
