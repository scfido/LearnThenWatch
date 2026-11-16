# -*- coding: utf-8 -*-
"""从 md 的 ARTICLE 区块生成可粘贴到公众号编辑器的 HTML（图片内嵌 base64）。"""
import os
import re
import base64

# 所有路径相对脚本所在目录，可随仓库迁移
BASE = os.path.dirname(os.path.abspath(__file__))
MD = os.path.join(BASE, "学后看-v1.6.1-公众号文章.md")
HTML = os.path.join(BASE, "学后看-v1.6.1-公众号文章.html")

TITLE = "学后看更新了：待机醒来，管控还在"
SUMMARY = "一周连发三个版本：待机唤醒自动恢复观看管控、答题朗读与到时缓冲调整、把挡住系统音量条的那一层拆掉。老用户需要补做一次配置。"

src = open(MD, encoding="utf-8").read()
article = src.split("<!-- ARTICLE_START -->")[1].split("<!-- ARTICLE_END -->")[0].strip()


def datauri(rel):
    p = os.path.join(BASE, rel.replace("/", os.sep))
    ext = os.path.splitext(p)[1].lower()
    mime = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}[ext]
    with open(p, "rb") as f:
        return "data:%s;base64,%s" % (mime, base64.b64encode(f.read()).decode())


def inline(s):
    return re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)


P = ('style="margin:0 0 18px;font-size:16px;line-height:1.85;'
     'color:#333;letter-spacing:.3px;"')
H3 = ('style="margin:34px 0 16px;padding-left:12px;border-left:4px solid #E7A23B;'
      'font-size:18px;line-height:1.5;color:#1F5B45;font-weight:700;"')
SMALL = ('style="margin:0 0 18px;font-size:13px;line-height:1.8;color:#999;"')
CAP = 'style="margin:-8px 0 24px;text-align:center;font-size:13px;color:#999;"'

out, in_ol = [], False
for raw in article.splitlines():
    line = raw.strip()
    if not line:
        if in_ol:
            out.append("</ol>")
            in_ol = False
        continue

    m = re.fullmatch(r"!\[(.*?)\]\((.*?)\)", line)
    if m:
        if in_ol:
            out.append("</ol>")
            in_ol = False
        alt, rel = m.group(1), m.group(2)
        out.append('<img src="%s" alt="%s" style="width:100%%;border-radius:6px;'
                   'margin:22px 0 8px;display:block;" />' % (datauri(rel), alt))
        out.append('<p %s>%s</p>' % (CAP, alt))
        continue

    m = re.fullmatch(r"\*\*(.+?)\*\*", line)
    if m:
        if in_ol:
            out.append("</ol>")
            in_ol = False
        out.append("<h3 %s>%s</h3>" % (H3, m.group(1)))
        continue

    m = re.match(r"^\d+\.\s+(.*)$", line)
    if m:
        if not in_ol:
            out.append('<ol style="margin:0 0 20px;padding-left:22px;color:#333;'
                       'font-size:16px;line-height:1.85;">')
            in_ol = True
        out.append('<li style="margin-bottom:8px;">%s</li>' % inline(m.group(1)))
        continue

    if in_ol:
        out.append("</ol>")
        in_ol = False
    sty = SMALL if (line.startswith("（") or line.startswith("github.com")) else P
    out.append("<p %s>%s</p>" % (sty, inline(line)))

if in_ol:
    out.append("</ol>")
body = "\n".join(out)

cover_uri = datauri("images/cover.png")

doc = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>学后看 · v1.6.1 公众号文章</title>
<style>
  body{margin:0;padding:28px 12px 60px;background:#f2f2f0;
       font-family:-apple-system,BlinkMacSystemFont,"Microsoft YaHei",sans-serif;}
  .wrap{max-width:720px;margin:0 auto;}
  .note{background:#FFF8E6;border:1px solid #EBD9AC;border-radius:8px;
        padding:18px 22px;margin-bottom:22px;font-size:14px;line-height:1.8;color:#6b5b3a;}
  .note b{color:#1F5B45;}
  .note img{width:100%;border-radius:6px;margin:12px 0 4px;display:block;}
  .note .k{color:#999;font-size:13px;}
  .paper{background:#fff;border-radius:8px;padding:34px 36px 44px;
         box-shadow:0 2px 12px rgba(0,0,0,.06);}
  @media (max-width:600px){.paper{padding:22px 18px 30px;}}
</style>
</head>
<body>
<div class="wrap">
  <div class="note">
    <b>发表前看这里</b>（此框不要复制进正文）<br />
    标题：<b>__TITLE__</b><br />
    摘要：__SUMMARY__<br />
    封面：images / cover.png（900×383，已在下方预览，请单独上传到封面位）<br />
    正文配图：updates.png（更新速览）、lesson.jpg（学习页示意），已内嵌在下方白纸里<br />
    用法：打开公众号图文编辑器 → 从下方白纸里框选正文复制 → 粘贴。若图片没自动上传，按 images 文件夹手动插入。
    <img src="__COVER__" alt="封面预览" />
    <span class="k">封面预览 · 900×383</span>
  </div>
  <div class="paper">
__BODY__
  </div>
</div>
</body>
</html>
"""
doc = (doc.replace("__TITLE__", TITLE)
          .replace("__SUMMARY__", SUMMARY)
          .replace("__COVER__", cover_uri)
          .replace("__BODY__", body))

with open(HTML, "w", encoding="utf-8") as f:
    f.write(doc)

assert "**" not in body and "![" not in body, "markdown 残留"
print("HTML written:", HTML)
print("size:", os.path.getsize(HTML), "bytes")
print("embedded images:", doc.count("data:image/"))
print("paragraphs:", body.count("<p "), "headings:", body.count("<h3 "))
