package com.paisapilot.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class FinanceFlowTest {
    private Context context;
    private Store store;
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("paisapilot.db");store=new Store(context);}
    @After public void teardown(){store.close();context.deleteDatabase("paisapilot.db");}
    private JSONObject tx(String id,String kind,long amount,boolean reviewed){
        JSONObject r=Store.record("personal","Transaction");Store.set(r,"id",id);Store.set(r,"title",id);Store.set(r,"kind",kind);Store.set(r,"amount",amount);
        Store.set(r,"date",LocalDate.now().toString());Store.set(r,"currency","INR");Store.set(r,"reviewed",reviewed);return r;
    }
    @Test public void accountingAndPersistenceUseNativeCore(){
        store.put(tx("salary","Income",25000000,true));store.put(tx("purchase","Expense",29000000,true));
        store.put(tx("repay","Card repayment",29000000,true));store.put(tx("transfer","Transfer",3000000,true));
        store.put(tx("borrow","Borrowed principal",1000000,true));store.put(tx("refund","Refund",10000,true));store.put(tx("pending","Income",1000000,false));
        assertArrayEquals(new long[]{25000000,29000000,10000,1},store.totals("personal",YearMonth.now()));
        store.close();store=new Store(context);assertEquals(7,store.list("personal","Transaction").size());
        assertEquals(1000500,Engine.plan(new long[]{25000000,8000000,0,2000000,0,45000000,7500000,6,667,5000000,0})[3]);
        assertEquals(22000,Engine.project(10000,1000,1,0,0,0)[0]);
    }
    @Test public void smsIsConservativeAndReviewIsMandatory(){
        String date=LocalDate.now().toString();
        assertNull(Imports.sms("OTP 123456 for INR 100 paid","bank","1",date,"personal"));
        assertNull(Imports.sms("INR 500 will be debited tomorrow","bank","2",date,"personal"));
        assertNull(Imports.sms("INR 100 debited. Balance INR 900","bank","3",date,"personal"));
        assertNull(Imports.sms("I will pay you INR 100","friend","4",date,"personal"));
        assertNull(Imports.sms("INR 1,00 debited","bank","5",date,"personal"));
        JSONObject r=Imports.sms("INR 1,234.56 debited from account","bank","6",date,"personal");
        assertNotNull(r);assertEquals(123456,r.optLong("amount"));assertFalse(r.optBoolean("reviewed"));
        assertFalse(r.toString().contains("from account"));
        assertEquals(1,store.importTransactions(Collections.singletonList(r),"personal"));
        assertEquals(0,store.importTransactions(Collections.singletonList(r),"personal"));
        assertEquals(0,store.totals("personal",YearMonth.now())[1]);
        Store.set(r,"reviewed",true);store.put(r);assertEquals(123456,store.totals("personal",YearMonth.now())[1]);
    }
    @Test public void csvRoundTripsQuotesNewlinesFormulasAndApostrophes(){
        for(String title:new String[]{"Food, tea","A \"quoted\" item","line\nnext","=1+2","  =1+2","'=literal","'","normal"}){
            JSONObject r=tx("id-"+Imports.hash(title),"Expense",29,true);Store.set(r,"title",title);
            String csv=Imports.csv(Collections.singletonList(r));JSONObject copy=Imports.csv(csv,"personal").get(0);
            assertEquals(title,copy.optString("title"));assertEquals(29,copy.optLong("amount"));assertFalse(copy.optBoolean("reviewed"));
        }
        try{Imports.readCsv("a,\"unclosed");fail("Accepted malformed CSV");}catch(IllegalArgumentException expected){}
        try{Imports.csv("wrong,columns\n1,2", "personal");fail("Accepted unknown format");}catch(IllegalArgumentException expected){}
    }
    @Test public void conflictingImportIsAtomicAndSpacesStaySeparate(){
        JSONObject existing=tx("existing","Expense",100,true);store.put(existing);
        JSONObject conflict=Store.copy(existing);Store.set(conflict,"amount",200);
        try{store.importTransactions(Arrays.asList(tx("new","Income",500,false),conflict),"personal");fail("Overwrote conflict");}catch(IllegalArgumentException expected){}
        assertNull(store.get("new"));assertEquals(100,store.get("existing").optLong("amount"));
        String household=store.addSpace("Household","Household");JSONObject h=tx("shared","Expense",200,true);Store.set(h,"space",household);store.put(h);
        assertEquals(1,store.list("personal","Transaction").size());assertEquals(1,store.list(household,"Transaction").size());
        assertFalse(Imports.csv(store.list(household,"Transaction")).contains("existing"));
    }
    @Test public void loanAndCardSettlementsDoNotDoubleCountExpenses(){
        for(String type:new String[]{"Loan taken","EMI principal","Card bill","Money lent"}){
            JSONObject c=Store.record("personal","Commitment");Store.set(c,"title",type);Store.set(c,"kind",type);Store.set(c,"amount",10000);Store.set(c,"date",LocalDate.now().toString());store.put(c);
            store.settle(c,4000,LocalDate.now().toString());assertEquals(6000,store.get(c.optString("id")).optLong("amount"));
            store.settle(c,6000,LocalDate.now().toString());assertTrue(store.get(c.optString("id")).optBoolean("done"));
        }
        JSONObject stale=Store.record("personal","Commitment");Store.set(stale,"title","Stale dialog");Store.set(stale,"kind","Card bill");Store.set(stale,"date",LocalDate.now().toString());Store.set(stale,"amount",100);store.put(stale);
        store.settle(stale,100,LocalDate.now().toString());
        try{store.settle(stale,100,LocalDate.now().toString());fail("Settled a stale dialog twice");}catch(IllegalArgumentException expected){}
        assertEquals(9,store.list("personal","Transaction").size());assertEquals(0,store.totals("personal",YearMonth.now())[1]);
    }
    @Test public void encryptedBackupRestoresAllSectionsAndRejectsTampering()throws Exception{
        store.put(tx("retained","Income",123,true));
        JSONObject goal=Store.record("personal","Goal");Store.set(goal,"title","Emergency");Store.set(goal,"amount",10000);Store.set(goal,"saved",29);Store.set(goal,"date","2027-04-01");store.put(goal);
        JSONObject original=store.snapshot();char[] pass="fixture passphrase only".toCharArray();byte[] backup=Backup.encrypt(original,pass);
        assertFalse(java.util.Arrays.equals(backup,Backup.encrypt(original,pass)));
        store.delete("retained");store.restore(Backup.decrypt(backup,pass));assertEquals(1,store.list("personal","Goal").size());assertNotNull(store.get("retained"));
        try{Backup.decrypt(backup,"wrong passphrase here".toCharArray());fail("Wrong password accepted");}catch(IllegalArgumentException expected){}
        backup[backup.length-1]^=1;try{Backup.decrypt(backup,pass);fail("Tampered backup accepted");}catch(IllegalArgumentException expected){}
        JSONObject invalid=Store.copy(original);Store.set(invalid.optJSONArray("records").optJSONObject(0),"amount",-1);
        try{store.restore(invalid);fail("Invalid backup restored");}catch(IllegalArgumentException expected){}
        assertNotNull(store.get("retained"));
        byte[] fixture;try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("backup-v1.b64")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);fixture=android.util.Base64.decode(out.toByteArray(),android.util.Base64.DEFAULT);}
        store.restore(Backup.decrypt(fixture,pass));assertEquals(29,store.get("fixture-29").optLong("amount"));assertNull(store.get("retained"));
    }
    @Test public void syncMergesIndependentChangesAndRequiresConflictChoice(){
        JSONObject one=tx("one","Expense",29,true);String p1=SyncProtocol.canonical(SyncProtocol.portable(one));
        SyncProtocol.Event first=new SyncProtocol.Event("e1","one","",p1,"deviceA","2026-10-26T00:00:00Z");
        SyncProtocol.Result initial=SyncProtocol.merge(Collections.singletonList(first),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),"B");
        assertEquals(p1,initial.incoming.get("one"));assertTrue(initial.outgoing.isEmpty());
        Map<String,String> local=new TreeMap<>();local.put("one",p1);local.put("two",SyncProtocol.canonical(SyncProtocol.portable(tx("two","Income",100,true))));
        SyncProtocol.Result next=SyncProtocol.merge(Collections.singletonList(first),local,initial.bases,Collections.emptyMap(),"B");assertEquals(1,next.outgoing.size());assertEquals("two",next.outgoing.get(0).record);
        JSONObject changed=Store.copy(one);Store.set(changed,"amount",30);String p2=SyncProtocol.canonical(SyncProtocol.portable(changed));SyncProtocol.Event branch=new SyncProtocol.Event("e2","one","",p2,"C","2026-10-26T00:00:01Z");
        try{SyncProtocol.merge(Arrays.asList(first,branch),Collections.singletonMap("one",p1),initial.bases,Collections.emptyMap(),"B");fail("Silently selected a branch");}catch(SyncProtocol.Conflict expected){}
        SyncProtocol.Result resolved=SyncProtocol.merge(Arrays.asList(first,branch),Collections.singletonMap("one",p1),initial.bases,Collections.singletonMap("one","e2"),"B");assertEquals(p2,resolved.incoming.get("one"));assertEquals("e1,e2",resolved.outgoing.get(0).parents);
        JSONObject altered=Store.copy(one);Store.set(altered,"amount",999);SyncProtocol.Event edited=new SyncProtocol.Event("e1","one","",SyncProtocol.canonical(SyncProtocol.portable(altered)),"A","now");
        try{SyncProtocol.merge(Collections.singletonList(edited),Collections.singletonMap("one",p1),initial.bases,Collections.emptyMap(),"B");fail("Accepted edited synced history");}catch(IllegalArgumentException expected){}
        SyncProtocol.Result deletion=SyncProtocol.merge(Collections.singletonList(first),Collections.emptyMap(),initial.bases,Collections.emptyMap(),"B");assertEquals("null",deletion.outgoing.get(0).payload);
    }
    @Test public void incomingSyncCannotOverwriteAnotherPrivateSpace()throws Exception{
        store.put(tx("private","Income",1000,true));String shared=store.addSpace("Shared","Household");store.connectCloud(shared,"abcdefghijklmnopqrstuv123456789",false);
        String payload=SyncProtocol.canonical(SyncProtocol.portable(tx("private","Expense",29,true)));
        SyncProtocol.Result plan=new SyncProtocol.Result();plan.incoming.put("private",payload);
        try{store.applySync(shared,"abcdefghijklmnopqrstuv123456789",SyncProtocol.fingerprint(Collections.emptyMap()),plan);fail("Overwrote a private record");}catch(IllegalArgumentException expected){}
        assertEquals(1000,store.get("private").optLong("amount"));assertTrue(store.list(shared,"Transaction").isEmpty());
        JSONObject snapshot=store.snapshot();store.restore(snapshot);assertNull(store.cloudLink(shared));
    }
    @Test public void versionOneUpgradePreservesRecordsAndAddsConnectionTables(){
        store.close();context.deleteDatabase("paisapilot.db");
        try(android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase("paisapilot.db",0,null)){
            old.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,space TEXT NOT NULL,section TEXT NOT NULL,data TEXT NOT NULL)");old.execSQL("CREATE TABLE spaces(id TEXT PRIMARY KEY,name TEXT NOT NULL,type TEXT NOT NULL)");old.execSQL("INSERT INTO spaces VALUES('personal','Personal','Personal')");
            android.content.ContentValues v=new android.content.ContentValues();JSONObject r=tx("upgrade-record","Expense",29,true);v.put("id",r.optString("id"));v.put("space","personal");v.put("section","Transaction");v.put("data",r.toString());old.insertOrThrow("records",null,v);old.setVersion(1);
        }
        store=new Store(context);assertEquals(29,store.get("upgrade-record").optLong("amount"));store.connectCloud("personal","abcdefghijklmnopqrstuv123456789",true);assertNotNull(store.cloudLink("personal"));
    }
    @Test public void receivedSharedTransactionsAlwaysNeedLocalReview()throws Exception{
        String shared=store.addSpace("Shared","Household"),sheet="abcdefghijklmnopqrstuv123456789";store.connectCloud(shared,sheet,false);
        JSONObject r=tx("remote-new","Income",2900,true);SyncProtocol.Result result=new SyncProtocol.Result();result.incoming.put("remote-new",SyncProtocol.canonical(SyncProtocol.portable(r)));
        store.applySync(shared,sheet,SyncProtocol.fingerprint(Collections.emptyMap()),result);assertFalse(store.get("remote-new").optBoolean("reviewed"));assertEquals(0,store.totals(shared,YearMonth.now())[0]);assertEquals(1,store.totals(shared,YearMonth.now())[3]);
    }
    @Test public void realScreenCreatesTransactionAndSurvivesRecreation(){
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Add transaction")).perform(scrollTo(),click());
            onView(withHint("Description")).perform(replaceText("Test groceries"));
            onView(withHint("Amount in INR")).perform(replaceText("123.45"),closeSoftKeyboard());
            onView(withText("Save")).perform(click());
            onView(withText("₹123.45")).perform(scrollTo()).check(matches(isDisplayed()));
            activity.recreate();
            onView(withText("₹123.45")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
}
