"""Render the supplied artwork as a bottom-right adaptive-icon foreground.

Launchers only reveal the central part of the 108dp adaptive-icon layer
(~72-84dp depending on vendor), so the composition is pre-shrunk about the
layer centre. On a phone that crops the layer, the result then matches the
intended design instead of appearing zoomed-in.
"""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
source = root / '微信图片_20261018163238_274_46.png'
art = Image.open(source).convert('RGBA')

# White-on-black artwork: luminance is the effective alpha mask.
luma = art.convert('L')
bounds = luma.point(lambda v: 255 if v > 32 else 0).getbbox()
assert bounds, 'no artwork'

CANVAS = 432                     # 108dp layer rendered at 4x
subject = luma.crop(bounds)

# Design pass: figure anchored bottom-right, jeans bleeding past the edges.
design_height = round(CANVAS * 0.92)
design_width = round(subject.width * design_height / subject.height)
scaled = subject.resize((design_width, design_height), Image.Resampling.LANCZOS)
design = Image.new('L', (CANVAS, CANVAS), 0)
design.paste(scaled, (CANVAS - design_width + round(CANVAS * 0.02),
                      CANVAS - design_height + round(CANVAS * 0.02)))

# Provisional crop compensation retained from 0.2.5. Screenshot segmentation
# did not establish a reliable device scale; 1.25 is an estimate, not a measured
# HyperOS property. The preview is a simulation and needs physical-device review.
CROP_ZOOM = 1.25
inner = round(CANVAS / CROP_ZOOM)
compensated = design.resize((inner, inner), Image.Resampling.LANCZOS)
offset = (CANVAS - inner) // 2

alpha = Image.new('L', (CANVAS, CANVAS), 0)
alpha.paste(compensated, (offset, offset))
# Keep the frame edges inked so the mask rounding never reveals a seam.
ImageDraw.Draw(alpha).rectangle((0, 0, CANVAS - 1, 2), fill=0)
foreground = Image.new('RGBA', (CANVAS, CANVAS), 'white')
foreground.putalpha(alpha)

out = root / 'app/src/main/res/drawable-nodpi/launcher_artwork.png'
out.parent.mkdir(parents=True, exist_ok=True)
foreground.save(out)

# Previews: what a full-layer launcher shows, and what a cropping launcher shows.
def tile_of(image):
    tile = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 255))
    tile.alpha_composite(image)
    return tile

def masked(tile, radius_factor):
    mask = Image.new('L', (CANVAS, CANVAS), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, CANVAS - 1, CANVAS - 1), radius=round(CANVAS * radius_factor), fill=255)
    out_tile = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
    out_tile.paste(tile, (0, 0), mask)
    return out_tile

full = tile_of(foreground)
# Simulated vendor view: show only the central 1/CROP_ZOOM, enlarged back to icon size.
crop_box = (offset, offset, offset + inner, offset + inner)
cropped = full.crop(crop_box).resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)

tiles = [('layer', masked(full, 0.26)), ('vendor crop', masked(cropped, 0.26))]
pad, gap = 24, 12
preview = Image.new('RGBA', (len(tiles) * CANVAS + (len(tiles) + 1) * gap,
                              CANVAS + 2 * pad), (245, 245, 245, 255))
for i, (_, tile) in enumerate(tiles):
    preview.alpha_composite(tile, (gap + i * (CANVAS + gap), pad))
preview_path = root / 'docs/图标预览-右下贴边.png'
preview_path.parent.mkdir(parents=True, exist_ok=True)
preview.save(preview_path)

print(f'bounds={bounds}; inner={inner}px at offset {offset}; '
      f'compensation {CROP_ZOOM:.2f}x; design {design_width}x{design_height}')
