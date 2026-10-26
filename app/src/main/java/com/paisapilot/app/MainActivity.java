package com.paisapilot.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private Store store;
    private String space="personal", page="Overview";
    private LinearLayout content;
    private YearMonth month=YearMonth.now();
    private String pendingCsv;
    private String importSpace;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final int EXPORT=10,IMPORT=11,SMS=12,BACKUP=13,RESTORE=14;
    private char[] backupPassword;
    private static final int GOOGLE=15;
    private interface CloudAction{void run(SheetsClient client)throws Exception;}
    private CloudAction cloudAction;
    private boolean cloudBusy=false,resumed=false;
    private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoSync=()->autoSyncTick();
    private String cloudTarget;
    private String lastAutoFingerprint="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new Store(this);
        if(state!=null) {space=state.getString("space","personal");page=state.getString("page","Overview");month=YearMonth.parse(state.getString("month",YearMonth.now().toString()));}
        else space=getPreferences(0).getString("space","personal");
        boolean exists=false;for(JSONObject s:store.spaces())if(s.optString("id").equals(space))exists=true;
        if(!exists)space="personal";render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putString("space",space);state.putString("page",page);state.putString("month",month.toString());
        // Pending SAF actions intentionally abort on recreation rather than export another space.
    }
    @Override protected void onResume(){super.onResume();resumed=true;timer.postDelayed(autoSync,60000);}
    @Override protected void onPause(){super.onPause();resumed=false;timer.removeCallbacks(autoSync);}
    @Override protected void onDestroy(){super.onDestroy();timer.removeCallbacks(autoSync);cloudAction=null;clearBackupPassword();worker.execute(store::close);worker.shutdown();}
    private void message(String text){new AlertDialog.Builder(this).setTitle("PaisaPilot").setMessage(text).setPositiveButton("OK",null).show();}
    private void error(Exception e){message(e.getMessage()==null?"Could not complete this action":e.getMessage());}
    private String selected(Spinner s){return String.valueOf(s.getSelectedItem());}
    private long money(EditText e){String v=e.getText().toString().trim();return Engine.parse(v.isEmpty()?"0":v);}
    private int integer(EditText e,int fallback){String v=e.getText().toString().trim();return v.isEmpty()?fallback:Integer.parseInt(v);}
    private void render() {
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setFitsSystemWindows(true);int pad=Ui.dp(this,18);root.setPadding(pad,pad,pad,pad);
        TextView brand=Ui.text(this,"PAISAPILOT  /  PRIVATE BY DESIGN",12,Ui.GREEN);root.addView(brand);
        List<JSONObject> spaces=store.spaces();String[] names=new String[spaces.size()];int index=0;
        for(int i=0;i<spaces.size();i++){names[i]=spaces.get(i).optString("name")+" · "+spaces.get(i).optString("type");if(spaces.get(i).optString("id").equals(space))index=i;}
        Spinner selector=Ui.choices(this,root,"Current space",names,names[index]);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String next=spaces.get(pos).optString("id");if(!next.equals(space)){space=next;getPreferences(0).edit().putString("space",space).apply();render();}}
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        HorizontalScrollView tabs=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);tabs.addView(row);
        for(String tab:new String[]{"Overview","Transactions","Plan","Goals","Commitments","Holdings","Data"}) {
            Button b=Ui.button(this,tab,tab.equals(page),v->{page=tab;render();});row.addView(b,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));
        }
        root.addView(tabs);content=Ui.column(this);ScrollView scroll=Ui.scroll(this,content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        content.addView(Ui.title(this,page));
        try {switch(page){case "Overview":overview();break;case "Transactions":transactions();break;case "Plan":plan();break;
            case "Goals":records("Goal");break;case "Commitments":records("Commitment");break;case "Holdings":records("Holding");break;default:data();}}
        catch(Exception e){content.addView(Ui.text(this,"Data needs attention: "+e.getMessage(),16,Ui.RED));}
        if(resumed&&!cloudBusy){String current=SyncProtocol.fingerprint(SyncProtocol.local(store.scopeRows(space)));if(!current.equals(lastAutoFingerprint)){timer.removeCallbacks(autoSync);timer.postDelayed(autoSync,5000);}}
    }
    private void note(String value){content.addView(Ui.text(this,value,14,Ui.MUTED));}
    private void card(String heading,String value,String detail){LinearLayout box=Ui.card(this);box.addView(Ui.text(this,heading,14,Ui.MUTED));box.addView(Ui.text(this,value,27,Ui.INK));box.addView(Ui.text(this,detail,13,Ui.MUTED));content.addView(box);}
    private void monthPicker(){
        LinearLayout row=new LinearLayout(this);Button prev=Ui.button(this,"‹",false,v->{month=month.minusMonths(1);render();});Button next=Ui.button(this,"›",false,v->{month=month.plusMonths(1);render();});
        row.addView(prev,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,month.toString(),18,Ui.INK),new LinearLayout.LayoutParams(0,-2,3));row.addView(next,new LinearLayout.LayoutParams(0,-2,1));content.addView(row);
    }
    private void overview(){
        monthPicker();long[] t=store.totals(space,month);
        card("Reviewed income",Engine.money(t[0]),"Confirmed earnings only. Borrowed principal and transfers excluded.");
        card("Net spending",Engine.money(t[1]-t[2]),"Purchases less refunds. Card repayments are not counted twice.");
        card("Awaiting review",String.valueOf(t[3]),"Unreviewed imports are excluded from these totals.");
        note("These are recorded earnings and expenses, not a bank balance. Missing records affect totals.");
        JSONObject settings=store.settings(space);
        if(settings.has("takehome")){long[] budget=calculate(settings);card("Monthly long-term contribution",Engine.money(budget[2]),"Scenario from your saved plan, not a transfer or investment order.");card("Risk contribution",Engine.money(budget[3]),"Limited by your chosen cap and pause/loss settings.");}
        content.addView(Ui.button(this,"Add transaction",true,v->editTransaction(null)));
        content.addView(Ui.button(this,"Create household or business space",false,v->newSpace()));
    }
    private void transactions(){
        monthPicker();content.addView(Ui.button(this,"Add transaction",true,v->editTransaction(null)));
        int count=0;
        for(JSONObject r:store.list(space,"Transaction")) {
            if(!YearMonth.from(LocalDate.parse(r.optString("date"))).equals(month))continue;
            LinearLayout box=Ui.card(this);box.addView(Ui.text(this,r.optString("title"),18,Ui.INK));
            box.addView(Ui.text(this,Engine.money(Store.amount(r,"amount"))+" · "+r.optString("kind"),17,Ui.GREEN));
            box.addView(Ui.text(this,r.optString("date")+" · "+r.optString("category")+" · "+(r.optBoolean("reviewed")?"Reviewed":"Needs review"),13,Ui.MUTED));
            box.addView(Ui.button(this,"Review / edit",false,v->editTransaction(r)));box.addView(Ui.button(this,"Copy to another space",false,v->copyToSpace(r)));box.addView(Ui.button(this,"Delete",false,v->confirmDelete(r)));content.addView(box);count++;
        }
        if(count==0)note("No transactions this month. Add one or import records from Data.");
    }
    private LinearLayout form(){LinearLayout f=Ui.column(this);int p=Ui.dp(this,16);f.setPadding(p,p,p,p);return f;}
    private void dialog(String title,LinearLayout form,String action,Runnable save){
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(Ui.scroll(this,form)).setNegativeButton("Cancel",null).setPositiveButton(action,null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();d.dismiss();render();}catch(Exception e){error(e);}}));d.show();
    }
    private void editTransaction(JSONObject existing){
        final JSONObject record=existing==null?Store.record(space,"Transaction"):existing;
        LinearLayout f=form();EditText title=Ui.input(this,f,"Description",record.optString("title"),false);
        EditText amount=Ui.input(this,f,"Amount in INR",existing==null?"":Engine.format(Store.amount(record,"amount")),true);
        EditText date=Ui.input(this,f,"Date YYYY-MM-DD",record.optString("date",LocalDate.now().toString()),false);
        Spinner kind=Ui.choices(this,f,"Type",Store.KINDS,record.optString("kind","Expense"));
        EditText category=Ui.input(this,f,"Category",record.optString("category","Uncategorised"),false);
        EditText account=Ui.input(this,f,"Account label (no credentials)",record.optString("account","Unspecified"),false);
        EditText owner=Ui.input(this,f,"Person / business owner",record.optString("owner"),false);
        EditText note=Ui.input(this,f,"Note",record.optString("note"),false);
        CheckBox reviewed=new CheckBox(this);reviewed.setText("I checked the amount, date and transaction type");reviewed.setChecked(record.optBoolean("reviewed",existing==null));f.addView(reviewed);
        f.addView(Ui.text(this,"Card purchases = Expense. Card payments = Card repayment. Record interest/fees separately as expenses. Investments and loans use their principal types.",13,Ui.MUTED));
        dialog("Transaction",f,"Save",()->{
            JSONObject r=Store.copy(record);Store.set(r,"title",title.getText().toString().trim());Store.set(r,"amount",money(amount));
            Store.set(r,"date",LocalDate.parse(date.getText().toString().trim()).toString());Store.set(r,"kind",selected(kind));Store.set(r,"currency","INR");
            Store.set(r,"category",category.getText().toString().trim());Store.set(r,"account",account.getText().toString().trim());Store.set(r,"owner",owner.getText().toString().trim());
            Store.set(r,"note",note.getText().toString());Store.set(r,"reviewed",reviewed.isChecked());if(!r.has("source"))Store.set(r,"source","manual");store.put(r);
        });
    }
    private void copyToSpace(JSONObject original){
        List<JSONObject> choices=new ArrayList<>();for(JSONObject s:store.spaces())if(!s.optString("id").equals(space))choices.add(s);
        if(choices.isEmpty()){message("Create a household or business space first.");return;}
        LinearLayout f=form();String[] names=new String[choices.size()];for(int i=0;i<names.length;i++)names[i]=choices.get(i).optString("name");
        Spinner target=Ui.choices(this,f,"Destination space",names,names[0]);
        CheckBox consent=new CheckBox(this);consent.setText("I approve copying this record, including notes, into the selected space.");f.addView(consent);
        dialog("Copy selected transaction",f,"Copy for review",()->{
            if(!consent.isChecked())throw new IllegalArgumentException("Approve sharing this selected record first");
            String destination=choices.get(target.getSelectedItemPosition()).optString("id");JSONObject copy=Store.copy(original);
            Store.set(copy,"id","copy-"+Imports.hash(original.optString("id")+"|"+destination));Store.set(copy,"reviewed",false);
            Store.set(copy,"source","approved-local-copy");int count=store.importTransactions(Collections.singletonList(copy),destination);
            message(count==0?"Already copied":"Copied to the selected space for review. No remote access was granted.");
        });
    }
    private void confirmDelete(JSONObject r){new AlertDialog.Builder(this).setTitle("Delete this record?").setMessage("This cannot be undone. Linked settlement transactions remain separate records.")
        .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.delete(r.optString("id"));render();}).show();}
    private void records(String section){
        content.addView(Ui.button(this,"Add "+section.toLowerCase(Locale.ROOT),true,v->editRecord(section,null)));
        if(section.equals("Holding"))note("Manual snapshots only. Values are not live prices. Updating a holding does not create a transaction.");
        if(section.equals("Commitment"))note("One-off due dates. Split loan principal from interest; enter interest as an expense. Add each EMI separately. Tasks can have zero amount.");
        List<JSONObject> rows=store.list(space,section);if(rows.isEmpty())note("Nothing here yet. Add your first record.");
        for(JSONObject r:rows){
            LinearLayout box=Ui.card(this);String title=r.optString("title");box.addView(Ui.text(this,title,19,Ui.INK));
            long amount=Store.amount(r,"amount");
            if(section.equals("Goal")){
                long saved=Store.amount(r,"saved"),remaining=Math.max(0,amount-saved);
                long months=Math.max(1,ChronoUnit.MONTHS.between(YearMonth.now(),YearMonth.from(LocalDate.parse(r.optString("date")))));
                box.addView(Ui.text(this,Engine.money(saved)+" / "+Engine.money(amount),18,Ui.GREEN));
                long needed=remaining/months+(remaining%months==0?0:1);
                box.addView(Ui.text(this,"Due "+r.optString("date")+" · "+Engine.money(needed)+" / month remaining, assuming no growth",13,Ui.MUTED));
            }else if(section.equals("Holding")){
                long current=Store.amount(r,"saved");box.addView(Ui.text(this,"Value "+Engine.money(current)+" · cost "+Engine.money(amount),17,Ui.GREEN));
                box.addView(Ui.text(this,"Unrealised change "+Engine.money(current-amount)+" · as of "+r.optString("date"),13,Ui.MUTED));
            }else{
                box.addView(Ui.text(this,Engine.money(amount)+" · "+r.optString("kind"),17,Ui.GREEN));
                box.addView(Ui.text(this,r.optBoolean("done")?"Completed":"Due "+r.optString("date"),14,r.optBoolean("done")?Ui.GREEN:Ui.MUTED));
                if(!r.optBoolean("done"))box.addView(Ui.button(this,r.optString("kind").equals("Task")?"Mark done":"Record payment / return",false,v->{
                    if(r.optString("kind").equals("Task")){Store.set(r,"done",true);store.put(r);render();}else settle(r);
                }));
            }
            if(!r.optString("owner").isEmpty())box.addView(Ui.text(this,r.optString("owner"),13,Ui.MUTED));
            box.addView(Ui.button(this,"Edit",false,v->editRecord(section,r)));box.addView(Ui.button(this,"Delete",false,v->confirmDelete(r)));content.addView(box);
        }
    }
    private void editRecord(String section,JSONObject existing){
        JSONObject record=existing==null?Store.record(space,section):existing;LinearLayout f=form();
        EditText title=Ui.input(this,f,"Name",record.optString("title"),false);
        EditText amount=Ui.input(this,f,section.equals("Goal")?"Target INR":section.equals("Holding")?"Cost basis INR":"Outstanding INR",existing==null?"":Engine.format(Store.amount(record,"amount")),true);
        EditText saved=section.equals("Commitment")?null:Ui.input(this,f,section.equals("Goal")?"Saved INR":"Current value INR",existing==null?"":Engine.format(Store.amount(record,"saved")),true);
        EditText date=Ui.input(this,f,section.equals("Holding")?"Valuation date YYYY-MM-DD":"Due date YYYY-MM-DD",record.optString("date",LocalDate.now().toString()),false);
        String[] types=section.equals("Commitment")?new String[]{"Bill","Card bill","EMI principal","Loan taken","Money lent","Task"}:section.equals("Holding")?new String[]{"Equity","Debt","Deposit","Gold","Other"}:new String[]{"Retirement","Emergency","Purchase","Other"};
        Spinner kind=Ui.choices(this,f,"Type",types,record.optString("kind",types[0]));
        EditText owner=Ui.input(this,f,"Owner / counterparty",record.optString("owner"),false);
        EditText note=Ui.input(this,f,"Note",record.optString("note"),false);
        dialog(section,f,"Save",()->{JSONObject r=Store.copy(record);Store.set(r,"title",title.getText().toString().trim());Store.set(r,"amount",money(amount));
            Store.set(r,"date",LocalDate.parse(date.getText().toString().trim()).toString());Store.set(r,"kind",selected(kind));Store.set(r,"owner",owner.getText().toString().trim());
            Store.set(r,"note",note.getText().toString());if(saved!=null)Store.set(r,"saved",money(saved));store.put(r);});
    }
    private void settle(JSONObject r){LinearLayout f=form();EditText amount=Ui.input(this,f,"Payment INR",Engine.format(Store.amount(r,"amount")),true);
        EditText date=Ui.input(this,f,"Payment date YYYY-MM-DD",LocalDate.now().toString(),false);
        dialog("Record actual payment",f,"Save payment",()->store.settle(r,money(amount),LocalDate.parse(date.getText().toString().trim()).toString()));}
    private long[] calculate(JSONObject s){
        long cap=s.optLong("riskcap"),funded=s.optLong("riskfunded"),loss=s.optLong("riskloss"),limit=s.optLong("losslimit");
        boolean paused=s.optBoolean("paused")||(limit>0&&loss>=limit);
        return Engine.plan(new long[]{s.optLong("takehome"),s.optLong("spending"),s.optLong("debt"),s.optLong("reserveadd"),s.optLong("goalsadd"),
            s.optLong("reserves"),s.optLong("essentials"),s.optInt("months",6),s.optInt("riskbp"),Math.max(0,cap-funded),paused?1:0});
    }
    private void plan(){
        JSONObject s=store.settings(space);note("Your assumptions, not a recommendation. No investment or trade is executed. Amounts are monthly unless labelled otherwise.");
        if(s.has("takehome")){
            long[] b=calculate(s);card("Long-term contribution",Engine.money(b[2]),"After commitments and the capped risk allocation.");card("Higher-risk contribution",Engine.money(b[3]),"User-set percentage and remaining funding cap. Loss threshold/manual pause stops new planned funding.");
            card("Monthly shortfall",Engine.money(b[1]),"A shortfall sets investment contributions to zero.");card("Emergency reserve gap",Engine.money(b[5]),"Target "+Engine.money(b[4]));
            long essentials=s.optLong("essentials"),reserves=s.optLong("reserves");
            card("Runway with no income",essentials==0?"Enter essentials":String.format(Locale.ROOT,"%.1f months",(double)reserves/essentials),"Accessible reserves only; exclude credit limits and market-dependent assets.");
            long retained=s.optLong("retained"),burn=Math.max(0,essentials-retained);
            card("One-income scenario",essentials==0?"Enter essentials":burn==0?"Essentials covered":String.format(Locale.ROOT,"%.1f months",(double)reserves/burn),"Assumes retained monthly household income of "+Engine.money(retained)+" and unchanged essential costs.");
            try{long[] p=Engine.project(s.optLong("initial"),b[2],s.optInt("years",10),s.optInt("returnbp",800),s.optInt("inflationbp",600),s.optInt("stepupbp"));
                card("Retirement growth scenario",Engine.money(p[0]),"After "+s.optInt("years",10)+" years; today's purchasing power "+Engine.money(p[1])+". Smooth assumed returns; excludes taxes/fees and future withdrawals.");
            }catch(IllegalArgumentException e){note("Projection unavailable: "+e.getMessage());}
            note("Risk headroom is manual: update already-funded capital and losses after activity. This app cannot enforce broker limits. Goals/holdings are separate records; avoid counting the same asset twice.");
        }
        content.addView(Ui.button(this,"Edit financial plan",true,v->editPlan()));
        content.addView(Ui.button(this,"Money until payday",false,v->payday()));
        content.addView(Ui.button(this,"Household contribution split",false,v->split()));
    }
    private void editPlan(){
        JSONObject s=store.settings(space);LinearLayout f=form();Map<String,EditText> fields=new LinkedHashMap<>();
        String[][] amounts={{"takehome","Take-home income"},{"spending","Spending excluding debt payments"},{"debt","Debt payments"},{"reserveadd","Emergency reserve contribution"},{"goalsadd","Other goal contributions"},{"reserves","Accessible emergency reserves"},{"essentials","Essential outgoings including debt"},{"retained","Income retained after one job loss"},{"initial","Existing long-term invested assets"},{"riskcap","Total experiment funding cap"},{"riskfunded","Capital already funded to experiment"},{"riskloss","Cumulative experiment loss"},{"losslimit","Loss threshold to pause (0 disables)"}};
        for(String[] entry:amounts)fields.put(entry[0],Ui.input(this,f,entry[1]+" INR",s.has(entry[0])?Engine.format(s.optLong(entry[0])):"",true));
        EditText months=Ui.input(this,f,"Emergency target months (0–120)",String.valueOf(s.optInt("months",6)),true);
        EditText risk=Ui.input(this,f,"Risk share % of surplus",Engine.format(s.optInt("riskbp")),true);
        EditText years=Ui.input(this,f,"Projection years (1–60)",String.valueOf(s.optInt("years",10)),true);
        EditText returns=Ui.input(this,f,"Assumed annual return % (-99 to 50)",Engine.format(s.optInt("returnbp",800)),false);
        EditText inflation=Ui.input(this,f,"Assumed annual inflation %",Engine.format(s.optInt("inflationbp",600)),true);
        EditText stepup=Ui.input(this,f,"Annual contribution increase %",Engine.format(s.optInt("stepupbp")),true);
        CheckBox paused=new CheckBox(this);paused.setText("Pause new risk funding");paused.setChecked(s.optBoolean("paused"));f.addView(paused);
        f.addView(Ui.text(this,"Enter assumptions you can afford. Increasing contributions needs income growth. Emergency reserves and near-term goals should not also be included in existing long-term assets.",13,Ui.MUTED));
        dialog("Financial plan",f,"Save plan",()->{JSONObject r=Store.copy(s);for(Map.Entry<String,EditText> e:fields.entrySet())Store.set(r,e.getKey(),money(e.getValue()));
            Store.set(r,"months",integer(months,6));Store.set(r,"riskbp",Math.toIntExact(money(risk)));Store.set(r,"years",integer(years,10));
            String rate=returns.getText().toString().trim();boolean negative=rate.startsWith("-");Store.set(r,"returnbp",Math.toIntExact(Engine.parse(negative?rate.substring(1):rate))*(negative?-1:1));
            Store.set(r,"inflationbp",Math.toIntExact(money(inflation)));Store.set(r,"stepupbp",Math.toIntExact(money(stepup)));Store.set(r,"paused",paused.isChecked());
            long[] b=calculate(r);Engine.project(r.optLong("initial"),b[2],r.optInt("years"),r.optInt("returnbp"),r.optInt("inflationbp"),r.optInt("stepupbp"));store.put(r);
        });
    }
    private void payday(){
        JSONObject settings=store.settings(space);LinearLayout f=form();
        EditText cash=Ui.input(this,f,"Available spending cash INR",Engine.format(settings.optLong("paydaycash")),true);
        EditText daily=Ui.input(this,f,"Daily spending excluding listed bills INR",Engine.format(settings.optLong("dailyspend")),true);
        EditText date=Ui.input(this,f,"Next payday YYYY-MM-DD",settings.optString("payday",LocalDate.now().plusDays(30).toString()),false);
        f.addView(Ui.text(this,"Enter cash available today after past payments. Keep emergency savings separate. Due/overdue unpaid commitments are included; money lent, expected income and tasks are excluded. Avoid entering the same bill in daily spending.",14,Ui.MUTED));
        dialog("Money until payday",f,"Calculate",()->{
            LocalDate today=LocalDate.now(),end=LocalDate.parse(date.getText().toString().trim());
            long days=ChronoUnit.DAYS.between(today,end);if(days<0||days>366)throw new IllegalArgumentException("Choose payday within the next 366 days");
            long available=money(cash),dailyValue=money(daily),bills=0;
            for(JSONObject r:store.list(space,"Commitment"))
                if(!r.optBoolean("done") && !r.optString("kind").equals("Task") && !r.optString("kind").equals("Money lent") && !LocalDate.parse(r.optString("date")).isAfter(end))
                    bills=Math.addExact(bills,Store.amount(r,"amount"));
            long[] b=Engine.plan(new long[]{available,Math.multiplyExact(days,dailyValue),bills,0,0,0,0,0,0,0,0});
            JSONObject saved=Store.copy(settings);Store.set(saved,"paydaycash",available);Store.set(saved,"dailyspend",dailyValue);Store.set(saved,"payday",end.toString());store.put(saved);
            message(days+" days until payday\nUnpaid commitments: "+Engine.money(bills)+"\n"+(b[1]>0?"Estimated shortfall: "+Engine.money(b[1]):"Estimated remaining: "+Engine.money(b[0]))+"\nNo expected salary or receivables counted before payday. Update cash after spending; this is a scenario, not a live bank balance.");
        });
    }
    private void split(){
        LinearLayout f=form();EditText total=Ui.input(this,f,"Shared monthly costs INR","",true);
        EditText a=Ui.input(this,f,"Person A income or weight","",true),b=Ui.input(this,f,"Person B income or weight","",true);
        Spinner method=Ui.choices(this,f,"Method",new String[]{"Equal","Income based / custom weights"},"Equal");
        dialog("Contribution calculator",f,"Calculate",()->{long costs=money(total),wa=selected(method).equals("Equal")?1:money(a),wb=selected(method).equals("Equal")?1:money(b);
            java.math.BigInteger sum=java.math.BigInteger.valueOf(wa).add(java.math.BigInteger.valueOf(wb));if(sum.signum()==0)throw new IllegalArgumentException("Enter at least one positive weight");
            // The quotient is within [0,costs], already bounded to MAX, so longValue is exact.
            long first=java.math.BigInteger.valueOf(costs).multiply(java.math.BigInteger.valueOf(wa)).divide(sum).longValue();message("Person A: "+Engine.money(first)+"\nPerson B: "+Engine.money(costs-first)+"\nThis calculation does not move money or share records.");});
    }
    private void newSpace(){LinearLayout f=form();EditText name=Ui.input(this,f,"Space name","",false);Spinner type=Ui.choices(this,f,"Space type",new String[]{"Household","Business"},"Household");
        f.addView(Ui.text(this,"Spaces are separate local ledgers. A household space does not connect another device or grant access. Export only records you explicitly choose to share.",14,Ui.MUTED));
        dialog("New space",f,"Create",()->{space=store.addSpace(name.getText().toString(),selected(type));getPreferences(0).edit().putString("space",space).apply();});}
    private void data(){
        note("Records stay in this app's private device storage. No app-operated server, analytics or cloud AI. Android/device security still applies. Uninstalling deletes local data.");
        content.addView(Ui.button(this,"Paste financial SMS / shared chat text",true,v->paste()));
        content.addView(Ui.button(this,"Import recent financial SMS",false,v->smsConsent()));
        content.addView(Ui.button(this,"Import PaisaPilot CSV",false,v->{importSpace=space;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}));
        content.addView(Ui.button(this,"Export this space's transactions CSV",false,v->export()));
        content.addView(Ui.button(this,"Create household or business space",false,v->newSpace()));
        content.addView(Ui.button(this,"Google Sheets & partner connection",false,v->cloudScreen()));
        content.addView(Ui.button(this,"Encrypted full backup",false,v->backupDialog(false)));
        content.addView(Ui.button(this,"Restore full backup",false,v->backupDialog(true)));
        note("CSV opens in Google Sheets/Excel and contains transactions only; goals, plans, commitments and holdings are not included. Use encrypted full backup for all records. Optional Google Sheets sync and partner access need Google OAuth/API setup. Standard bank-statement adapters are not connected.");
        note("SMS import is optional, on demand and limited to supported unambiguous formats. Permission may be restricted by Android or store policy. Paste/manual entry works without SMS access. No background chat access.");
        note("Imported records start unreviewed. Match them to statements, remove duplicates from different sources, and correct transfers, loans and card repayments before approving. No claim of complete capture or automatic reconciliation.");
        content.addView(Ui.button(this,"App capabilities",false,v->message("PaisaPilot 0.3 pilot\nOffline INR tracking, review, CSV, planning, separate spaces, goals, holdings and commitments.\nNo live market prices, payment execution, investment orders, tax filing or return guarantees.\nOptional Google Sheets collaboration is Google-account controlled and needs OAuth/API setup. Business spaces are not full accounting.")));
    }
    private void cloudScreen(){
        JSONObject link=store.cloudLink(space);LinearLayout f=form();final String target=space;
        f.addView(Ui.text(this,"Optional Google-hosted sync. Only this selected space is sent, including its existing history, goals, holdings, notes and plan. Other spaces remain private. Received transactions require local review. Google authorization includes spreadsheet-wide scope; this app targets only the connected file.",14,Ui.MUTED));
        if(link==null){EditText sheet=Ui.input(this,f,"Existing PaisaPilot spreadsheet link (blank creates one)","",false);
            CheckBox consent=new CheckBox(this);consent.setText("I approve syncing ALL current and future records in this selected space to Google and its collaborators.");f.addView(consent);
            dialog("Connect selected space",f,"Authorize & connect",()->{if(!consent.isChecked())throw new IllegalArgumentException("Approve the sync scope first");String value=sheet.getText().toString().trim();if(!value.isEmpty())SheetsClient.sheetId(value);
                authorize(client->{String id=value.isEmpty()?client.create("PaisaPilot shared space"):SheetsClient.sheetId(value);client.events(id);store.connectCloud(target,id,value.isEmpty());runOnUiThread(()->{if(!isDestroyed()){render();message("Spreadsheet connected. Use Sync now to exchange records. OAuth/API configuration must match this app's signing certificate.");}});},true,target);
            });return;
        }
        f.addView(Ui.text(this,"Sheet: "+link.optString("sheet")+"\nLast successful sync: "+(link.optLong("last")==0?"Never":new java.util.Date(link.optLong("last")))+"\n"+link.optString("error"),13,Ui.MUTED));
        f.addView(Ui.button(this,"Sync now",true,v->sync(target,true,new HashMap<>())));
        CheckBox auto=new CheckBox(this);auto.setText("Automatic sync while this app is open (about every minute)");auto.setChecked(link.optBoolean("auto"));f.addView(auto);
        auto.setOnCheckedChangeListener((b,checked)->{store.cloudStatus(target,"",checked);if(checked)timer.postDelayed(autoSync,1000);});
        f.addView(Ui.button(this,"Open spreadsheet",false,v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://docs.google.com/spreadsheets/d/"+link.optString("sheet"))))));
        if(link.optBoolean("owner")){
            EditText email=Ui.input(this,f,"Partner Google account email","",false);CheckBox approval=new CheckBox(this);approval.setText("Grant this account writer access to ALL data in this shared space. No email notification will be sent.");f.addView(approval);
            f.addView(Ui.button(this,"Grant partner access",false,v->{if(!approval.isChecked()){message("Approve partner access first");return;}String address=email.getText().toString().trim();authorize(client->{client.invite(link.optString("sheet"),address);runOnUiThread(()->{if(!isDestroyed())message("Access granted. Share the spreadsheet link with your partner separately. They must authorize Google and explicitly connect their chosen local space.");});},true,target);}));
            f.addView(Ui.button(this,"Review / revoke collaborator access",false,v->authorize(client->{org.json.JSONArray permissions=client.permissions(link.optString("sheet"));runOnUiThread(()->{if(!isDestroyed())permissionChoices(target,link.optString("sheet"),permissions);});},true,target)));
        }
        f.addView(Ui.button(this,"Disconnect this device's space",false,v->{store.disconnectCloud(target);timer.removeCallbacks(autoSync);render();message("Disconnected locally. Other Google collaborators and their copies keep access until the spreadsheet owner revokes it.");}));
        f.addView(Ui.text(this,"Requires Google Play services, a registered Android OAuth client, enabled Sheets/Drive APIs and approved consent configuration. Sheet access is Google-account controlled. Automatic sync stops when this app closes or an error needs review. Sync journal rows are append-only: make reports in separate tabs; direct journal edits stop sync rather than being overwritten.",13,Ui.MUTED));
        new AlertDialog.Builder(this).setTitle("Google Sheets connection").setView(Ui.scroll(this,f)).setPositiveButton("Close",null).show();
    }
    private void permissionChoices(String target,String sheet,org.json.JSONArray permissions){
        List<JSONObject> users=new ArrayList<>();if(permissions!=null)for(int i=0;i<permissions.length();i++){JSONObject p=permissions.optJSONObject(i);if(p!=null&&!p.optString("role").equals("owner"))users.add(p);}
        if(users.isEmpty()){message("No non-owner collaborators found");return;}String[] labels=new String[users.size()];for(int i=0;i<labels.length;i++)labels[i]=users.get(i).optString("emailAddress",users.get(i).optString("type"))+" · "+users.get(i).optString("role");
        new AlertDialog.Builder(this).setTitle("Choose access to revoke").setItems(labels,(d,index)->new AlertDialog.Builder(this).setTitle("Revoke "+labels[index]+"?").setMessage("Stops future access through this permission. Previously downloaded copies remain with the recipient.").setNegativeButton("Cancel",null).setPositiveButton("Revoke",(q,w)->authorize(client->{client.revoke(sheet,users.get(index).optString("id"));runOnUiThread(()->{if(!isDestroyed())message("Google permission revoked");});},true,target)).show()).show();
    }
    private void sync(String target,boolean manual,Map<String,String> choices){
        if(cloudBusy){if(manual)message("A Google operation is already running");return;}
        store.cloudStatus(target,"",null);
        String device=getPreferences(0).getString("syncDevice",null);if(device==null){device=UUID.randomUUID().toString();getPreferences(0).edit().putString("syncDevice",device).apply();}final String deviceId=device;
        authorize(client->{try{client.sync(store,target,deviceId,choices);runOnUiThread(()->{if(!isDestroyed()){lastAutoFingerprint=SyncProtocol.fingerprint(SyncProtocol.local(store.scopeRows(space)));render();if(manual)message("Sync complete. Review newly received transactions before counting them.");}});}
            catch(SyncProtocol.Conflict conflict){store.cloudStatus(target,"Concurrent edits need review",null);runOnUiThread(()->{if(!isDestroyed()&&manual)resolveConflict(target,conflict,choices);});}
        },manual,target);
    }
    private void resolveConflict(String target,SyncProtocol.Conflict conflict,Map<String,String> choices){
        LinearLayout f=form();f.addView(Ui.text(this,"Concurrent versions of "+conflict.record+". Choose which record should become the shared version. No version is silently discarded.",14,Ui.MUTED));
        RadioGroup radio=new RadioGroup(this);List<String> ids=new ArrayList<>();ids.add("LOCAL");List<String> payloads=new ArrayList<>();payloads.add(conflict.local);
        for(SyncProtocol.Event e:conflict.remote){ids.add(e.id);payloads.add(e.payload);}
        List<Integer> viewIds=new ArrayList<>();for(int i=0;i<ids.size();i++){RadioButton b=new RadioButton(this);int viewId=View.generateViewId();viewIds.add(viewId);b.setId(viewId);b.setText((i==0?"This device":"Cloud revision "+ids.get(i))+"\n"+payloads.get(i));radio.addView(b);}f.addView(radio);
        dialog("Review sync conflict",f,"Use selected version",()->{int index=viewIds.indexOf(radio.getCheckedRadioButtonId());if(index<0||index>=ids.size())throw new IllegalArgumentException("Choose a version after reviewing it");Map<String,String> next=new HashMap<>(choices);next.put(conflict.record,ids.get(index));sync(target,true,next);});
    }
    private void autoSyncTick(){
        if(!resumed||isDestroyed())return;
        if(!cloudBusy){JSONObject link=store.cloudLink(space);if(link!=null&&link.optBoolean("auto")&&link.optString("error").isEmpty())sync(space,false,new HashMap<>());}
        timer.removeCallbacks(autoSync);timer.postDelayed(autoSync,60000);
    }
    private void authorize(CloudAction action,boolean interactive,String target){
        if(cloudBusy){if(interactive)message("A Google operation is already running");return;}cloudBusy=true;cloudAction=action;cloudTarget=target;
        com.google.android.gms.auth.api.identity.AuthorizationRequest request=com.google.android.gms.auth.api.identity.AuthorizationRequest.builder().setRequestedScopes(Arrays.asList(new com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/spreadsheets"),new com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))).build();
        com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result->{
            if(isDestroyed()){cloudBusy=false;cloudAction=null;return;}
            if(result.hasResolution()){if(!interactive){cloudFailure(new IllegalArgumentException("Open Google connection and Sync now to authorize access"),false);return;}
                try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),GOOGLE,null,0,0,0);}catch(Exception e){cloudFailure(e,true);}}
            else runCloud(result.getAccessToken(),interactive);
        }).addOnFailureListener(e->cloudFailure(e,interactive));
    }
    private void runCloud(String token,boolean interactive){CloudAction action=cloudAction;cloudAction=null;if(action==null){cloudBusy=false;message("Google action cancelled after app recreation. Start again.");return;}
        worker.execute(()->{try{action.run(new SheetsClient(token));}catch(Exception e){runOnUiThread(()->cloudFailure(e,interactive));}finally{runOnUiThread(()->cloudBusy=false);}});
    }
    private void cloudFailure(Exception e,boolean interactive){cloudBusy=false;cloudAction=null;if(isDestroyed())return;if(cloudTarget!=null)store.cloudStatus(cloudTarget,"Google connection needs review: "+e.getClass().getSimpleName(),null);if(interactive&&!isDestroyed())message("Google connection failed: "+e.getMessage()+"\nVerify the Cloud project's Android package/signing SHA-1, consent test users, and enabled Sheets/Drive APIs. Offline finance and backups still work.");}
    private void clearBackupPassword(){if(backupPassword!=null){Arrays.fill(backupPassword,'\0');backupPassword=null;}}
    private void backupDialog(boolean restore){
        LinearLayout f=form();EditText pass=Ui.input(this,f,"Backup passphrase (12–128 English characters)","",false);pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm=restore?null:Ui.input(this,f,"Confirm passphrase","",false);if(confirm!=null)confirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox consent=new CheckBox(this);consent.setText(restore?"Replace ALL local spaces and records with this backup. I have saved any newer data.":"Include ALL spaces, private records, goals, holdings, commitments and plans.");f.addView(consent);
        f.addView(Ui.text(this,"Keep the passphrase separately. It cannot be recovered. Backups are encrypted; Google credentials and connection state are never included. Restore validates everything and rolls back if invalid.",14,Ui.MUTED));
        dialog(restore?"Restore all data":"Encrypted backup",f,"Choose file",()->{
            String password=pass.getText().toString();if(password.length()<12||password.length()>128||!password.matches("[ -~]+"))throw new IllegalArgumentException("Use 12–128 printable English characters");
            if(confirm!=null&&!password.equals(confirm.getText().toString()))throw new IllegalArgumentException("Passphrases do not match");
            if(!consent.isChecked())throw new IllegalArgumentException("Confirm the backup/restore scope first");
            clearBackupPassword();backupPassword=password.toCharArray();pass.setText("");if(confirm!=null)confirm.setText("");
            Intent i=new Intent(restore?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);
            if(!restore)i.putExtra(Intent.EXTRA_TITLE,"PaisaPilot-"+LocalDate.now()+".paisabackup");startActivityForResult(i,restore?RESTORE:BACKUP);
        });
    }
    private byte[] readFile(Uri uri,int limit)throws IOException{
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Could not read file");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("File exceeds size limit");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    private void paste(){LinearLayout f=form();EditText text=Ui.input(this,f,"Paste one transaction alert","",false);text.setSingleLine(false);text.setMinLines(4);
        EditText date=Ui.input(this,f,"Transaction date YYYY-MM-DD",LocalDate.now().toString(),false);
        dialog("Import text",f,"Import for review",()->{String body=text.getText().toString();String day=LocalDate.parse(date.getText().toString().trim()).toString();
            JSONObject r=Imports.sms(body,"pasted",Imports.hash(body+"|"+day),day,space);
            if(r==null)throw new IllegalArgumentException("No unambiguous supported transaction found. OTPs, multiple amounts and promises to pay are not imported. Enter this transaction manually.");
            int count=store.importTransactions(Collections.singletonList(r),space);message(count==0?"Already imported":"Imported. Review the transaction before it counts in totals.");});
    }
    private void smsConsent(){new AlertDialog.Builder(this).setTitle("Read recent SMS on this device?").setMessage("On your request, inspect up to 500 SMS from the last 30 days. Only supported financial candidates are saved for review; raw bodies and OTPs are not stored. Nothing is sent to a server. You can revoke permission in Android settings.")
        .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{if(checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED)readSms();else requestPermissions(new String[]{Manifest.permission.READ_SMS},SMS);}).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);
        if(request==SMS){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)readSms();else message("SMS access was not granted. Use paste, manual entry or CSV import.");}}
    private void readSms(){final String target=space;message("Import started. Candidates will be saved as unreviewed in the current space.");
        worker.execute(()->{try{List<JSONObject> rows=new ArrayList<>();long since=System.currentTimeMillis()-30L*24*60*60*1000;
            try(Cursor c=getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"_id","address","body","date"},"date>=?",new String[]{String.valueOf(since)},"date DESC")){
                int count=0;while(c!=null&&c.moveToNext()&&count++<500){String body=c.getString(2);String day=Instant.ofEpochMilli(c.getLong(3)).atZone(ZoneId.systemDefault()).toLocalDate().toString();
                    JSONObject r=Imports.sms(body,c.getString(1)==null?"unknown":c.getString(1),c.getString(0),day,target);if(r!=null)rows.add(r);}}
            int added=store.importTransactions(rows,target);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){render();message("Imported "+added+" candidates. Review before counting them as income or expenses.");}});
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())error(e);});}});
    }
    private void export(){
        CheckBox consent=new CheckBox(this);consent.setText("I approve exporting all transactions in this selected space, including notes and pending records.");
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Export selected space").setMessage("Anyone with the exported file can read it. Private records from other spaces are excluded. This is a transaction export, not a complete backup.").setView(consent).setNegativeButton("Cancel",null).setPositiveButton("Choose file",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(!consent.isChecked()){consent.setError("Approval required");return;}
            pendingCsv=Imports.csv(store.list(space,"Transaction"));Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"PaisaPilot-"+LocalDate.now()+".csv");startActivityForResult(i,EXPORT);d.dismiss();}));d.show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==GOOGLE){
            if(result!=RESULT_OK||data==null){cloudFailure(new IllegalArgumentException("Google authorization cancelled"),true);return;}
            try{runCloud(com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data).getAccessToken(),true);}catch(Exception e){cloudFailure(e,true);}return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null){pendingCsv=null;importSpace=null;clearBackupPassword();return;}
        Uri uri=data.getData();
        if(request==BACKUP||request==RESTORE){
            char[] password=backupPassword;backupPassword=null;if(password==null){message("Action cancelled after app recreation. Please start again.");return;}
            worker.execute(()->{try{
                if(request==BACKUP){byte[] encrypted=Backup.encrypt(store.snapshot(),password);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Could not write backup");out.write(encrypted);}}
                else store.restore(Backup.decrypt(readFile(uri,Backup.LIMIT),password));
                runOnUiThread(()->{if(!isDestroyed()){if(request==RESTORE){space="personal";getPreferences(0).edit().putString("space",space).apply();}render();message(request==BACKUP?"Full encrypted backup saved":"All records restored. Cloud connections must be reconnected separately.");}});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error(e);});}finally{Arrays.fill(password,'\0');}});
        }else if(request==EXPORT){String bytes=pendingCsv;pendingCsv=null;if(bytes==null){message("Export cancelled after app recreation. Please export again.");return;}
            worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Could not open destination");out.write(bytes.getBytes(StandardCharsets.UTF_8));runOnUiThread(()->{if(!isDestroyed())message("Transaction CSV exported. Open it with Sheets or Excel.");});}
                catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error(e);});}});
        }else if(request==IMPORT){String target=importSpace;importSpace=null;if(target==null){message("Import cancelled after app recreation. Please select the file again.");return;}
            worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Could not read file");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(bytes.size()+n>2_000_000)throw new IOException("Import files up to 2 MB");bytes.write(buffer,0,n);}
                List<JSONObject> rows=Imports.csv(bytes.toString(StandardCharsets.UTF_8.name()),target);int count=store.importTransactions(rows,target);
                runOnUiThread(()->{if(!isDestroyed()){render();message("Imported "+count+" new records for review. Existing IDs were preserved.");}});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error(e);});}});
        }
    }
}
