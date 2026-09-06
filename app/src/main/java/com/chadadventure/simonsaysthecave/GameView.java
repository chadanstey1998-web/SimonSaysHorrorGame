package com.chadadventure.simonsaysthecave;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.*;

public class GameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private Bitmap cave;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private enum Screen { MENU, PLAYING, GAME_OVER, HELP }
    private enum Puzzle { SIMON, ODD_ONE, MATH, REVERSE, COLOR_WORD, DONT_TAP }
    private Screen screen = Screen.MENU;
    private Puzzle puzzle = Puzzle.SIMON;

    private int level = 1, lives = 3, score = 0, bestLevel = 1;
    private float distance = 58f;
    private long lastTick = 0, puzzleStarted = 0;
    private String prompt = "";
    private final String[] answers = new String[4];
    private int correctIndex = 0;
    private boolean paused = false;
    private boolean flashRed = false;
    private long flashUntil = 0;

    public GameView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // Disable Android density scaling for the large background image.
        // When a large bitmap lives in res/drawable, decodeResource can otherwise
        // upscale it several times on high-density phones and cause an OOM crash
        // immediately when the app opens.
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inScaled = false;
        int caveResId = getResources().getIdentifier("cave_monster", "drawable", context.getPackageName());
        cave = BitmapFactory.decodeResource(getResources(), caveResId, bitmapOptions);
        if (cave != null) src.set(0, 0, cave.getWidth(), cave.getHeight());
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
        stroke.setColor(Color.rgb(130, 20, 20));
        lastTick = SystemClock.elapsedRealtime();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        drawBackground(c);
        if (screen == Screen.MENU) drawMenu(c);
        else if (screen == Screen.PLAYING) drawGame(c);
        else if (screen == Screen.GAME_OVER) drawGameOver(c);
        else drawHelp(c);

        if (screen == Screen.PLAYING && !paused) {
            long now = SystemClock.elapsedRealtime();
            float dt = Math.min(0.05f, (now - lastTick) / 1000f);
            lastTick = now;
            // Creature advances faster as levels rise.
            distance -= dt * (1.25f + level * 0.12f);
            if (distance <= 0f) loseLife();
            if (now - puzzleStarted > Math.max(2800, 7200 - level * 260L)) {
                wrongAnswer();
            }
            if (flashRed && now > flashUntil) flashRed = false;
            invalidate();
        }
    }

    private void drawBackground(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setColor(Color.BLACK); c.drawRect(0,0,w,h,p);
        if (cave != null) {
            float scale = Math.max(w/(float)cave.getWidth(), h/(float)cave.getHeight());
            float dw = cave.getWidth()*scale, dh = cave.getHeight()*scale;
            dst.set((w-dw)/2f,(h-dh)/2f,(w+dw)/2f,(h+dh)/2f);
            p.setAlpha(210); c.drawBitmap(cave, src, dst, p); p.setAlpha(255);
        }
        LinearGradient shade = new LinearGradient(0,0,0,h,0x22000000,0xE8000000,Shader.TileMode.CLAMP);
        p.setShader(shade); c.drawRect(0,0,w,h,p); p.setShader(null);
        if (flashRed) { p.setColor(0x55FF0000); c.drawRect(0,0,w,h,p); }
    }

    private void drawMenu(Canvas c) {
        int w=getWidth(), h=getHeight();
        text(c,"SIMON SAYS",w/2f,h*0.13f,w*0.09f,Color.WHITE,true);
        text(c,"HORROR GAME",w/2f,h*0.20f,w*0.075f,0xFFFF1B16,true);
        text(c,"THINK FAST. DON'T LET IT REACH YOU.",w/2f,h*0.27f,w*0.034f,0xFFE0E0E0,true);
        button(c,w*0.16f,h*0.58f,w*0.84f,h*0.67f,"PLAY");
        button(c,w*0.16f,h*0.70f,w*0.84f,h*0.79f,"HOW TO PLAY");
        text(c,"BEST LEVEL  " + bestLevel,w/2f,h*0.86f,w*0.035f,0xFFFFD369,true);
        text(c,"No ads • No accounts • Offline gameplay",w/2f,h*0.93f,w*0.028f,0xFFBDBDBD,true);
    }

    private void drawGame(Canvas c) {
        int w=getWidth(), h=getHeight();
        text(c,"LEVEL " + level,w*0.06f,h*0.065f,w*0.052f,Color.WHITE,false);
        text(c,String.format(Locale.US,"DISTANCE %.0f M",Math.max(0,distance)),w*0.94f,h*0.065f,w*0.040f,Color.WHITE,true);
        text(c,"LIVES " + hearts(),w*0.94f,h*0.11f,w*0.036f,0xFFFF3636,true);
        text(c,"SCORE " + score,w*0.06f,h*0.11f,w*0.032f,0xFFFFD369,false);
        text(c,"II",w*0.08f,h*0.16f,w*0.045f,Color.WHITE,true);

        // distance bar
        p.setColor(0xAA111111); c.drawRoundRect(w*0.59f,h*0.13f,w*0.94f,h*0.15f,16,16,p);
        p.setColor(distance<18?0xFFFF1A17:0xFFBE1D1D);
        c.drawRoundRect(w*0.59f,h*0.13f,w*(0.59f+0.35f*Math.min(1f,distance/60f)),h*0.15f,16,16,p);

        float boxTop=h*0.56f;
        p.setColor(0xD9161111); c.drawRoundRect(w*0.06f,boxTop,w*0.94f,h*0.67f,22,22,p);
        stroke.setColor(0xFF9F1A18); c.drawRoundRect(w*0.06f,boxTop,w*0.94f,h*0.67f,22,22,stroke);
        text(c,prompt,w/2f,h*0.615f,w*0.038f,Color.WHITE,true);

        float top=h*0.70f, bottom=h*0.90f, gap=w*0.025f;
        float bw=(w*0.88f-gap)/2f, left=w*0.06f;
        for(int i=0;i<4;i++) {
            int row=i/2,col=i%2;
            float x1=left+col*(bw+gap), y1=top+row*((bottom-top)/2f+gap/2f);
            float y2=y1+(bottom-top)/2f-gap/2f;
            answerButton(c,x1,y1,x1+bw,y2,answers[i],i);
        }
        text(c,"Correct answers push it back. Hesitate and it keeps crawling.",w/2f,h*0.955f,w*0.024f,0xFFCFCFCF,true);
        if(paused) {
            p.setColor(0xD9000000); c.drawRect(0,0,w,h,p);
            text(c,"PAUSED",w/2f,h*0.43f,w*0.075f,Color.WHITE,true);
            button(c,w*0.20f,h*0.50f,w*0.80f,h*0.59f,"RESUME");
            button(c,w*0.20f,h*0.62f,w*0.80f,h*0.71f,"MAIN MENU");
        }
    }

    private void drawGameOver(Canvas c) {
        int w=getWidth(),h=getHeight();
        p.setColor(0xBB000000);c.drawRect(0,0,w,h,p);
        text(c,"IT REACHED YOU",w/2f,h*0.31f,w*0.071f,0xFFFF211B,true);
        text(c,"LEVEL " + level + "   •   SCORE " + score,w/2f,h*0.39f,w*0.038f,Color.WHITE,true);
        button(c,w*0.16f,h*0.52f,w*0.84f,h*0.61f,"TRY AGAIN");
        button(c,w*0.16f,h*0.65f,w*0.84f,h*0.74f,"MAIN MENU");
    }

    private void drawHelp(Canvas c) {
        int w=getWidth(),h=getHeight();
        p.setColor(0xD9000000);c.drawRect(0,0,w,h,p);
        text(c,"HOW TO PLAY",w/2f,h*0.13f,w*0.065f,Color.WHITE,true);
        String[] lines={
                "Solve each brain teaser before the creature reaches you.",
                "Correct answer: pushes the creature farther away.",
                "Wrong answer: it lunges closer and costs time.",
                "Levels get faster and use different puzzle types.",
                "Watch for trick instructions such as DON'T TAP.",
                "Survive as long as you can and beat your best level."
        };
        for(int i=0;i<lines.length;i++) text(c,lines[i],w/2f,h*(0.25f+i*0.075f),w*0.029f,0xFFE6E6E6,true);
        button(c,w*0.16f,h*0.76f,w*0.84f,h*0.85f,"BACK");
        text(c,"Privacy Policy",w/2f,h*0.92f,w*0.030f,0xFF8FCBFF,true);
    }

    private String hearts(){ return lives==3?"♥ ♥ ♥":lives==2?"♥ ♥":lives==1?"♥":"—"; }

    private void button(Canvas c,float l,float t,float r,float b,String label){
        p.setColor(0xDD1B1B1B);c.drawRoundRect(l,t,r,b,22,22,p);
        stroke.setColor(0xFF8D1717);c.drawRoundRect(l,t,r,b,22,22,stroke);
        text(c,label,(l+r)/2,(t+b)/2+getWidth()*0.012f,getWidth()*0.045f,Color.WHITE,true);
    }
    private void answerButton(Canvas c,float l,float t,float r,float b,String label,int i){
        p.setColor(0xEA161616);c.drawRoundRect(l,t,r,b,18,18,p);
        stroke.setColor(0xFF5C5C5C);c.drawRoundRect(l,t,r,b,18,18,stroke);
        text(c,label,(l+r)/2,(t+b)/2+getWidth()*0.010f,getWidth()*0.037f,Color.WHITE,true);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean center){
        p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);
        p.setShadowLayer(7,2,3,Color.BLACK);c.drawText(s,x,y,p);p.clearShadowLayer();
    }

    private void startGame(){ level=1;lives=3;score=0;distance=58;screen=Screen.PLAYING;paused=false;newPuzzle();lastTick=SystemClock.elapsedRealtime();invalidate(); }
    private void newPuzzle(){
        puzzle=Puzzle.values()[rng.nextInt(Puzzle.values().length)];
        Arrays.fill(answers,"");
        switch(puzzle){
            case SIMON: {
                String[] opts={"RED","BLUE","GREEN","YELLOW"};
                correctIndex=rng.nextInt(4); prompt="SIMON SAYS: TAP " + opts[correctIndex];
                System.arraycopy(opts,0,answers,0,4); break;
            }
            case ODD_ONE: {
                prompt="FIND THE ODD ONE OUT";
                String common = rng.nextBoolean()?"▲":"●", odd=common.equals("▲")?"●":"▲";
                correctIndex=rng.nextInt(4);for(int i=0;i<4;i++)answers[i]=i==correctIndex?odd:common;break;
            }
            case MATH: {
                int a=2+rng.nextInt(8), b=2+rng.nextInt(7);int ans=a+b;
                prompt="QUICK MATH: " + a + " + " + b + " = ?"; correctIndex=rng.nextInt(4);
                for(int i=0;i<4;i++)answers[i]=String.valueOf(i==correctIndex?ans:Math.max(0,ans+(i-correctIndex)*2+(i==0?1:0)));
                break;
            }
            case REVERSE: {
                String[] seq={"3  1  4","4  1  3","1  4  3","3  4  1"};
                prompt="REVERSE: 4  1  3"; correctIndex=0;System.arraycopy(seq,0,answers,0,4);break;
            }
            case COLOR_WORD: {
                prompt="TAP THE WORD 'GREEN'";String[] opts={"BLUE","GREEN","RED","YELLOW"};
                correctIndex=1;System.arraycopy(opts,0,answers,0,4);break;
            }
            case DONT_TAP: {
                String[] opts={"SKULL","EYE","HAND","MOON"};correctIndex=1+rng.nextInt(3);
                prompt="DON'T TAP SKULL — TAP ANYTHING ELSE";System.arraycopy(opts,0,answers,0,4);break;
            }
        }
        puzzleStarted=SystemClock.elapsedRealtime();
    }

    private void correctAnswer(){ score += 100 + level*20; distance=Math.min(60f,distance+7.5f); level++; bestLevel=Math.max(bestLevel,level); newPuzzle(); }
    private void wrongAnswer(){ distance-=10f;flashRed=true;flashUntil=SystemClock.elapsedRealtime()+220;newPuzzle();if(distance<=0)loseLife(); }
    private void loseLife(){ lives--;distance=32f;flashRed=true;flashUntil=SystemClock.elapsedRealtime()+450;if(lives<=0){screen=Screen.GAME_OVER;}else newPuzzle(); }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=e.getX(),y=e.getY(),w=getWidth(),h=getHeight();
        if(screen==Screen.MENU){
            if(in(x,y,w*.16f,h*.58f,w*.84f,h*.67f)) startGame();
            else if(in(x,y,w*.16f,h*.70f,w*.84f,h*.79f)){screen=Screen.HELP;invalidate();}
        } else if(screen==Screen.HELP){
            if(in(x,y,w*.16f,h*.76f,w*.84f,h*.85f)){screen=Screen.MENU;invalidate();}
            else if(y>h*.88f){
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/playboygamesprivacy/home")));
            }
        } else if(screen==Screen.GAME_OVER){
            if(in(x,y,w*.16f,h*.52f,w*.84f,h*.61f))startGame();
            else if(in(x,y,w*.16f,h*.65f,w*.84f,h*.74f)){screen=Screen.MENU;invalidate();}
        } else if(screen==Screen.PLAYING){
            if(paused){
                if(in(x,y,w*.20f,h*.50f,w*.80f,h*.59f)){paused=false;lastTick=SystemClock.elapsedRealtime();invalidate();}
                else if(in(x,y,w*.20f,h*.62f,w*.80f,h*.71f)){paused=false;screen=Screen.MENU;invalidate();}
                return true;
            }
            if(y<h*.20f && x<w*.18f){paused=true;invalidate();return true;}
            float top=h*.70f,bottom=h*.90f,gap=w*.025f,bw=(w*.88f-gap)/2f,left=w*.06f;
            for(int i=0;i<4;i++){
                int row=i/2,col=i%2;float x1=left+col*(bw+gap),y1=top+row*((bottom-top)/2f+gap/2f),y2=y1+(bottom-top)/2f-gap/2f;
                if(in(x,y,x1,y1,x1+bw,y2)){
                    if(puzzle==Puzzle.DONT_TAP && i!=0) correctAnswer(); else if(i==correctIndex)correctAnswer(); else wrongAnswer();
                    invalidate();return true;
                }
            }
        }
        return true;
    }
    private boolean in(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}
}
