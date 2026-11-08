# 离线课程与覆盖说明

内容版本：`2026.11.07`。课程以 Cambridge 2025 年 8 月的 KET、PET 参考词表确定范围，使用自己的主题顺序和练习，不包含官方 PDF、试题、例句或商标素材。

| 项目 | KET | PET |
| --- | ---: | ---: |
| 从参考 PDF 解析的头词/词性记录 | 1,733 | 3,113 |
| 归一化后的课程学习项 | 1,715 | 3,100 |
| 有自然例句的学习项 | 1,162 | 1,709 |
| 明确标记的词义卡学习项 | 553 | 1,391 |

PET 包含与 KET 重合的基础词，表中按阶段分别统计。变体和同头词不同词性的来源记录归并到同一个学习项；`May`/`may`、`Miss`/`miss` 等不同意义的大小写形式单独保存。

原头词、规范词和学习 ID 见[覆盖映射](../app/src/main/assets/licenses/reference-coverage.tsv)。覆盖统计以参考头词为单位，尚未逐个拆分全部义项。

## 内容来源与练习边界

- 中文释义来自 ECDICT 仓库的 MIT 授权数据，并进行词性选择、常见意义简化和针对性纠错。完整许可证随 APK 保存在 `app/src/main/assets/licenses/ECDICT-LICENSE.txt`。
- 713 个阶段内学习项使用本项目原创双语例句；其余自然例句来自 Tatoeba/ManyThings 的英中句对，按目标词、长度、中文意义和敏感内容规则挑选。逐句作者与句子 ID 保存在 `sentence-attribution.tsv`，许可证为 CC BY 2.0 France；中文做繁简转换。
- 暂无合适自然句的词条保留明确的 `Meaning: 单词` 词义卡，只用于认词和听辨。`VocabularyCatalog.Item.naturalExample()` 可区分二者，不能把词义卡机械挖空当作例句或语境题。
- 语料句与目标词的意义匹配经过自动检查和抽查，全量逐句语义与难度审校尚待完成。

## 离线发音

3,187 份去重后的词/短语发音，约 20 MiB，使用 `Microsoft Zira Desktop`（美式英语）合成为单声道 MP3。同一个词在两个阶段共用音频，电视播放无需安装英语 TTS 或连接网络。

全部音频通过文件完整性与解码检查。逐词人工听审尚待完成，缩写、多音词及英美差异是重点复核项。

## 复现与检查

内容生成工具只在开发机访问网络，源下载保存在被忽略的 `build/content-source`，源 URL 与 SHA-256 摘要随 `coverage.json` 保存。工具依赖：Python 3、`tools/requirements-content.txt` 中的可选依赖，以及 Windows System.Speech 英语语音。

```powershell
pip install -r tools/requirements-content.txt
python tools/generate_curriculum.py --download
powershell -NoProfile -ExecutionPolicy Bypass -File tools/Export-Pronunciation.ps1
python tools/encode_pronunciation.py
python tools/validate_curriculum.py --decode-audio
```

`tools/editorial.tsv` 保存人工修订，修改后重新生成课程与覆盖报告。词条 ID 由规范词确定，不受排序变化影响。音频按词去重，原始词典和双语语料不随 APK 分发。
