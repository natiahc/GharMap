package com.gharmap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MainActivity extends Activity {
    private ProjectStore store;private JSONObject project;private int floor=0;
    private LinearLayout body;private String exportType="";private int exportRoom=-1;
    private static final int SCAN=41,EXPORT=42;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new ProjectStore(this);
        if(state!=null){floor=state.getInt("floor",0);exportType=state.getString("exportType","");exportRoom=state.getInt("exportRoom",-1);
            String id=state.getString("project");if(id!=null)try{project=store.load(id);}catch(Exception ex){error(ex);}}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("floor",floor);
        out.putString("exportType",exportType);out.putInt("exportRoom",exportRoom);if(project!=null)out.putString("project",project.optString("id"));}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        body=Ui.column(this);int p=Ui.dp(this,24);body.setPadding(p,p,p,p);scroll.addView(body);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){var bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            return insets;
        });
        body.addView(Ui.text(this,"GHARMAP  /  EARLY ACCESS",12,Ui.GREEN));
        if(project==null)home();else detail();
    }
    private void home(){
        body.addView(Ui.title(this,"Every space,\na clearer picture."));
        body.addView(Ui.text(this,"Walk. Scan. Plan.\nYour projects stay on this device. Basic plans are free.",16,Ui.MUTED));
        body.addView(Ui.button(this,"+  New property",true,v->nameDialog("Name your property","",name->{project=store.create(name);floor=0;render();})));
        LinearLayout note=Ui.card(this);note.addView(Ui.text(this,"Start with one room",19,Ui.INK));
        note.addView(Ui.text(this,"Scan on a supported phone, or enter measured corners on any supported installation. Add floors without a paid limit.",14,Ui.MUTED));body.addView(note);
        body.addView(Ui.text(this,"YOUR PROPERTIES",12,Ui.MUTED));
        try {
            var projects=store.list();
            if(projects.isEmpty())body.addView(Ui.text(this,"No properties yet. Create your first project to begin.",16,Ui.MUTED));
            for(JSONObject p:projects){LinearLayout card=Ui.card(this);card.addView(Ui.text(this,p.getString("name"),22,Ui.INK));
                card.addView(Ui.text(this,p.getJSONArray("floors").length()+" floors · saved locally",14,Ui.MUTED));
                card.addView(Ui.button(this,"Open property →",false,v->{project=p;floor=0;render();}));body.addView(card);}
        }catch(Exception ex){error(ex);}
        body.addView(Ui.text(this,"Development build · Measurement accuracy has not been validated. Keep physical measurements for important decisions.",12,Ui.MUTED));
    }
    private void detail(){
        try {
            body.addView(Ui.button(this,"← All properties",false,v->{project=null;render();}));
            body.addView(Ui.title(this,project.getString("name")));
            body.addView(Ui.text(this,"Free plans · no application-imposed floor limit",14,Ui.MUTED));
            JSONArray floors=project.getJSONArray("floors");floor=Math.max(0,Math.min(floor,floors.length()-1));
            for(int i=0;i<floors.length();i++){final int index=i;
                body.addView(Ui.button(this,(i==floor?"●  ":"○  ")+floors.getJSONObject(i).getString("name"),i==floor,v->{floor=index;render();}));}
            body.addView(Ui.button(this,"+ Add floor",false,v->nameDialog("Floor name","Floor "+floors.length(),name->{
                floors.put(new JSONObject().put("name",name).put("rooms",new JSONArray()));floor=floors.length()-1;store.save(project);render();})));
            body.addView(Ui.button(this,"Scan a room",true,v->{
                Intent intent=new Intent(this,ScanActivity.class);intent.putExtra("directory",store.directory(project.optString("id")).getAbsolutePath());startActivityForResult(intent,SCAN);
            }));
            body.addView(Ui.button(this,"Enter measured room",false,v->measuredRoom()));
            JSONArray rooms=floors.getJSONObject(floor).getJSONArray("rooms");
            if(rooms.length()==0)body.addView(Ui.text(this,"This floor is empty. Scan a room or enter your measurements.",16,Ui.MUTED));
            if(rooms.length()>1)body.addView(Ui.text(this,"Rooms are shown separately. Independent scans have not been spatially aligned into a whole-floor plan.",14,Ui.MUTED));
            for(int i=0;i<rooms.length();i++){
                final int index=i;JSONObject room=rooms.getJSONObject(i);double[] xy=ProjectStore.coordinates(room.getJSONArray("points"));
                LinearLayout card=Ui.card(this);card.addView(Ui.text(this,room.getString("name"),22,Ui.INK));
                card.addView(Ui.text(this,String.format(Locale.US,"%.2f m² · %s",ProjectStore.area(xy),room.getString("source")),14,Ui.GREEN));
                PlanView view=new PlanView(this,xy,changed->{try{
                    room.put("points",ProjectStore.array(changed));room.put("edited",true);store.save(project);
                }catch(Exception ex){error(ex);}});
                card.addView(view,new LinearLayout.LayoutParams(-1,Ui.dp(this,300)));
                card.addView(Ui.text(this,"Drag a corner to adjust; use coordinates for exact edits. Confirm all measurements on site.",12,Ui.MUTED));
                card.addView(Ui.button(this,"Edit name & coordinates",false,v->editRoom(room)));
                card.addView(Ui.button(this,"Export room SVG",false,v->{exportRoom=index;exportType="svg";chooseExport("image/svg+xml","room.svg");}));
                card.addView(Ui.button(this,"Delete room",false,v->new AlertDialog.Builder(this).setTitle("Delete this room?")
                    .setMessage("Its associated scan recording will also be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                        try{deleteCapture(room);rooms.remove(index);store.save(project);render();}catch(Exception ex){error(ex);}}).show()));
                body.addView(card);
            }
            body.addView(Ui.button(this,"Export floor PDF",false,v->{exportType="pdf";chooseExport("application/pdf","floor-report.pdf");}));
            body.addView(Ui.button(this,"Export project JSON",false,v->{exportType="json";chooseExport("application/json","gharmap-project.json");}));
            body.addView(Ui.button(this,"Rename property",false,v->nameDialog("Property name",project.optString("name"),name->{project.put("name",name);store.save(project);render();})));
            body.addView(Ui.button(this,"Delete property & recordings",false,v->new AlertDialog.Builder(this).setTitle("Delete property permanently?")
                .setMessage("This removes all floors, rooms and recordings from this device.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                    try{store.delete(project.getString("id"));project=null;render();}catch(Exception ex){error(ex);}}).show()));
        }catch(Exception ex){error(ex);}
    }
    private void deleteCapture(JSONObject room) throws Exception {
        String capture=room.optString("capture","");if(capture.isEmpty())return;
        // Only application-generated basenames are accepted; never resolve imported paths.
        if(!capture.matches("scan-[a-f0-9-]+\\.mp4"))throw new IllegalArgumentException("Invalid capture reference");
        java.io.File dir=store.directory(project.getString("id"));
        for(String suffix:new String[]{"",".jsonl"}){java.io.File file=new java.io.File(dir,capture+suffix);if(file.exists()&&!file.delete())throw new java.io.IOException("Cannot delete scan");}
    }
    private interface Named {void accept(String value) throws Exception;}
    private void nameDialog(String title,String initial,Named action){
        EditText input=Ui.input(this,"Name",initial);LinearLayout box=Ui.card(this);box.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString().trim();if(value.isEmpty()){input.setError("Enter a name");return;}
            if(value.length()>100){input.setError("Use 100 characters or fewer");return;}
            try{action.accept(value);dialog.dismiss();}catch(Exception ex){error(ex);}
        }));dialog.show();
    }
    private void measuredRoom(){
        LinearLayout box=Ui.card(this);EditText name=Ui.input(this,"Room name","Room"),width=Ui.input(this,"Width in metres",""),length=Ui.input(this,"Length in metres","");
        width.setInputType(8194);length.setInputType(8194);box.addView(name);box.addView(width);box.addView(length);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Measured rectangle").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Add room",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            double w=Double.parseDouble(width.getText().toString()),l=Double.parseDouble(length.getText().toString());
            if(!Double.isFinite(w)||!Double.isFinite(l)||w<=0||l<=0)throw new IllegalArgumentException("Enter positive dimensions in metres");
            addRoom(name.getText().toString(),ProjectStore.array(new double[]{0,0,w,0,w,l,0,l}),"user-measured","");dialog.dismiss();
        }catch(Exception ex){error(ex);}}));dialog.show();
    }
    private void editRoom(JSONObject room){
        try{
            LinearLayout box=Ui.card(this);EditText name=Ui.input(this,"Room name",room.getString("name"));box.addView(name);
            box.addView(Ui.text(this,"Coordinates in metres, one x,y pair per line. Keep perimeter order.",13,Ui.MUTED));
            EditText coordinates=Ui.input(this,"x,y","");coordinates.setSingleLine(false);coordinates.setMinLines(5);
            StringBuilder value=new StringBuilder();double[] xy=ProjectStore.coordinates(room.getJSONArray("points"));
            for(int i=0;i<xy.length;i+=2)value.append(String.format(Locale.US,"%.6f,%.6f\n",xy[i],xy[i+1]));coordinates.setText(value);box.addView(coordinates);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Edit room").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
            dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
                JSONArray p=new JSONArray();for(String line:coordinates.getText().toString().trim().split("\\n")){
                    String[] parts=line.trim().split(",");if(parts.length!=2)throw new IllegalArgumentException("Use x,y on each line");
                    p.put(new JSONArray().put(Double.parseDouble(parts[0])).put(Double.parseDouble(parts[1])));}
                String problem=NativeBridge.validate(ProjectStore.coordinates(p));if(!problem.isEmpty())throw new IllegalArgumentException(problem);
                String label=name.getText().toString().trim();if(label.isEmpty()||label.length()>100)throw new IllegalArgumentException("Name must have 1–100 characters");
                room.put("name",label).put("points",p).put("edited",true);store.save(project);dialog.dismiss();render();
            }catch(Exception ex){error(ex);}}));dialog.show();
        }catch(Exception ex){error(ex);}
    }
    private void addRoom(String name,JSONArray points,String source,String capture) throws Exception {
        String label=name.trim();if(label.isEmpty()||label.length()>100)throw new IllegalArgumentException("Name must have 1–100 characters");
        String problem=NativeBridge.validate(ProjectStore.coordinates(points));if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        JSONObject room=new JSONObject().put("name",label).put("points",points).put("source",source).put("verified",false)
            .put("capture",capture).put("coordinateFrame","independent-room-local");
        project.getJSONArray("floors").getJSONObject(floor).getJSONArray("rooms").put(room);store.save(project);render();
    }
    private void chooseExport(String mime,String name){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,EXPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        try{
            if(request==SCAN)addRoom(data.getStringExtra("name"),new JSONArray(data.getStringExtra("points")),data.getStringExtra("source"),data.getStringExtra("capture"));
            else if(request==EXPORT&&data.getData()!=null){
                byte[] bytes;
                if(exportType.equals("json"))bytes=project.toString(2).getBytes(StandardCharsets.UTF_8);
                else if(exportType.equals("svg")){
                    JSONObject room=project.getJSONArray("floors").getJSONObject(floor).getJSONArray("rooms").getJSONObject(exportRoom);
                    bytes=NativeBridge.exportSvg(ProjectStore.coordinates(room.getJSONArray("points")),room.getString("name")).getBytes(StandardCharsets.UTF_8);
                }else bytes=pdf();
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    if(out==null)throw new java.io.IOException("Cannot open export destination");out.write(bytes);
                }Toast.makeText(this,"Export saved",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception ex){error(ex);}
    }
    private byte[] pdf() throws Exception {
        JSONObject f=project.getJSONArray("floors").getJSONObject(floor);JSONArray rooms=f.getJSONArray("rooms");
        if(rooms.length()==0)throw new IllegalStateException("Add a room before exporting");
        PdfDocument doc=new PdfDocument();
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            for(int k=0;k<rooms.length();k++){
                JSONObject room=rooms.getJSONObject(k);double[] xy=ProjectStore.coordinates(room.getJSONArray("points"));
                PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,k+1).create());Canvas c=page.getCanvas();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Ui.INK);p.setTextSize(24);c.drawText("GharMap",36,48,p);p.setTextSize(14);
                c.drawText(project.getString("name"),36,78,p);c.drawText(f.getString("name")+" / "+room.getString("name"),36,102,p);
                double minx=xy[0],maxx=minx,miny=xy[1],maxy=miny;
                for(int i=0;i<xy.length;i+=2){minx=Math.min(minx,xy[i]);maxx=Math.max(maxx,xy[i]);miny=Math.min(miny,xy[i+1]);maxy=Math.max(maxy,xy[i+1]);}
                double scale=Math.min(450/Math.max(.01,maxx-minx),500/Math.max(.01,maxy-miny));Path path=new Path();
                for(int i=0;i<xy.length;i+=2){float x=(float)(72+(xy[i]-minx)*scale),y=(float)(170+(xy[i+1]-miny)*scale);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();
                p.setColor(0xffd6ece0);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Ui.INK);c.drawPath(path,p);
                p.setStyle(Paint.Style.FILL);p.setTextSize(10);p.setTextAlign(Paint.Align.CENTER);
                for(int i=0;i<xy.length;i+=2){int j=(i+2)%xy.length;float x=(float)(72+((xy[i]+xy[j])/2-minx)*scale),y=(float)(170+((xy[i+1]+xy[j+1])/2-miny)*scale);
                    c.drawText(String.format(Locale.US,"%.2f m",Math.hypot(xy[i]-xy[j],xy[i+1]-xy[j+1])),x,y-7,p);}
                p.setTextAlign(Paint.Align.LEFT);p.setTextSize(13);c.drawText(String.format(Locale.US,"Area: %.2f m²",ProjectStore.area(xy)),36,725,p);
                p.setTextSize(10);c.drawText("Unverified room geometry. Not a construction or survey drawing.",36,760,p);
                c.drawText("Rooms are separate local scans; geographic north is not established.",36,780,p);doc.finishPage(page);
            }doc.writeTo(out);return out.toByteArray();
        } finally { doc.close(); }
    }
    private void error(Exception ex){new AlertDialog.Builder(this).setTitle("Could not complete action").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
    @Override public void onBackPressed(){if(project!=null){project=null;render();}else super.onBackPressed();}
}
