#!/usr/bin/env python3
"""
Derive display assets from the delivered company logo.

Source (untouched): public/company-logo.png - 781x777 PNG, white background,
emblem on top and the "PVP STAFFING" lockup below it.

Outputs
  public/company-logo-trim.png  the logo trimmed to its ink, for the sign-in card
  public/company-mark.png       the emblem alone with the white background made
                                transparent, for the sidebar and small marks

The app falls back to a letter monogram when either file is missing, so this
script only needs to run when the logo changes:

    python scripts/derive_logo_assets.py
"""

import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = os.path.join(os.path.dirname(HERE), "public")
SRC = os.path.join(PUBLIC, "company-logo.png")


def read_png(path):
    data = open(path, "rb").read()
    pos, idat, w, h, ct = 8, b"", 0, 0, 6
    while pos < len(data):
        (ln,) = struct.unpack(">I", data[pos:pos + 4])
        typ = data[pos + 4:pos + 8].decode("latin1")
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == "IHDR":
            w, h, _bd, ct, _c, _f, _i = struct.unpack(">IIBBBBB", chunk)
        elif typ == "IDAT":
            idat += chunk
        pos += 12 + ln
    if ct != 6:
        sys.exit("expected an RGBA PNG, got colour type %d" % ct)

    raw = zlib.decompress(idat)
    bpp, stride, i = 4, w * 4, 0
    prev = bytearray(stride)
    rows = []
    for _y in range(h):
        ft = raw[i]
        i += 1
        line = bytearray(raw[i:i + stride])
        i += stride
        if ft == 1:
            for x in range(bpp, stride):
                line[x] = (line[x] + line[x - bpp]) & 255
        elif ft == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif ft == 3:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif ft == 4:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                c = prev[x - bpp] if x >= bpp else 0
                p = a + prev[x] - c
                pa, pb, pc = abs(p - a), abs(p - prev[x]), abs(p - c)
                pr = a if pa <= pb and pa <= pc else (prev[x] if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, rows


def pixels(rows, w):
    """Yield (row_index, [(r, g, b, a), ...]) for each row."""
    for y, row in enumerate(rows):
        yield y, [tuple(row[x:x + 4]) for x in range(0, w * 4, 4)]


def is_ink(px):
    r, g, b, a = px
    return a > 16 and (r < 235 or g < 235 or b < 235)


def ink_box(rows, w, y0, y1):
    min_x, max_x = w, -1
    first_y, last_y = None, None
    for y, line in pixels(rows[y0:y1], w):
        hit = False
        for x, px in enumerate(line):
            if is_ink(px):
                hit = True
                min_x = min(min_x, x)
                max_x = max(max_x, x)
        if hit:
            if first_y is None:
                first_y = y + y0
            last_y = y + y0
    if max_x < 0:
        sys.exit("no ink found in rows %d..%d" % (y0, y1))
    return min_x, first_y, max_x, last_y


def write_png(path, w, h, rows, alpha_from_white=False):
    out = bytearray()
    for row in rows:
        out.append(0)  # filter: none
        if alpha_from_white:
            for x in range(0, len(row), 4):
                r, g, b, a = row[x], row[x + 1], row[x + 2], row[x + 3]
                if a == 0:
                    out += b"\x00\x00\x00\x00"
                    continue
                # Distance from white becomes opacity, so anti-aliased edges stay smooth.
                alpha = max(0, min(255, int((255 - min(r, g, b)) * 1.25)))
                out += bytes((r, g, b, alpha))
        else:
            out += row

    def chunk(typ, payload):
        return (struct.pack(">I", len(payload)) + typ + payload
                + struct.pack(">I", zlib.crc32(typ + payload) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(out), 9))
    png += chunk(b"IEND", b"")
    open(path, "wb").write(png)
    print("wrote %s (%dx%d, %d bytes)" % (path, w, h, len(png)))


def crop(rows, w, box, pad=6):
    x0, y0, x1, y1 = box
    x0, y0 = max(0, x0 - pad), max(0, y0 - pad)
    x1, y1 = min(w - 1, x1 + pad), min(len(rows) - 1, y1 + pad)
    return [rows[y][x0 * 4:(x1 + 1) * 4] for y in range(y0, y1 + 1)], (x1 - x0 + 1)


def main():
    if not os.path.exists(SRC):
        sys.exit("missing source logo: %s" % SRC)

    w, h, rows = read_png(SRC)

    # Full lockup (emblem + wordmark + tagline), trimmed for the sign-in card.
    full_rows, full_w = crop(rows, w, ink_box(rows, w, 0, h), pad=8)
    write_png(os.path.join(PUBLIC, "company-logo-trim.png"), full_w, len(full_rows), full_rows)

    # Emblem only: the wordmark starts below the first large white gap.
    bands, start = [], None
    for y, line in pixels(rows, w):
        hit = any(is_ink(px) for px in line)
        if hit and start is None:
            start = y
        elif not hit and start is not None:
            bands.append((start, y - 1))
            start = None
    if start is not None:
        bands.append((start, h - 1))
    if not bands:
        sys.exit("logo looks empty")
    top = bands[0]
    emblem_rows, emblem_w = crop(rows, w, ink_box(rows, w, top[0], top[1] + 1), pad=4)
    write_png(os.path.join(PUBLIC, "company-mark.png"), emblem_w, len(emblem_rows), emblem_rows,
              alpha_from_white=True)


if __name__ == "__main__":
    main()
