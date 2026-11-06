package local.mytv.bilistart;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

public final class StartupActivity extends Activity {
    private final Handler handler = new Handler();
    private SharedPreferences preferences;
    private int boot;
    private long deadline;
    private final Runnable launch = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            UserManager users = (UserManager) getSystemService(USER_SERVICE);
            if (!users.isUserUnlocked() || SystemClock.elapsedRealtime() < 45000) {
                if (SystemClock.elapsedRealtime() < deadline) handler.postDelayed(this, 1000);
                else finish();
                return;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("com.xiaodianshi.tv.yst",
                        "com.xiaodianshi.tv.yst.ui.main.MainActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                preferences.edit().putInt("completed_boot", boot).commit();
                Log.i("BiliStartup", "Opened Bilibili TV for boot " + boot);
            } catch (RuntimeException error) {
                Log.e("BiliStartup", "Could not open Bilibili TV", error);
            }
            finish();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = createDeviceProtectedStorageContext().getSharedPreferences("startup", MODE_PRIVATE);
        boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0 || preferences.getInt("completed_boot", -2) == boot
                || SystemClock.elapsedRealtime() > 180000) {
            finish();
            return;
        }
        TextView message = new TextView(this);
        message.setText("正在打开云视听小电视…");
        message.setTextSize(24);
        message.setGravity(Gravity.CENTER);
        setContentView(message);
        deadline = SystemClock.elapsedRealtime() + 120000;
        handler.post(launch);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(launch);
        super.onDestroy();
    }
}
