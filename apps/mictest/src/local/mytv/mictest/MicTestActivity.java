package local.mytv.mictest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public final class MicTestActivity extends Activity {
    private static final String TAG = "MicTest";
    private final Handler handler = new Handler();
    private final ArrayList<AudioDeviceInfo> devices = new ArrayList<>();
    private TextView status, inventory;
    private Button inputButton, sourceButton, startButton, playButton, filterButton, dismissButton;
    private int selected = -1;
    private boolean recognitionSource = true;
    private boolean filterEnabled = false;
    private boolean dismissEnabled = true;
    private volatile boolean recording;
    private volatile boolean busy;
    private volatile AudioRecord recorder;
    private MediaPlayer player;
    private File wav;
    private boolean ready, destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wav = new File(getFilesDir(), "last-recording.wav");
        ready = wav.exists() && wav.length() > 44;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(50, 20, 50, 20);
        page.setBackgroundColor(Color.rgb(15, 23, 42));
        text(page, "电视 / 遥控器麦克风测试", 26);
        text(page, "请读：I like reading English books.\n每次最多录音 12 秒；先不按语音键读一遍，再按住语音键读一遍。\n请暂停电视声音。录音仅保存在本机，下次覆盖；未接入识别或评分。", 17);
        inputButton = button(page, "", () -> {
            if (busy) return;
            selected++;
            if (selected >= devices.size()) selected = -1;
            updateSelection();
        });
        sourceButton = button(page, "音源：VOICE_RECOGNITION（语音输入）", () -> {
            if (busy) return;
            recognitionSource = !recognitionSource;
            sourceButton.setText(recognitionSource ? "音源：VOICE_RECOGNITION（语音输入）" : "音源：MIC（普通麦克风）");
        });
        filterButton = button(page, "语音键拦截实验：关（对照测试）", () -> {
            if (busy) return;
            filterEnabled = !filterEnabled;
            filterButton.setText("语音键拦截实验：" + (filterEnabled ? "开（仅录音时）" : "关（对照测试）"));
            report("拦截服务：" + (VoiceKeyService.connected ? "已连接" : "未连接，请开启「麦克风测试语音键拦截」无障碍服务"));
        });
        dismissButton = button(page, "自动收起小爱：开（每次录音最多一次）", () -> {
            if (busy) return;
            dismissEnabled = !dismissEnabled;
            dismissButton.setText("自动收起小爱：" + (dismissEnabled ? "开（每次录音最多一次）" : "关（对照测试）"));
            report("收起小爱后请继续朗读，回放确认录音是否中断。");
        });
        startButton = button(page, "开始录音（12 秒）", () -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                report("请允许麦克风权限，再点开始录音。");
            } else startRecording();
        });
        button(page, "停止录音 / 停止回放", () -> { stopRecording(); stopPlayer(); });
        playButton = button(page, "回放最近一次录音", this::play);
        button(page, "刷新输入设备", () -> { if (!busy) refreshDevices(); });
        status = text(page, "尚未录音。先开始录音，再按住遥控器语音键。", 17);
        inventory = text(page, "", 15);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        refreshDevices();
        startButton.requestFocus();
    }

    private TextView text(LinearLayout page, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        page.addView(view); return view;
    }
    private Button button(LinearLayout page, String value, Runnable action) {
        Button view = new Button(this);
        view.setText(value); view.setTextSize(17); view.setMinHeight(0); view.setPadding(16, 0, 16, 0);
        view.setOnClickListener(v -> action.run());
        page.addView(view, new LinearLayout.LayoutParams(-1, 72));
        return view;
    }
    private static String describe(AudioDeviceInfo device) {
        if (device == null) return "尚无路由";
        String kind;
        switch (device.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: kind = "电视内置麦克风"; break;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: kind = "有线/厂商遥控器输入"; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: kind = "蓝牙 SCO"; break;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: kind = "USB"; break;
            default: kind = "其他";
        }
        return "#" + device.getId() + " " + device.getProductName() + " / " + kind + " (type=" + device.getType() + ")";
    }
    private void refreshDevices() {
        devices.clear(); selected = -1;
        StringBuilder info = new StringBuilder("系统输入设备（出现不代表可录音）：\n");
        for (AudioDeviceInfo device : ((AudioManager)getSystemService(AUDIO_SERVICE)).getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            devices.add(device); info.append(describe(device)).append('\n');
        }
        inventory.setText(info.toString());
        Log.i(TAG, info.toString());
        updateSelection();
    }
    private void updateSelection() {
        inputButton.setText("输入：" + (selected < 0 ? "系统默认（点此切换）" : describe(devices.get(selected))));
    }
    private void report(String message) {
        Log.i(TAG, message);
        if (!destroyed) status.setText(message + "\n拦截服务：" + (VoiceKeyService.connected ? "已连接" : "未连接")
                + "；拦截次数=" + VoiceKeyService.blocked + "；" + VoiceKeyService.lastKey
                + "\n收起实验：" + VoiceKeyService.dismissState);
    }
    private void startRecording() {
        if (busy) return;
        if ((filterEnabled || dismissEnabled) && !VoiceKeyService.connected) {
            report("实验未开始：请先开启「麦克风测试语音键拦截」无障碍服务，或关闭两个实验开关做对照。"); return;
        }
        stopPlayer(); busy = true; recording = true; ready = false;
        VoiceKeyService.observed = 0; VoiceKeyService.blocked = 0;
        VoiceKeyService.lastKey = "尚未收到按键";
        VoiceKeyService.beginExperiment(filterEnabled, dismissEnabled);
        filterButton.setEnabled(false); dismissButton.setEnabled(false);
        Log.i(TAG, "voice-key experiment enabled=" + filterEnabled + ", autoDismiss=" + dismissEnabled);
        inputButton.setEnabled(false); sourceButton.setEnabled(false);
        startButton.setEnabled(false); playButton.setEnabled(false);
        final AudioDeviceInfo preferred = selected < 0 ? null : devices.get(selected);
        final int source = recognitionSource ? MediaRecorder.AudioSource.VOICE_RECOGNITION : MediaRecorder.AudioSource.MIC;
        report("正在准备录音：" + (preferred == null ? "系统默认" : describe(preferred)));
        new Thread(() -> capture(preferred, source), "mic-capture").start();
    }
    private void capture(AudioDeviceInfo preferred, int source) {
        AudioRecord audio = null;
        long samples = 0; int peak = 0; double energy = 0;
        String result;
        boolean success = false;
        try (RandomAccessFile file = new RandomAccessFile(wav, "rw")) {
            file.setLength(0); file.write(new byte[44]);
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("不支持 16kHz 单声道 PCM: " + minimum);
            audio = new AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 6400));
            recorder = audio;
            if (audio.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord 初始化失败");
            boolean accepted = preferred == null || audio.setPreferredDevice(preferred);
            Log.i(TAG, "preferred=" + describe(preferred) + ", accepted=" + accepted + ", source=" + source);
            if (!recording) throw new IllegalStateException("已取消录音");
            audio.startRecording();
            if (audio.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("未进入录音状态");
            short[] buffer = new short[1600];
            ByteBuffer bytes = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN);
            long began = SystemClock.elapsedRealtime(), lastUpdate = 0;
            while (recording && SystemClock.elapsedRealtime() - began < 12000) {
                int count = audio.read(buffer, 0, buffer.length, AudioRecord.READ_NON_BLOCKING);
                if (count < 0) { if (!recording) break; throw new IllegalStateException("读取录音失败: " + count); }
                if (count == 0) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUpdate >= 500) {
                        lastUpdate = now;
                        String message = "录音中 " + ((now - began) / 1000) + "/12 秒，等待输入数据\n实际输入：" + describe(audio.getRoutedDevice());
                        handler.post(() -> report(message));
                    }
                    SystemClock.sleep(20); continue;
                }
                bytes.clear(); double blockEnergy = 0;
                for (int i = 0; i < count; i++) {
                    int value = buffer[i]; peak = Math.max(peak, Math.abs(value));
                    blockEnergy += (double)value * value; bytes.putShort(buffer[i]);
                }
                file.write(bytes.array(), 0, count * 2); samples += count; energy += blockEnergy;
                long now = SystemClock.elapsedRealtime();
                if (now - lastUpdate >= 500) {
                    lastUpdate = now;
                    String message = "录音中 " + ((now - began) / 1000) + "/12 秒　RMS=" + (int)Math.sqrt(blockEnergy/count)
                            + "\n实际输入：" + describe(audio.getRoutedDevice()) + "\n首选请求：" + (accepted ? "已接受（以实际输入为准）" : "被拒绝，已回退");
                    handler.post(() -> report(message));
                }
            }
            writeHeader(file, (int)(samples * 2));
            success = samples > 0;
            result = "录音结束：" + (samples / 16000.0) + " 秒，峰值=" + peak + "，RMS=" + (samples == 0 ? 0 : (int)Math.sqrt(energy/samples))
                    + "\n实际输入：" + describe(audio.getRoutedDevice())
                    + "\n" + (samples == 0 ? "未收到音频数据；可换音源或按住遥控器语音键再试。" : peak == 0 ? "全静音：没有取得有效声音。" : "有非零音频；请回放确认是人声，并确认声音来自哪个麦克风。");
        } catch (Exception error) {
            Log.e(TAG, "capture failed", error); result = "录音失败：" + error;
        } finally {
            if (audio != null) { try { audio.stop(); } catch (RuntimeException ignored) {} audio.release(); }
            recorder = null; recording = false;
        }
        final String message = result; final boolean saved = success;
        handler.post(() -> {
            VoiceKeyService.endExperiment();
            busy = false; ready = saved;
            if (destroyed) return;
            inputButton.setEnabled(true); sourceButton.setEnabled(true); filterButton.setEnabled(true); dismissButton.setEnabled(true);
            startButton.setEnabled(true); playButton.setEnabled(saved);
            report(message);
        });
    }
    private static void writeHeader(RandomAccessFile file, int length) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R','I','F','F'}).putInt(36+length).put(new byte[]{'W','A','V','E','f','m','t',' '});
        header.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        header.put(new byte[]{'d','a','t','a'}).putInt(length);
        file.seek(0); file.write(header.array());
    }
    private void stopRecording() { recording = false; VoiceKeyService.endExperiment(); }
    private void stopPlayer() { if (player != null) { player.release(); player = null; } }
    private void play() {
        if (busy || !ready) { report("请先完成一次录音。"); return; }
        stopPlayer();
        try {
            player = new MediaPlayer(); player.setDataSource(wav.getAbsolutePath());
            player.setOnCompletionListener(p -> { stopPlayer(); report("回放结束。请确认听到的是自己的朗读。"); });
            player.setOnErrorListener((p, what, extra) -> { stopPlayer(); report("回放失败：" + what + "/" + extra); return true; });
            player.prepare(); player.start(); report("正在回放最近一次录音。");
        } catch (Exception error) { stopPlayer(); report("回放失败：" + error); }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOICE_ASSIST || event.getKeyCode() == KeyEvent.KEYCODE_SEARCH)
            Log.i(TAG, "voice key=" + event.getKeyCode() + ", action=" + event.getAction());
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onStop() {
        super.onStop(); stopRecording(); stopPlayer();
        Log.i(TAG, "Activity stopped; capture stopping (assistant may have covered app)");
    }
    @Override protected void onDestroy() { destroyed = true; stopRecording(); stopPlayer(); super.onDestroy(); }
}
