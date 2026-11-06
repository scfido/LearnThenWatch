package local.mytv.english;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() { super.onResume(); render(); }
    private void render() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(80, 35, 80, 35);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(15, 23, 42));
        TextView title = new TextView(this);
        title.setText("看电视 · 学英语"); title.setTextSize(30); title.setTextColor(Color.WHITE);
        layout.addView(title);
        TextView details = new TextView(this);
        boolean enabled = getSharedPreferences("learning", 0).getBoolean("enabled", true);
        details.setText("云视听小电视累计播放 2 分钟，答对一题继续观看。\n暂停和浏览页面不计时。24 道基础英语题随机出现。\n监控：" + (enabled ? "开启" : "关闭") + "　服务：" + (LearningService.instance != null ? "已连接" : "未开启")
                + "\n需要在系统无障碍设置中开启「英语答题提醒」。\n仅在电视本机处理，不上传屏幕或答题记录。");
        details.setTextSize(18); details.setTextColor(Color.LTGRAY); details.setPadding(0, 18, 0, 18);
        layout.addView(details);
        button(layout, enabled ? "关闭提醒" : "开启提醒", () -> {
            getSharedPreferences("learning", 0).edit().putBoolean("enabled", !enabled).apply();
            if (LearningService.instance != null) LearningService.instance.settingsChanged(); render();
        });
        button(layout, "电视系统设置", () -> {
            Intent settings = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
            try { startActivity(settings != null ? settings : new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        button(layout, "预览一道题", () -> {
            if (LearningService.instance != null) LearningService.instance.preview();
            else android.widget.Toast.makeText(this, "请先开启英语答题提醒服务", 0).show();
        });
        setContentView(layout);
    }
    private void button(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(19);
        button.setPadding(16, 0, 16, 0);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(760, 96);
        size.topMargin = 8;
        layout.addView(button, size);
    }
}
