package scfido.learnthenwatch.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonitorRecoveryPolicyTest {
    private static final String OWN = "scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.PlaybackAccessService";
    private static final String SHORT = "scfido.learnthenwatch.tv/.PlaybackAccessService";
    private static final String OTHER = "reader/.ReaderService";
    private final Fake access = new Fake();
    private final MonitorRecoveryPolicy policy = new MonitorRecoveryPolicy(access, OWN);

    static final class Fake implements MonitorRecoveryPolicy.Access {
        boolean allowed = true, connected, enabled = true, reject;
        String list = OTHER;
        int writes, enableWrites;
        public boolean allowed() { return allowed; }
        public boolean connected() { return connected; }
        public String services() { return list; }
        public void services(String value) {
            if (reject) throw new SecurityException("denied");
            list = value; writes++;
        }
        public boolean accessibilityEnabled() { return enabled; }
        public void enableAccessibility() { enabled = true; enableWrites++; }
    }

    @Test public void addsOwnServiceAndKeepsOthers() {
        policy.restore();
        assertEquals(OTHER + ":" + OWN, access.list);
        assertEquals(0, access.enableWrites);
        policy.restore();
        assertEquals(1, access.writes);
    }
    @Test public void shortNameIsAlreadyAuthorized() {
        access.list = OTHER + ":" + SHORT;
        policy.restore();
        assertEquals(0, access.writes);
    }
    @Test public void missingListsDoNotCreateNullComponents() {
        for (String list : new String[]{null, "", "null", ":null:"}) {
            access.list = list;
            policy.restore();
            assertEquals(OWN, access.list);
        }
    }
    @Test public void recoveryEnablesGlobalSwitchOnlyWhenNeeded() {
        access.enabled = false;
        policy.restore(); policy.restore();
        assertEquals(1, access.enableWrites);
    }
    @Test public void noPermissionOrParentControlMeansNoWrites() {
        access.allowed = false; access.enabled = false;
        policy.restore();
        assertFalse(policy.detachStalled());
        assertEquals(OTHER, access.list);
        assertEquals(0, access.enableWrites);
    }
    @Test public void connectedServiceIsNeverDisturbed() {
        access.connected = true;
        policy.restore();
        assertFalse(policy.detachStalled());
        assertEquals(0, access.writes);
    }
    @Test public void rebindPreservesConcurrentChangesAndNeverTogglesGlobalSwitch() {
        access.list = OTHER + ":" + SHORT + ":" + OWN;
        assertTrue(policy.detachStalled());
        assertEquals(OTHER, access.list);
        access.list += ":new.reader/.Service";
        policy.restore();
        assertEquals(OTHER + ":new.reader/.Service:" + OWN, access.list);
        assertEquals(0, access.enableWrites);
    }
    @Test public void delayedRestoreRechecksParentChoiceAndPermission() {
        access.list = OWN;
        assertTrue(policy.detachStalled());
        access.allowed = false;
        policy.restore();
        assertEquals("", access.list);
    }
    @Test public void debugAndProductionServicesAreDistinct() {
        String debug = "scfido.learnthenwatch.tv.debug/scfido.learnthenwatch.tv.PlaybackAccessService";
        MonitorRecoveryPolicy debugPolicy = new MonitorRecoveryPolicy(access, debug);
        access.list = OWN;
        debugPolicy.restore();
        assertEquals(OWN + ":" + debug, access.list);
        assertTrue(debugPolicy.detachStalled());
        assertEquals(OWN, access.list);
    }
    @Test public void failedWriteDoesNotClaimAuthorizationOrEnableGlobalSwitch() {
        access.reject = true; access.enabled = false;
        try { policy.restore(); fail("expected denied write"); }
        catch (SecurityException expected) { }
        assertEquals(OTHER, access.list);
        assertEquals(0, access.enableWrites);
    }
}
