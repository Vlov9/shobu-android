from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math
ROOT=Path(__file__).parent.parent
OUT=Path(__file__).parent/'screenshots'
S=2.75; W,H=1080,2400; STATUS=68; NAV=68
DARK=dict(bg='#111c1d',panel='#1d2c2b',edge='#364842',ink='#efeada',muted='#9fab9d',gold='#dcaa63',woodd='#604e39',woodl='#ddc79d')
LIGHT=dict(bg='#f1eee5',panel='#fffdf7',edge='#cfc7b1',ink='#25342e',muted='#677469',gold='#a46726',woodd='#604e39',woodl='#ddc79d')
REG='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; BOLD='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def sc(v): return round(v*S)
def boxxy(x1,y1,x2,y2): return sc(x1), STATUS+sc(y1), sc(x2), STATUS+sc(y2)
def font(size,b=False): return ImageFont.truetype(BOLD if b else REG,max(10,sc(size)))
def txt(d,text,x,y,size,color,align='left',bold=False):
    f=font(size,bold); bb=d.textbbox((0,0),text,font=f); w=bb[2]-bb[0]
    xx=sc(x); yy=STATUS+sc(y)-bb[3]
    if align=='center': xx-=w//2
    elif align=='right': xx-=w
    d.text((xx,yy),text,font=f,fill=color)
def rr(d,x1,y1,x2,y2,r,fill,outline=None,width=1):
    d.rounded_rectangle(boxxy(x1,y1,x2,y2),radius=sc(r),fill=fill,outline=outline,width=max(1,sc(width)) if outline else 1)
def dot(d,x,y,r,fill,outline=None,width=1):
    X=sc(x); Y=STATUS+sc(y); R=sc(r)
    d.ellipse((X-R,Y-R,X+R,Y+R),fill=fill,outline=outline,width=max(1,sc(width)))
def stone(d,x,y,r,player):
    shadow='#141615' if player==1 else '#846f51'; fill='#2a302e' if player==1 else '#f2ead3'; hi='#636e65' if player==1 else '#fff9e6'; edge='#747f71' if player==1 else '#dbceb0'
    dot(d,x+r*.08,y+r*.13,r*1.02,shadow); dot(d,x,y,r,fill,edge,.75); dot(d,x-r*.28,y-r*.32,r*.21,hi)
def initial():
    return [[[2,2,2,2],[0,0,0,0],[0,0,0,0],[1,1,1,1]] for _ in range(4)]
def render(name,theme='dark',phase='setup',selected=False,victory=False,ai_chooser=False):
    p=DARK if theme=='dark' else LIGHT; im=Image.new('RGB',(W,H),p['bg']); d=ImageDraw.Draw(im)
    w=W/S; h=(H-STATUS-NAV)/S
    d.rectangle((0,STATUS,W,H-NAV),fill=p['bg'])
    # Status bar (system UI area), painted after the app background.
    d.text((60,13), '9:41', font=ImageFont.truetype(BOLD,28), fill=p['ink'])
    for i,hpx in enumerate([8,12,16,20]): d.rounded_rectangle((W-108+i*7,43-hpx,W-104+i*7,43),radius=2,fill=p['ink'])
    d.arc((W-75,22,W-49,48),210,330,fill=p['ink'],width=2); d.arc((W-70,27,W-54,43),210,330,fill=p['ink'],width=2); d.ellipse((W-64,39,W-60,43),fill=p['ink'])
    d.rounded_rectangle((W-43,24,W-19,44),radius=4,outline=p['ink'],width=2); d.rectangle((W-39,28,W-24,40),fill=p['ink']); d.rectangle((W-18,30,W-15,38),fill=p['ink'])
    # Header and controls
    rr(d,18,23,56,61,19,p['panel'],p['edge']); dot(d,38,42,6,p['gold'])
    for i in range(8):
        a=math.pi*i/4; d.line((sc(38+math.cos(a)*9),STATUS+sc(42+math.sin(a)*9),sc(38+math.cos(a)*12),STATUS+sc(42+math.sin(a)*12)),fill=p['gold'],width=sc(1.5))
    txt(d,'STONE & STEM  /  LOCAL TABLE',68,25,9,p['muted'],bold=True); txt(d,'SHŌBU',68,59,29,p['ink'],bold=True); txt(d,'Quiet moves. Decisive pushes.',70,80,12,p['muted'])
    rr(d,w-105,23,w-67,61,19,p['panel'],p['edge']); txt(d,'?',w-86,48,14,p['muted'],'center',True)
    rr(d,w-58,23,w-20,61,19,p['panel'],p['edge']); txt(d,'↻',w-39,48,15,p['muted'],'center',True)
    # Turn panel
    rr(d,18,99,w-18,164,17,p['panel'],p['edge']); stone(d,49,130,10,1)
    txt(d,'BLACK WINS' if victory else 'BLACK TO MOVE',70,126,15,p['ink'],bold=True)
    txt(d,'GAME OVER' if victory else ('02  /  PUSH' if phase=='push' else '01  /  SET UP'),w-33,126,10,p['gold'],'right',True)
    txt(d,'All opposing stones are off one board.' if victory else ('Repeat that move on a board of the other shade.' if phase=='push' else 'Move a stone on either of your homeboards.'),38,148,10,p['muted'])
    cell=max(25,min(46,(w-38)/8.48,(h-280)/8.48)); side=cell*4; gap=cell*.46; total=side*2+gap; left=(w-total)/2; top=(h+6-total)/2
    pos=[(left,top),(left+side+gap,top),(left,top+side+gap),(left+side+gap,top+side+gap)]
    bds=initial()
    if phase=='push': bds[2][3][0]=0; bds[2][2][0]=1
    if victory:
        for c in range(4): bds[0][0][c]=0
    for b,(bx,by) in enumerate(pos):
        dark=((b//2+b%2)&1)==0; base=p['woodd'] if dark else p['woodl']; frame='#7e674b' if dark else '#f2e0bc'; tile2='#695740' if dark else '#d3bc91'
        rr(d,bx-5,by-5,bx+side+5,by+side+5,12,'#23231e','#3c4137'); rr(d,bx-2,by-2,bx+side+2,by+side+2,9,frame)
        for r in range(4):
            for c in range(4):
                x1=bx+c*cell; y1=by+r*cell; col=base if (r+c)%2==0 else tile2
                d.rectangle((sc(x1),STATUS+sc(y1),sc(x1+cell),STATUS+sc(y1+cell)),fill=col,outline=('#4c4031' if dark else '#c2aa81'),width=max(1,sc(.65)))
        for r in range(4):
            for c in range(4):
                piece=bds[b][r][c]
                if phase=='push' and b==3 and r==3 and c==0: piece=1
                if piece: stone(d,bx+(c+.5)*cell,by+(r+.5)*cell,cell*.34,piece)
        if selected and b==2:
            for r,c in [(2,0),(1,0),(2,1),(1,2)]:
                x=bx+(c+.5)*cell; y=by+(r+.5)*cell
                d.ellipse((sc(x-cell*.37),STATUS+sc(y-cell*.37),sc(x+cell*.37),STATUS+sc(y+cell*.37)),fill=None,outline='#f1c279',width=sc(2))
                dot(d,x,y,2,'#f1c279')
            x=bx+.5*cell; y=by+3.5*cell
            d.ellipse((sc(x-cell*.43),STATUS+sc(y-cell*.43),sc(x+cell*.43),STATUS+sc(y+cell*.43)),fill=None,outline=p['gold'],width=sc(2.5))
        if phase=='push' and b==3:
            x=bx+.5*cell; y=by+2.5*cell; dot(d,x,y,cell*.34,p['bg']+'00','#e8b778',2); dot(d,x,y,2,'#e8b778')
        label=('BLACK HOME' if b>=2 else 'WHITE HOME')+'  ·  '+('DARK' if dark else 'LIGHT')
        txt(d,label,bx+side/2,(by-8 if b<2 else by+side+14),8,p['muted'],'center',True)
    ly=top+side+gap/2; d.line((sc(left+4),STATUS+sc(ly),sc(left+side*2+gap-4),STATUS+sc(ly)),fill='#696f5e',width=sc(1.2)); rr(d,w/2-35,ly-9,w/2+35,ly+9,9,p['bg']); txt(d,'HOME LINE',w/2,ly+3,7,p['muted'],'center',True)
    guide=min(h-103,top+side*2+gap+36); txt(d,'Match direction + distance  ·  push one stone' if phase=='push' else 'Choose a glowing destination  ·  1 or 2 spaces',w/2,guide,10,p['muted'],'center')
    y=h-65
    for (l,t,r,b),label,en in [((18,y,109,y+43),'UNDO',phase=='push'),((w/2-71,y,w/2+71,y+43),'NEW GAME',False),((w-101,y,w-18,y+43),'RULES',False)]:
        if label=='NEW GAME': label='PLAY MODE' if not ai_chooser else 'PLAY MODE'
        rr(d,l,t,r,b,22,'#435344' if en else p['panel'],'#78916b' if en else p['edge']); txt(d,label,(l+r)/2,y+26,10,p['ink'] if en else p['muted'],'center',True)
    if victory:
        over=Image.new('RGBA',(W,H),(0,0,0,0)); od=ImageDraw.Draw(over); od.rectangle((0,STATUS,W,H-NAV),fill=('#080e0fb7' if theme=='dark' else '#dddad0af')); im=im.convert('RGBA'); im.alpha_composite(over); im=im.convert('RGB'); d=ImageDraw.Draw(im)
        cw=min(w-44,340); l=(w-cw)/2; t=h*.37; b=h*.64
        rr(d,l,t,l+cw,b,24,p['panel'],p['edge'],1.4); stone(d,w/2,t+43,18,1); txt(d,'VICTORY',w/2,t+80,10,p['gold'],'center',True); txt(d,'BLACK WINS',w/2,t+113,26,p['ink'],'center',True); txt(d,'A board is clear of opposing stones.',w/2,t+136,10,p['muted'],'center')
        rr(d,w/2-74,b-52,w/2+74,b-14,19,p['gold'],p['gold']); txt(d,'PLAY AGAIN',w/2,b-28,10,p['bg'],'center',True)
    if ai_chooser:
        overlay=Image.new('RGBA',(W,H),(0,0,0,0)); od=ImageDraw.Draw(overlay); od.rectangle((0,STATUS,W,H-NAV),fill=('#080e0fd7' if theme=='dark' else '#dddad0c8')); im=im.convert('RGBA'); im.alpha_composite(overlay); im=im.convert('RGB'); d=ImageDraw.Draw(im)
        left=22; right=w-22; top=h*.27; bottom=h*.76
        rr(d,left,top,right,bottom,22,p['panel'],p['edge'],1.3); txt(d,'CHOOSE AI DIFFICULTY',left+22,top+35,18,p['ink'],bold=True); txt(d,'You play Black. The AI plays White.',left+22,top+58,10,p['muted'])
        for i,label in enumerate(['EASY','MEDIUM','HARD','BACK']):
            y=top+78+i*54; rr(d,left+18,y,right-18,y+46,23,p['panel'],p['edge']); txt(d,label,w/2,y+28,10,p['muted'],'center',True)
    d=ImageDraw.Draw(im); d.rectangle((0,H-NAV,W,H),fill=p['bg']); d.rounded_rectangle((W//2-sc(54),H-sc(16),W//2+sc(54),H-sc(12)),radius=sc(2),fill=p['ink'])
    im.save(OUT/name,optimize=True)

render('01-local-two-player-dark.png')
render('02-clear-move-highlights.png',phase='setup',selected=True)
render('03-clear-winner-light.png',theme='light',victory=True)
render('04-ai-difficulty-picker.png',ai_chooser=True)
