package com.gharmap.app;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Tests persistence and the native geometry path without pretending an emulator
 * validates real camera reconstruction or sensor accuracy. */
@RunWith(AndroidJUnit4.class)
public class ProjectFlowTest {
    @Rule public ActivityScenarioRule<MainActivity> activity = new ActivityScenarioRule<>(MainActivity.class);
    @Test public void measuredRoomSurvivesActivityRecreationOnAnotherFloor() throws Exception {
        String property="Smoke-"+System.currentTimeMillis();
        onView(withText("+  New property")).perform(click());
        onView(withHint("Name")).perform(replaceText(property),closeSoftKeyboard());
        onView(withText("Save")).perform(click());
        onView(withText("+ Add floor")).perform(scrollTo(),click());
        onView(withText("Save")).perform(click());
        onView(withText("Enter measured room")).perform(scrollTo(),click());
        onView(withHint("Room name")).perform(replaceText("SmokeRoom"));
        onView(withHint("Width in metres")).perform(replaceText("4"));
        onView(withHint("Length in metres")).perform(replaceText("3"),closeSoftKeyboard());
        onView(withText("Add room")).perform(click());
        onView(withText("12.00 m² · user-measured")).perform(scrollTo()).check(matches(isDisplayed()));
        activity.getScenario().recreate();
        onView(withText("12.00 m² · user-measured")).perform(scrollTo()).check(matches(isDisplayed()));
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot();
        if(image!=null){
            File file=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"smoke-room.png");
            try(FileOutputStream output=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,output);}
            image.recycle();
        }
        onView(withText("Delete property & recordings")).perform(scrollTo(),click());
        onView(withText("Delete")).perform(click());
        onView(withText("+  New property")).check(matches(isDisplayed()));
    }
}
