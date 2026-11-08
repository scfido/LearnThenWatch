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

`run_device_checks.py --suite sqlite|ui|remote` 可分别运行。测试使用隔离数据库、偏好与 PIN，不更改电视日期；临时停用冲突的学习管控服务，保留其他服务，结束时恢复原列表和无障碍总开关。不要并行运行设备工具。电脑进程被强杀时，应核对并恢复原无障碍列表。

截图工具生成五张真实画面至 `docs/screenshots`，只使用演示记录，结束后删除演示数据。监控检查会真实播放、暂停和切换 Home，要求云视听小电视已有可恢复视频且没有推广弹窗；播放器云端首页变化可能影响自动选片。当前小米固件待机会强停进程，使用 `--skip-standby`；普通待机恢复只在确认支持的固件上验收。

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
3. 创建并推送版本标签。标签必须为小写 `v` 加三段数字版本号，例如 `v1.5.0`，且严格等于 `v` 加 `appVersionName`；不匹配会在构建前失败。只有标签推送会触发自动构建，分支推送、PR 和手动操作不触发。

```powershell
git tag -a v1.5.0 -m "LearnThenWatch v1.5.0"
git push origin v1.5.0
```

`Build and publish version tag` 先校验标签、版本和 CHANGELOG，再检查课程、运行 JUnit、构建并上传调试 APP、测试 APK 与测试报告；随后临时还原签名，构建和验签，发布正式 APK、`SHA256SUMS` 与 CHANGELOG 对应说明。仅发布任务有 `contents: write`，临时私钥始终清理，Actions 依赖固定到官方发布提交。

缺 Secrets、标签不符、签名密码错误或已有同名 Release 会报错。先定位失败步骤；不要换密钥重发同一包名，也不要自动覆盖公开附件。

本地验签并整理发布附件：

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
python tools/prepare_release.py --tag v1.5.0
```

SHA-256 用于检测文件损坏，安装还要检查证书，见 [Agent 手册](DEPLOYMENT-AGENT.md)。GitHub 的 [Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets) 和 [令牌权限](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token) 以官方文档为准。
