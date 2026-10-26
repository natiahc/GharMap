package com.paisapilot.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only the system may send this receiver's protected SMS broadcast. */
public final class SmsReceiver extends BroadcastReceiver {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())||!SmsCapture.enabled(context)||
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED||
            context.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return;
        SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);if(messages==null||messages.length==0||messages[0]==null)return;
        String sender=messages[0].getOriginatingAddress();long timestamp=messages[0].getTimestampMillis();StringBuilder body=new StringBuilder();
        for(SmsMessage message:messages){if(message==null||sender==null||!sender.equals(message.getOriginatingAddress()))return;body.append(message.getMessageBody()==null?"":message.getMessageBody());if(body.length()>4000)return;}
        PendingResult pending=goAsync();Context app=context.getApplicationContext();String text=body.toString();
        worker.execute(()->{try{SmsCapture.ingest(app,text,sender,timestamp);}
            catch(Exception e){app.getSharedPreferences("sms_capture",0).edit().putString("error","Live capture needs review; import the missing transaction manually").apply();}
            finally{pending.finish();}});
    }
}
