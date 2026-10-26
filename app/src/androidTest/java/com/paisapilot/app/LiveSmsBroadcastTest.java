package com.paisapilot.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.List;
import static org.junit.Assert.*;

/** Host sends a synthetic SMS only to an emulator after the ready marker. */
@RunWith(AndroidJUnit4.class)
public class LiveSmsBroadcastTest {
    @Test public void systemSmsCreatesPendingRecord()throws Exception{
        Assume.assumeTrue("Run with scripts/test_live_sms.sh","true".equals(InstrumentationRegistry.getArguments().getString("liveSms")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("paisapilot.db");
        try(androidx.test.core.app.ActivityScenario<MainActivity> screen=androidx.test.core.app.ActivityScenario.launch(MainActivity.class);Store store=new Store(context)){
            screen.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            for(String permission:new String[]{Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS}){
                ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("pm grant "+context.getPackageName()+" "+permission);
                try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[256];while(in.read(b)!=-1){ /* drain grant command */ }}
                assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(permission));
            }
            store.spaces();SmsCapture.enable(context,true);Bundle ready=new Bundle();ready.putString("stream","WAITING_FOR_TEST_SMS\n");InstrumentationRegistry.getInstrumentation().sendStatus(1,ready);
            List<JSONObject> rows=java.util.Collections.emptyList();long deadline=System.currentTimeMillis()+25000;
            while(System.currentTimeMillis()<deadline){rows=store.list("personal","Transaction");if(!rows.isEmpty())break;Thread.sleep(250);}
            assertEquals("System SMS was not captured",1,rows.size());assertEquals(2900,rows.get(0).optLong("amount"));assertFalse(rows.get(0).optBoolean("reviewed"));
            assertFalse(rows.get(0).toString().contains("test account"));
        }finally{SmsCapture.enable(context,false);context.deleteDatabase("paisapilot.db");}
    }
}
