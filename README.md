# 学后看 · LearnThenWatch

[![构建与发布](https://github.com/scfido/LearnThenWatch/actions/workflows/release.yml/badge.svg)](https://github.com/scfido/LearnThenWatch/actions/workflows/release.yml)
[![最新版本](https://img.shields.io/github/v/release/scfido/LearnThenWatch)](https://github.com/scfido/LearnThenWatch/releases/latest)
[![MIT](https://img.shields.io/github/license/scfido/LearnThenWatch)](LICENSE)

**先学一轮英语，再看一会儿电视。**

面向已有英语基础孩子的离线 Android TV 应用：完成词汇学习与错题订正，兑换一轮观看时间。家长用 PIN 管理学习计划和每日额度，孩子用电视遥控器完成全部操作。

[下载 APK](https://github.com/scfido/LearnThenWatch/releases/latest) · [安装指南](docs/DEPLOYMENT-AGENT.md) · [更新记录](CHANGELOG.md) · [反馈问题](https://github.com/scfido/LearnThenWatch/issues)

## 界面预览

| 学习首页 | 学习与订正 |
| --- | --- |
| ![学习首页](docs/screenshots/home.png) | ![学习页](docs/screenshots/lesson.png) |
| 探险地图 | 学习记录 |
| ![KET 探险地图](docs/screenshots/map.png) | ![成长概览和七日图表](docs/screenshots/progress.png) |

<img src="docs/screenshots/parent-settings.png" alt="家长配置" width="720">

## 功能与适用环境

- **离线课程**：按 KET/PET 两阶段编排，共 4,815 个学习项（含阶段间重合词），配有中文释义、例句或词义卡、3,187 份离线发音。
- **学习换时间**：默认每轮 5 项主任务，集中订正后兑换 10 分钟；每日最多实际观看 60 分钟。已有余额不叠加，跨日旧余额清零。
- **实际播放计时**：结合播放器页面、屏幕与媒体音频状态计时，暂停、浏览、待机和学习期间不扣时；余额耗尽后显示学习页。
- **持续学习**：任务自动保存，记录首次作答与错题，按 1、3、7、14、30 天间隔复习，提供地图、词卡和学习记录。
- **家长配置**：四位 PIN 保护任务数、兑换分钟、每日上限、课程阶段、新词数量和发音设置。

需要 Android 8.0（API 26）或以上的电视／盒子及遥控器。观看管控目前仅适配已安装的 **云视听小电视**（`com.xiaodianshi.tv.yst`），本项目不提供播放器。独立英语学习可以离线使用。

已在 MiTV-MFTR0 / Android 14、播放器 1.8.9 上测试。无障碍授权保留时可在开机后重连；部分小米固件待机会撤销授权，唤醒后需重新开启。Home 与系统设置可正常使用。

课程范围、内容来源和审核状态见[内容说明](docs/CONTENT.md)。

## 下载与安装

从 [Releases](https://github.com/scfido/LearnThenWatch/releases/latest) 下载最新正式版 APK 和 `SHA256SUMS`。

### 开启电视 ADB

电脑与电视连接同一可信局域网，准备电视地址与调试端口。

**小米电视／盒子**：进入「设置 → 关于」，在「产品型号」连续确认 7 次，开启开发者选项；再到「账号与安全」或固件对应的开发者设置允许「ADB 调试」。入口随固件变化，参见[小米官方说明](https://www.mi.com/ph/support/article/KA-06513/)。

**Android TV／Google TV**：进入「设置 → 系统／设备偏好设置 → 关于」，在「Android TV 操作系统版本／版本号」连续确认，直到提示成为开发者；返回开发者选项开启 USB 或无线调试。[Android 官方说明](https://developer.android.com/training/tv/get-started/create)

1. 在网络设置查看 IP。传统网络 ADB 常使用 `5555`，以实际开放端口为准；仅开启 USB 调试不保证网络 ADB 开放，必要时使用 USB 连接。
2. 提供「无线调试 → 使用配对码配对」的电视，需要记录配对地址、配对码和连接地址；两个端口可能不同。
3. 首次连接时，在电视上允许该电脑的调试授权。只向可信设备开放 ADB。[ADB 官方指引](https://developer.android.com/tools/adb)

### 安装应用

开启 ADB 后，可将以下指令交给能够操作 ADB 的 Agent：

> 阅读 https://github.com/scfido/LearnThenWatch/blob/main/docs/DEPLOYMENT-AGENT.md ，从 scfido/LearnThenWatch 的最新 Release 安装学后看。电视设备是 `<电视IP:连接端口或USB序列号>`，云视听小电视已安装。保留现有数据及其他无障碍服务，完成安装、授权和验收。

也可以直接按照 [ADB 安装指南](docs/DEPLOYMENT-AGENT.md) 连接设备、校验 APK、安装并开启无障碍服务。

## 开始使用

1. 打开「学后看」，设置并确认四位家长 PIN；没有云端找回，请妥善保存。
2. 在「家长配置」调整学习计划、阶段、起点、发音和额度，默认也可直接使用。
3. 开启无障碍服务「学后看 · 观看管控」，回到「监控检查」确认已连接。
4. 完成主任务和订正，确认「继续观看」。以后从保存的进度继续。

| 按键 | 普通页面 | 学习与词卡 |
| --- | --- | --- |
| 上／下 | 移动卡片或设置焦点 | 选择答案或「不认识」 |
| 左 | 进入侧栏 | 重听，保留答案焦点 |
| 右 | 回到内容区 | 答后下一题；未作答时记为「不认识」 |
| 确认 | 打开卡片或调整设置 | 提交答案；词卡内重听 |
| 返回 | 按入口逐页回退 | 保存任务并离开；观看拦截中返回会退出观看 |

任务数与兑换分钟在下一轮生效，降低日上限立即裁剪余额。达到上限仍可学习，但不再发放当天观看时间；演示模式不记录学习或发奖。

## 开发与文档

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python tools/validate_curriculum.py
```

正式包名为 `dev.learnthenwatch.tv`，调试包为 `dev.learnthenwatch.tv.debug`。构建、测试和版本标签发布方式见[开发文档](docs/BUILDING.md)。

| 文档 | 内容 |
| --- | --- |
| [构建与发布](docs/BUILDING.md) | 环境、签名、Secrets、标签与输出 |
| [Agent 部署](docs/DEPLOYMENT-AGENT.md) | 下载、校验、安装、授权与验收 |
| [课程说明](docs/CONTENT.md) | 来源、覆盖、音频与内容复现 |
| [验证记录](docs/VALIDATION.md) | 实测范围和固件限制 |
| [贡献指南](CONTRIBUTING.md) / [更新记录](CHANGELOG.md) | 维护约定与版本变化 |
| [隐私说明](PRIVACY.md) / [第三方声明](THIRD_PARTY_NOTICES.md) | 本地数据、权限与素材许可 |

本项目与 Cambridge、哔哩哔哩或电视厂商无隶属或授权背书关系，第三方素材保留各自许可与署名。
