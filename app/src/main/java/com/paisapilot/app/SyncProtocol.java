package com.paisapilot.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Append-only record revisions. No remote row is overwritten; divergent heads require a choice. */
final class SyncProtocol {
    static final String[] HEADER={"event_id","record_id","parents","payload_json","sha256","device_id","created_utc","description","type","amount_INR"};
    static String canonical(Object value){
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();object.keys().forEachRemaining(keys::add);Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");for(String key:keys){if(out.length()>1)out.append(',');out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key)));}return out.append('}').toString();}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<a.length();i++){if(i>0)out.append(',');out.append(canonical(a.opt(i)));}return out.append(']').toString();}
        if(value instanceof String)return JSONObject.quote((String)value);
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        throw new IllegalArgumentException("Unsupported sync value");
    }
    static String logicalId(JSONObject row){return row.optString("section").equals("Settings")?"settings":row.optString("id");}
    static JSONObject portable(JSONObject row){JSONObject p=Store.copy(row);p.remove("space");p.remove("reviewed");p.remove("source");Store.set(p,"id",logicalId(row));return p;}
    static Map<String,String> local(List<JSONObject> rows){Map<String,String> result=new TreeMap<>();for(JSONObject r:rows)if(result.put(logicalId(r),canonical(portable(r)))!=null)throw new IllegalArgumentException("Record IDs collide with the reserved plan identity");return result;}
    static String fingerprint(Map<String,String> rows){JSONObject root=Store.object();for(Map.Entry<String,String> r:rows.entrySet())Store.set(root,r.getKey(),r.getValue());return Imports.hash(canonical(root));}
    static final class Event {
        final String id,record,parents,payload,hash,device,created;
        Event(String id,String record,String parents,String payload,String device,String created){
            this.id=id;this.record=record;this.parents=parents;this.payload=payload;this.hash=Imports.hash(payload);this.device=device;this.created=created;
            if(!id.matches("[A-Za-z0-9_-]{1,200}")||record.isEmpty()||record.length()>200||payload.length()>40000)throw new IllegalArgumentException("Invalid sync revision");
        }
        JSONArray row(){JSONArray row=new JSONArray();for(String value:new String[]{id,record,parents,payload,hash,device,created})row.put(value);
            JSONObject p=null;try{if(!payload.equals("null"))p=new JSONObject(payload);}catch(Exception e){throw new IllegalArgumentException("Invalid revision payload");}
            row.put(p==null?"Deleted":p.optString("title",p.optString("section")));row.put(p==null?"Deleted":p.optString("kind",p.optString("section")));row.put(p!=null&&p.has("amount")?Engine.format(Store.amount(p,"amount")):"");return row;
        }
    }
    static final class Conflict extends IllegalArgumentException {
        final String record,local;final List<Event> remote;
        Conflict(String record,String local,List<Event> remote){super("Concurrent edits need review: "+record);this.record=record;this.local=local;this.remote=remote;}
    }
    static final class Result {
        final Map<String,String> incoming=new TreeMap<>();
        final Map<String,JSONObject> bases=new TreeMap<>();
        final List<Event> outgoing=new ArrayList<>();
    }
    static Result merge(List<Event> events,Map<String,String> local,Map<String,JSONObject> bases,Map<String,String> choices,String device){
        Map<String,Event> byId=new LinkedHashMap<>();Map<String,List<Event>> groups=new TreeMap<>();Set<String> referenced=new HashSet<>();
        for(Event e:events){Event previous=byId.get(e.id);if(previous!=null){if(!previous.record.equals(e.record)||!previous.payload.equals(e.payload)||!previous.parents.equals(e.parents))throw new IllegalArgumentException("Edited sync revision detected");continue;}
            for(String parent:e.parents.isEmpty()?new String[0]:e.parents.split(",")){Event p=byId.get(parent);if(p==null||!p.record.equals(e.record))throw new IllegalArgumentException("Incomplete or edited sync history");referenced.add(parent);}
            byId.put(e.id,e);groups.computeIfAbsent(e.record,k->new ArrayList<>()).add(e);
        }
        Set<String> ids=new TreeSet<>(local.keySet());ids.addAll(groups.keySet());ids.addAll(bases.keySet());Result result=new Result();
        for(String id:ids){List<Event> all=groups.getOrDefault(id,Collections.emptyList()),heads=new ArrayList<>();for(Event e:all)if(!referenced.contains(e.id))heads.add(e);
            heads.sort(Comparator.comparing(e->e.id));JSONObject base=bases.get(id);String localPayload=local.getOrDefault(id,"null"),localHash=Imports.hash(localPayload);
            if(base!=null){for(String old:base.optString("heads").split(","))if(!byId.containsKey(old)||!byId.get(old).hash.equals(base.optString("hash")))throw new IllegalArgumentException("Previously synced history is missing or edited; sync stopped");}
            String baseHash=base==null?Imports.hash("null"):base.optString("hash");String remotePayload=heads.isEmpty()?"null":heads.get(0).payload;
            Set<String> hashes=new HashSet<>();for(Event e:heads)hashes.add(e.hash);
            boolean dirty=!localHash.equals(baseHash),divergent=hashes.size()>1;
            boolean conflict=divergent || (dirty&&!Imports.hash(remotePayload).equals(baseHash)&&!localHash.equals(Imports.hash(remotePayload)));
            String choice=choices.get(id),chosen;
            if(conflict){if(choice==null)throw new Conflict(id,localPayload,heads);if(choice.equals("LOCAL"))chosen=localPayload;else{Event e=byId.get(choice);if(e==null||!heads.contains(e))throw new IllegalArgumentException("Conflict changed; review again");chosen=e.payload;}}
            else chosen=dirty?localPayload:remotePayload;
            String parents=String.join(",",heads.stream().map(e->e.id).toArray(String[]::new));
            boolean append=conflict || (!Imports.hash(chosen).equals(Imports.hash(remotePayload)));
            JSONObject next=Store.object();String selectedHeads=parents;
            if(append){Event e=new Event(UUID.randomUUID().toString(),id,parents,chosen,device,java.time.Instant.now().toString());result.outgoing.add(e);selectedHeads=e.id;}
            if(!chosen.equals(localPayload))result.incoming.put(id,chosen);
            if(!selectedHeads.isEmpty()){Store.set(next,"heads",selectedHeads);Store.set(next,"hash",Imports.hash(chosen));result.bases.put(id,next);}
        }
        return result;
    }
    static List<Event> read(JSONArray rows){
        if(rows==null||rows.length()==0)throw new IllegalArgumentException("Not a PaisaPilot sync spreadsheet");
        JSONArray header=rows.optJSONArray(0);if(header==null||header.length()!=HEADER.length)throw new IllegalArgumentException("Unsupported sync sheet columns");
        for(int i=0;i<HEADER.length;i++)if(!HEADER[i].equals(header.optString(i)))throw new IllegalArgumentException("Unsupported sync sheet columns");
        if(rows.length()>20000)throw new IllegalArgumentException("Sync journal is full; archive and reconnect a new sheet");
        List<Event> events=new ArrayList<>();for(int i=1;i<rows.length();i++){JSONArray r=rows.optJSONArray(i);if(r==null||r.length()<7)throw new IllegalArgumentException("Incomplete sync row");
            Event e=new Event(r.optString(0),r.optString(1),r.optString(2),r.optString(3),r.optString(5),r.optString(6));if(!e.hash.equals(r.optString(4)))throw new IllegalArgumentException("A sync payload was edited; no local data changed");events.add(e);}
        return events;
    }
}
