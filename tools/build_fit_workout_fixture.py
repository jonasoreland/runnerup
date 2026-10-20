"""Build an original, deterministic FIT Workout test file from protocol fields.

The file contains a running warm-up and a 400 m work interval. No Garmin SDK
code or binary assets are redistributed. The generated FIT file is checked by
an independent BSD-licensed decoder during development.
"""

from pathlib import Path
import struct
import sys

OUTPUT = Path(__file__).resolve().parents[1] / "app/test/resources/fit/running-intervals.fit"


def crc(data: bytes) -> int:
    value = 0
    for byte in data:
        value ^= byte
        for _ in range(8):
            value = (value >> 1) ^ (0xA001 if value & 1 else 0)
    return value & 0xFFFF


def definition(local: int, global_number: int, fields: list[tuple[int, int, int]]) -> bytes:
    return bytes([0x40 | local, 0, 0]) + struct.pack("<HB", global_number, len(fields)) + b"".join(
        bytes(field) for field in fields
    )


def string(value: str, size: int) -> bytes:
    return value.encode("utf-8").ljust(size, b"\0")


def step(index: int, name: str, duration_type: int, duration: int, intensity: int) -> bytes:
    return (
        bytes([2])
        + struct.pack("<H", index)
        + string(name, 16)
        + bytes([duration_type])
        + struct.pack("<I", duration)
        + bytes([2])  # open target
        + struct.pack("<I", 0)
        + bytes([intensity])
    )


def build() -> bytes:
    records = b"".join(
        [
            definition(0, 0, [(0, 1, 0), (1, 2, 0x84)]),
            bytes([0, 5]) + struct.pack("<H", 255),  # File ID: workout, development manufacturer
            definition(1, 26, [(4, 1, 0), (6, 2, 0x84), (8, 24, 7)]),
            bytes([1, 1]) + struct.pack("<H", 2) + string("Relven intervals", 24),
            definition(
                2,
                27,
                [
                    (254, 2, 0x84),
                    (0, 16, 7),
                    (1, 1, 0),
                    (2, 4, 0x86),
                    (3, 1, 0),
                    (4, 4, 0x86),
                    (7, 1, 0),
                ],
            ),
            step(0, "Warm up", 0, 600_000, 2),
            step(1, "400 m", 1, 40_000, 0),
        ]
    )
    header_without_crc = bytes([14, 0x20]) + struct.pack("<HI", 0, len(records)) + b".FIT"
    header = header_without_crc + struct.pack("<H", crc(header_without_crc))
    body = header + records
    return body + struct.pack("<H", crc(body))


if __name__ == "__main__":
    expected = build()
    if "--check" in sys.argv:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != expected:
            raise SystemExit("FIT fixture is missing or stale")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(expected)
        print(f"Wrote {OUTPUT} ({len(expected)} bytes)")
