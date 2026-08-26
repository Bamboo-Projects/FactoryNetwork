"""Leitet die Texturen der Chemikalienzelle aus denen der Fluessigkeitszelle ab.

Der Unterschied ist der Farbton: Die Fluessigkeitszelle ist blau, die
Chemikalienzelle wird gruen-gelb — die Farbe, in der Mekanism seine Gase
zeichnet. Alles andere bleibt gleich, damit die drei Zellenarten als eine
Familie zu erkennen sind.

Reines Python: PNG lesen, entfiltern, Farbe drehen, wieder filtern.
"""
import colorsys
import io
import os
import struct
import zlib

SRC = 'src/main/resources/assets/factorynetwork/textures/item'
SIZES = {'64': '64k', '256': '256k', '1024': '1024k', '4096': '4096k'}


def read_png(path):
    data = io.open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    idat = b''
    width = height = 0
    i = 8
    while i < len(data):
        length = struct.unpack('>I', data[i:i + 4])[0]
        kind = data[i + 4:i + 8]
        body = data[i + 8:i + 8 + length]
        if kind == b'IHDR':
            width, height, depth, colour = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and colour == 6, (depth, colour)
        elif kind == b'IDAT':
            idat += body
        i += 12 + length
    return width, height, unfilter(zlib.decompress(idat), width, height)


def unfilter(raw, width, height):
    stride = width * 4
    out = bytearray()
    prev = bytearray(stride)
    pos = 0
    for _ in range(height):
        kind = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if kind == 1:
                line[x] = (line[x] + a) & 0xFF
            elif kind == 2:
                line[x] = (line[x] + b) & 0xFF
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 0xFF
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 0xFF
        out += line
        prev = line
    return out


def write_png(path, width, height, pixels):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw += pixels[y * stride:(y + 1) * stride]
    body = zlib.compress(bytes(raw), 9)

    def chunk(kind, data):
        return (struct.pack('>I', len(data)) + kind + data
                + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF))

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', body)
    out += chunk(b'IEND', b'')
    io.open(path, 'wb').write(out)


def recolour(pixels):
    """Dreht den Farbton auf Gruengelb und laesst Graues grau."""
    out = bytearray(pixels)
    for i in range(0, len(out), 4):
        r, g, b, a = out[i] / 255, out[i + 1] / 255, out[i + 2] / 255, out[i + 3]
        if a == 0:
            continue
        h, l, s = colorsys.rgb_to_hls(r, g, b)
        if s < 0.12:
            continue  # Gehaeuse und Kanten bleiben, wie sie sind
        # Blau (~0.58) wird Gruengelb (~0.22).
        h = (h + 0.64) % 1.0
        r, g, b = colorsys.hls_to_rgb(h, l, s)
        out[i], out[i + 1], out[i + 2] = int(r * 255), int(g * 255), int(b * 255)
    return out


for fluid, chemical in SIZES.items():
    src = os.path.join(SRC, 'fluid_cell_%s.png' % fluid)
    dst = os.path.join(SRC, 'chemical_cell_%s.png' % chemical)
    width, height, pixels = read_png(src)
    write_png(dst, width, height, recolour(pixels))
    print('geschrieben:', dst)
