"""Package the app-rendered PNGs as portable brand assets and a Windows launcher icon.

Run IDEARM_VISUAL_SMOKE first, then:
    python scripts/export-branding.py scratch/workbench-review
BrandLogo.java renders the bundled brand PNG at each size. No imaging dependencies are needed.
"""
import argparse
from pathlib import Path
import shutil
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("snapshots", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
branding = root / "idearm-app/src/main/resources/io/github/dinamo541/idearm/app/branding"
sizes = (16, 24, 32, 48, 64, 128, 256)
images = [(args.snapshots / f"idearm-icon-{size}.png").read_bytes() for size in sizes]
for size, image in zip(sizes, images):
    if image[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", image[16:24]) != (size, size):
        raise ValueError(f"The icon must be a {size} x {size} PNG rendered by BrandLogo.")
# ICO supports embedded PNG payloads; 0 in the width/height bytes represents 256 pixels.
header = struct.pack("<HHH", 0, 1, len(images))
offset = 6 + 16 * len(images)
entries = []
for size, image in zip(sizes, images):
    entries.append(struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(image), offset))
    offset += len(image)
(branding / "idearm.ico").write_bytes(header + b"".join(entries) + b"".join(images))
shutil.copyfile(args.snapshots / "idearm-logo.png", branding / "idearm.png")
print(f"Exported PNG and Windows icon to {branding}")
