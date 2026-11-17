# 构建与发布

## 环境和目录

- JDK 17、Gradle Wrapper 8.11.1、Android Gradle Plugin 8.9.2。
- Android SDK Platform 35、Build Tools 35.0.0；设置 `ANDROID_HOME` 或不入库的 `local.properties` 中的 `sdk.dir`。
- minSdk 26、targetSdk 28、compileSdk 35。升级 targetSdk 时须验证无障碍、覆盖层和音频焦点。APK 通过 GitHub Releases 分发，Google Play 目标 API 截止日期检查不适用于此发布渠道。
- 普通 APK 构建直接打包已提交课程和音频，无需重新下载语料或在 Windows 合成发音。

`app/src/main` 包含正式源码、Manifest、资源与 assets；`app/src/test` 是 JUnit；`app/src/androidTest` 是设备测试。维护工具在 `tools`，文档在 `docs`。

## 本地构建

```text
git clone https://github.com/scfido/LearnThenWatch.git
cd LearnThenWatch
```

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python tools/validate_curriculum.py
```

Linux/macOS 使用 `bash ./gradlew`。首次下载 Gradle 和依赖；Wrapper 校验发行包 SHA-256。需要代理时配置本机 Gradle 用户设置，不提交代理和凭据。

| 产物 | 路径 |
| --- | --- |
| 调试 APP | `app/build/outputs/apk/debug/app-debug.apk` |
| 调试测试 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| 单元测试报告 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| 正式 APP | `app/build/outputs/apk/release/app-release.apk` |
| 发布附件 | `build/release/learn-then-watch-<版本>.apk`、`SHA256SUMS` |

## 电视检查与截图

先唤醒电视并安装两个调试 APK，然后执行：

```powershell
adb -s <DEVICE> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <DEVICE> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python tools/run_device_checks.py --device <DEVICE>
python tools/capture_screenshots.py --device <DEVICE>
python tools/check_playback_access.py --device <DEVICE> --skip-standby
```

监控回归同时检查首次零余额拦截和观看时间耗尽后的答题焦点：默认选中第一个答案，遥控器下键选择第二个，上键返回第一个，移动焦点不提交答案。添加 `--screenshots-dir build/device-checks/focus` 可保存时间耗尽后的答案焦点图，供视觉检查；测试在电视上绘制当前已布局的到时学习页视图，避免播放器受保护画面导致系统截图失败，且仅使用隔离测试记录。到时学习页由普通应用窗口承载，以让系统原生音量条位于其上方；音量检查还保存系统窗口信息，不能只凭窗口存在判断没有遮挡。

`run_device_checks.py --suite sqlite|ui|remote` 可分别运行。测试使用隔离数据库、偏好与 PIN，不更改电视日期；临时停用冲突的学习管控服务，保留其他服务，结束时恢复原列表和无障碍总开关。不要并行运行设备工具。电脑进程被强杀时，应核对并恢复原无障碍列表。

已配置后台恢复时，设备工具会临时撤销正式版及调试版已有的 `WRITE_SECURE_SETTINGS` 授权，结束后还原，避免恢复过程干扰隔离测试；不修改通知监听列表。电脑进程被强杀时，还需按测试前记录还原这两个包原有的恢复权限。后台恢复本身的完整重启与待机验收需单独执行，见 [Agent 手册](DEPLOYMENT-AGENT.md#可选开机与待机后的后台恢复)。

后台恢复的可重复隔离检查（会安装临时调试版，要求电视尚未安装调试 APP；已有测试 APK 会备份并还原）：

```powershell
python -m unittest discover -s tools -p test_device_tools.py
python tools/check_monitor_recovery.py --device <DEVICE> --reboot --standby
```

先约定电视可重启和待机的时间，再选择对应参数；不带参数时只检查通知监听恢复与覆盖升级。工具在新装的调试版中生成随机测试 PIN，不输出密码，不修改正式版学习记录，结束后移除临时调试 APP、还原原测试 APK、无障碍列表及已有恢复权限。若 ADB 因固件关机策略断开，需先恢复连接，再按原记录完成清理；不能把连接丢失当作恢复测试通过。

截图工具生成五张画面至 `docs/screenshots`，使用演示记录，结束后删除演示数据。监控检查会实际播放、暂停和切换 Home，应在[已验证的兼容环境](VALIDATION.md)运行；页面变化可能影响自动选片。当前小米固件待机会强停进程，使用 `--skip-standby`；普通待机恢复只在确认支持的固件上验收。

主题修改需运行 `ui` 与 `remote` 检查，覆盖准备期间、答题反馈和完成页切换配色后的焦点、发音与记录；`run_device_checks.py --device <DEVICE> --suite theme` 可单独检查主题设置的遥控器操作。分别使用 `capture_screenshots.py --device <DEVICE> --theme light` 和 `--theme dark` 查看两种配色，可用 `--output-dir build/theme-checks/light` 等参数指定截图目录。自动模式还需在前台切换电视系统主题，检查 PIN 输入和当前学习视图的连续性；使用 `check_playback_access.py --device <DEVICE> --skip-standby --theme dark` 验证深色自动学习页。

## 观看到期过渡回归

修改观看过渡后，除基线构建和课程校验外，在明确指定的电视上串行运行 `run_device_checks.py --suite ui` 和 `check_playback_access.py --skip-standby`（均需指定 `--device <DEVICE>`）。UI 检查覆盖显示真实题目后的两秒内禁止作答与自动发音、关闭页面后取消准备回调，以及完成页双按钮、焦点连接和额度再次校验；播放检查覆盖到期后没有独立等待页、准备期间不提交答案和不扣观看时间。

另需实看实际 bilibili 播放：剩余 30 秒时顶部只有约 4dp 的窄条，暂停后不再缩短，恢复后继续；离开播放器、待机或关闭管控时移除。检查约 600ms 的视频渐隐与题目渐显是否连续、有无黑屏或视频闪回；题目完全显示两秒后才启用答案和自动发音。准备中检查返回与原生音量条，完成后分别验证回学习首页、返回原播放器，以及当天额度用完时不能继续观看。使用隔离记录，保留正式学习数据和无关无障碍服务；截图不能代替动画及真实遥控器验收。

## 界面颜色维护

所有应用颜色值集中在 `app/src/main/res/values/colors.xml`。`theme_light_*` 与 `theme_dark_*` 分别定义浅色和深色的背景、卡片、文字、强调色等语义角色，Java 的 `TvUiTheme.Role` 和 XML 窗口样式共同引用这些资源。修改配色只需编辑该文件，不在页面或测试中复制色值。

两套配色使用明确的资源名，不依赖 `values-night` 覆盖色值，以支持用户在系统深色时手动选择浅色，以及反向选择。自动模式仍按系统夜间状态选择相应配色。`mascot_*` 和 `launcher_*` 是独立的插画、桌面素材颜色；它们不随界面主题变化。新增控件复用已有语义角色，需要新用途时在资源表和 `TvUiTheme.Role` 中一并命名。

## 正式签名

首次生成并构建：

```powershell
.\tools\New-ReleaseSigning.ps1
.\tools\Build-Release.ps1
```

默认写入 `%USERPROFILE%\.android\learnthenwatch` 的 `release.jks` 与 `signing.json`，包含长期私钥与随机密码，已有文件拒绝覆盖。此目录必须私人保存并离线备份，不能上传到仓库、Issue、聊天或构建产物。所有后续升级沿用同一签名。

本地正式构建接口为四个环境变量：`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。Linux 可用 `keytool` 创建长期密钥后注入同样变量，再运行 `:app:assembleRelease`。缺少任一变量即失败，不生成临时发布签名。

## 维护者发布

正式构建由 [GitHub Actions](https://github.com/scfido/LearnThenWatch/actions/workflows/release.yml) 执行，APK 发布到 [Releases](https://github.com/scfido/LearnThenWatch/releases)。

1. 维护者在仓库 **Settings → Secrets and variables → Actions** 配置四个签名 Secrets。已有本地密钥时，可直接传给 GitHub CLI；Fork 发布应替换仓库名并使用自己的签名：

```powershell
$repo = 'scfido/LearnThenWatch'
$config = Get-Content "$env:USERPROFILE\.android\learnthenwatch\signing.json" -Raw | ConvertFrom-Json
[Convert]::ToBase64String([IO.File]::ReadAllBytes($config.ANDROID_KEYSTORE_PATH)) | gh secret set ANDROID_KEYSTORE_BASE64 --repo $repo
$config.ANDROID_KEYSTORE_PASSWORD | gh secret set ANDROID_KEYSTORE_PASSWORD --repo $repo
$config.ANDROID_KEY_ALIAS | gh secret set ANDROID_KEY_ALIAS --repo $repo
$config.ANDROID_KEY_PASSWORD | gh secret set ANDROID_KEY_PASSWORD --repo $repo
Remove-Variable config
```

2. 在 `gradle.properties` 更新版本名和递增的版本代码，在 `CHANGELOG.md` 添加对应 `## [版本]` 条目，提交并推送 `main`。
3. 创建并推送版本标签。标签必须为小写 `v` 加三段数字版本号，例如 `v1.5.3`，且严格等于 `v` 加 `appVersionName`；不匹配会在构建前失败。只有标签推送会触发自动构建，分支推送、PR 和手动操作不触发。

```powershell
git tag -a v1.5.3 -m "LearnThenWatch v1.5.3"
git push origin v1.5.3
```

`Build and publish version tag` 先校验标签、版本和 CHANGELOG，初始化 Android SDK，再检查课程、运行 JUnit、构建并上传调试 APP、测试 APK 与测试报告；随后临时还原签名，构建和验签，发布正式 APK、`SHA256SUMS` 与 CHANGELOG 对应说明。仅发布任务有 `contents: write`，临时私钥始终清理，Actions 依赖固定到发布提交。

Android SDK 初始化使用 `android-actions/setup-android` v4.0.4（Node.js 24 运行时），安装 Platform 35 和 Build Tools 35.0.0。Node.js 仅用于运行构建辅助 Action，电视 APP 仍使用 Java。

工作流通过 `tools/cache_gradle.py` 准备 Wrapper 缓存，优先从 Gradle 官方源下载，失败后尝试华为云、腾讯云镜像。所有来源都必须匹配 `gradle-wrapper.properties` 固定的官方 SHA-256；校验失败的文件不会用于构建。

缺 Secrets、标签不符、签名密码错误或已有同名 Release 会报错。先定位失败步骤；不要换密钥重发同一包名，也不要自动覆盖公开附件。

本地验签并整理发布附件：

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
python tools/prepare_release.py --tag v1.5.3
```

SHA-256 用于检测文件损坏，安装还要检查证书，见 [Agent 手册](DEPLOYMENT-AGENT.md)。GitHub 的 [Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets) 和 [令牌权限](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token) 以官方文档为准。

## 到期音量恢复回归

先执行基线构建、课程校验及 `python -m unittest discover -s tools -p test_device_tools.py`，再在明确指定且已唤醒的电视上串行运行播放检查。播放检查包含真实媒体流的渐弱、暂停恢复、关闭恢复、持久化恢复、六秒独立恢复、原本静音和手动调音优先，并在自动调整和真实视频最后三秒检查没有可见的系统音量窗口。

```powershell
python tools/check_playback_access.py --device <DEVICE> --skip-standby --screenshots-dir build/device-checks/fade
python tools/check_volume_fade_recovery.py --device <DEVICE>
```

第二条要求已安装当前调试 APK；它只结束调试包进程，验证独立定时任务重新启动应用并恢复真实媒体音量，结束后还原音量和原系统设置。`VolumeFadeProbeActivity` 只存在于调试构建，没有桌面入口，正式 APK 不包含该测试入口。不要用强行停止代替结束进程来测试自动恢复：Android 强行停止可能取消定时任务，此时要靠下一次应用启动恢复。使用可调音量、当前未静音且媒体音量至少为 2 的测试环境，保留原媒体音量，设备工具串行执行；不能只凭音频焦点获批推断已静音。
