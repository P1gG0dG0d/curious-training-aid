"""Read-only SFNT metadata inspection; Python standard library, no fontTools needed.
Run from project root: python licenses/inspect-font-metadata.py
Output is JSON on stdout; does not modify fonts or any other files.
"""
import hashlib
import json
from pathlib import Path
import struct

results = []
for path in sorted((Path(__file__).resolve().parent.parent / 'app/src/main/res/font').glob('*.ttf')):
    data = path.read_bytes()
    tables = {}
    for index in range(struct.unpack_from('>H', data, 4)[0]):
        tag, checksum, offset, size = struct.unpack_from('>4sIII', data, 12 + 16 * index)
        tables[tag.decode('ascii')] = (offset, size)
    offset, size = tables['name']
    fmt, count, strings = struct.unpack_from('>HHH', data, offset)
    names = []
    for index in range(count):
        platform, encoding, language, name_id, length, position = struct.unpack_from('>6H', data, offset + 6 + 12 * index)
        if name_id in (0, 1, 2, 3, 4, 5, 6, 8, 9, 11, 13, 14, 16, 17):
            raw = data[offset + strings + position:offset + strings + position + length]
            names.append({'nameID': name_id, 'platform': platform, 'encoding': encoding, 'language': language,
                          'value': raw.decode('utf-16-be' if platform in (0, 3) else 'mac_roman', errors='replace')})
    axes = []
    if 'fvar' in tables:
        start = tables['fvar'][0]
        axis_offset, reserved, axis_count, axis_size = struct.unpack_from('>4H', data, start + 4)
        for index in range(axis_count):
            tag, minimum, default, maximum, flags, name_id = struct.unpack_from('>4siiiHH', data, start + axis_offset + index * axis_size)
            axes.append({'tag': tag.decode('ascii'), 'min': minimum / 65536, 'default': default / 65536, 'max': maximum / 65536})
    results.append({'file': 'app/src/main/res/font/' + path.name, 'bytes': len(data),
                    'sha256': hashlib.sha256(data).hexdigest(),
                    'head_fontRevision': struct.unpack_from('>i', data, tables['head'][0] + 4)[0] / 65536,
                    'variation_axes': axes, 'names': names})
print(json.dumps(results, ensure_ascii=True, indent=2))
