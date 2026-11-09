package scfido.learnthenwatch.tv;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

/** Bounded background recovery; never opens an activity or directly starts accessibility. */
final class MonitorRecovery {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final long[] DELAYS = {5000, 5000, 10000, 15000, 30000, 60000};
    private static MonitorRecovery active;
    private final MonitorRecoveryPolicy policy;
    private int attempt;
    private boolean rebound;
    private final Runnable check = this::check;

    private MonitorRecovery(Context context) {
        policy = new MonitorRecoveryPolicy(new MonitorRecoveryPolicy.Access() {
            public boolean allowed() {
                UserManager users = context.getSystemService(UserManager.class);
                if (users == null || !users.isUserUnlocked()
                        || context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                        != PackageManager.PERMISSION_GRANTED) return false;
                return LearningRepository.monitorRecoveryEnabled(context);
            }
            public boolean connected() { return PlaybackAccessService.isConnected(); }
            public String services() { return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); }
            public void services(String value) {
                if (!Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value))
                    throw new IllegalStateException("System refused accessibility list write");
            }
            public boolean accessibilityEnabled() { return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1; }
            public void enableAccessibility() {
                if (!Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1))
                    throw new IllegalStateException("System refused accessibility enable");
            }
        }, new ComponentName(context, PlaybackAccessService.class).flattenToString());
    }

    static void request(Context context) {
        request(context, () -> {});
    }

    static void request(Context context, Runnable received) {
        Context app = context.getApplicationContext();
        HANDLER.post(() -> {
            try {
                if (active != null) return;
                active = new MonitorRecovery(app);
                active.check();
            } finally { received.run(); }
        });
    }

    private void check() {
        if (PlaybackAccessService.isConnected()) { finish(); return; }
        try {
            policy.restore();
            // Give Android 20 seconds before trying one own-service rebind.
            if (attempt >= 3 && !rebound && policy.detachStalled()) {
                rebound = true;
                // Independent of retry/listener lifetime; rereads permission, parent settings
                // and the latest service list, preserving changes made during the delay.
                HANDLER.postDelayed(() -> {
                    try { policy.restore(); }
                    catch (RuntimeException error) { Log.w("LearnThenWatch", "Monitor rebind restore failed", error); }
                }, 1500);
            }
        } catch (RuntimeException error) {
            Log.w("LearnThenWatch", "Background monitor recovery failed", error);
        }
        if (attempt < DELAYS.length) HANDLER.postDelayed(check, DELAYS[attempt++]);
        else {
            Log.i("LearnThenWatch", "Monitor recovery window ended; connected=" + PlaybackAccessService.isConnected());
            finish();
        }
    }
    private void finish() {
        HANDLER.removeCallbacks(check);
        if (active == this) active = null;
    }
}
