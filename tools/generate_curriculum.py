"""Reproducible offline course import. Network is used only by this build tool.

Run with Python 3 + requests + pymupdf + opencc-python-reimplemented.
Source files remain in ignored build/content-source. Ship the generated assets and notices.
"""
from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import re
import shutil

import fitz
from opencc import OpenCC
import requests

APP = Path(__file__).resolve().parents[1]
CACHE = APP / "build/content-source"
ASSETS = APP / "app/src/main/assets"
CC = OpenCC("t2s")
SOURCES = {
    "ket.pdf": "https://www.cambridgeenglish.org/Images/506886-a2-key-2020-vocabulary-list.pdf",
    "pet.pdf": "https://www.cambridgeenglish.org/Images/506887-b1-preliminary-vocabulary-list.pdf",
    "ecdict.csv": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv",
    "ECDICT-LICENSE.txt": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/LICENSE",
    "cmn.txt": "https://raw.githubusercontent.com/shaoshengsong/pytorch-transformer-en-zh-translation-demo/main/cmn-eng/cmn.txt",
}
POS = re.compile(r"^(.+?)\s*\((adj|adv|det|pron|prep|conj|exclam|n|v|av|mv|phr|abbrev|pl|sing)(?:\b|[,. ])")
PET_MARGIN_EXCEPTIONS = {"border", "communicate", "engaged", "fantastic", "farm", "generation", "get back", "get off", "get on", "give way", "grab", "grade"}
BAN = re.compile(r"\b(?:kill|murder|suicide|sex|sexy|rape|naked|gun|guns|bomb|war|wars|soldier|army|nazi|hitler|god|christ|jesus|religion|church|die|died|dead|death|blood|corpse|hate|stupid|idiot|fool|drunk|beer|wine|whisky|smoke|smoking|cigarette|alcohol|drug|drugs|hell|damn|shit|fuck|bitch|prison|jail|crime|criminal|steal|stole|stolen|rob|kiss|marry|married|divorce|girlfriend|boyfriend|pregnant|politic|president|election|republican|democrat|weapon|execute|hang|hanged|sexual|attack|enemy|fight|fighting|bullet)\b", re.I)
BAN_ZH = re.compile(r"自杀|杀人|杀死|性[交爱]|裸体|强奸|毒品|吸烟|抽烟|喝酒|啤酒|葡萄酒|威士忌|政治|总统|上帝|耶稣|宗教|战争|军队|尸体|愚蠢|笨蛋|傻瓜|滚开|闭嘴|讨厌|憎恨|亲吻|结婚|离婚|怀孕|男朋友|女朋友|战斗|武器|敌人|枪|子弹")


def download():
    CACHE.mkdir(parents=True, exist_ok=True)
    for filename, url in SOURCES.items():
        path = CACHE / filename
        if not path.exists():
            response = requests.get(url, timeout=120)
            response.raise_for_status()
            path.write_bytes(response.content)


def reference(stage):
    words = []
    doc = fitz.open(CACHE / (stage.lower() + ".pdf"))
    for page in doc[3:]:
        for block in page.get_text("dict")["blocks"]:
            for line in block.get("lines", []):
                x = line["bbox"][0]
                # These two official PDFs have different column margins. Examples
                # are indented and must not become vocabulary headwords.
                head = (x < 52 or 308 < x < 324) if stage == "KET" else (x < 74 or 310 < x < 337)
                text = "".join(span["text"] for span in line["spans"]).strip()
                match = POS.match(text)
                if not head and not (stage == "PET" and match and match[1].strip() in PET_MARGIN_EXCEPTIONS):
                    continue
                if match:
                    word = re.sub(r"\s+", " ", match[1]).strip()
                    word = re.sub(r"^\((?:n|v|adj|adv)\)\s*", "", word)
                    if word and (word, match[2]) not in words:
                        words.append((word, match[2]))
    return words


def normalise(word):
    word = {"v/versus": "versus", "suprising": "surprising"}.get(word, word)
    word = word.replace("’", "'").replace("–", "-").replace("’", "'")
    word = re.sub(r"\s*\((?:Br|Am) Eng.*", "", word)
    word = re.sub(r"\s*\([^)]*(?:advertisement|hall|one|plural|ll|Internet)[^)]*\)", "", word)
    word = word.replace("yog(h)urt", "yoghurt").replace("(up)", "up")
    word = re.sub(r"\s*\([^)]*\)", "", word)
    if "/" in word:
        word = word.split("/")[0].strip()
    if "," in word:
        word = word.split(",")[0].strip()
    word = re.sub(r"\s+", " ", word).strip(" !.,()")
    word = {"café": "cafe"}.get(word, word)
    return word


def meaning(row, pos):
    lines = row.get("translation", "").replace("\\n", "\n").splitlines()
    match_pos = {"adj": ("a.", "adj."), "adv": ("adv.", "r."), "v": ("v.", "vi.", "vt."), "n": ("n.",), "mv": ("aux.", "v.",), "av": ("aux.", "v.",), "prep": ("prep.",), "conj": ("conj.",), "det": ("art.", "det.", "pron."), "pron": ("pron.",), "exclam": ("interj.",)}.get(pos, ())
    suitable = [line for line in lines if any(line.startswith(p + " ") for p in match_pos)]
    line = (suitable or [x for x in lines if not x.startswith("[")] or lines or [""])[0]
    line = re.sub(r"\[[^]]*\]", "", line)
    line = re.sub(r"^(?:a|adj|ad|adv|r|n|v|vi|vt|prep|pron|conj|interj|num|art|det|pl|abbr|aux|s)\.\s*", "", line)
    line = re.sub(r"\([^)]*\)", "", line).strip(" ,;；")
    senses = [x.strip() for x in re.split(r"[,，;；]", line) if x.strip()]
    return CC.convert("；".join(senses[:1]))[:45]


TOPICS = {
    "学校与学习": "学校 教室 学生 老师 教师 学习 课程 作业 考试 笔 字典 词典 词汇 语法 数学 科学 生物 地理 历史 图书馆 铅笔 课本 教育 阅读 写作 练习 校园 书包 大学",
    "家庭与朋友": "父亲 母亲 父母 兄弟 姐妹 家庭 家人 朋友 邻居 邻人 婴儿 孩子 祖父 祖母 儿子 女儿 叔叔 阿姨 妻子 丈夫 居住 家务 房子 卧室 客厅 厨房 浴室 家具 沙发 床 椅子 桌子 地板 窗户 门 楼梯 天花板",
    "食物与购物": "食物 水果 蔬菜 苹果 香蕉 橙子 面包 黄油 饼 牛奶 奶酪 肉 牛肉 猪肉 鸡肉 鸡蛋 甜点 蛋糕 汤 食盐 糖 餐 咖啡 茶 饮料 水 瓶 罐 袋 盒 钱 价格 商店 商场 购物 购买 买 卖 出售 花费 超市 货币 硬币 昂贵 便宜",
    "旅行与城市": "旅行 出行 火车 汽车 出租车 飞机 机场 航空 巴士 公共汽车 自行车 船 轮船 地铁 车站 交通 道路 公路 街道 小路 地图 方向 旅馆 酒店 住宿 机票 车票 护照 行李 游客 旅游 导游 城市 镇 村庄 桥 银行 邮局 博物馆 假期 海滩",
    "自然与动物": "动物 猫 狗 鸟 鱼 兔 兔子 鼠 老鼠 狮子 老虎 熊 熊猫 斑马 大象 猴子 长颈鹿 马 羊 牛 猪 鸡 鸭 鹅 蜜蜂 蝴蝶 昆虫 蛇 鲸 豚 树 花 叶 草 植物 森林 山 河 湖 海洋 海 天气 晴 阳光 下雨 雨 雪 风 云 环境 自然 地球 沙 漠",
    "兴趣与运动": "运动 体育 比赛 游戏 足球 篮球 网球 排球 羽毛球 游泳 跑步 跑步 跳舞 舞蹈 唱歌 音乐 乐器 钢琴 吉他 小提琴 鼓 电影 电影院 剧院 相机 摄影 照片 兴趣 爱好 艺术 绘画 油画 露营 徒步 攀登 俱乐部 球 球队 冠军 选手",
    "生活与健康": "身体 头 脸 眼睛 耳朵 鼻子 嘴 牙齿 手 手臂 脚 腿 心脏 健康 疾病 医生 护士 医院 药 伤 病 疲劳 累 饿 口渴 睡觉 睡眠 清洁 洗 衣服 衣着 衬衫 裤子 袜 鞋 外套 裙子 帽子 颜色 红色 蓝色 绿色 黄色 白色 黑色 时间 早晨 上午 下午 晚上 日历 星期 月份 小时 分钟 秒",
    "科技与信息": "计算机 电脑 互联网 网络 网站 软件 程序 应用 手机 电话 信息 消息 邮件 电子 键盘 屏幕 鼠标 打印 视频 电视 科技 技术 科学 电池 电 缆 设备 发明 实验 下载 上传 文件 密码 数据 摄像机 机器 机器人",
    "表达与思考": "想法 思考 认为 知道 知识 理解 明白 解释 意义 决定 选择 希望 计划 梦想 原因 结果 目的 建议 观点 意见 同意 反对 相信 怀疑 问题 回答 解决 答案 比较 分析 描述 表达 讨论 谈话 说 讲 告诉 询问 确信 肯定 否定 可能 必须 应该 需要 能力 改进 提高",
    "情感与品质": "开心 高兴 快乐 悲伤 难过 担心 忧虑 生气 愤怒 友好 善良 诚实 礼貌 勇敢 害羞 耐心 仔细 认真 安静 平静 镇静 自信 兴奋 惊讶 骄傲 自豪 感激 感谢 热情 可爱 聪明 有趣 美丽 漂亮 令人愉快 舒适 幸运 负责 独立 可靠",
}


def topic_for(word, gloss):
    scores = {key: sum(len(w) for w in values.split() if (len(w) > 1 and w in gloss) or w == gloss) for key, values in TOPICS.items()}
    best = max(scores, key=scores.get)
    return best if scores[best] else "常用词与表达"


def sentences():
    result = []
    for line in (CACHE / "cmn.txt").read_text(encoding="utf8").splitlines():
        c = line.split("\t")
        if len(c) != 3 or "CC-BY" not in c[2] or "tatoeba.org #" not in c[2]:
            continue
        english, chinese, attribution = c
        chinese = CC.convert(chinese)
        n = len(english.split())
        if 4 <= n <= 14 and len(english) <= 110 and len(chinese) <= 55 and not BAN.search(english) and not BAN_ZH.search(chinese):
            result.append((english, chinese, attribution))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    if args.download:
        download()
    ASSETS.mkdir(parents=True, exist_ok=True)
    notices = ASSETS / "licenses"
    notices.mkdir(exist_ok=True)
    dictionary = {r["word"]: r for r in csv.DictReader((CACHE / "ecdict.csv").open(encoding="utf8"))}
    lower_dictionary = {w.lower(): r for w, r in dictionary.items()}
    pairs = sentences()
    overrides = {}
    override_file = APP / "tools/editorial.tsv"
    if override_file.exists():
        for row in csv.DictReader(override_file.open(encoding="utf8"), delimiter="\t"):
            overrides[row["word"]] = row
    index = collections.defaultdict(list)
    for pair in pairs:
        for token in set(re.findall(r"[A-Za-z'-]+", pair[0].lower())):
            index[token].append(pair)
    items, mapping, missing, attributes = [], [], [], []
    done = set()
    references = {}
    for stage in ("KET", "PET"):
        refs = reference(stage)
        references[stage] = len(refs)
        for raw, pos in refs:
            word = normalise(raw)
            key = word if word in {"May", "Miss", "Mr", "Mrs", "Ms", "Dr", "ID", "IT", "PC", "CD", "DVD", "CV", "I"} else word.lower()
            if not key or not re.match(r"^[A-Za-z]", word):
                missing.append((stage, raw, "unrecognised headword"))
                continue
            item_id = stage.lower() + "_" + hashlib.sha256(key.encode()).hexdigest()[:12]
            mapping.append((stage, raw, word, item_id))
            if (stage, key) in done:
                continue
            done.add((stage, key))
            editorial = overrides.get(word) or overrides.get(key) or {}
            row = dictionary.get(word) or lower_dictionary.get(key.lower()) or lower_dictionary.get(key.lower().replace("-", " ")) or lower_dictionary.get(key.lower().replace(" ", "-")) or {}
            gloss = editorial.get("meaning") or meaning(row, pos)
            if not gloss or not re.search(r"[\u4e00-\u9fff]", gloss):
                missing.append((stage, raw, "no Chinese meaning"))
                continue
            example, translation, attribution = "", "", ""
            if editorial.get("example"):
                example, translation = editorial["example"], editorial["translation"]
            else:
                candidates = index.get(key.lower().split()[0], [])
                pattern = re.compile(r"(?<![A-Za-z])" + re.escape(word) + r"(?![A-Za-z])", re.I)
                candidates = [p for p in candidates if pattern.search(p[0])]
                senses = [t for t in re.split(r"[；，、,;. ]", gloss) if len(t) >= 2 or (len(t) == 1 and re.match(r"[\u4e00-\u9fff]", t))]
                def score(pair):
                    matches = sum(len(t) * 4 for t in senses if t in pair[1])
                    return matches * 100 - abs(len(pair[0].split()) - 8)
                if candidates:
                    chosen = max(candidates, key=score)
                    # A lexical match without a matching Chinese sense is not enough:
                    # e.g. bank (financial) must not receive a river-bank example.
                    if score(chosen) >= 100:
                        example, translation, attribution = chosen
            if not example:
                # Dictionary English and Chinese sense orders are not aligned.
                # Do not pair a possibly unrelated definition (pupil/iris, bank/river)
                # with a learner sense. A marked word card is honest and cannot be
                # accidentally selected for sentence/context questions.
                example = "Meaning: " + word
                translation = "词义提示：" + gloss
            topic = editorial.get("topic") or topic_for(word, gloss)
            audio = "audio/" + hashlib.sha256(key.encode()).hexdigest()[:16] + ".mp3"
            items.append((item_id, stage, topic, word, gloss, example, translation, audio))
            if attribution:
                attributes.append((item_id, example, translation, attribution))
    topic_order = list(TOPICS) + ["常用词与表达"]
    items.sort(key=lambda r: (("KET", "PET").index(r[1]), topic_order.index(r[2]), 0 if r[3] in overrides or r[3].lower() in overrides else 1, r[3].lower()))
    write_tsv(ASSETS / "curriculum.tsv", ("id", "stage", "topic", "word", "meaning", "example", "translation", "audio"), items)
    write_tsv(notices / "reference-coverage.tsv", ("stage", "source_headword", "course_word", "item_id"), mapping)
    write_tsv(notices / "uncovered.tsv", ("stage", "source_headword", "reason"), missing)
    write_tsv(notices / "sentence-attribution.tsv", ("item_id", "english", "chinese_simplified", "attribution"), attributes)
    shutil.copyfile(CACHE / "ECDICT-LICENSE.txt", notices / "ECDICT-LICENSE.txt")
    counts = {s: sum(r[1] == s for r in items) for s in references}
    report = {
        "version": "2026.11.07", "reference_headwords": references,
        "course_items": counts,
        "natural_examples": {s: sum(r[1] == s and not r[5].startswith("Meaning: ") for r in items) for s in references},
        "definition_clues": {s: sum(r[1] == s and r[5].startswith("Meaning: ") for r in items) for s in references},
        "editorial_examples": sum(bool((overrides.get(r[3]) or overrides.get(r[3].lower()) or {}).get("example")) for r in items),
        "missing": len(missing), "audio_files_expected": len(set(r[7] for r in items)),
        "review_status": "automated validation and spot checks; full human editorial review pending",
        "sources": {f: {"url": u, "sha256": hashlib.sha256((CACHE / f).read_bytes()).hexdigest()} for f, u in SOURCES.items()},
    }
    (notices / "coverage.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


def write_tsv(path, header, rows):
    with path.open("w", encoding="utf8", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE, quotechar=None, escapechar="\\")
        writer.writerow(header)
        for row in rows:
            row = [re.sub(r"[\t\r\n]+", " ", str(c)).strip() for c in row]
            writer.writerow(row)


if __name__ == "__main__":
    main()
