"""Lays frames from OverlayFramesTest out as one contact sheet.

usage: contact_sheet.py <scene dir> <phase> <every nth frame> <columns> <scale> <out.png> [crop]

crop is left,top,right,bottom as fractions of the frame, e.g. 0.5,0.25,1,0.75.
"""
import sys,glob,os
from PIL import Image, ImageDraw
d,phase,step,cols,scale,out=sys.argv[1:7]
crop=[float(x) for x in sys.argv[7].split(',')] if len(sys.argv)>7 else None
files=sorted(glob.glob(os.path.join(d,phase+'_*.png')))[::int(step)]
ims=[]
for f in files:
    im=Image.open(f).convert('RGB')
    if crop:
        W,H=im.size; im=im.crop((int(crop[0]*W),int(crop[1]*H),int(crop[2]*W),int(crop[3]*H)))
    im=im.resize((int(im.width*float(scale)),int(im.height*float(scale))))
    ImageDraw.Draw(im).text((4,4),os.path.basename(f)[:-4].split('_')[1]+'ms',fill=(255,255,0))
    ims.append(im)
cols=int(cols); rows=(len(ims)+cols-1)//cols
w,h=ims[0].size
sheet=Image.new('RGB',(cols*w+(cols-1)*2,rows*h+(rows-1)*2),(255,0,255))
for i,im in enumerate(ims): sheet.paste(im,((i%cols)*(w+2),(i//cols)*(h+2)))
sheet.save(out); print(out,len(ims),sheet.size)
