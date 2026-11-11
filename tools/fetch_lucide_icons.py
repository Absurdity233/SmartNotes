#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 Lucide (https://lucide.dev, ISC License) 下载 SVG 图标并转换成
Android VectorDrawable，放到 app/src/main/res/drawable/。

Lucide 是 24×24 网格、2px 描边、圆头圆角的极简线性图标集，
与本项目的 Compose 主题配合良好，且体积远小于 material-icons-extended。
"""
import os
import re
import sys
import urllib.request

BASE = "https://cdn.jsdelivr.net/npm/lucide-static@1.70.0/icons/{}.svg"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")

# lucide 名称 -> Android 资源名
ICONS = {
    "plus": "ic_lucide_plus",
    "search": "ic_lucide_search",
    "arrow-left": "ic_lucide_arrow_left",
    "square": "ic_lucide_square",
    "square-check": "ic_lucide_square_check",
    "trash-2": "ic_lucide_trash",
    "pin": "ic_lucide_pin",
    "settings": "ic_lucide_settings",
    "user": "ic_lucide_user",
    "user-plus": "ic_lucide_user_plus",
    "users": "ic_lucide_users",
    "bell": "ic_lucide_bell",
    "x": "ic_lucide_close",
    "eye": "ic_lucide_eye",
    "eye-off": "ic_lucide_eye_off",
    "archive": "ic_lucide_archive",
    "archive-restore": "ic_lucide_archive_restore",
    "refresh-cw": "ic_lucide_refresh",
    "tag": "ic_lucide_tag",
    "rotate-ccw": "ic_lucide_restore",
    "palette": "ic_lucide_palette",
    "lock": "ic_lucide_lock",
    "sun": "ic_lucide_sun",
    "moon": "ic_lucide_moon",
    "info": "ic_lucide_info",
    "cloud-upload": "ic_lucide_cloud_upload",
    "cloud-off": "ic_lucide_cloud_off",
    "image-plus": "ic_lucide_image_plus",
    "send": "ic_lucide_send",
    "ellipsis-vertical": "ic_lucide_more",
    "pencil": "ic_lucide_pencil",
    "check": "ic_lucide_check",
    "calendar-days": "ic_lucide_calendar",
    "log-out": "ic_lucide_logout",
    "layout-grid": "ic_lucide_layout_grid",
    "alarm-clock": "ic_lucide_alarm",
    "cloud-check": "ic_lucide_cloud_check",
    "list-checks": "ic_lucide_list_checks",
    "image": "ic_lucide_image",
    "notebook-pen": "ic_lucide_notebook",
    "cloud-sun": "ic_lucide_cloud_sun",
    "list-todo": "ic_lucide_list_todo",
    "map-pin": "ic_lucide_map_pin",
    "clock": "ic_lucide_clock",
    "bell-ring": "ic_lucide_bell_ring",
    "rows-3": "ic_lucide_rows",
    "cloud": "ic_lucide_cloud",
}


def attr(tag, name, default=None):
    m = re.search(r'\b' + name + r'="([^"]*)"', tag)
    return m.group(1) if m else default


def circle_to_path(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return (f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 "
            f"a{r:g},{r:g} 0 1,0 {-2 * r:g},0 Z")


def rect_to_path(x, y, w, h, rx="0", ry=None):
    x, y, w, h = map(float, (x, y, w, h))
    rx = float(rx or 0)
    ry = float(ry) if ry else rx
    if rx <= 0:
        return f"M{x:g},{y:g} h{w:g} v{h:g} h{-w:g} Z"
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    return (f"M{x + rx:g},{y:g} H{x + w - rx:g} A{rx:g},{ry:g} 0 0 1 {x + w:g},{y + ry:g} "
            f"V{y + h - ry:g} A{rx:g},{ry:g} 0 0 1 {x + w - rx:g},{y + h:g} "
            f"H{x + rx:g} A{rx:g},{ry:g} 0 0 1 {x:g},{y + h - ry:g} "
            f"V{y + ry:g} A{rx:g},{ry:g} 0 0 1 {x + rx:g},{y:g} Z")


def points_to_path(points, close=False):
    pts = [p for p in re.split(r"[\s,]+", points.strip()) if p]
    pairs = list(zip(pts[0::2], pts[1::2]))
    if not pairs:
        return ""
    d = f"M{pairs[0][0]},{pairs[0][1]}"
    for px, py in pairs[1:]:
        d += f" L{px},{py}"
    return d + (" Z" if close else "")


def convert(svg):
    """把 SVG 里的基础图形统一转成 pathData 列表。"""
    paths = []
    for m in re.finditer(r"<(path|circle|rect|line|polyline|polygon)\b([^>]*)/?>", svg):
        kind, attrs = m.group(1), m.group(2)
        tag = kind + attrs
        if kind == "path":
            d = attr(tag, "d")
            if d:
                paths.append(d.strip())
        elif kind == "circle":
            paths.append(circle_to_path(attr(tag, "cx"), attr(tag, "cy"), attr(tag, "r")))
        elif kind == "rect":
            paths.append(rect_to_path(
                attr(tag, "x", "0"), attr(tag, "y", "0"),
                attr(tag, "width"), attr(tag, "height"),
                attr(tag, "rx", "0"), attr(tag, "ry"),
            ))
        elif kind == "line":
            paths.append(f"M{attr(tag, 'x1')},{attr(tag, 'y1')} L{attr(tag, 'x2')},{attr(tag, 'y2')}")
        elif kind == "polyline":
            paths.append(points_to_path(attr(tag, "points", "")))
        elif kind == "polygon":
            paths.append(points_to_path(attr(tag, "points", ""), close=True))
    return [p for p in paths if p]


AUTO_MIRRORED = {"ic_lucide_arrow_left", "ic_lucide_logout"}


def render(paths, stroke_width="2", auto_mirror=False):
    body = "\n".join(
        '    <path\n'
        '        android:pathData="%s"\n'
        '        android:fillColor="#00000000"\n'
        '        android:strokeColor="#FF000000"\n'
        '        android:strokeWidth="%s"\n'
        '        android:strokeLineCap="round"\n'
        '        android:strokeLineJoin="round" />' % (p, stroke_width)
        for p in paths
    )
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Lucide 图标 · https://lucide.dev · ISC License · 24dp / 2px 描边 -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="24"\n'
            '    android:viewportHeight="24"%s>\n'
            '%s\n'
            '</vector>\n' % ('\n    android:autoMirrored="true"' if auto_mirror else '', body))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    ok, bad = 0, []
    for lucide_name, res_name in ICONS.items():
        try:
            with urllib.request.urlopen(BASE.format(lucide_name), timeout=30) as r:
                svg = r.read().decode("utf-8")
            paths = convert(svg)
            if not paths:
                bad.append(lucide_name)
                continue
            with open(os.path.join(OUT_DIR, res_name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
                f.write(render(paths, auto_mirror=res_name in AUTO_MIRRORED))
            ok += 1
        except Exception as e:  # noqa: BLE001
            bad.append("%s (%s)" % (lucide_name, e))
    print("generated: %d" % ok)
    if bad:
        print("failed:", bad)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
