package scfido.learnthenwatch.tv;

import android.app.Application;

/** Recover an interrupted fade before any activity, service, receiver or boot recovery runs. */
public final class LearnThenWatchApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        PlaybackVolumeFade.recover(this);
    }
}
