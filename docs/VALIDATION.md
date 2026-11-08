# 兼容性与验证

## 测试环境

| 项目 | 版本 |
| --- | --- |
| 设备测试版本 | 1.5.0（versionCode 8） |
| 电视 | MiTV-MFTR0 |
| 系统 | Android 14 |
| 播放器 | 云视听小电视 1.8.9 |
| 设备测试日期 | 2026-11-07 |

## 验证结果

| 检查 | 结果 |
| --- | --- |
| JUnit：学习、复习、余额、计时、导航与监控策略 | 5 组通过 |
| SQLite 事务、幂等发奖与任务恢复 | 55 项断言通过 |
| 学习布局、按键、错题与音频失败回退 | 569 项断言通过 |
| 真实遥控器导航、焦点、返回路径与 PIN | 153 项断言通过 |
| 实际播放器计时与服务生命周期 | 34 项断言通过 |
| 离线课程完整性 | 4,815 项课程、2,871 个自然例句学习项、19,260 道练习通过 |
| 离线发音 | 3,187 份 MP3 通过完整性检查 |
| APK 构建 | 调试版、设备测试版与正式签名版通过 |

播放测试覆盖零余额学习覆盖层、完成学习后恢复播放、暂停不扣时、恢复计时、余额耗尽、返回/Home 清理覆盖层、任务保留和无障碍服务断开重连。设备测试使用独立数据库与偏好；命令见[构建与测试](BUILDING.md)。

## 自动构建与发布

当前源码的正式包名为 `scfido.learnthenwatch.tv`，调试包为 `scfido.learnthenwatch.tv.debug`。包名调整后已通过干净构建、五组 JUnit、调试与设备测试 APK 构建、正式签名 APK 构建，并核对启动组件、无障碍配置及测试运行器标识。

2026-11-08，[v1.5.3 云端构建](https://github.com/scfido/LearnThenWatch/actions/runs/42468599173)通过构建工具校验、课程检查、五组 JUnit、调试及设备测试 APK 构建、正式 APK 构建和签名验证，并发布到 [Releases](https://github.com/scfido/LearnThenWatch/releases/tag/v1.5.3)。

下载发布附件后，已核对 SHA-256、正式证书、包名 `scfido.learnthenwatch.tv`、版本 `1.5.3` 与 versionCode `11`。全部构建记录见 [GitHub Actions](https://github.com/scfido/LearnThenWatch/actions/workflows/release.yml)。

## 兼容性限制

- **当前小米固件的电源待机**会强停第三方应用并移除其无障碍授权，唤醒后需要重新开启。监控回归使用 `--skip-standby`，不包含待机恢复。
- 完整系统重启与长期后台运行尚待进一步验证。
- 其他电视、固件及播放器尚未验证。当前仅适配 `com.xiaodianshi.tv.yst`；播放器云端首页变化可能影响测试工具自动选片。
- 课程词义、例句匹配和合成发音尚未完成全量人工审校，详见[内容说明](CONTENT.md)。

## 正式签名

官方 APK 使用 RSA 3072 位发布证书，APK Signature Scheme v2。证书 SHA-256 指纹：

```text
7869da6d0207998b7c022ccb3103a0f28eb5bfbdaec56666f26d4d04528f4e8d
```

安装前可使用 `apksigner verify --verbose --print-certs <APK>` 核对。Fork 发布的 APK 使用其维护者自己的证书。
