# 云视听小电视开机启动

针对 MiTV-MFTR0 / Android 14 的本地启动辅助应用。

小米桌面在开机早期通过 `getLaunchIntentForPackage()` 启动第三方应用时，云视听小电视尚不可解析，日志出现空 Intent 异常。辅助应用支持 Direct Boot，等用户解锁且开机至少 45 秒后，再明确启动云视听小电视。每次开机只执行一次，开机三分钟后手动打开辅助应用不会触发跳转。

不修改 Home 键，不禁用系统桌面，不使用网络、无障碍、悬浮窗或常驻服务。

## 构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0，在仓库根目录执行：

```powershell
.\build.ps1 -App bilistart
```

APK 位于 `apps\bilistart\build\bilistart.apk`。两个应用共用仓库根目录的本地签名密钥 `build\local-signing.jks`。

## 配置

```powershell
adb install -r .\apps\bilistart\build\bilistart.apk
adb shell am start -n local.mytv.bilistart/.StartupActivity
adb shell settings put system start_3rd_app local.mytv.bilistart
```

等待至少十秒后重启电视。验证前台应用与 `adb shell logcat -d -s BiliStartup:I` 日志。

## 恢复

此电视原始 `start_3rd_app` 是空字符串。先恢复设置，再卸载辅助应用：

```powershell
adb shell "settings put system start_3rd_app ''"
adb uninstall local.mytv.bilistart
```

该方案依赖电视当前固件的内部启动设置。系统升级后需要重新验证。
