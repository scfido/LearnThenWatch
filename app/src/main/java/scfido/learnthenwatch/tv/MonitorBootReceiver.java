package scfido.learnthenwatch.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Post-unlock boot and upgrades restore monitoring behind the current TV screen. */
public final class MonitorBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            PendingResult pending = goAsync();
            MonitorRecovery.request(context, pending::finish);
        }
    }
}
