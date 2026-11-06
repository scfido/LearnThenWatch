# MyTV

小米电视上的两个独立 Android 应用，放在同一个仓库维护，共用构建脚本，分别生成 APK。

| 应用 | 用途 | 包名 | 文档 |
| --- | --- | --- | --- |
| 小电视开机启动 | 开机后等待系统就绪，再打开云视听小电视 | `local.mytv.bilistart` | [配置和恢复](apps/bilistart/README.md) |
| 看电视学英语 | 累计播放两分钟后出一道小学英语题 | `local.mytv.english` | [安装和使用](apps/english/README.md) |

两个应用的权限、生命周期和用途不同，保持独立安装、升级和卸载。当前没有共享业务代码，因此只共用构建工具；暂不增加公共代码模块或引入 Gradle。

## 目录结构

```text
MyTV/
├── apps/
│   ├── bilistart/
│   │   ├── AndroidManifest.xml
│   │   ├── src/
│   │   ├── README.md
│   │   └── build/              # 构建产物，不入库
│   └── english/
│       ├── AndroidManifest.xml
│       ├── src/
│       ├── res/
│       ├── tests/
│       ├── README.md
│       ├── VALIDATION.md
│       └── build/              # 构建产物，不入库
├── build/                      # 本地签名密钥，不入库
├── build.ps1                   # 两个应用共用的构建入口
└── README.md
```

## 构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。SDK 默认位于 `%LOCALAPPDATA%\Android\Sdk`。在仓库根目录执行：

```powershell
.\build.ps1 -App bilistart
.\build.ps1 -App english
.\build.ps1 -App all
```

不带参数仍构建开机启动应用；原来的 `.\build.ps1 -English` 也继续支持。

输出位置：

- 开机启动：`apps/bilistart/build/bilistart.apk`
- 英语答题：`apps/english/build/englishquiz.apk`

两个应用沿用根目录的 `build/local-signing.jks`。请保留并备份此密钥，覆盖安装时需要使用同一签名；密钥不存在时脚本会自动创建本地密钥。

英语应用的纯 Java 测试命令见[应用文档](apps/english/README.md#检查日志)，电视实测范围见[验证记录](apps/english/VALIDATION.md)。
