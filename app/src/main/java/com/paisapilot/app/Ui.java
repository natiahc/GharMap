package com.paisapilot.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG=0xfff3f6f1, INK=0xff153a36, GREEN=0xff197a67, MUTED=0xff5d6f67, RED=0xff9c3030;
    static int dp(Activity a,float value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    static TextView text(Activity a,String value,int size,int color){
        TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,5),0,dp(a,5));return v;
    }
    static TextView title(Activity a,String value){TextView v=text(a,value,30,INK);v.setTypeface(Typeface.create("serif",Typeface.NORMAL));return v;}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static LinearLayout card(Activity a){LinearLayout l=column(a);int p=dp(a,16);l.setPadding(p,p,p,p);l.setBackground(rounded(Color.WHITE,dp(a,18)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,8),0,dp(a,8));l.setLayoutParams(lp);return l;}
    static Button button(Activity a,String label,boolean primary,View.OnClickListener action){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:INK);
        b.setBackground(rounded(primary?GREEN:0xffe5eee7,dp(a,12)));b.setPadding(dp(a,10),dp(a,8),dp(a,10),dp(a,8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,5),0,dp(a,5));b.setLayoutParams(lp);b.setMinHeight(dp(a,48));b.setOnClickListener(action);return b;
    }
    static EditText input(Activity a,LinearLayout parent,String label,String value,boolean numeric){
        parent.addView(text(a,label,13,MUTED));EditText e=new EditText(a);e.setHint(label);e.setText(value);e.setTextColor(INK);e.setTextSize(16);e.setSingleLine(true);
        if(numeric)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(e);return e;
    }
    static Spinner choices(Activity a,LinearLayout parent,String label,String[] items,String selected){
        parent.addView(text(a,label,13,MUTED));Spinner s=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);
        for(int i=0;i<items.length;i++)if(items[i].equals(selected))s.setSelection(i);parent.addView(s);return s;
    }
    static ScrollView scroll(Activity a,LinearLayout content){ScrollView s=new ScrollView(a);s.setFillViewport(true);s.addView(content);return s;}
}
