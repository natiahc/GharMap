package com.gharmap.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** A room's local coordinate frame. Independent scans are never falsely merged. */
final class PlanView extends View {
    interface Edited { void accept(double[] points); }
    private double[] xy;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private double minX,minY,scale=1;private float offsetX,offsetY;
    private final Edited edited;private int dragged=-1;private double[] before;
    PlanView(Activity a,double[] points,Edited edited){super(a);xy=points.clone();this.edited=edited;setContentDescription("Room floor plan. Dimensions in metres.");}
    private void fit(){
        if(xy.length<6)return;minX=xy[0];minY=xy[1];double maxX=minX,maxY=minY;
        for(int i=0;i<xy.length;i+=2){minX=Math.min(minX,xy[i]);minY=Math.min(minY,xy[i+1]);maxX=Math.max(maxX,xy[i]);maxY=Math.max(maxY,xy[i+1]);}
        float margin=60*getResources().getDisplayMetrics().density;
        scale=Math.min(Math.max(1,getWidth()-margin*2)/Math.max(.01,maxX-minX),Math.max(1,getHeight()-margin*2)/Math.max(.01,maxY-minY));
        offsetX=(float)((getWidth()-(maxX-minX)*scale)/2);offsetY=(float)((getHeight()-(maxY-minY)*scale)/2);
    }
    private float x(int i){return offsetX+(float)((xy[i]-minX)*scale);}
    private float y(int i){return offsetY+(float)((xy[i+1]-minY)*scale);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(xy.length<6)return;if(dragged<0)fit();
        float density=getResources().getDisplayMetrics().density;
        paint.setColor(0xffe6ede6);paint.setStrokeWidth(1);
        for(float i=0;i<getWidth();i+=24*density)c.drawLine(i,0,i,getHeight(),paint);
        for(float i=0;i<getHeight();i+=24*density)c.drawLine(0,i,getWidth(),i,paint);
        Path p=new Path();p.moveTo(x(0),y(0));for(int i=2;i<xy.length;i+=2)p.lineTo(x(i),y(i));p.close();
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xffd6ece0);c.drawPath(p,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(Ui.INK);paint.setStrokeWidth(3*density);c.drawPath(p,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(12*density);
        for(int i=0;i<xy.length;i+=2){int j=(i+2)%xy.length;
            c.drawText(String.format(Locale.US,"%.2f m",Math.hypot(xy[i]-xy[j],xy[i+1]-xy[j+1])),(x(i)+x(j))/2,(y(i)+y(j))/2-9*density,paint);
            if(edited!=null){paint.setColor(Ui.GREEN);c.drawCircle(x(i),y(i),7*density,paint);paint.setColor(Ui.INK);}
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(edited==null)return false;
        if(event.getAction()==MotionEvent.ACTION_DOWN){
            fit();double nearest=28*getResources().getDisplayMetrics().density;
            for(int i=0;i<xy.length;i+=2){double d=Math.hypot(event.getX()-x(i),event.getY()-y(i));if(d<nearest){nearest=d;dragged=i;}}
            if(dragged<0)return false;before=xy.clone();getParent().requestDisallowInterceptTouchEvent(true);return true;
        }
        if(dragged<0)return false;
        if(event.getAction()==MotionEvent.ACTION_MOVE){xy[dragged]=minX+(event.getX()-offsetX)/scale;xy[dragged+1]=minY+(event.getY()-offsetY)/scale;invalidate();return true;}
        if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){
            if(event.getAction()==MotionEvent.ACTION_CANCEL||!NativeBridge.validate(xy).isEmpty())xy=before;
            else edited.accept(xy.clone());
            dragged=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
