package scfido.learnthenwatch.tv;

import android.app.Activity;
import android.os.Bundle;

/** Debug-only, no launcher entry: deliberately leave a fade pending for process-death tests. */
public final class VolumeFadeProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("fade", false)) new PlaybackVolumeFade(this).update(1500, true);
        else PlaybackVolumeFade.recover(this);
        finish();
    }
}
