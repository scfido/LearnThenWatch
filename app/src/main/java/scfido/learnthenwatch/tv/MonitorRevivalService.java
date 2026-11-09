package scfido.learnthenwatch.tv;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/** System rebind is an entry point after some firmwares stop the app.
 * Notification content is never requested, read, stored or logged.
 */
public final class MonitorRevivalService extends NotificationListenerService {
    private boolean registered;
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { MonitorRecovery.request(context); }
    };
    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        registerReceiver(wake, filter);
        registered = true;
        MonitorRecovery.request(this);
    }
    @Override public void onListenerConnected() {
        Log.i("LearnThenWatch", "Monitor revival listener connected");
        MonitorRecovery.request(this);
    }
    @Override public void onListenerDisconnected() {
        requestRebind(new ComponentName(this, MonitorRevivalService.class));
    }
    @Override public void onDestroy() {
        if (registered) unregisterReceiver(wake);
        super.onDestroy();
    }
}
