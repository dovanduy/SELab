package semeru.odbr;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Brendan Otten on 4/20/2016.
 * Replay service that iterates over the ReportEvents and utilizes sendevent to send the getEvent lines to re-enact
 * the input traces. Just a feature to allow the tester to replay the events they put in, gives a visual representation
 * for how the developer will be able to re-enact their reported bug
 */
public class ReplayService extends IntentService {

    Process su_replay;
    OutputStream os;
    ArrayList<ReplayEvent.SendEventBundle> preProcessedEvents;

    public ReplayService() {
        super("ReplayService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        //we're going to have to do an SU thing here, but for now, lets just log something every 10 seconds
        ExecutorService service = Executors.newSingleThreadExecutor();

        try {
            su_replay = Runtime.getRuntime().exec("su", null, null);
            os = su_replay.getOutputStream();
            service.submit(new ReplayEvent(this));
        } catch(Exception e){}
    }


    class ReplayEvent implements Runnable {
        private long wait_before = 2000; //Milliseconds to wait before starting inputs
        private long wait_after = 2000; //Milliseconds to wait after executing inputs
        private Context context;

        public ReplayEvent(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            freezeOrientation();
            changeOrientation(BugReport.getInstance().getStartOrientation());
            try {
                HashMap<String, DataOutputStream> procs = new HashMap<String, DataOutputStream>();
                for (String device : getDevices()) {
                    //Grant permissions to directly write to device
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream dataOutputStream = new DataOutputStream(process.getOutputStream());
                    dataOutputStream.writeBytes("chmod 777 " + device + " \n");
                    dataOutputStream.flush();
                    dataOutputStream.writeBytes("exec 3>" + device + "\n");
                    dataOutputStream.flush();
                    procs.put(device, dataOutputStream);
                }

                Thread.sleep(wait_before);
                long previousEventTime = BugReport.getInstance().getStartTime();
                ArrayList<SendEventBundle> events = preprocessEvents();
                long waitUntil = 0;
                for (SendEventBundle bundle : events) {
                    waitUntil = System.currentTimeMillis() + min(bundle.timeMillis - previousEventTime, 2000);
                    while (System.currentTimeMillis() < waitUntil) {/* <(^_^)> */}
                    if ("ORIENTATION".equals(bundle.device)) {
                        changeOrientation(bundle.orientation);
                    }
                    else {
                        for (String cmd : bundle.commandStrings) {
                            procs.get(bundle.device).writeBytes("echo -n '" + cmd + "' >&3 \n");
                        }
                        procs.get(bundle.device).flush();
                    }
                    previousEventTime = bundle.timeMillis;
                }
                releaseOrientation();
                os.close();
                su_replay.waitFor();
                Thread.sleep(wait_after);
            } catch (Exception e) {
                Log.e("ReplayService", "Unable to replay event: " + e.getMessage());
                e.printStackTrace();
                replayUsingSendEvent();
            }

            Intent record_intent = new Intent(ReplayService.this, ReportActivity.class);
            record_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            record_intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(record_intent);
        }

        public void replayUsingSendEvent() {
            try {
                Thread.sleep(wait_before);
                su_replay = Runtime.getRuntime().exec("su", null, null);
                os = su_replay.getOutputStream();
            } catch (Exception e) {
                Log.e("ReplayService", "Could not start su!");
            }
            ArrayList<SendEventBundle> events = preprocessEvents();
            long waitUntil = 0;
            long previousEventTime = BugReport.getInstance().getStartTime();
            for (SendEventBundle bundle : events) {
                waitUntil = System.currentTimeMillis() + min(bundle.timeMillis - previousEventTime, 2000);
                while (System.currentTimeMillis() < waitUntil) {/* <(^_^)> */}
                if ("ORIENTATION".equals(bundle.device)) {
                    changeOrientation(bundle.orientation);
                }
                else {
                    for (byte[] cmd : bundle.commands) {
                        try {
                            os.write((new GetEvent(cmd).getSendEvent(bundle.device) + " \n").getBytes("ASCII"));
                        } catch (Exception e) {
                            Log.e("ReplayService", e.getMessage());
                        }
                    }
                    try {
                        os.flush();
                    } catch (Exception e) {
                        Log.e("ReplayService", e.getMessage());
                    }
                }
                previousEventTime = bundle.timeMillis;
            }
            try {
                releaseOrientation();
                os.close();
                su_replay.waitFor();
                Thread.sleep(wait_after);
            } catch(Exception e) {
                Log.e("ReplayService", e.getMessage());
            }
        }


        /**
         * Prevent accelerometer from affecting device orientation
         */
        public void freezeOrientation() {
            try {
                os.write(("settings put system accelerometer_rotation 0 \n").getBytes("ASCII"));
                os.flush();
            } catch (Exception e) {
                Log.e("ReplayService", "Could not freeze screen rotation!");
            }
        }

        private long min(long a, long b) {
            return a < b ? a : b;
        }

        /**
         * Allow accelerometer to change orientation
         */
        public void releaseOrientation() {
            try {
                os.write(("settings put system accelerometer_rotation 1 \n").getBytes("ASCII"));
                os.flush();
            } catch (Exception e) {
                Log.e("ReplayService", "Could not release screen orientation!");
            }
        }

        /**
         * Change device orientation to match given orientation
         * @param orientation
         */
        public void changeOrientation(int orientation) {
            try {
                os.write(("settings put system user_rotation " + orientation + " \n").getBytes("ASCII"));
                os.flush();
                Thread.sleep(2000);
            } catch (Exception e) {
                Log.e("ReplayService", "Could not change screen orientation to: " + orientation + "!");
            }
        }

        public String[] getDevices() {
            Set<String> devices = new HashSet<String>();
            for (ReportEvent event : BugReport.getInstance().getEventList()) {
                devices.add(event.getDevice());
            }
            return devices.toArray(new String[devices.size()]);
        }


        public ArrayList<SendEventBundle> preprocessEvents() {
            if (preProcessedEvents != null) {
                return preProcessedEvents;
            }
            ArrayList<SendEventBundle> events =  new ArrayList<SendEventBundle>();
            ArrayList<GetEvent> buffer = new ArrayList<GetEvent>();
            String device = "";
            long time = 0;
            for (ReportEvent event : BugReport.getInstance().getEventList()) {
                if (event.type == ReportEvent.TYPE_ORIENTATION) {
                    SendEventBundle bundle = new SendEventBundle("ORIENTATION", null, event.getStartTime());
                    bundle.orientation = event.getOrientation();
                    events.add(bundle);
                }
                else {
                    device = event.getDevice();
                    for (GetEvent e : event.getInputEvents()) {
                        if (buffer.isEmpty() || time == e.getTimeMillis()) {
                            buffer.add(e);
                        }
                        else {
                            events.add(makeBundle(buffer, device));
                            buffer.clear();
                            buffer.add(e);
                        }
                        time = e.getTimeMillis();
                    }
                }
                if (!buffer.isEmpty()) {
                    events.add(makeBundle(buffer, device));
                    buffer.clear();
                }
            }
            preProcessedEvents = events;
            return events;
        }


        public SendEventBundle makeBundle(ArrayList<GetEvent> events, String device) {
            byte[][] eventBundle = new byte[events.size()][];
            for (int i = 0; i < events.size(); ++i) {
                try {
                    eventBundle[i] = events.get(i).getBytes();
                } catch (Exception err) {
                    Log.e("ReplayService", "Unexpected error in preprocess: " + err.getMessage());
                }
            }
            return new SendEventBundle(device, eventBundle, events.get(0).getTimeMillis());
        }


        class SendEventBundle {
            public String device;
            public byte[][] commands;
            public String[] commandStrings;
            public long timeMillis;
            public int orientation;

            public SendEventBundle(String device, byte[][] commands, long timeMillis) {
                this.device = device;
                this.commands = commands;
                this.timeMillis = timeMillis;
                if (commands != null) {
                    commandStrings = new String[commands.length];
                    for (int i = 0; i < commands.length; i++) {
                        StringBuilder cmdString = new StringBuilder();
                        for (byte b : commands[i]) {
                            cmdString.append(String.format("\\x%02x", b));
                        }
                        commandStrings[i] = cmdString.toString();
                    }
                }
            }
        }

    }
}
