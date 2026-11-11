package scfido.learnthenwatch.tv;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/** Hosts the playback gate below system UI, without a privileged accessibility overlay. */
public final class LearningGateActivity extends Activity {
    private PlaybackAccessService owner;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setTitle("LearnThenWatchGate");
        bindGate();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); bindGate();
    }

    private void bindGate() {
        owner = PlaybackAccessService.instance;
        if (owner == null || !owner.attachGateActivity(this, getIntent().getLongExtra("gateWindow", -1)))
            finishAndRemoveTask();
    }

    void showGate(View gate) {
        if (gate.getParent() instanceof ViewGroup) ((ViewGroup) gate.getParent()).removeView(gate);
        setContentView(gate);
        gate.requestFocus(View.FOCUS_DOWN);
    }

    void releaseGate(View gate) {
        if (gate != null && gate.getParent() instanceof ViewGroup) ((ViewGroup) gate.getParent()).removeView(gate);
    }

    @Override protected void onStop() {
        super.onStop();
        if (owner != null && !isChangingConfigurations()) owner.gateActivityStopped(this);
    }

    @Override protected void onDestroy() {
        if (owner != null) owner.gateActivityDestroyed(this, isChangingConfigurations());
        super.onDestroy();
    }
}
