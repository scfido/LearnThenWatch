# -*- coding: utf-8 -*-
"""生成公众号「贴图」图组：6 张竖版 3:4（1080×1440）图片。

贴图是公众号里与「写文章」并列的轻量形式：图片为主、短文案为辅。
路径全部相对脚本所在目录，源界面截图取自 docs/screenshots/。
改文案后在脚本所在目录重跑 `python make_tietu.py` 即可。
"""
import os
import random
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "images")
SRC = os.path.normpath(os.path.join(BASE, "..", "..", "screenshots"))
os.makedirs(OUT, exist_ok=True)

FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_R = r"C:\Windows\Fonts\msyh.ttc"

BG = (245, 234, 211)
GREEN = (31, 91, 69)
ORANGE = (231, 162, 59)
BLUE = (138, 184, 198)
SCREEN = (220, 233, 220)
CREAM = (239, 224, 194)
DEEP = (22, 42, 36)
DEEP_CARD = (31, 60, 51)
DEEP_SOFT = (70, 110, 95)
DARK = (58, 62, 50)
WHITE = (255, 253, 247)
SOFT = (120, 130, 115)
LINE = (214, 222, 208)
WOOD = (193, 145, 96)

W, H = 1080, 1440
SS = 2


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


def txr(d, x, y, s, f, fill):
    """右对齐（按文字实际宽度定位）。"""
    d.text((S(x) - d.textlength(s, font=f), S(y)), s, font=f, fill=fill)


def paper(d):
    d.rectangle([0, 0, S(W), S(H)], fill=BG)
    random.seed(11)
    for _ in range(2600):
        x, y = random.randint(0, W), random.randint(0, H)
        el(d, (x, y, x + 1.2, y + 1.2), (238, 226, 202))


NO_START = "、，。；：？！）】》」』%,.;:!?)"


def wrap(d, text, f, maxw):
    """按宽度折行，并避免标点落在行首。"""
    lines, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=f) / SS > maxw and cur:
            if ch in NO_START:
                lines.append(cur + ch)  # 标点跟上上一行，允许略微超宽
                cur = ""
            else:
                lines.append(cur)
                cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def paragraph(d, x, y, text, size, maxw, fill=DARK, lead=48, bold=False):
    f = F(FONT_B if bold else FONT_R, size)
    for line in wrap(d, text, f, maxw):
        tx(d, (x, y), line, f, fill)
        y += lead
    return y


def card(idx):
    """统一页眉页脚，返回画布与画笔。"""
    im = Image.new("RGB", (S(W), S(H)), BG)
    d = ImageDraw.Draw(im)
    paper(d)
    tx(d, (60, 50), "学后看 · 电视英语", F(FONT_B, 24), GREEN)
    txr(d, W - 60, 50, "v1.7.0 · 深浅配色", F(FONT_R, 22), SOFT)
    d.line([S(60), S(102), S(W - 60), S(102)], fill=LINE, width=S(2))
    d.line([S(60), S(1352), S(W - 60), S(1352)], fill=LINE, width=S(2))
    tx(d, (60, 1372), "先学一轮英语，再看一会儿电视", F(FONT_R, 22), SOFT)
    txr(d, W - 60, 1372, "%02d / 06" % idx, F(FONT_R, 22), SOFT)
    return im, d


def save(im, name):
    im.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, name))
    print(name, W, H)


def shot(name, w, h):
    p = os.path.join(SRC, name)
    return Image.open(p).convert("RGB").resize((S(w), S(h)), Image.LANCZOS)


def paste_shot(im, name, x, y, w, h):
    tile = shot(name, w, h)
    frame = Image.new("RGB", (S(w) + S(2), S(h) + S(2)), LINE)
    frame.paste(tile, (S(1), S(1)))
    im.paste(frame, (S(x - 1), S(y - 1)))


def label(d, x, y, text, bg, fg, w=132):
    rr(d, (x, y, x + w, y + 52), 10, bg)
    tx(d, (x + 22, y + 9), text, F(FONT_B, 26), fg)


# ---------------- 01 首图：大字标题 + 对半屏幕电视 ----------------
def c1():
    im, d = card(1)
    rr(d, (60, 152, 352, 208), 28, ORANGE)
    tx(d, (86, 164), "v1.7.0 · 深浅配色", F(FONT_B, 24), WHITE)

    tx(d, (60, 258), "学习页也能", F(FONT_B, 78), GREEN)
    tx(d, (60, 356), "跟着暗下来", F(FONT_B, 78), GREEN)
    rr(d, (60, 466, 184, 476), 5, ORANGE)
    tx(d, (60, 506), "学后看 · 电视英语 · 界面配色更新", F(FONT_R, 30), DARK)
    tx(d, (60, 552), "自动 / 浅色 / 深色 · 确认键切换", F(FONT_R, 26), SOFT)

    # 电视柜与支架
    rr(d, (196, 1120, 884, 1156), 8, WOOD)
    rr(d, (500, 1080, 580, 1122), 4, GREEN)
    rr(d, (450, 1110, 630, 1124), 6, GREEN)

    # 电视：左浅右深两块屏
    rr(d, (236, 656, 844, 1080), 24, GREEN)
    rr(d, (262, 684, 538, 1044), 12, SCREEN)
    rr(d, (544, 684, 820, 1044), 12, DEEP)
    rr(d, (286, 736, 514, 920), 16, WHITE)
    tx(d, (400, 828), "ABC", F(FONT_B, 56), GREEN, anchor="mm")
    rr(d, (568, 736, 796, 920), 16, DEEP_CARD, outline=(86, 140, 120), width=1)
    tx(d, (682, 828), "ABC", F(FONT_B, 56), WHITE, anchor="mm")
    for x, c in ((286, BLUE), (340, CREAM), (394, ORANGE)):
        rr(d, (x, 960, x + 62, 972), 5, c)
    for x, c in ((568, (110, 170, 145)), (622, (70, 105, 90)), (676, ORANGE)):
        rr(d, (x, 960, x + 62, 972), 5, c)
    for i, c in enumerate((ORANGE, BLUE, GREEN)):
        el(d, (280 + i * 24, 1060, 292 + i * 24, 1072), c)

    # 月亮徽章
    el(d, (784, 616, 884, 716), ORANGE)
    el(d, (802, 634, 858, 690), WHITE)
    el(d, (818, 626, 874, 682), ORANGE)
    for cx, cy in ((852, 646), (866, 664)):
        d.polygon([(S(cx), S(cy - 6)), (S(cx + 2), S(cy - 2)),
                   (S(cx + 6), S(cy)), (S(cx + 2), S(cy + 2)),
                   (S(cx), S(cy + 6)), (S(cx - 2), S(cy + 2)),
                   (S(cx - 6), S(cy)), (S(cx - 2), S(cy - 2))], fill=WHITE)

    # 品牌小鸟
    d.polygon([(S(196), S(1040)), (S(172), S(1052)), (S(196), S(1064))], fill=(24, 78, 58))
    el(d, (188, 992, 256, 1068), GREEN)
    el(d, (206, 1020, 248, 1062), SCREEN)
    el(d, (192, 1020, 222, 1050), (24, 78, 58))
    el(d, (230, 1006, 244, 1020), WHITE)
    el(d, (236, 1012, 240, 1016), DARK)
    d.polygon([(S(254), S(1024)), (S(272), S(1030)), (S(254), S(1036))], fill=ORANGE)
    rr(d, (212, 984, 244, 994), 4, ORANGE)

    tx(d, (60, 1210), "LearnThenWatch · 开源电视英语学习", F(FONT_R, 24), GREEN)
    tx(d, (60, 1256), "深色底配浅色文字，当前焦点一眼能看出来", F(FONT_R, 24), SOFT)
    save(im, "tietu-01.png")


# ---------------- 02 问题：暗画面 → 一整屏浅底 ----------------
def c2():
    im, d = card(2)
    tx(d, (60, 158), "改动的原因很具体", F(FONT_B, 54), GREEN)

    rr(d, (60, 272, 1020, 570), 18, DEEP)
    tx(d, (92, 296), "电视画面 · 暗", F(FONT_R, 26), (150, 190, 170))
    d.polygon([(S(500), S(382)), (S(500), S(492)), (S(590), S(437))], fill=DEEP_SOFT)
    rr(d, (92, 528, 420, 538), 5, (48, 78, 64))
    rr(d, (92, 548, 300, 558), 5, (48, 78, 64))

    tx(d, (540, 612), "观看时间到  ↓", F(FONT_B, 32), ORANGE, anchor="mm")

    rr(d, (60, 666, 1020, 964), 18, SCREEN)
    tx(d, (92, 690), "之前弹出的学习页 · 一整屏浅底", F(FONT_R, 26), GREEN)
    rr(d, (120, 736, 960, 934), 14, WHITE)
    tx(d, (540, 826), "ABC", F(FONT_B, 88), GREEN, anchor="mm")
    rr(d, (160, 900, 420, 910), 5, LINE)
    rr(d, (160, 920, 340, 930), 5, LINE)

    y = paragraph(d, 60, 1020,
                  "晚上客厅灯关了，电视画面大多是暗的。观看时间一到，自动弹出的学习页是一整屏浅底——切换那一下，光线跳得厉害。",
                  32, 960, lead=50)
    tx(d, (60, y + 24), "这次把学习页和其余页面都做成能变暗。", F(FONT_B, 32), GREEN)
    save(im, "tietu-02.png")


# ---------------- 03 三种主题 ----------------
def c3():
    im, d = card(3)
    tx(d, (60, 158), "三种主题，默认自动", F(FONT_B, 54), GREEN)
    tx(d, (60, 236), "按遥控器确认键依次切换，立即生效并自动保存", F(FONT_R, 28), SOFT)

    items = [
        (296, ORANGE, "自动", "跟随电视系统的深浅主题走。新装和旧版升级默认都是它。"),
        (526, SCREEN, "浅色", "想一直用浅色，固定住就行。"),
        (756, DEEP, "深色", "系统不提供深色状态时，手动选这个。"),
    ]
    for y, accent, title, body in items:
        rr(d, (60, y, 1020, y + 206), 18, WHITE)
        el(d, (100, y + 42, 180, y + 122), accent)
        tx(d, (218, y + 36), title, F(FONT_B, 42), GREEN)
        yy = y + 108
        for line in wrap(d, body, F(FONT_R, 28), 740):
            tx(d, (218, yy), line, F(FONT_R, 28), DARK)
            yy += 42

    rr(d, (60, 1010, 1020, 1230), 18, CREAM)
    tx(d, (92, 1034), "设置路径", F(FONT_B, 26), GREEN)
    tx(d, (92, 1080), "家长配置 → 系统与记录 → 界面主题", F(FONT_B, 34), DARK)
    tx(d, (92, 1140), "旧版本升上来不用补配；不新增授权，不用联网。", F(FONT_R, 26), SOFT)
    tx(d, (92, 1180), "电视系统没有深色状态时，自动模式会停在浅色。", F(FONT_R, 26), SOFT)
    save(im, "tietu-03.png")


# ---------------- 04 浅深对比 ----------------
def c4():
    im, d = card(4)
    tx(d, (60, 158), "同一页，两种配色", F(FONT_B, 54), GREEN)
    tx(d, (60, 236), "改的是界面配色，不是调电视屏幕亮度", F(FONT_R, 28), SOFT)

    paste_shot(im, "home.png", 90, 286, 900, 506)
    paste_shot(im, "home-dark.png", 90, 820, 900, 506)
    label(d, 112, 304, "浅色", SCREEN, GREEN)
    label(d, 112, 838, "深色", DEEP, WHITE)
    save(im, "tietu-04.png")


# ---------------- 05 覆盖页面 ----------------
def c5():
    im, d = card(5)
    tx(d, (60, 158), "到点弹出的那一页也换了", F(FONT_B, 48), GREEN)
    tx(d, (60, 230), "词卡、选项、答题反馈与完成页都跟着主题走", F(FONT_R, 28), SOFT)

    paste_shot(im, "lesson-dark.png", 90, 286, 900, 506)
    paste_shot(im, "map-dark.png", 90, 816, 440, 248)
    paste_shot(im, "progress-dark.png", 550, 816, 440, 248)
    tx(d, (90, 1074), "探险地图", F(FONT_R, 24), SOFT)
    tx(d, (550, 1074), "学习记录", F(FONT_R, 24), SOFT)

    y = paragraph(d, 60, 1130,
                  "首页、探险地图、学习记录、家长配置和 PIN 页也都有对应的深色配色。",
                  28, 960, lead=44)
    y = paragraph(d, 60, y + 8,
                  "实机切换主题时，当前页面、焦点、学习统计和观看余额保持连续。",
                  28, 960, lead=44)
    tx(d, (60, y + 20), "学习和观看的规则一个没改。", F(FONT_B, 30), GREEN)
    save(im, "tietu-05.png")


# ---------------- 06 边界与升级 ----------------
def c6():
    im, d = card(6)
    tx(d, (60, 158), "验到哪儿，没验到哪儿", F(FONT_B, 48), GREEN)

    pts = [
        "只在小米 MiTV-MFTR0 / Android 14 上做过实机检查，其他品牌、固件和系统版本还没验。",
        "自动模式取决于电视系统有没有深色状态；没有就停在浅色，手动选深色。",
        "任务数、复习、观看计时、家长密码、离线发音都没改。",
    ]
    for i, (y, text) in enumerate(zip((276, 476, 676), pts), 1):
        el(d, (60, y, 116, y + 56), ORANGE)
        tx(d, (88, y + 28), str(i), F(FONT_B, 30), WHITE, anchor="mm")
        yy = y + 2
        for line in wrap(d, text, F(FONT_R, 30), 880):
            tx(d, (140, yy), line, F(FONT_R, 30), DARK)
            yy += 46

    rr(d, (60, 880, 1020, 1160), 18, CREAM)
    tx(d, (92, 908), "v1.7.0 已发布", F(FONT_B, 38), GREEN)
    yy = paragraph(d, 92, 972,
                   "到 Releases 下载覆盖安装即可；安装方式还是 U 盘和 Agent 两种。",
                   28, 880, lead=44)
    tx(d, (92, yy + 12), "github.com/scfido/LearnThenWatch", F(FONT_R, 28), GREEN)

    tx(d, (60, 1216), "学后看 · 电视英语 · 项目开源，原创代码与文档用 MIT 协议", F(FONT_R, 26), SOFT)
    tx(d, (60, 1262), "界面截图为演示数据", F(FONT_R, 24), SOFT)
    save(im, "tietu-06.png")


if __name__ == "__main__":
    need = ("home.png", "home-dark.png", "lesson-dark.png", "map-dark.png", "progress-dark.png")
    missing = [n for n in need if not os.path.exists(os.path.join(SRC, n))]
    if missing:
        raise SystemExit("缺少源截图：%s（应在 %s）" % (", ".join(missing), SRC))
    for fn in (c1, c2, c3, c4, c5, c6):
        fn()
