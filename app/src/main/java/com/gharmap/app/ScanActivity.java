package com.gharmap.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.UUID;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class ScanActivity extends Activity implements GLSurfaceView.Renderer {
    private long handle;private GLSurfaceView surface;private TextView status;
    private Button record;private boolean running=false,installRequested=false,automatic=false;
    private String capture="";private long lastUi=0;private boolean leaving=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handle=NativeBridge.create();FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.INK);
        surface=new GLSurfaceView(this);surface.setEGLContextClientVersion(2);surface.setPreserveEGLContextOnPause(true);surface.setRenderer(this);root.addView(surface);
        LinearLayout overlay=Ui.column(this);overlay.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,20));
        overlay.setBackgroundColor(0xc9153a36);
        overlay.addView(Ui.text(this,"ROOM CAPTURE",12,0xffb5e8b7));
        status=Ui.text(this,"Preparing camera…",16,Color.WHITE);overlay.addView(status);
        overlay.addView(Ui.text(this,"Move slowly across textured surfaces. Point the centre at a visible floor corner to mark it. Walk safely; do not watch the screen on stairs.",13,0xffe1ede5));
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(overlay,top);
        TextView cross=Ui.text(this,"＋",36,Color.WHITE);cross.setGravity(Gravity.CENTER);
        root.addView(cross,new FrameLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,60),Gravity.CENTER));
        LinearLayout controls=Ui.column(this);controls.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,14));controls.setBackgroundColor(0xd9153a36);
        record=Ui.button(this,"Record walkthrough",true,v->startRecording());controls.addView(record);
        controls.addView(Ui.button(this,"Mark corner at centre",false,v->surface.queueEvent(()->{
            String message=NativeBridge.markCorner(handle,surface.getWidth()/2f,surface.getHeight()/2f);automatic=false;notifyUser(message);
        })));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button undo=Ui.button(this,"Undo",false,v->surface.queueEvent(()->{NativeBridge.undo(handle);automatic=false;}));
        Button auto=Ui.button(this,"Try auto outline",false,v->surface.queueEvent(()->{
            String message=NativeBridge.autoOutline(handle);if(message.startsWith("Experimental"))automatic=true;notifyUser(message);
        }));
        row.addView(undo,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));row.addView(auto,new LinearLayout.LayoutParams(0,Ui.dp(this,48),2));controls.addView(row);
        controls.addView(Ui.button(this,"Review room →",true,v->review()));
        controls.addView(Ui.button(this,"Cancel scan",false,v->cancel()));
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(controls,bottom);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){var bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}return insets;
        });
        // A recreated AR session has a new coordinate origin: never restore old corners into it.
        if(state!=null)notifyUser("Previous session ended. Saved recordings remain local; start a fresh room capture.");
    }
    @Override protected void onResume(){super.onResume();resumeCapture();}
    private void resumeCapture(){
        if(running||leaving)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},7);return;}
        try {
            ArCoreApk.Availability availability=ArCoreApk.getInstance().checkAvailability(this);
            if(availability.isTransient()){status.setText("Checking camera tracking support…");status.postDelayed(()->{if(!isFinishing()&&!isDestroyed()&&hasWindowFocus())resumeCapture();},500);return;}
            if(!availability.isSupported()){unavailable("This phone does not support ARCore scanning. You can still enter measured rooms and export plans.");return;}
            if(ArCoreApk.getInstance().requestInstall(this,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){installRequested=true;return;}
            String error=NativeBridge.resume(handle,this);if(!error.isEmpty()){unavailable(error);return;}
            running=true;surface.onResume();
        }catch(Exception ex){unavailable("Camera tracking could not start: "+ex.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==7){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)resumeCapture();else unavailable("Camera permission is required to scan. Measured-room entry remains available.");}
    }
    private void unavailable(String message){if(leaving||isFinishing())return;leaving=true;
        new AlertDialog.Builder(this).setTitle("Scanning unavailable").setMessage(message).setCancelable(false).setPositiveButton("Back to property",(d,w)->finish()).show();}
    @Override protected void onPause(){
        if(surface!=null)surface.onPause();if(handle!=0)NativeBridge.pause(handle);running=false;super.onPause();
    }
    @Override protected void onDestroy(){if(handle!=0){NativeBridge.destroy(handle);handle=0;}super.onDestroy();}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){NativeBridge.surfaceCreated(handle);}
    @Override public void onSurfaceChanged(GL10 gl,int width,int height){NativeBridge.resize(handle,getWindowManager().getDefaultDisplay().getRotation(),width,height);}
    @Override public void onDrawFrame(GL10 gl){
        String state=NativeBridge.draw(handle);long now=System.currentTimeMillis();if(now-lastUi<250)return;lastUi=now;
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;try{
            JSONObject s=new JSONObject(state);String message=s.optString("error");
            status.setText(message.isEmpty()?((s.getBoolean("tracking")?"Tracking ready":"Move slowly · finding surfaces")+
                "\n"+s.getInt("corners")+" corners · "+s.getInt("walls")+" wall planes · "+(s.getBoolean("depth")?"depth available":"camera tracking")+
                (s.getBoolean("recording")?"\n● Recording on this device":"")):message);
        }catch(Exception ex){status.setText("Could not read camera state");}});
    }
    private void startRecording(){
        String directory=getIntent().getStringExtra("directory");if(directory==null){notifyUser("Missing project folder");return;}
        File dir=new File(directory);if(!dir.isDirectory()){notifyUser("Project folder is unavailable");return;}
        String name="scan-"+UUID.randomUUID()+".mp4";String path=new File(dir,name).getAbsolutePath();
        record.setEnabled(false);surface.queueEvent(()->{String result=NativeBridge.startRecording(handle,path);
            runOnUiThread(()->{if(result.isEmpty()){capture=name;record.setText("Walkthrough recording started");}else{record.setEnabled(true);notifyUser(result);}});
        });
    }
    private void review(){
        surface.queueEvent(()->{String points=NativeBridge.polygon(handle);runOnUiThread(()->showReview(points));});
    }
    private void showReview(String points){
        try {
            double[] xy=ProjectStore.coordinates(new JSONArray(points));String problem=NativeBridge.validate(xy);
            if(!problem.isEmpty()){notifyUser(problem);return;}
            LinearLayout box=Ui.card(this);EditText name=Ui.input(this,"Room name","Room");box.addView(name);
            box.addView(new PlanView(this,xy,null),new LinearLayout.LayoutParams(-1,Ui.dp(this,260)));
            box.addView(Ui.text(this,(automatic?"Experimental convex-room estimate. ":"Assisted floor-corner outline. ")+"Check every wall and dimension before relying on this plan. No doors or windows are inferred.",13,Ui.MUTED));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Review room outline").setView(box).setNegativeButton("Keep scanning",null).setPositiveButton("Save unverified room",null).create();
            dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
                String label=name.getText().toString().trim();if(label.isEmpty()||label.length()>100){name.setError("Use 1–100 characters");return;}
                dialog.getButton(-1).setEnabled(false);
                surface.queueEvent(()->{String recordingError=NativeBridge.stopRecording(handle);runOnUiThread(()->{
                    if(!recordingError.isEmpty())notifyUser(recordingError);
                    Intent result=new Intent().putExtra("name",label).putExtra("points",points).putExtra("capture",capture)
                        .putExtra("source",automatic?"experimental-wall-planes":"assisted-AR-corners");
                    setResult(RESULT_OK,result);leaving=true;dialog.dismiss();finish();
                });});
            }));dialog.show();
        }catch(Exception ex){notifyUser("Could not review outline: "+ex.getMessage());}
    }
    private void cancel(){
        new AlertDialog.Builder(this).setTitle("Discard room capture?").setMessage("The current outline and recording will be deleted.")
            .setNegativeButton("Keep scanning",null).setPositiveButton("Discard",(d,w)->surface.queueEvent(()->{
                NativeBridge.stopRecording(handle);String directory=getIntent().getStringExtra("directory");
                if(!capture.isEmpty()&&directory!=null){new File(directory,capture).delete();new File(directory,capture+".jsonl").delete();}
                runOnUiThread(()->{leaving=true;finish();});
            })).show();
    }
    private void notifyUser(String value){runOnUiThread(()->{if(!isFinishing())Toast.makeText(this,value,Toast.LENGTH_LONG).show();});}
    @Override public void onBackPressed(){cancel();}
}
