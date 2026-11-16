# -*- coding: utf-8 -*-
"""生成「学后看 · 电视英语」v1.6.1 公众号封面与更新速览图（可复用：改文案后重跑即可）。"""
import os
import random
from PIL import Image, ImageDraw, ImageFont

# 输出路径相对脚本所在目录，可随仓库迁移
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "images")
os.makedirs(OUT, exist_ok=True)
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_R = r"C:\Windows\Fonts\msyh.ttc"

BG = (245, 234, 211)
GREEN = (31, 91, 69)
ORANGE = (231, 162, 59)
BLUE = (138, 184, 198)
SCREEN = (220, 233, 220)
CREAM = (239, 224, 194)
DARK = (58, 62, 50)
WHITE = (255, 253, 247)
WOOD = (193, 145, 96)
LEAF = (38, 92, 70)
SOFT = (120, 130, 115)

SS = 4


def S(v):
    return int(round(v * SS))


def F(path, size):
    return ImageFont.truetype(path, S(size))


def rr(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle([S(box[0]), S(box[1]), S(box[2]), S(box[3])],
                        radius=S(r), fill=fill, outline=outline, width=S(width))


def el(d, box, fill, outline=None, width=1):
    d.ellipse([S(box[0]), S(box[1]), S(box[2]), S(box[3])],
              fill=fill, outline=outline, width=S(width))


def tx(d, xy, s, f, fill, anchor=None):
    d.text((S(xy[0]), S(xy[1])), s, font=f, fill=fill, anchor=anchor)


def paper(d, w, h):
    d.rectangle([0, 0, S(w), S(h)], fill=BG)
    random.seed(7)
    for _ in range(int(w * h / 260)):
        x, y = random.randint(0, w), random.randint(0, h)
        el(d, (x, y, x + 1.2, y + 1.2), (238, 226, 202))


def wrap(d, text, f, maxw):
    lines, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=f) / SS > maxw and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


# ---------------- 封面 900 x 383 ----------------
def cover():
    W, H = 900, 383
    im = Image.new("RGB", (S(W), S(H)), BG)
    d = ImageDraw.Draw(im)
    paper(d, W, H)

    # 右侧浅色大圆
    el(d, (450, 8, 880, 372), CREAM)

    # 电视柜
    rr(d, (468, 300, 832, 332), 8, WOOD)
    d.rectangle([S(560), S(304), S(562), S(329)], fill=(170, 125, 80))
    d.rectangle([S(740), S(304), S(742), S(329)], fill=(170, 125, 80))
    el(d, (600, 314, 607, 321), (240, 215, 180))
    el(d, (690, 314, 697, 321), (240, 215, 180))
    rr(d, (487, 332, 502, 346), 2, (170, 125, 80))
    rr(d, (798, 332, 813, 346), 2, (170, 125, 80))

    # 绿植（右侧）
    d.line([S(866), S(286), S(866), S(248)], fill=LEAF, width=S(3))
    el(d, (852, 232, 872, 272), LEAF)
    el(d, (834, 242, 854, 278), LEAF)
    el(d, (872, 244, 892, 278), LEAF)
    rr(d, (846, 282, 892, 288), 2, (205, 165, 120))
    rr(d, (850, 288, 888, 318), 4, (190, 150, 110))

    # 电视
    rr(d, (503, 70, 792, 290), 16, GREEN)
    rr(d, (519, 86, 776, 264), 8, SCREEN)
    rr(d, (558, 112, 737, 206), 10, WHITE)
    tx(d, (647, 158), "ABC", F(FONT_B, 52), GREEN, anchor="mm")
    rr(d, (566, 224, 618, 231), 3, BLUE)
    rr(d, (628, 224, 680, 231), 3, CREAM)
    rr(d, (690, 224, 730, 231), 3, ORANGE)
    el(d, (549, 274, 556, 281), ORANGE)
    el(d, (562, 274, 569, 281), BLUE)
    el(d, (575, 274, 582, 281), GREEN)
    rr(d, (628, 290, 668, 300), 3, GREEN)
    rr(d, (598, 298, 698, 307), 4, GREEN)

    # 循环更新徽章
    el(d, (770, 34, 838, 102), ORANGE)
    d.arc([S(786), S(50), S(822), S(86)], 35, 320, fill=WHITE, width=S(4))
    d.polygon([(S(822), S(52)), (S(822), S(68)), (S(810), S(60))], fill=WHITE)
    d.polygon([(S(786), S(82)), (S(786), S(66)), (S(798), S(74))], fill=WHITE)

    # 雾蓝气泡
    rr(d, (762, 118, 836, 162), 8, BLUE)
    d.polygon([(S(774), S(160)), (S(788), S(160)), (S(778), S(172))], fill=BLUE)
    rr(d, (774, 130, 824, 135), 2, WHITE)
    rr(d, (774, 143, 810, 148), 2, WHITE)

    # 音量柱
    rr(d, (812, 248, 821, 274), 3, (222, 186, 120))
    rr(d, (827, 236, 836, 274), 3, ORANGE)
    rr(d, (842, 222, 851, 274), 3, ORANGE)
    d.arc([S(800), S(232), S(818), S(252)], 300, 60, fill=GREEN, width=S(3))

    # 品牌小鸟
    d.polygon([(S(452), S(272)), (S(436), S(280)), (S(452), S(288))], fill=(24, 78, 58))
    el(d, (448, 242, 496, 296), GREEN)
    el(d, (460, 260, 492, 292), SCREEN)
    el(d, (450, 260, 472, 282), (24, 78, 58))
    el(d, (476, 252, 486, 262), WHITE)
    el(d, (480, 256, 484, 260), DARK)
    d.polygon([(S(494), S(262)), (S(507), S(266)), (S(494), S(271))], fill=ORANGE)
    rr(d, (466, 236, 490, 244), 3, ORANGE)
    rr(d, (472, 230, 486, 238), 2, ORANGE)
    d.line([S(466), S(296), S(466), S(302)], fill=ORANGE, width=S(3))
    d.line([S(482), S(296), S(482), S(302)], fill=ORANGE, width=S(3))

    # 左侧文字：品牌组合 + 固定句
    rr(d, (48, 42, 246, 76), 17, ORANGE)
    tx(d, (64, 50), "v1.6.1 · 本周三次更新", F(FONT_B, 14), WHITE)
    tx(d, (48, 100), "学后看 · 电视英语", F(FONT_B, 38), GREEN)
    rr(d, (48, 168, 104, 174), 3, ORANGE)
    tx(d, (48, 190), "更新了：待机醒来，管控还在", F(FONT_R, 22), DARK)
    tx(d, (48, 226), "先学一轮英语，再看一会儿电视", F(FONT_R, 17), SOFT)
    tx(d, (48, 258), "答题、音量、安装也都顺了", F(FONT_R, 15), SOFT)
    tx(d, (48, 330), "LearnThenWatch · 开源电视英语学习", F(FONT_R, 14), GREEN)

    im.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, "cover.png"))
    print("cover.png", W, H)


# ---------------- 更新速览 900 x 380 ----------------
def updates():
    W, H = 900, 380
    im = Image.new("RGB", (S(W), S(H)), BG)
    d = ImageDraw.Draw(im)
    paper(d, W, H)

    tx(d, (48, 28), "这一周改了什么", F(FONT_B, 28), GREEN)
    tx(d, (48, 72), "v1.5.5 → v1.6.0 → v1.6.1  ·  2026.11.09 – 11.11", F(FONT_R, 14), SOFT)

    cards = [
        (48, ORANGE, "v1.5.5", "11 月 9 日", "后台恢复",
         "开机、待机唤醒、应用更新时，会尝试自动接回观看管控。恢复时保留电视当前画面。老用户需补配一次。"),
        (340, BLUE, "v1.6.0", "11 月 11 日", "答题与过渡",
         "填空题不再朗读；其他选择题答后再读一次。到时先给约 2 秒缓冲提示。补齐特殊机型安装说明。"),
        (632, GREEN, "v1.6.1", "11 月 11 日", "音量条修复",
         "学习页改用普通窗口，不再挡住电视原生音量与静音提示；移除自定义音量条。"),
    ]
    for x, accent, ver, date, title, body in cards:
        rr(d, (x, 108, x + 260, 340), 14, WHITE)
        rr(d, (x, 108, x + 260, 116), 4, accent)
        tx(d, (x + 20, 130), ver, F(FONT_B, 24), GREEN)
        tx(d, (x + 20, 166), date, F(FONT_R, 13), SOFT)
        tx(d, (x + 20, 194), title, F(FONT_B, 18), DARK)
        y = 226
        for line in wrap(d, body, F(FONT_R, 14), 218):
            tx(d, (x + 20, y), line, F(FONT_R, 14), DARK)
            y += 22

    tx(d, (48, 352), "学后看 · 电视英语 ｜ LearnThenWatch ｜ 完整更新记录见仓库 CHANGELOG.md", F(FONT_R, 13), SOFT)

    im.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, "updates.png"))
    print("updates.png", W, H)


if __name__ == "__main__":
    cover()
    updates()
