package com.paisapilot.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared ingestion for live broadcasts and historical alerts. Never stores a raw SMS body. */
final class SmsCapture {
    private static final CopyOnWriteArrayList<WeakReference<Runnable>> observers=new CopyOnWriteArrayList<>();
    static boolean enabled(Context context){return context.getSharedPreferences("sms_capture",0).getBoolean("enabled",false);}
    static void enable(Context context,boolean value){context.getSharedPreferences("sms_capture",0).edit().putBoolean("enabled",value).apply();}
    static JSONObject candidate(String body,String sender,long timestamp,String space){
        if(sender==null||sender.isEmpty()||timestamp<=0)return null;
        String source=timestamp+"|"+Imports.hash(body==null?"":body);
        String date=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        return Imports.sms(body,sender,source,date,space);
    }
    static int ingest(Context context,String body,String sender,long timestamp){
        if(!enabled(context))return 0;
        // New captures stay in Personal. Sharing to a household/business is a separate user choice.
        JSONObject row=candidate(body,sender,timestamp,"personal");if(row==null)return 0;
        int added;
        try(Store store=new Store(context)){added=store.importTransactions(Collections.singletonList(row),"personal");}
        if(added>0){context.getSharedPreferences("sms_capture",0).edit().putLong("last",System.currentTimeMillis()).remove("error").apply();signal();}
        return added;
    }
    static void observe(Runnable listener){unobserve(listener);observers.add(new WeakReference<>(listener));}
    static void unobserve(Runnable listener){for(WeakReference<Runnable> ref:observers){Runnable item=ref.get();if(item==null||item==listener)observers.remove(ref);}}
    static void signal(){new Handler(Looper.getMainLooper()).post(()->{for(WeakReference<Runnable> ref:observers){Runnable item=ref.get();if(item==null)observers.remove(ref);else item.run();}});}
}
