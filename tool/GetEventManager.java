package semeru.odbr;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Process;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Rich on 4/25/16.
 * Manages the GetEvent tasks. Interacts with the GetEventDeviceInfo to know which areas to cat to based on the
 * device array list (/dev/input/eventX). The manager toggles them on whenever we want to record inputs from the
 * record floating widget, and toggles them off whenever the overlay reappears. It is also responsible for triggering
 * the start of the screenshot and hierarchy execution
 */
public class GetEventManager {

    private boolean recording;
    private ExecutorService service;
    private ArrayList<Process> processes;
    public ScreenshotManager sm;
    public HierarchyDumpManager hdm;
    private

    int EV_SYN = 0;
    int EV_KEY = 1;
    int EV_ABS = 3;

    int SYN_REPORT = 0;
    int TOUCH_DOWN = 1;
    int TOUCH_UP = 0;

    public GetEventManager() {
        recording = false;
        service = Executors.newCachedThreadPool();
        processes = new ArrayList<Process>();
        sm = new ScreenshotManager(Globals.screenshotDirectory);
        hdm = new HierarchyDumpManager(Globals.hierarchyDumpDirectory);
        sm.initialize();
        hdm.initialize();
        if (BugReport.getInstance().getStartScreenshot() == null) {
            try {
                BugReport.getInstance().setStartScreenshot(sm.takeScreenshot());
            } catch (Exception e) {
                Log.e("GetEventManager", "Could not take screenshot!");
            }
        }
    }

    /**
     * Method that starts the recording process and initalizes the screenshot and hierarchy managers
     */
    public void startRecording() {
        if (recording) {
            return;
        }
        try {
            for (String device : getInputDevices()) {
                service.submit(new GetEventTask(device));
            }
            recording = true;
        } catch (Exception e) {
            Log.v("GetEventManager", "Error starting GetEvent process: " + e.getMessage());
        }
    }

    public void pauseRecording() {
        try {
            recording = false;
            BugReport.getInstance().setEndScreenshot(sm.takeScreenshot());
            for (Process p : processes) {
                p.getInputStream().close();
                p.destroy();
            }
        } catch (Exception e) {
            Log.v("GetEventManager", "Error stopping GetEvent process: " + e.getMessage());
        }
    }

    /**
     * Iterates through the recording processes and stops them, also destroys the hierarchy and screenshot managers
     */
    public void stopRecording() {
        pauseRecording();
        sm.destroy();
        hdm.destroy();
    }

    private ArrayList<String> getInputDevices(){
        return GetEventDeviceInfo.getInstance().getInputDevices();
    }


    /**
     * A GetEventTask is a runnable that is responsible for creating multiple report events based on the output
     * of the cat Process. Within our implementation we account for multiple fingers to be touching if the device
     * is a multitouch device. The specific task is responsible for executing its process for cat on the specific device
     * and then that process is added to the getEvent manager
     */
    class GetEventTask implements Runnable {
        private byte[] res = new byte[16];
        private InputStream is;
        private String device;
        private ReportEvent event;
        private int id_num;
        private int downCount;

        public GetEventTask(String device) {
            this.device = device;
            downCount = 0;
            try {
                //starts cat for /dev/input/eventX
                Process su = Runtime.getRuntime().exec("su", null, null);
                OutputStream outputStream = su.getOutputStream();
                outputStream.write(("cat " + device).getBytes("ASCII"));
                outputStream.flush();
                outputStream.close();
                is = su.getInputStream();
                processes.add(su);
            } catch (Exception e) {
                Log.v("GetEventTask", "Error: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                //runnable that creates new report events based on the output of the cat process
                event = new ReportEvent(device);
                while (is.read(res) > 0) {
                    Globals.event_active = true;
                    GetEvent getevent = new GetEvent(res);
                    event.addGetEvent(getevent);

                    //tracks the number of fingers down and up, if 0 are down and we get a fingerDown that's the start
                    //of one report event, it finishes once we have a fingerUp that then has 0 fingers on the screen
                    if (fingerDown(getevent)) {
                        if (downCount == 0) {
                            event.setScreenshot(sm.takeScreenshot());
                            try {
                                event.setHierarchyDump(hdm.takeHierarchyDump());
                            } catch (Exception e) {Log.e("GetEventManger", e.getMessage());}
                        }
                        ++downCount;
                    }
                    else if (fingerUp(getevent)) {
                        --downCount;
                        if (downCount == 0) {
                            do {
                                is.read(res);
                                getevent = new GetEvent(res);
                                event.addGetEvent(getevent);
                            } while (getevent.getCode() != SYN_REPORT);
                            event.setOrientation(BugReport.getInstance().getCurrentOrientation());
                            BugReport.getInstance().addEvent(event);
                            event = new ReportEvent(device);
                        }
                    }
                }
            } catch (Exception e) {
                Log.v("GetEventTask", "Whoops! " + e.getMessage());
            }
        }

        /**
         * methods to determine if a getevent line is registering finger down/up based on the device type
         * @param e: getEvent line for interpretation
         * @return: true/false
         */

        private boolean fingerDown(GetEvent e) {

            if(GetEventDeviceInfo.getInstance().isMultiTouchA() || GetEventDeviceInfo.getInstance().isMultiTouchB()){
                Integer code = GetEventDeviceInfo.getInstance().get_code("ABS_MT_TRACKING_ID");
                if(code == null){
                    return false;
                }
                else {
                    return e.getCode() == code && e.getValue() != 0xffffffff;
                }
            }
            else {
                if (e.getType() == EV_KEY && e.getCode() == GetEventDeviceInfo.getInstance().get_code("BTN_TOUCH") && e.getValue() == TOUCH_DOWN) {
                    return true;
                }
            }
            return false;
        }

        private boolean fingerUp(GetEvent e) {
            if(GetEventDeviceInfo.getInstance().isMultiTouchA() || GetEventDeviceInfo.getInstance().isMultiTouchB()){
                Integer code = GetEventDeviceInfo.getInstance().get_code("ABS_MT_TRACKING_ID");
                if(code == null){
                    return false;
                }
                else {
                    return e.getCode() == code && e.getValue() == 0xffffffff;
                }
            }
            else {
                if (e.getType() == EV_KEY && e.getCode() == GetEventDeviceInfo.getInstance().get_code("BTN_TOUCH") && e.getValue() == TOUCH_UP) {
                    return true;
                }
            }
            return false;
        }

    }
}


/**
 * Class to convert the byte information of the cat to a replica of the getevent outputs. With this, we can then
 * parse get event lines in the same way that we initially parse get event logs.
 */
class GetEvent {
    private transient byte[] bytes;

    public GetEvent(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    private int toInt(byte b) {
        return b & 0x000000FF;
    }

    @Override
    public String toString() {
        return String.format("[%d.%06d] %04x %04x %08x", getSeconds(), getMicroseconds(), getType(), getCode(), getValue());
    }

    public String toString(String device) {
        return String.format("[%d.%06d] %s: %04x %04x %08x", getSeconds(), getMicroseconds(), device, getType(), getCode(), getValue());
    }

    public String toStringInts(String device) {
        return String.format("[%d.%06d] %s: %d %d %d", getSeconds(), getMicroseconds(), device, getType(), getCode(), getValue());
    }

    public String getSendEvent(String device) {
        return String.format("sendevent %s %d %d %d", device, getType(), getCode(), getValue());
    }

    public byte[] getBytes() {
        return bytes;
    }

    public short getType() {
        return (short) (toInt(bytes[8]) + (toInt(bytes[9]) << 8));
    }

    public short getCode() {
        return (short) (toInt(bytes[10]) + (toInt(bytes[11]) << 8));
    }

    public int getValue() {
        return toInt(bytes[12]) + (toInt(bytes[13]) << 8) + (toInt(bytes[14]) << 16) + (toInt(bytes[15]) << 24);
    }

    public long getTimeMillis() {
        long time = getSeconds()  * 1000;
        time = time < 0 ? time * -1 : time;
        time += getMicroseconds() / 1000;
        return time;
    }

    public long getMicroseconds() {
        return toInt(bytes[4]) + (toInt(bytes[5]) << 8) + (toInt(bytes[6]) << 16) + (toInt(bytes[7]) << 24);
    }

    public long getSeconds() {
        return toInt(bytes[0]) + (toInt(bytes[1]) << 8) + (toInt(bytes[2]) << 16) + (toInt(bytes[3]) << 24);
    }


}