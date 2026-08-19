# -*- coding: utf-8 -*-
"""Erzeugt eine leere Strukturvorlage für die GameTests.

Eine .nbt-Struktur ist gzip-komprimiertes NBT. Der Aufbau ist klein genug,
um ihn von Hand zu schreiben — das spart es, Minecraft zu starten und die
Vorlage im Spiel zu speichern.
"""
import gzip
import struct
import os

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def name(text):
    data = text.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag_int(label, value):
    return bytes([TAG_INT]) + name(label) + struct.pack(">i", value)


def tag_string(label, value):
    data = value.encode("utf-8")
    return bytes([TAG_STRING]) + name(label) + struct.pack(">H", len(data)) + data


def tag_int_list(label, values):
    payload = bytes([TAG_LIST]) + name(label) + bytes([TAG_INT])
    payload += struct.pack(">i", len(values))
    for value in values:
        payload += struct.pack(">i", value)
    return payload


def tag_empty_list(label, element_type):
    return bytes([TAG_LIST]) + name(label) + bytes([element_type]) + struct.pack(">i", 0)


def tag_compound(label, body):
    return bytes([TAG_COMPOUND]) + name(label) + body + bytes([TAG_END])


def air_palette():
    """Eine Palette mit genau einem Eintrag: Luft."""
    entry = tag_string("Name", "minecraft:air")
    element = entry + bytes([TAG_END])
    payload = bytes([TAG_LIST]) + name("palette") + bytes([TAG_COMPOUND])
    payload += struct.pack(">i", 1) + element
    return payload


def build(size):
    body = b""
    body += tag_int_list("size", list(size))
    body += air_palette()
    body += tag_empty_list("blocks", TAG_COMPOUND)
    body += tag_empty_list("entities", TAG_COMPOUND)
    body += tag_int("DataVersion", 3955)   # 1.21.1
    return tag_compound("", body)


def main():
    target = os.path.join(
        r"D:\Projekte\FactoryNetwork\src\main\resources\data\factorynetwork\structure",
        "empty.nbt")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with gzip.open(target, "wb") as handle:
        handle.write(build((9, 5, 9)))
    print("geschrieben: " + target + " (" + str(os.path.getsize(target)) + " Bytes)")


if __name__ == "__main__":
    main()
