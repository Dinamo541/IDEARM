"""Package the app-rendered PNGs as portable brand assets and a Windows launcher icon.

Run IDEARM_VISUAL_SMOKE first, then:
    python scripts/export-branding.py scratch/workbench-review
The source SVG and BrandLogo.java share the same vector geometry. No imaging dependencies are needed.
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
image = (args.snapshots / "idearm-icon-256.png").read_bytes()
if image[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", image[16:24]) != (256, 256):
    raise ValueError("The icon must be a 256 x 256 PNG rendered by BrandLogo.")
# ICO supports embedded PNG payloads; 0 in the width/height bytes represents 256 pixels.
header = struct.pack("<HHH", 0, 1, 1)
entry = struct.pack("<BBBBHHII", 0, 0, 0, 0, 1, 32, len(image), 22)
(branding / "idearm.ico").write_bytes(header + entry + image)
shutil.copyfile(args.snapshots / "idearm-logo.png", branding / "idearm.png")
print(f"Exported PNG and Windows icon to {branding}")
