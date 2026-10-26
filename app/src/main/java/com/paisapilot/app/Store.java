package com.paisapilot.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One local record store and one persistence path for all spaces and features. */
final class Store extends SQLiteOpenHelper {
    static final String[] KINDS = {"Income", "Expense", "Refund", "Transfer", "Card repayment",
        "Borrowed principal", "Loan principal repayment", "Investment contribution", "Investment withdrawal",
        "Money lent", "Lent principal returned"};
    static final String[] SECTIONS = {"Transaction", "Commitment", "Goal", "Holding", "Settings"};
    static final long MAX = 1_000_000_000_000_000L;
    Store(Context context) { super(context, "paisapilot.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY, space TEXT NOT NULL, section TEXT NOT NULL, data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX record_scope ON records(space,section)");
        db.execSQL("CREATE TABLE spaces(id TEXT PRIMARY KEY,name TEXT NOT NULL,type TEXT NOT NULL)");
        db.execSQL("INSERT INTO spaces VALUES('personal','Personal','Personal')");
        createCloudTables(db);
    }
    private void createCloudTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_links(space TEXT PRIMARY KEY,sheet TEXT NOT NULL,owner INTEGER NOT NULL,auto INTEGER NOT NULL DEFAULT 0,last_sync INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_bases(space TEXT NOT NULL,record_id TEXT NOT NULL,heads TEXT NOT NULL,hash TEXT NOT NULL,PRIMARY KEY(space,record_id))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion==1&&newVersion==2)createCloudTables(db);else throw new IllegalStateException("Unsupported database migration");
    }
    static JSONObject copy(JSONObject value) {
        try { return new JSONObject(value.toString()); }
        catch(JSONException e) { throw new IllegalArgumentException("Invalid record",e); }
    }
    static JSONObject object() { return new JSONObject(); }
    static JSONObject set(JSONObject object, String key, Object value) {
        try { object.put(key, value); return object; }
        catch (JSONException e) { throw new IllegalArgumentException("Invalid record", e); }
    }
    static JSONObject record(String space, String section) {
        JSONObject row = object();
        set(row, "id", UUID.randomUUID().toString()); set(row, "space", space); set(row, "section", section);
        return row;
    }
    List<JSONObject> spaces() {
        List<JSONObject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,type FROM spaces ORDER BY rowid", null)) {
            while(c.moveToNext()) { JSONObject r=object(); set(r,"id",c.getString(0)); set(r,"name",c.getString(1)); set(r,"type",c.getString(2)); result.add(r); }
        }
        return result;
    }
    String addSpace(String name, String type) {
        if(name.trim().isEmpty() || name.length()>80 || !(type.equals("Household") || type.equals("Business")))
            throw new IllegalArgumentException("Enter a space name and type");
        String id=UUID.randomUUID().toString(); ContentValues v=new ContentValues();
        v.put("id",id);v.put("name",name.trim());v.put("type",type);getWritableDatabase().insertOrThrow("spaces",null,v);return id;
    }
    List<JSONObject> list(String space, String section) {
        List<JSONObject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT data FROM records WHERE space=? AND section=? ORDER BY rowid DESC", new String[]{space,section})) {
            while(c.moveToNext()) { try { result.add(new JSONObject(c.getString(0))); }
                catch(JSONException e) { throw new IllegalStateException("Stored record needs recovery",e); } }
        }
        return result;
    }
    JSONObject get(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM records WHERE id=?",new String[]{id})) {
            if(!c.moveToFirst()) return null;
            try { return new JSONObject(c.getString(0)); } catch(JSONException e) { throw new IllegalStateException(e); }
        }
    }
    static long amount(JSONObject r,String key) {
        Object raw=r.opt(key);
        if(!(raw instanceof Long || raw instanceof Integer) || ((Number)raw).longValue()<0 || ((Number)raw).longValue()>MAX)
            throw new IllegalArgumentException("Invalid amount: "+key);
        return ((Number)raw).longValue();
    }
    void validate(JSONObject r) {
        String id=r.optString("id"), space=r.optString("space"), section=r.optString("section");
        if(id.isEmpty() || id.length()>200 || !java.util.Arrays.asList(SECTIONS).contains(section))
            throw new IllegalArgumentException("Invalid record identity");
        boolean found=false; for(JSONObject s:spaces()) if(space.equals(s.optString("id"))) found=true;
        if(!found) throw new IllegalArgumentException("Unknown space");
        if(section.equals("Settings")) {
            if(!id.equals("settings-"+space))throw new IllegalArgumentException("Invalid settings identity");
            for(String key:new String[]{"takehome","spending","debt","reserveadd","goalsadd","reserves","essentials","retained","initial","riskcap","riskfunded","riskloss","losslimit","paydaycash","dailyspend"})
                if(r.has(key))amount(r,key);
            for(String key:new String[]{"months","riskbp","years","returnbp","inflationbp","stepupbp"})
                if(r.has(key)&&!(r.opt(key) instanceof Integer || r.opt(key) instanceof Long))throw new IllegalArgumentException("Invalid plan value");
            long months=r.optLong("months",6),risk=r.optLong("riskbp"),years=r.optLong("years",10),rate=r.optLong("returnbp",800),inflation=r.optLong("inflationbp",600),step=r.optLong("stepupbp");
            if(months<0||months>120||risk<0||risk>10000||years<1||years>60||rate< -9900||rate>5000||inflation<0||inflation>2500||step<0||step>2500)
                throw new IllegalArgumentException("Plan values out of range");
            if(r.has("paused")&&!(r.opt("paused") instanceof Boolean))throw new IllegalArgumentException("Invalid pause value");
            if(r.has("payday"))LocalDate.parse(r.optString("payday"));
            return;
        }
        if(r.optString("title").trim().isEmpty() || r.optString("title").length()>300 || r.optString("note").length()>2000)
            throw new IllegalArgumentException("Enter a description (up to 300 characters)");
        LocalDate.parse(r.optString("date")); amount(r,"amount");
        if(section.equals("Transaction")) {
            if(!java.util.Arrays.asList(KINDS).contains(r.optString("kind")) || !"INR".equals(r.optString("currency")))
                throw new IllegalArgumentException("Unsupported transaction type or currency");
            if(!(r.opt("reviewed") instanceof Boolean)) throw new IllegalArgumentException("Review status required");
        }
        if(section.equals("Goal") || section.equals("Holding")) amount(r,"saved");
    }
    void put(JSONObject r) {
        validate(r); ContentValues v=new ContentValues();v.put("id",r.optString("id"));v.put("space",r.optString("space"));
        v.put("section",r.optString("section"));v.put("data",r.toString());
        if(getWritableDatabase().insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)
            throw new IllegalStateException("Could not save record");
    }
    void delete(String id) { getWritableDatabase().delete("records","id=?",new String[]{id}); }
    long[] totals(String space, YearMonth month) {
        List<JSONObject> rows=new ArrayList<>();
        for(JSONObject r:list(space,"Transaction"))
            if(YearMonth.from(LocalDate.parse(r.optString("date"))).equals(month)) rows.add(r);
        String[] ids=new String[rows.size()];int[] kinds=new int[rows.size()];
        long[] amounts=new long[rows.size()];boolean[] reviewed=new boolean[rows.size()];
        for(int i=0;i<rows.size();i++) {JSONObject r=rows.get(i);ids[i]=r.optString("id");
            kinds[i]=java.util.Arrays.asList(KINDS).indexOf(r.optString("kind"));
            amounts[i]=amount(r,"amount");reviewed[i]=r.optBoolean("reviewed");}
        return Engine.totals(ids,kinds,amounts,reviewed);
    }

    JSONObject settings(String space) {
        JSONObject r=get("settings-"+space);
        if(r==null) { r=record(space,"Settings"); set(r,"id","settings-"+space); }
        return r;
    }
    // All-or-nothing import: exact IDs deduplicate; external edits require manual reconciliation.
    int importTransactions(List<JSONObject> rows, String space) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); int added=0;
        try {
            for(JSONObject row:rows) {
                set(row,"space",space); set(row,"section","Transaction"); validate(row);
                JSONObject old=get(row.optString("id"));
                if(old!=null) {
                    for(String key:new String[]{"space","section","title","date","kind","currency","amount","category","account","owner","note"})
                        if(!old.optString(key).equals(row.optString(key)))
                            throw new IllegalArgumentException("An imported ID conflicts with an existing record; no rows imported");
                    continue; // A local review decision must not be reset by a repeat import.
                }
                set(row,"reviewed",false); put(row); added++;
            }
            db.setTransactionSuccessful(); return added;
        } finally { db.endTransaction(); }
    }
    JSONObject snapshot() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject out=object();set(out,"format","PaisaPilot");set(out,"version",1);
            JSONArray scopes=new JSONArray(),records=new JSONArray();
            for(JSONObject scope:spaces())scopes.put(scope);
            try(Cursor c=db.rawQuery("SELECT data FROM records ORDER BY id",null)){
                while(c.moveToNext())try{records.put(new JSONObject(c.getString(0)));}catch(JSONException e){throw new IllegalStateException(e);}
            }
            set(out,"spaces",scopes);set(out,"records",records);db.setTransactionSuccessful();return out;
        } finally{db.endTransaction();}
    }
    void restore(JSONObject snapshot) {
        if(!"PaisaPilot".equals(snapshot.optString("format"))||snapshot.optInt("version")!=1)
            throw new IllegalArgumentException("Unsupported backup version");
        JSONArray scopes=snapshot.optJSONArray("spaces"),rows=snapshot.optJSONArray("records");
        if(scopes==null||rows==null||scopes.length()==0||scopes.length()>100||rows.length()>100000)
            throw new IllegalArgumentException("Invalid backup record counts");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("records",null,null);db.delete("spaces",null,null);db.delete("cloud_links",null,null);db.delete("sync_bases",null,null);
            boolean personal=false;java.util.HashSet<String> scopeIds=new java.util.HashSet<>(),ids=new java.util.HashSet<>();
            for(int i=0;i<scopes.length();i++){
                JSONObject scope=scopes.optJSONObject(i);if(scope==null)throw new IllegalArgumentException("Invalid space");
                String id=scope.optString("id"),name=scope.optString("name"),type=scope.optString("type");
                if(id.isEmpty()||id.length()>200||name.trim().isEmpty()||name.length()>80||!scopeIds.add(id)||!java.util.Arrays.asList("Personal","Household","Business").contains(type))throw new IllegalArgumentException("Invalid space");
                if(id.equals("personal")){if(!type.equals("Personal"))throw new IllegalArgumentException("Invalid personal space");personal=true;}
                ContentValues v=new ContentValues();v.put("id",id);v.put("name",name);v.put("type",type);db.insertOrThrow("spaces",null,v);
            }
            if(!personal)throw new IllegalArgumentException("Backup lacks personal space");
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row==null||!ids.add(row.optString("id")))throw new IllegalArgumentException("Invalid or repeated record");put(row);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    List<JSONObject> scopeRows(String space){List<JSONObject> out=new ArrayList<>();for(String section:SECTIONS)out.addAll(list(space,section));return out;}
    JSONObject cloudLink(String space){try(Cursor c=getReadableDatabase().rawQuery("SELECT sheet,owner,auto,last_sync,error FROM cloud_links WHERE space=?",new String[]{space})){
        if(!c.moveToFirst())return null;JSONObject r=object();set(r,"sheet",c.getString(0));set(r,"owner",c.getInt(1)==1);set(r,"auto",c.getInt(2)==1);set(r,"last",c.getLong(3));set(r,"error",c.getString(4));return r;}}
    void connectCloud(String space,String sheet,boolean owner){if(cloudLink(space)!=null)throw new IllegalArgumentException("Disconnect this space before connecting another sheet");ContentValues v=new ContentValues();v.put("space",space);v.put("sheet",SheetsClient.sheetId(sheet));v.put("owner",owner?1:0);getWritableDatabase().insertOrThrow("cloud_links",null,v);}
    void disconnectCloud(String space){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("cloud_links","space=?",new String[]{space});db.delete("sync_bases","space=?",new String[]{space});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void cloudStatus(String space,String error,Boolean auto){ContentValues v=new ContentValues();v.put("error",error);if(auto!=null)v.put("auto",auto?1:0);getWritableDatabase().update("cloud_links",v,"space=?",new String[]{space});}
    Map<String,JSONObject> syncBases(String space){Map<String,JSONObject> out=new java.util.TreeMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT record_id,heads,hash FROM sync_bases WHERE space=?",new String[]{space})){while(c.moveToNext()){JSONObject r=object();set(r,"heads",c.getString(1));set(r,"hash",c.getString(2));out.put(c.getString(0),r);}}return out;}
    void applySync(String space,String sheet,String expected,SyncProtocol.Result result)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            JSONObject link=cloudLink(space);if(link==null||!sheet.equals(link.optString("sheet"))||!expected.equals(SyncProtocol.fingerprint(SyncProtocol.local(scopeRows(space)))))throw new IllegalArgumentException("Local data changed during sync. Retry to merge the changes.");
            for(Map.Entry<String,String> entry:result.incoming.entrySet()){
                String logical=entry.getKey(),id=logical.equals("settings")?"settings-"+space:logical;JSONObject old=get(id);
                if(old!=null&&!space.equals(old.optString("space")))throw new IllegalArgumentException("Record ID belongs to another private space; nothing imported");
                if(entry.getValue().equals("null")){delete(id);continue;}
                JSONObject row=new JSONObject(entry.getValue());if(!logical.equals(row.optString("id")))throw new IllegalArgumentException("Invalid remote record identity");
                set(row,"id",id);set(row,"space",space);if(row.optString("section").equals("Settings")&&!logical.equals("settings"))throw new IllegalArgumentException("Invalid remote plan identity");
                if(row.optString("section").equals("Transaction")){set(row,"reviewed",false);set(row,"source","google-sheet");}put(row);
            }
            db.delete("sync_bases","space=?",new String[]{space});for(Map.Entry<String,JSONObject> entry:result.bases.entrySet()){ContentValues v=new ContentValues();v.put("space",space);v.put("record_id",entry.getKey());v.put("heads",entry.getValue().optString("heads"));v.put("hash",entry.getValue().optString("hash"));db.insertOrThrow("sync_bases",null,v);}
            ContentValues status=new ContentValues();status.put("last_sync",System.currentTimeMillis());status.put("error","");db.update("cloud_links",status,"space=?",new String[]{space});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void settle(JSONObject commitment, long payment, String date) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            // Reload inside the write transaction: a stale dialog must not repay twice.
            JSONObject current=get(commitment.optString("id"));
            if(current==null || !"Commitment".equals(current.optString("section")))
                throw new IllegalArgumentException("Commitment no longer exists");
            long outstanding=amount(current,"amount");
            if(payment<=0 || payment>outstanding || current.optBoolean("done"))
                throw new IllegalArgumentException("Payment must be within the current outstanding amount");
            String type=current.optString("kind");
            JSONObject tx=record(current.optString("space"),"Transaction");
            set(tx,"title",current.optString("title"));set(tx,"date",date);set(tx,"amount",payment);
            set(tx,"currency","INR");set(tx,"reviewed",true);set(tx,"source","manual-settlement");
            set(tx,"kind",type.equals("Money lent")?"Lent principal returned":(type.equals("Loan taken")||type.equals("EMI principal"))?"Loan principal repayment":type.equals("Card bill")?"Card repayment":"Expense");
            set(tx,"category","Commitments");set(tx,"account",current.optString("account","Unspecified"));
            set(tx,"owner",current.optString("owner"));set(tx,"note","Linked to commitment "+current.optString("id"));
            put(tx);set(current,"amount",outstanding-payment);set(current,"done",payment==outstanding);put(current);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
