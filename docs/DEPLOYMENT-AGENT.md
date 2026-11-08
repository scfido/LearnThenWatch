# 学后看部署手册（供 Agent 执行）

目标：只部署本项目的正式 APP，保留现有用户数据和其他系统服务。人工前置操作为 [README 的 ADB 开启与连接授权](../README.md)。云视听小电视应已安装；缺失时报告前置条件，不下载或安装播放器。

## 输入与身份

| 输入 | 要求 |
| --- | --- |
| `DEVICE` | 明确的 ADB 序列号或 `IP:连接端口`，每条 ADB 命令都指定 `-s` |
| `REPO` | 默认 `scfido/LearnThenWatch`；部署 Fork 时使用用户指定的仓库 |
| `TAG` | 指定版本或默认最新稳定 Release，不自动选择预发布 |
| 本地包（可选） | 用户明确提供的正式 APK、SHA256SUMS 和可信证书指纹，可代替在线下载 |
| 可信签名指纹 | 首次安装从维护者可信渠道取得；已有安装时与已安装包证书比较 |
| 家长 PIN（可选） | 由家长指定或直接在电视输入，不生成公开默认 PIN，不打印或保存到脚本和日志 |

正式包：`dev.learnthenwatch.tv`；启动组件：`dev.learnthenwatch.tv/dev.learnthenwatch.tv.MainActivity`；管控服务：`dev.learnthenwatch.tv/dev.learnthenwatch.tv.PlaybackAccessService`。不要把 `.debug` APK 或测试 APK 部署给孩子使用。

## 1. 连接并记录现状

先确认本机有 Android Platform Tools；验签另需 Build Tools 的 `apksigner`。网络设备使用 `adb connect <DEVICE>`；需要配对时先执行 `adb pair <配对地址>`，交互输入临时配对码，再连接电视显示的连接地址。USB 直接使用设备序列号。

```text
adb devices -l
adb -s <DEVICE> get-state
adb -s <DEVICE> shell getprop ro.build.version.sdk
adb -s <DEVICE> shell pm path com.xiaodianshi.tv.yst
adb -s <DEVICE> shell pm path dev.learnthenwatch.tv
adb -s <DEVICE> shell settings get secure enabled_accessibility_services
adb -s <DEVICE> shell settings get secure accessibility_enabled
```

只继续操作明确选定且状态为 `device`、API ≥ 26 的设备。`unauthorized` 时等待电视允许连接；`offline` 时检查网络、端口或重连；多设备时不能省略 `-s`。设备可能需要先唤醒，不能把关屏状态下的界面检查当作失败。

保存原无障碍列表和总开关，列出是否有旧服务 `local.mytv.english/.LearningService` 或调试版管控服务。安装本身不改变该列表；配置新版前停用冲突的学习服务，保留其他服务和旧 APP 数据，不自行卸载旧应用。

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
adb -s <DEVICE> shell am start -n dev.learnthenwatch.tv/dev.learnthenwatch.tv.MainActivity
```

安装失败应解释原始错误。`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 为签名冲突，`VERSION_DOWNGRADE` 为降级；两者均停止。磁盘不足时报告空间需求，不替用户删除数据。禁止 `pm clear`，不自动卸载重装。

新包与旧 English 独立，首次没有旧记录或 PIN。由家长设置四位 PIN；如未提供 PIN，停在设置界面请家长直接输入，之后继续。已有 PIN 时不重置。按用户目标调整计划；未指定则保留 5 项任务／10 分钟奖励／60 分钟日上限与 KET 阶段。

## 4. 启用管控并保留其他服务

优先从应用「家长配置 → 监控检查」进入电视无障碍设置，启用「学后看 · 观看管控」。已有 ADB 授权的设备若允许 shell 修改设置，可执行等价操作：

1. **重新读取**当前无障碍列表，按冒号分隔为组件；`null` 或空值视为空列表。
2. 只移除旧 English、新版调试服务等已确认冲突的学习管控组件，保留所有其他组件；使用组件的展开全名比对，避免误判短写形式。
3. 若正式服务尚未存在，追加 `dev.learnthenwatch.tv/dev.learnthenwatch.tv.PlaybackAccessService`，去重后写回整张列表，再将 `accessibility_enabled` 设为 `1`。
4. 回读确认，并观察服务是否实际连接。设置成功不等于服务已连上；需等待并查看应用实时状态或下面的诊断。

不要只把列表覆盖成一个服务，不要清空全部无障碍设置，不要启动 root、设备所有者或持久化提权方案。固件拒绝 shell 写设置时，引导家长在系统界面授权；受限设置也按设备正常交互解锁，不能声称授权已完成。

```text
adb -s <DEVICE> shell dumpsys activity service dev.learnthenwatch.tv/dev.learnthenwatch.tv.PlaybackAccessService
adb -s <DEVICE> logcat -d -s LearnThenWatch:I
```

发生配置失败时恢复保存的原列表与总开关，并报告当前实际状态。正常部署成功则保留正式服务与所有原有非冲突服务。旧应用仍保留，学习记录不迁移。

## 5. 验收与交付

逐项记录实际结果：

1. 包名、版本、版本代码、APK SHA-256 和证书指纹符合预期；正式版不包含测试运行器。
2. 应用可从电视桌面打开，遥控器可浏览首页、地图和家长配置；家长 PIN 保护有效。
3. 监控检查明确显示服务已连接；目标播放器缺失、未设置 PIN、关闭管控等状态不能算成功。
4. 经家长同意进行真实学习／播放验收：零余额进入播放页出现学习页，完成学习后获得一轮额度，实际播放扣时，暂停不扣时，Home 清理覆盖层。使用正式数据的学习和观看会产生真实记录，应提前说明。
5. 需要余额耗尽、事务或大量 UI 回归时，按 [构建文档](BUILDING.md) 使用调试包隔离测试，不修改孩子余额或正式数据库来加速测试。
6. 如果没有进行真实播放或到时验收，明确写“未验收”，不能由服务连接推断全部闭环通过。完整重启、待机验证可能打断电视使用，仅在约定后操作。

交付报告只含版本、设备型号、安装与授权状态、检查结果和遗留问题，不包含 PIN、配对码或私钥。提醒家长：部分小米固件待机会撤销授权，唤醒后需重新开启；后台白名单效果需按固件验证。
