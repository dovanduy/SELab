package semeru.odbr;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Created by Rich on 2/16/16.
 * Record Floating Widget is an overlay that is a service that is displayed over the application we are reporting.
 * The overlay allows for recording events or submitting the report. If the recording is started, it post delays to
 * a handler to see if it should reappear. Once the time since last event has been over 3 seconds it will appear again
 * and restart the process. Once it reappears it is responsible for sending notifications to each of the process managers
 * to terminate their processes.
 */
public class RecordFloatingWidget extends Service {
    WindowManager wm;
    LinearLayout ll;
    Handler handler = new Handler();

    final static WindowManager.LayoutParams parameters = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSPARENT);

    private SensorDataManager sdm;
    private GetEventManager gem;
    private boolean recording;
    private Display display;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Short wait for app to startup
        try {
            Thread.sleep(1000);
        } catch (Exception e) {}

        // Prepare Report, start Data collection
        BugReport.getInstance().clearReport();
        gem = new GetEventManager();
        sdm = new SensorDataManager(this);
        display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        BugReport.getInstance().setStartOrientation(display.getRotation());

        // Short wait for gem / sdm startup
        try {
            Thread.sleep(500);
        } catch (Exception e) {}

        //Initialize Overlay
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ll = new LinearLayout(this);
        ll.setGravity(Gravity.CENTER);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.floating_widget_layout, ll);
        ll.setBackgroundColor(0xA0000000);
        wm.addView(ll, parameters);
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        Log.d("RFW", "Orientation: " + BugReport.getInstance().getCurrentOrientation() + " | " + display.getRotation());
        if (recording) {
            BugReport.getInstance().addOrientationChange(System.currentTimeMillis(), display.getRotation());
            Globals.event_active = true;
        }
    }

    /**
     * Hides the overlay
     */
    public void hideOverlay() {
        wm.removeView(ll);
    }

    /**
     * Restores the overlay and stops the managers
     */
    public void restoreOverlay() {
        stopRecording();
        wm.addView(ll, parameters);
    }


    /**
     * Launches Record Activity and destroys itself, as the service is not associated with a specific activity
     * so the overlay would persist otherwise
     * @param v
     */
    public void submitReport(View v) {
        ll.setVisibility(View.GONE);
        Intent intent = new Intent();
        intent.setClass(this, ReportActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        onDestroy();
    }

    @Override
    public void onDestroy() {
        gem.stopRecording();
        super.onDestroy();
        stopSelf();
    }

    /**
     * Starts the recording process for the events, called when the Record Inputs button is pressed
     * @param view
     */
    public void recordEvents(View view){
        recording = true;
        Globals.event_active = true;
        gem.startRecording();
        sdm.startRecording();
        hideOverlay();
        handler.post(widget_timer);
    }

    /**
     * Finishes the recording process for the events, called when the overlay reappears
     */
    public void stopRecording() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                recording = false;
                sdm.stopRecording();
                gem.pauseRecording();
            }
        });
    }

    /**
     * Runnable to be posted to the handler that tests whether or not we should restore the overlay
     */
    public Runnable widget_timer = new Runnable() {
        @Override
        public void run() {

            //check to see if we have reached the condition.
            if(Globals.event_active == false){
                //we dont want to loop anymore
                restoreOverlay();
            }
            else{
                //check again 3 seconds later
                Globals.event_active = false;
                handler.postDelayed(widget_timer, 3000);
            }
        }
    };

}