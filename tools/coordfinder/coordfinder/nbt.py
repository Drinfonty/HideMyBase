"""A small, complete NBT reader/writer.

Only here because the capture harness has to place the player at an exact coordinate looking
straight down, and that means editing level.dat. Kept general and tested rather than hacked to the
one field, because a half-correct NBT writer silently truncates the file and Minecraft then quietly
regenerates the world instead of failing.
"""

from __future__ import annotations

import gzip
import struct
from dataclasses import dataclass
from typing import Any, BinaryIO

END = 0
BYTE = 1
SHORT = 2
INT = 3
LONG = 4
FLOAT = 5
DOUBLE = 6
BYTE_ARRAY = 7
STRING = 8
LIST = 9
COMPOUND = 10
INT_ARRAY = 11
LONG_ARRAY = 12


@dataclass
class Tag:
    """A tag keeps its own type so a round trip cannot silently change one."""

    type: int
    value: Any


class _Reader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.pos = 0

    def take(self, count: int) -> bytes:
        chunk = self.data[self.pos : self.pos + count]

        if len(chunk) != count:
            raise ValueError("truncated NBT")

        self.pos += count
        return chunk

    def unpack(self, fmt: str):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self) -> str:
        return self.take(self.unpack(">H")).decode("utf-8", "surrogatepass")


def _read_payload(reader: _Reader, tag_type: int) -> Any:
    if tag_type == BYTE:
        return reader.unpack(">b")
    if tag_type == SHORT:
        return reader.unpack(">h")
    if tag_type == INT:
        return reader.unpack(">i")
    if tag_type == LONG:
        return reader.unpack(">q")
    if tag_type == FLOAT:
        return reader.unpack(">f")
    if tag_type == DOUBLE:
        return reader.unpack(">d")
    if tag_type == BYTE_ARRAY:
        return bytearray(reader.take(reader.unpack(">i")))
    if tag_type == STRING:
        return reader.string()
    if tag_type == LIST:
        element_type = reader.unpack(">b")
        count = reader.unpack(">i")
        # An empty list may declare element type END; preserve it so the file round-trips.
        return (element_type, [_read_payload(reader, element_type) for _ in range(count)])
    if tag_type == COMPOUND:
        entries: dict[str, Tag] = {}

        while True:
            child_type = reader.unpack(">b")

            if child_type == END:
                return entries

            name = reader.string()
            entries[name] = Tag(child_type, _read_payload(reader, child_type))
    if tag_type == INT_ARRAY:
        return [reader.unpack(">i") for _ in range(reader.unpack(">i"))]
    if tag_type == LONG_ARRAY:
        return [reader.unpack(">q") for _ in range(reader.unpack(">i"))]

    raise ValueError(f"unknown tag type {tag_type}")


def _write_string(out: bytearray, text: str) -> None:
    encoded = text.encode("utf-8", "surrogatepass")
    out += struct.pack(">H", len(encoded))
    out += encoded


def _write_payload(out: bytearray, tag_type: int, value: Any) -> None:
    if tag_type == BYTE:
        out += struct.pack(">b", value)
    elif tag_type == SHORT:
        out += struct.pack(">h", value)
    elif tag_type == INT:
        out += struct.pack(">i", value)
    elif tag_type == LONG:
        out += struct.pack(">q", value)
    elif tag_type == FLOAT:
        out += struct.pack(">f", value)
    elif tag_type == DOUBLE:
        out += struct.pack(">d", value)
    elif tag_type == BYTE_ARRAY:
        out += struct.pack(">i", len(value))
        out += bytes(value)
    elif tag_type == STRING:
        _write_string(out, value)
    elif tag_type == LIST:
        element_type, items = value
        out += struct.pack(">b", element_type)
        out += struct.pack(">i", len(items))

        for item in items:
            _write_payload(out, element_type, item)
    elif tag_type == COMPOUND:
        for name, tag in value.items():
            out += struct.pack(">b", tag.type)
            _write_string(out, name)
            _write_payload(out, tag.type, tag.value)

        out += b"\x00"
    elif tag_type == INT_ARRAY:
        out += struct.pack(">i", len(value))

        for item in value:
            out += struct.pack(">i", item)
    elif tag_type == LONG_ARRAY:
        out += struct.pack(">i", len(value))

        for item in value:
            out += struct.pack(">q", item)
    else:
        raise ValueError(f"unknown tag type {tag_type}")


def load(path) -> tuple[str, Tag]:
    """Read a gzipped NBT file, returning the root's name and tag."""
    with open(path, "rb") as handle:
        raw = handle.read()

    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)

    reader = _Reader(raw)
    root_type = reader.unpack(">b")
    root_name = reader.string()
    return root_name, Tag(root_type, _read_payload(reader, root_type))


def save(path, root_name: str, root: Tag) -> None:
    out = bytearray()
    out += struct.pack(">b", root.type)
    _write_string(out, root_name)
    _write_payload(out, root.type, root.value)

    with open(path, "wb") as handle:
        handle.write(gzip.compress(bytes(out)))
