package com.paisapilot.app;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** User-authorized Google REST calls only. Access tokens stay in memory. */
final class SheetsClient {
    private final String token;
    SheetsClient(String token){if(token==null||token.isEmpty())throw new IllegalArgumentException("Google authorization is required");this.token=token;}
    private JSONObject request(String method,String host,String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);
        try{if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Google request failed (HTTP "+code+"). Check account access, enabled APIs and OAuth registration; no automatic overwrite was attempted.");
            if(code==204)return Store.object();try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>10_000_000)throw new IOException("Google response exceeds supported size");out.write(b,0,n);}String text=out.toString(StandardCharsets.UTF_8.name());return text.isEmpty()?Store.object():new JSONObject(text);}
        }finally{c.disconnect();}
    }
    static String sheetId(String input){String id=input.trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("/spreadsheets/d/([A-Za-z0-9_-]+)").matcher(id);if(m.find())id=m.group(1);if(!id.matches("[A-Za-z0-9_-]{20,200}"))throw new IllegalArgumentException("Enter a Google spreadsheet ID or link");return id;}
    String create(String title)throws Exception{
        JSONObject body=Store.object();Store.set(body,"properties",Store.set(Store.object(),"title",title));
        JSONArray sheets=new JSONArray();sheets.put(Store.set(Store.object(),"properties",Store.set(Store.object(),"title","PaisaPilotEvents")));Store.set(body,"sheets",sheets);
        String id=request("POST","sheets.googleapis.com","/v4/spreadsheets",body).getString("spreadsheetId");
        JSONArray header=new JSONArray();for(String value:SyncProtocol.HEADER)header.put(value);JSONObject values=Store.set(Store.object(),"values",new JSONArray().put(header));
        request("PUT","sheets.googleapis.com","/v4/spreadsheets/"+id+"/values/"+Uri.encode("PaisaPilotEvents!A1:J1")+"?valueInputOption=RAW",values);return id;
    }
    List<SyncProtocol.Event> events(String id)throws Exception{return SyncProtocol.read(request("GET","sheets.googleapis.com","/v4/spreadsheets/"+sheetId(id)+"/values/"+Uri.encode("PaisaPilotEvents!A1:J20001"),null).optJSONArray("values"));}
    void append(String id,List<SyncProtocol.Event> events)throws Exception{if(events.isEmpty())return;JSONArray rows=new JSONArray();for(SyncProtocol.Event e:events)rows.put(e.row());JSONObject body=Store.set(Store.object(),"values",rows);
        request("POST","sheets.googleapis.com","/v4/spreadsheets/"+sheetId(id)+"/values/"+Uri.encode("PaisaPilotEvents!A:J")+":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS",body);}
    void sync(Store store,String space,String device,Map<String,String> choices)throws Exception{
        JSONObject link=store.cloudLink(space);if(link==null)throw new IllegalArgumentException("Connect a spreadsheet first");String id=link.optString("sheet");
        Map<String,String> local=SyncProtocol.local(store.scopeRows(space));String before=SyncProtocol.fingerprint(local);
        SyncProtocol.Result result=SyncProtocol.merge(events(id),local,store.syncBases(space),choices,device);
        // Edits made during the request abort this apply. A timed-out append is deduplicated on next pull by payload/hash.
        append(id,result.outgoing);store.applySync(space,id,before,result);
    }
    void invite(String id,String email)throws Exception{if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())throw new IllegalArgumentException("Enter a valid Google account email");
        JSONObject p=Store.object();Store.set(p,"type","user");Store.set(p,"role","writer");Store.set(p,"emailAddress",email);
        request("POST","www.googleapis.com","/drive/v3/files/"+sheetId(id)+"/permissions?sendNotificationEmail=false",p);}
    JSONArray permissions(String id)throws Exception{return request("GET","www.googleapis.com","/drive/v3/files/"+sheetId(id)+"/permissions?fields=permissions(id,emailAddress,role,type)",null).optJSONArray("permissions");}
    void revoke(String id,String permission)throws Exception{if(!permission.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid permission");request("DELETE","www.googleapis.com","/drive/v3/files/"+sheetId(id)+"/permissions/"+permission,null);}
}
