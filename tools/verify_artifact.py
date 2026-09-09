#!/usr/bin/env python3
"""Inspect decompressed DEX string tables / JVM UTF8 pools for synthetic sentinels."""
import argparse
import io
import json
import struct
import zipfile
from pathlib import Path

SENTINELS = {
    "app": "client-java-sentinel-7349",
    "kotlin": "https://kotlin-sentinel.example.test/v1/network",
    "library": "/v1/library-sentinel-9275",
    "feature": "/v1/feature-sentinel-5821",
}


def strings(data):
    if data.startswith(b"dex\n"):
        count, offset = struct.unpack_from("<II", data, 56)
        for index in range(count):
            position, = struct.unpack_from("<I", data, offset + index * 4)
            while data[position] & 0x80:
                position += 1
            position += 1
            end = data.index(0, position)
            yield data[position:end]  # Sentinels are ASCII; modified UTF8 has same bytes.
    elif data.startswith(b"\xca\xfe\xba\xbe"):
        count, = struct.unpack_from(">H", data, 8)
        position, index = 10, 1
        widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                  12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        while index < count:
            tag = data[position]
            position += 1
            if tag == 1:
                length, = struct.unpack_from(">H", data, position)
                position += 2
                yield data[position:position + length]
                position += length
            else:
                position += widths[tag]
                if tag in (5, 6):
                    index += 1
            index += 1
    else:
        raise ValueError("Unsupported class/DEX format")


def inspect(data, name, found, counters):
    if name.endswith((".jar", ".aar", ".apk", ".aab")):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for item in archive.namelist():
                if item.endswith((".class", ".dex", ".jar")):
                    inspect(archive.read(item), name + "!" + item, found, counters)
    elif name.endswith((".class", ".dex")):
        counters["containers"] += 1
        for value in strings(data):
            for key, sentinel in SENTINELS.items():
                if sentinel.encode() in value:
                    found[key].add(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--absent", nargs="*", choices=SENTINELS, default=[])
    parser.add_argument("--present", nargs="*", choices=SENTINELS, default=[])
    args = parser.parse_args()
    found = {key: set() for key in SENTINELS}
    counters = {"containers": 0}
    inspect(args.artifact.read_bytes(), args.artifact.name, found, counters)
    assert counters["containers"] > 0, "No class/DEX containers inspected"
    failures = [key for key in args.absent if found[key]] + [key for key in args.present if not found[key]]
    print(json.dumps({"artifact": str(args.artifact), **counters,
                      "sentinelLocations": {k: sorted(v) for k, v in found.items()},
                      "passed": not failures}, indent=2))
    if failures:
        raise SystemExit("Unexpected sentinel state: " + ", ".join(failures))


if __name__ == "__main__":
    main()
