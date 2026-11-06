# 看电视学英语

独立于原有「小电视开机启动」的 Android TV 应用，包名 `local.mytv.english`。当前适配云视听小电视 `com.xiaodianshi.tv.yst`。

## 功能

- 确认云视听小电视的播放页面在前台、屏幕开启且媒体音频正在播放时累计计时。累计 120 秒后显示全屏题目。
- 暂停、退出播放页面、待机时不累计；答对后从零开始下一个 120 秒。
- 24 道离线小学英语基础题，每次随机出题、打乱选项，避免连续重复。题库见 `src/local/mytv/english/Questions.java`。
- 遥控器方向键选择、确认键提交；答错保持答题界面，答对关闭界面并释放临时音频焦点。
- 监控进度每五秒保存，待答题状态在显示前保存。服务重启会恢复状态，最多丢失五秒播放累计时间。
- 「看电视学英语」应用内可以关闭提醒和预览题目；预览不改变实际观看累计时间。

## 构建和安装

在项目根目录执行：

```powershell
.\build.ps1 -App english
adb -s 192.168.1.132:5555 install -r .\apps\english\build\englishquiz.apk
adb -s 192.168.1.132:5555 shell am start -n local.mytv.english/.SettingsActivity
```

在电视系统无障碍设置中开启「英语答题提醒」。服务由 Android 管理，不要求电脑持续在线；部分电视管家可能需要额外允许后台运行。不要覆盖其他无障碍服务设置。

应用无网络权限，不上传屏幕、题目或答题记录。无障碍权限用于确认播放页面、显示可操作的答题覆盖层。音频焦点用于临时暂停播放，答对后释放焦点。恢复播放的具体行为依赖播放器对焦点恢复的处理。

## 使用边界

当前播放检测依据是前台页面与媒体音频流，未读取视频本身的播放时间：静音视频有音频流时仍可计时，没有音频流的视频不计时；其他应用在后台播放音频时可能造成误计。播放器更新改变页面结构后需重新验证。

这不是设备锁定或防绕过的家长控制。Home、系统设置和关闭提醒保留可用；离开观看应用后可以操作系统，未答题状态保留，回到播放页面会再次要求答题。题目显示期间拦截返回键与播放键，错误答案不会放行。

## 检查日志

```powershell
adb -s 192.168.1.132:5555 shell logcat -d -s EnglishQuiz:I
javac -encoding UTF-8 -d apps/english/build/tests apps/english/src/local/mytv/english/PlaybackClock.java apps/english/src/local/mytv/english/Questions.java apps/english/tests/PlaybackClockTest.java
java -cp apps/english/build/tests PlaybackClockTest
```

## 移除

先在应用中关闭提醒，或在系统无障碍设置中关闭「英语答题提醒」，再卸载：

```powershell
adb -s 192.168.1.132:5555 uninstall local.mytv.english
```

不会卸载或修改原有开机启动辅助应用。
