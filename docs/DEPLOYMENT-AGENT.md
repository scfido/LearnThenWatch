# 学后看部署手册（供 Agent 执行）

本手册供 TRAE、腾讯 WorkBuddy、Codex、Claude Code、Cursor Agent 等电脑端智能体执行。家长需要完成的 ADB 开启、配对与授权操作见[安装指南](INSTALL.md)。

操作范围为「学后看」正式 APP，保留电视上的其他应用、用户数据与系统服务。向家长说明问题时使用日常语言，明确下一步需要按哪个按钮或打开哪个页面。

## 输入与身份

| 输入 | 要求 |
| --- | --- |
| `DEVICE` | 明确的 ADB 序列号或 `IP:连接端口`，每条 ADB 命令都指定 `-s` |
| `REPO` | 默认 `scfido/LearnThenWatch`；部署 Fork 时使用用户指定的仓库 |
| `TAG` | 指定版本或默认最新稳定 Release，不自动选择预发布 |
| 本地包（可选） | 用户明确提供的正式 APK、SHA256SUMS 和可信证书指纹，可代替在线下载 |
| 可信签名指纹 | 首次安装从维护者可信渠道取得；已有安装时与已安装包证书比较 |
| 家长 PIN（可选） | 由家长指定或直接在电视输入，不生成公开默认 PIN，不打印或保存到脚本和日志 |

正式包：`scfido.learnthenwatch.tv`；启动组件：`scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.MainActivity`；管控服务：`scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.PlaybackAccessService`。不要把 `.debug` APK 或测试 APK 部署给孩子使用。

## 1. 连接并记录现状

先确认本机有 Android Platform Tools；验签另需 Build Tools 的 `apksigner`。网络设备使用 `adb connect <DEVICE>`；需要配对时先执行 `adb pair <配对地址>`，交互输入临时配对码，再连接电视显示的连接地址。USB 直接使用设备序列号。

```text
adb devices -l
adb -s <DEVICE> get-state
adb -s <DEVICE> shell getprop ro.build.version.sdk
adb -s <DEVICE> shell pm path scfido.learnthenwatch.tv
adb -s <DEVICE> shell settings get secure enabled_accessibility_services
adb -s <DEVICE> shell settings get secure accessibility_enabled
```

只继续操作明确选定且状态为 `device`、API ≥ 26 的设备。`unauthorized` 时等待电视允许连接；`offline` 时检查网络、端口或重连；多设备时不能省略 `-s`。设备可能需要先唤醒，不能把关屏状态下的界面检查当作失败。

保存原无障碍列表和总开关，检查是否有本项目调试版管控服务。安装本身不改变该列表；启用正式服务时保留其他应用的服务和数据。

### 特殊连接排查

- 使用最新版 [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)。只有配对式无线调试时，使用电视显示的两组地址分别执行 `adb pair` 和 `adb connect`；不要默认回退到 5555，不把配对码写在命令行或日志中。[Android 官方说明](https://developer.android.com/tools/adb#wireless)
- TCL / 雷鸟没有无线配对入口时，先按完整型号确认是否支持 USB 调试，以及对应的接口和数据线。支持时通过 USB 连接电脑，请家长在电视上允许调试。
- 有线设备在 `adb devices -l` 中显示 `device` 后，可直接以该 USB 序列号完成安装。确需改用网络、且家长希望启用网络 ADB 时，执行 `adb -s <USB_SERIAL> tcpip 5555`，再 `adb connect <电视IP>:5555`；后续 `DEVICE` 改为该网络地址，并重新检查身份。支持的设备可用 `adb -s <网络DEVICE> usb` 退出此 TCP 模式；厂商网络调试开关按原设置还原。重启后重新检查调试开关和连接地址。
- 没有可用的调试入口时，转到家长指南的 U 盘安装。不要代装第三方 ADB 开启器、应用商店或其他辅助 APP；不能连接就如实报告无法执行 ADB 验收，不猜端口、不用投屏码配对。

## 2. 下载、校验与验签

官方发布页：[scfido/LearnThenWatch Releases](https://github.com/scfido/LearnThenWatch/releases/latest)。使用 GitHub CLI 下载，`<REPO>` 默认替换为 `scfido/LearnThenWatch`：

```text
gh release view --repo <REPO> --json tagName,assets,isPrerelease
gh release download <TAG> --repo <REPO> --pattern "learn-then-watch-*.apk" --pattern SHA256SUMS --dir <新的空下载目录>
```

将查看到的最新稳定标签固定为 `TAG` 再下载，避免两次查询跨版本。目录中必须恰有一个预期正式 APK，文件名版本应与标签一致；不要从分支 Artifact 安装发布版。

- Linux/macOS：在下载目录运行 `sha256sum -c SHA256SUMS`。
- PowerShell：运行 `Get-FileHash -Algorithm SHA256 <APK>`，逐字比较 `SHA256SUMS` 中对应文件的十六进制摘要。
- 运行 `apksigner verify --verbose --print-certs <APK>`，检查签名有效，并比对证书 SHA-256 指纹。SHA 文件和 APK 同源只能验证完整性，不能独立建立发布者身份。
- 使用 SDK 的 `apkanalyzer manifest application-id <APK>`、`apkanalyzer manifest version-name <APK>`，或 `aapt dump badging <APK>`，确认正式包名和预期版本。
- 若已有正式安装，用 `pm path` 找到 base APK，`adb pull` 到本地后对比证书；签名不一致或版本将降级时停止，不能用卸载、清数据或 `-d` 绕过。

官方证书指纹见[正式签名](VALIDATION.md#正式签名)。Fork 发布时使用其维护者提供的可信指纹。

## 3. 安装与首次设置

```text
adb -s <DEVICE> install -r <已验证的正式APK>
adb -s <DEVICE> shell am start -n scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.MainActivity
```

安装失败应解释原始错误。`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 为签名冲突，`VERSION_DOWNGRADE` 为降级；两者均停止。磁盘不足时报告空间需求，不替用户删除数据。禁止 `pm clear`，不自动卸载重装。

### 特殊安装排查

检查安装输出中的 `Success`，再回读已安装包的版本；同时检查错误文本，不能只依赖进程退出码。

| 情况 | 处理 |
| --- | --- |
| `INSTALL_FAILED_OLDER_SDK` | 回读 API；低于 26 时停止安装，并说明需要 Android 8.0 或更高版本 |
| TCL / 雷鸟 `INSTALL_FAILED_VERIFICATION_FAILURE` | 检查电视的安装授权；改用 U 盘或已有文件管理器。使用电视自带的多屏互动推送时，请家长在安装提示中选择「同意」 |
| 创维等 `INSTALL_FAILED_USER_RESTRICTED` | 检查电视「安装未知应用」开关及安装确认提示；仍受限时，改用 U 盘或厂商提供的安装入口，保留系统安装校验设置 |
| 提示禁止桌面类应用 | 核对 APK 包名为 `scfido.learnthenwatch.tv`，确认下载的是正式发布包；仍失败时记录原始错误，按完整型号和固件版本排查 |

ADB 已授权但安装通道被拒绝时，可将已验证的学后看 APK 放到电视下载目录，再请家长用**已有**文件管理器打开：

```text
adb -s <DEVICE> push <已验证的正式APK> /sdcard/Download/LearnThenWatch-install.apk
```

先确认目标文件不存在，存在则换一个未使用的文件名；仅清理本次创建的临时 APK。若没有文件管理器或固件仍拒绝安装，停止并说明限制。不要为了安装而换签名、改包名、停用系统组件或下载第三方商店。

首次打开时由家长设置四位数字密码；停在设置界面请家长直接输入，之后继续。已有密码时不重置。按家长要求调整计划；未指定则保留 5 项任务／10 分钟奖励／60 分钟日上限与 KET 阶段。

## 4. 启用管控并保留其他服务

优先从应用「家长配置 → 监控检查」进入电视无障碍设置，启用「学后看 · 观看管控」。已有 ADB 授权的设备若允许 shell 修改设置，可执行等价操作：

1. **重新读取**当前无障碍列表，按冒号分隔为组件；`null` 或空值视为空列表。
2. 若本项目调试版服务正在运行，在家长确认后停用该调试服务；保留其他应用的服务，使用组件的展开全名比对，避免误判短写形式。
3. 若正式服务尚未存在，追加 `scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.PlaybackAccessService`，去重后写回整张列表，再将 `accessibility_enabled` 设为 `1`。
4. 回读确认，并观察服务是否实际连接。设置成功不等于服务已连上；需等待并查看应用实时状态或下面的诊断。

不要只把列表覆盖成一个服务，不要清空全部无障碍设置，不要启动 root、设备所有者或持久化提权方案。固件拒绝 shell 写设置时，引导家长在系统界面授权；受限设置也按设备正常交互解锁，不能声称授权已完成。

Android 13 及以上从文件安装后，可能需要家长在「设置 → 应用 → 学后看 → 更多 → 允许受限制的设置」放行，再开启无障碍；菜单以电视实际界面为准。[Google 官方说明](https://support.google.com/android/answer/12623953?hl=zh-Hans) 若固件删除无障碍入口，且现有 ADB 也不能授权，则只交付已确认可用的学习功能，明确观看管控不可用。服务授权反复被清除时，记录固件版本，按下节配置并验证后台恢复。

```text
adb -s <DEVICE> shell dumpsys activity service scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.PlaybackAccessService
adb -s <DEVICE> logcat -d -s LearnThenWatch:I
```

发生配置失败时恢复保存的原列表与总开关，并报告当前实际状态。正常安装完成后保留正式服务和其他原有服务。

### 可选：开机与待机后的后台恢复

1.5.5 起提供此功能，部署前确认所选 Release 包含 `MonitorRevivalService`；旧版不能执行以下授权。先说明并确认家长需要后台恢复：这会允许学后看恢复自己的无障碍服务；关闭管控请使用受 PIN 保护的「家长配置 → 观看管控」。通知访问的系统权限范围较宽，但实现不读取、保存或上传通知内容。

仅在已安装正确版本、设置家长 PIN 并开启观看管控后执行。记录本包原 `WRITE_SECURE_SETTINGS` 授权和通知监听授权，保留其他监听服务：

```text
adb -s <DEVICE> shell dumpsys package scfido.learnthenwatch.tv
adb -s <DEVICE> shell settings get secure enabled_notification_listeners
adb -s <DEVICE> shell pm grant scfido.learnthenwatch.tv android.permission.WRITE_SECURE_SETTINGS
adb -s <DEVICE> shell cmd notification allow_listener scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.MonitorRevivalService
```

首条输出仅在本地检查 `android.permission.WRITE_SECURE_SETTINGS: granted=true`，不要公开整份设备信息。`allow_listener` 只添加此组件，不覆盖监听列表。若固件拒绝命令，家长可在系统「通知使用权 / 通知访问」中允许「学后看 · 后台恢复」；若 `pm grant` 被拒绝，本功能不可用，回到普通无障碍授权，不使用提权绕过。

打开一次学后看触发恢复，再回到电视原桌面。回读授权，并确认服务实际连接：

```text
adb -s <DEVICE> shell dumpsys activity service scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.MonitorRevivalService
adb -s <DEVICE> shell dumpsys activity service scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.PlaybackAccessService
```

后台恢复不会启动学习页面，不接管桌面或 Home 键，不改变 ADB 开关。检查窗口约两分钟，授权仍在但服务未连上时，只移除并重新添加自己的组件一次，保持其他服务，不关闭无障碍总开关。未设置 PIN、家长关闭观看管控、用户尚未解锁或无额外权限时不恢复。不要给正式版和调试版同时启用此功能。

撤销后台恢复时，仅操作本组件：

```text
adb -s <DEVICE> shell pm revoke scfido.learnthenwatch.tv android.permission.WRITE_SECURE_SETTINGS
adb -s <DEVICE> shell cmd notification disallow_listener scfido.learnthenwatch.tv/scfido.learnthenwatch.tv.MonitorRevivalService
```

撤销恢复权限不会自动撤销原观看管控授权；不再需要管控时由家长关闭观看管控或系统无障碍服务。配置失败时，仅还原本次新增的授权，不撤销原本已有权限，不覆盖其他服务列表。

Android 10 及以下的低内存设备可能不支持通知监听（[Android 官方说明](https://developer.android.com/reference/android/service/notification/NotificationListenerService)）；通知监听不可用或系统禁止后台运行时，请家长在唤醒后重新开启观看管控。

## 5. 验收与交付

逐项记录实际结果：

1. 包名、版本、版本代码、APK SHA-256 和证书指纹符合预期；正式版不包含测试运行器。
2. 应用可从电视桌面打开，遥控器可浏览首页、地图和家长配置；家长 PIN 保护有效。
3. 家长需要观看时间管理时，监控检查应明确显示服务已连接；未设置密码、未授权或关闭管控等情况应说明当前状态。
4. 经家长同意后，可在电视现有的兼容使用场景中验证学习换时、实际观看扣时、暂停不扣时以及 Home 清理学习页。该检查会产生真实学习和观看记录，应提前说明。
5. 需要余额耗尽、事务或大量 UI 回归时，按 [构建文档](BUILDING.md) 使用调试包隔离测试，不修改孩子余额或正式数据库来加速测试。
6. 如果没有进行真实播放或到时验收，明确写“未验收”，不能由服务连接推断全部闭环通过。完整重启、待机验证可能打断电视使用，仅在约定后操作。

启用后台恢复后，在家长约定的时间分别检查完整重启与待机唤醒：等待最多约两分钟，确认原画面未被学习界面覆盖、管控服务实际连接且其他无障碍服务仍在。连接成功后，再按隔离播放测试验收计时与拦截。完整重启、待机、长期后台三项分别记录，未测试的项写“未验收”。

交付报告只含版本、设备型号、安装与授权状态、检查结果和遗留问题，不包含 PIN、配对码或私钥。提醒家长：部分小米固件待机会撤销授权，未配置后台恢复或恢复失败时仍需重新开启；恢复效果需按固件验证。
