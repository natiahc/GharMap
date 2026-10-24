package com.gharmap.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class ProjectStore {
    private final File root;
    ProjectStore(Context context) { root=new File(context.getFilesDir(),"projects"); }
    File directory(String id) {
        if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid project ID");
        return new File(root,id);
    }
    JSONObject create(String name) throws Exception {
        JSONObject project=new JSONObject().put("schemaVersion",1).put("id",UUID.randomUUID().toString())
            .put("name",name).put("createdAt",System.currentTimeMillis()).put("units","metres")
            .put("floors",new JSONArray().put(new JSONObject().put("name","Ground floor").put("rooms",new JSONArray())));
        save(project);return project;
    }
    void save(JSONObject project) throws Exception {
        File directory=directory(project.getString("id"));
        if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create project folder");
        project.put("updatedAt",System.currentTimeMillis());
        AtomicFile file=new AtomicFile(new File(directory,"project.json"));FileOutputStream out=null;
        try {out=file.startWrite();out.write(project.toString(2).getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
    JSONObject load(String id) throws Exception {
        byte[] bytes=new AtomicFile(new File(directory(id),"project.json")).readFully();
        JSONObject p=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(p.getInt("schemaVersion")!=1)throw new IllegalStateException("Unsupported project version");
        return p;
    }
    List<JSONObject> list() throws Exception {
        List<JSONObject> projects=new ArrayList<>();File[] dirs=root.listFiles();
        if(dirs!=null)for(File dir:dirs)if(dir.isDirectory())projects.add(load(dir.getName()));
        projects.sort(Comparator.comparingLong((JSONObject p)->p.optLong("updatedAt")).reversed());return projects;
    }
    void delete(String id) throws Exception {
        try(var paths=Files.walk(directory(id).toPath())) {
            for(var path:paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()))Files.delete(path);
        }
    }
    static double[] coordinates(JSONArray p) throws Exception {
        double[] xy=new double[p.length()*2];
        for(int i=0;i<p.length();i++){xy[2*i]=p.getJSONArray(i).getDouble(0);xy[2*i+1]=p.getJSONArray(i).getDouble(1);}
        return xy;
    }
    static JSONArray array(double[] xy) throws Exception {
        JSONArray p=new JSONArray();for(int i=0;i<xy.length;i+=2)p.put(new JSONArray().put(xy[i]).put(xy[i+1]));return p;
    }
    static double area(double[] xy) {
        double twice=0;int n=xy.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;twice+=xy[2*i]*xy[2*j+1]-xy[2*j]*xy[2*i+1];}
        return Math.abs(twice)*0.5;
    }
}
