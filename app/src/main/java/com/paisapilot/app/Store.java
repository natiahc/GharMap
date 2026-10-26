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
import java.util.UUID;

/** One local record store and one persistence path for all spaces and features. */
final class Store extends SQLiteOpenHelper {
    static final String[] KINDS = {"Income", "Expense", "Refund", "Transfer", "Card repayment",
        "Borrowed principal", "Loan principal repayment", "Investment contribution", "Investment withdrawal",
        "Money lent", "Lent principal returned"};
    static final String[] SECTIONS = {"Transaction", "Commitment", "Goal", "Holding", "Settings"};
    static final long MAX = 1_000_000_000_000_000L;
    Store(Context context) { super(context, "paisapilot.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY, space TEXT NOT NULL, section TEXT NOT NULL, data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX record_scope ON records(space,section)");
        db.execSQL("CREATE TABLE spaces(id TEXT PRIMARY KEY,name TEXT NOT NULL,type TEXT NOT NULL)");
        db.execSQL("INSERT INTO spaces VALUES('personal','Personal','Personal')");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database migration");
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
        if(section.equals("Settings")) return;
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
