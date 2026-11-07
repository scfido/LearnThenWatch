# 贡献指南

先阅读 [README](README.md)、[构建与测试](docs/BUILDING.md) 和 [Agent 约定](AGENTS.md)。本项目是单模块、原生 Java Android TV 应用，所有操作应支持遥控器；欢迎修复适配问题和改进课程质量。

## 提交前

1. 运行 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 与 `python tools/validate_curriculum.py`。
2. 界面、导航、音频或管控有变化时，在真实电视运行相应隔离测试；更新验证记录，写明型号、固件、播放器版本及未覆盖项。
3. 说明问题触发方式、改变后的行为和实际验证。截图使用演示数据，不包含孩子记录或家长 PIN。
4. 功能与兼容性变化更新 CHANGELOG；发布时同时修改 `gradle.properties` 的版本与递增版本代码。

## 代码和内容

- 保持 `LearningRepository` 为学习、观看余额和进度的唯一写入入口；发奖必须幂等，暂停和断连不能补扣时间。
- 名称按职责表达；正式 APP、JUnit 和设备测试分别放在标准 source set。界面避免引入只有触摸才能触发的操作。
- 修改词义或原创例句优先编辑 `tools/editorial.tsv`，按 [课程说明](docs/CONTENT.md) 重新生成并检查。保持稳定词条 ID、来源映射、逐句署名和许可证。
- 不提交签名密钥、密码、个人电视地址、日志中的敏感数据或构建产物；不要为了测试清除正式数据。
- 原创贡献按项目 MIT 提供。引入第三方代码或内容须保留其兼容许可及署名，并更新 [第三方声明](THIRD_PARTY_NOTICES.md)。

报告问题请附可复现步骤、应用版本、电视型号／Android 版本、播放器版本以及经过脱敏的错误片段；无需上传整份私人数据库。
