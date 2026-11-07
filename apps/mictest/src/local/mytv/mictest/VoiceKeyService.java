package local.mytv.mictest;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;

/** Only consumes voice keys during an explicitly started experimental recording. */
public final class VoiceKeyService extends AccessibilityService {
    static volatile boolean connected;
    static volatile boolean armed;
    static volatile int observed, blocked;
    static volatile String lastKey = "尚未收到按键";
    static volatile boolean autoDismiss;
    static volatile String dismissState = "未尝试收起";
    private final Handler handler = new Handler();
    private boolean dismissPending;
    private boolean dismissAttempted;
    private CharSequence currentWindowPackage;
    private final HashSet<String> held = new HashSet<>();

    @Override protected void onServiceConnected() {
        instance = this;
        connected = true;
        Log.i("MicTest", "VoiceKeyService connected; flags=" + getServiceInfo().flags);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        InputDevice device = event.getDevice();
        // This TV reports its physical Xiaomi voice button as UNKNOWN, scan 63.
        // Restrict the workaround to the observed remote, not every UNKNOWN key.
        boolean xiaomiVoice = code == KeyEvent.KEYCODE_UNKNOWN && event.getScanCode() == 63
                && device != null && device.getVendorId() == 0x2717 && device.getProductId() == 0x32b8;
        boolean voice = code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST
                || code == KeyEvent.KEYCODE_SEARCH || xiaomiVoice;
        String identity = event.getDeviceId() + ":" + code + ":" + event.getScanCode();
        boolean consume = held.contains(identity) || (armed && voice);
        if (armed || consume) {
            observed++;
            if (consume) blocked++;
            lastKey = KeyEvent.keyCodeToString(code) + "/" + event.getAction() + " 扫描码=" + event.getScanCode() + (consume ? " 已拦截" : " 放行");
            Log.i("MicTest", "key-filter: " + lastKey + ", scan=" + event.getScanCode()
                    + ", repeat=" + event.getRepeatCount() + ", armed=" + armed
                    + ", device=" + (device == null ? "null" : device.getName() + "/" + device.getVendorId() + "/" + device.getProductId()));
        }
        if (consume) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) held.add(identity);
            else if (event.getAction() == KeyEvent.ACTION_UP) held.remove(identity);
        }
        return consume;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        currentWindowPackage = event.getPackageName();
        if (armed || autoDismiss) Log.i("MicTest", "window during voice test: " + currentWindowPackage);
        if (autoDismiss && !dismissAttempted && !dismissPending
                && "com.xiaomi.voicecontrol".contentEquals(currentWindowPackage == null ? "" : currentWindowPackage)) {
            dismissPending = true;
            dismissState = "检测到小爱，等待收起";
            handler.postDelayed(() -> {
                dismissPending = false;
                if (!autoDismiss || dismissAttempted) return;
                if (!"com.xiaomi.voicecontrol".contentEquals(currentWindowPackage == null ? "" : currentWindowPackage)) {
                    dismissState = "窗口已切换，取消收起"; return;
                }
                dismissAttempted = true;
                boolean sent = performGlobalAction(GLOBAL_ACTION_BACK);
                dismissState = sent ? "已发送一次返回，须确认音频是否继续" : "发送返回失败";
                Log.i("MicTest", "dismiss assistant: " + dismissState);
            }, 350);
        }
    }
    static volatile VoiceKeyService instance;
    static void beginExperiment(boolean filter, boolean dismiss) {
        armed = filter; autoDismiss = dismiss; dismissState = "未尝试收起";
        VoiceKeyService service = instance;
        if (service != null) {
            service.handler.removeCallbacksAndMessages(null);
            service.dismissPending = false; service.dismissAttempted = false;
            service.currentWindowPackage = null;
        }
    }
    static void endExperiment() {
        armed = false; autoDismiss = false;
        VoiceKeyService service = instance;
        if (service != null) { service.handler.removeCallbacksAndMessages(null); service.dismissPending = false; }
    }
    @Override public void onInterrupt() { Log.i("MicTest", "VoiceKeyService interrupted"); }
    @Override public void onDestroy() {
        connected = false; endExperiment(); instance = null; held.clear();
        Log.i("MicTest", "VoiceKeyService disconnected");
        super.onDestroy();
    }
}
