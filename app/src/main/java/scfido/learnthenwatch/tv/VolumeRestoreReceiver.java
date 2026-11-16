package scfido.learnthenwatch.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Independent deadline: starts the app process to restore volume after an interrupted fade. */
public final class VolumeRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { PlaybackVolumeFade.recover(context); }
}
