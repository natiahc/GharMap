package com.gharmap.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG=Color.rgb(245,247,243),INK=Color.rgb(21,58,54),GREEN=Color.rgb(25,122,103),MUTED=Color.rgb(93,111,103);
    static int dp(Activity a,float value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    static TextView text(Activity a,String value,int size,int color){
        TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,5),0,dp(a,5));return v;
    }
    static TextView title(Activity a,String value){TextView v=text(a,value,32,INK);v.setTypeface(Typeface.create("serif",Typeface.NORMAL));return v;}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static LinearLayout card(Activity a){LinearLayout l=column(a);int p=dp(a,18);l.setPadding(p,p,p,p);l.setBackground(rounded(Color.WHITE,dp(a,20)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,8),0,dp(a,8));l.setLayoutParams(lp);return l;}
    static Button button(Activity a,String label,boolean primary,View.OnClickListener action){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:INK);
        b.setBackground(rounded(primary?GREEN:Color.rgb(229,238,231),dp(a,14)));b.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,52));lp.setMargins(0,dp(a,6),0,dp(a,6));b.setLayoutParams(lp);b.setOnClickListener(action);return b;
    }
    static EditText input(Activity a,String hint,String value){EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setTextColor(INK);e.setTextSize(16);e.setSingleLine(true);return e;}
}
