from PIL import Image, ImageDraw
from pathlib import Path
root=Path(__file__).resolve().parents[2]; S=8; size=108*S
im=Image.new('RGBA',(size,size),(252,234,240,255)); d=ImageDraw.Draw(im); rose=(214,93,138,255)
def cubic(p0,p1,p2,p3):
    return [tuple((1-t)**3*p0[j]+3*(1-t)**2*t*p1[j]+3*(1-t)*t*t*p2[j]+t**3*p3[j] for j in range(2)) for t in (i/32 for i in range(1,33))]
segments=[((39,52),(38,45),(43,42),(51,42)),((51,42),(58,42),(63,47),(62,53)),((62,53),(61,59),(57,61),(57,68)),((57,68),(57,75),(53,80),(48,80)),((48,80),(43,80),(40,76),(41,70)),((41,70),(42,64),(40,58),(39,52))]
points=[(39,52)]
for seg in segments: points.extend(cubic(*seg))
d.polygon([(x*S,y*S) for x,y in points],fill=rose)
for cx,cy,rx,ry in [(41,32,6,7),(53,32,4.5,5),(62.5,35,3.5,4)]: d.ellipse(((cx-rx)*S,(cy-ry)*S,(cx+rx)*S,(cy+ry)*S),fill=rose)
for cx,cy in [(72,40),(67.5,43.3),(69.2,48.5),(74.8,48.5),(76.5,43.3)]:
    r=2.8; d.ellipse(((cx-r)*S,(cy-r)*S,(cx+r)*S,(cy+r)*S),fill=rose)
d.ellipse(((72-1.6)*S,(44.5-1.6)*S,(72+1.6)*S,(44.5+1.6)*S),fill=(255,248,248,255))
# Android reserves the outer 18dp for adaptive motion/masking; legacy assets use the visible 72dp viewport.
visible=im.crop((18*S,18*S,90*S,90*S))
def resized(n,round=False):
    v=visible.resize((n,n),Image.Resampling.LANCZOS)
    if round:
        mask=Image.new('L',visible.size,0); ImageDraw.Draw(mask).ellipse((0,0,visible.width-1,visible.height-1),fill=255); v.putalpha(mask.resize((n,n),Image.Resampling.LANCZOS))
    return v
for density,n in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    for suffix,round in [('',False),('_round',True)]: resized(n,round).save(root/f'app/src/main/res/mipmap-{density}/ic_launcher{suffix}.webp','WEBP',lossless=True)
resized(512).save(root/'docs/assets/app-icon-preview.png')
# Review board includes square/circle masks, a monochrome theme preview, and native small sizes.
board=Image.new('RGB',(1040,360),'white'); bd=ImageDraw.Draw(board)
try:
    from PIL import ImageFont
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',18)
except OSError:
    font=None
for label,n,x,y,rounded in [('Square',240,20,58,False),('Round',240,290,58,True),('Themed',240,560,58,True),('48px',48,855,90,False),('24px',24,955,102,False)]:
    icon=resized(n,rounded)
    if label=='Themed':
        color=icon.load()
        for yy in range(n):
            for xx in range(n):
                r,g,b,a=color[xx,yy]
                if a:
                    color[xx,yy]=(116,39,76,a) if r>170 and g<170 and b<195 else (247,217,231,a)
    board.paste(icon,(x,y),icon)
    bd.text((x,y+n+12),label,fill=(67,46,56),font=font)
board.save(root/'docs/assets/app-icon-sizes.png')
