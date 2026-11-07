# 离线课程与覆盖说明

内容版本：`2026.11.07`。课程以 Cambridge 2025 年 8 月的 KET、PET 参考词表确定范围，使用自己的主题顺序和练习，不包含官方 PDF、试题、例句或商标素材。

| 项目 | KET | PET |
| --- | ---: | ---: |
| 从参考 PDF 解析的头词/词性记录 | 1,733 | 3,113 |
| 归一化后的课程学习项 | 1,715 | 3,100 |
| 有自然例句的学习项 | 1,162 | 1,709 |
| 明确标记的词义卡学习项 | 553 | 1,391 |

PET 保留与 KET 共同的基础词，在两个阶段分别显示；不应把两阶段数量相加当作独立词汇量。变体、同头词不同词性的来源记录归并到同一个学习项。`May`/`may`、`Miss`/`miss` 等有不同意义的大小写形式单独保存。官方源文件中的 `suprising` 规范为 `surprising`，`v/versus` 为 `versus`，`café/cafe` 为 `cafe`。

所有已解析记录均可在 `app/src/main/assets/licenses/reference-coverage.tsv` 查到原头词、规范词和稳定学习 ID；`uncovered.tsv` 当前没有缺项。这个覆盖口径是**参考头词覆盖**，不表示已完整拆分每个词的全部义项，也不代表考试所有可能词汇。

## 内容来源与练习边界

- 中文释义来自 ECDICT 仓库的 MIT 授权数据，并进行词性选择、常见意义简化和针对性纠错。完整许可证随 APK 保存在 `app/src/main/assets/licenses/ECDICT-LICENSE.txt`。
- 713 个阶段内学习项使用本项目原创双语例句；其余自然例句来自 Tatoeba/ManyThings 的英中句对，按目标词、长度、中文意义和敏感内容规则挑选。逐句作者与句子 ID 保存在 `sentence-attribution.tsv`，许可证为 CC BY 2.0 France；中文做繁简转换。
- 暂无合适自然句的词条保留明确的 `Meaning: 单词` 词义卡，只用于认词和听辨。`VocabularyCatalog.Item.naturalExample()` 可区分二者，不能把词义卡机械挖空当作例句或语境题。
- 语料句的目标词意义匹配经过自动规则检查和抽查；没有宣称全部经过逐句人工审核。长期给孩子使用前，仍建议继续做逐条语义和难度复核。

## 离线发音

3,187 份去重后的词/短语发音，约 20 MiB。本机 `Microsoft Zira Desktop`（美式英语）离线合成，编码为 Android 可播放的单声道 MP3；同一个词在两个阶段共用音频。应用播放不依赖 Android 英语 TTS，也不使用网络服务。

所有文件执行存在性、格式头和可解码检查。没有宣称逐词完成真人发音听审。较少见的缩写、多音词及英美差异需要继续复核。

## 复现与检查

内容生成工具只在开发机访问网络，源下载保存在被忽略的 `build/content-source`，源 URL 与 SHA-256 摘要随 `coverage.json` 保存。工具依赖：Python 3、`tools/requirements-content.txt` 中的可选依赖，以及 Windows System.Speech 英语语音。

```powershell
pip install -r tools/requirements-content.txt
python tools/generate_curriculum.py --download
powershell -NoProfile -ExecutionPolicy Bypass -File tools/Export-Pronunciation.ps1
python tools/encode_pronunciation.py
python tools/validate_curriculum.py --decode-audio
```

`tools/editorial.tsv` 保存可追踪的人工修订；更新后重新生成课程与覆盖报告。词条 ID 由规范词确定，不受排序变化影响。音频按词去重，课程文件不需要把原词典或原双语语料完整打包进 APK。
