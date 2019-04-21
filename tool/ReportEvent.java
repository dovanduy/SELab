package semeru.odbr;

import android.util.SparseArray;
import android.util.SparseIntArray;

import java.util.ArrayList;

/**
 * Created by Richard Bonett on 4/22/16.
 * A Report Event is our classification for a singular event. Each Report Event will have a screenshot, a hierarchy,
 * and then multiple getevent lines associated with it until the trace reaches a point where that specific report event
 * has finished. The Report Event interacts with the GetEventDeviceInfo to get appropriate data on the device and the
 * managers as well to get feeds of data.
 *
 * The traces for getEvent can be classified into 3 categories:
 *  --SingleTouchDevice
 *  --MultiTouchTypeA
 *  --MultiTouchTypeB
 */
public class ReportEvent {
    short EV_ABS = 3;

    public static transient int TYPE_USER_EVENT = 0;
    public static transient int TYPE_ORIENTATION = 1;
    public transient int type = TYPE_USER_EVENT;

    private Screenshot screenshot;
    private HierarchyDump hierarchy;
    private ArrayList<GetEvent> inputList;
    private SparseArray<ArrayList<int[]>> coords;
    private String description = "";
    private long event_start_time = 0;
    private long event_end_time = 0;
    private String device;
    private int orientation;

    public ReportEvent(String device) {
        this.device = device;
        inputList = new ArrayList<GetEvent>();
    }

    public void setScreenshot(Screenshot s) {
        screenshot = s;
    }

    public void setHierarchyDump(HierarchyDump d) {
        hierarchy = d;
    }

    public void addGetEvent(GetEvent e) {
        if (inputList.isEmpty()) {
            event_start_time = e.getTimeMillis();
        }
        event_end_time = e.getTimeMillis();
        inputList.add(e);
    }

    public String getData() {
        String screenshotFile = screenshot == null ? "none" : screenshot.getFilename();
        String hierarchyFile = hierarchy == null ? "none" : hierarchy.getFilename();
        return "Time: " + getStartTime() + " | Screenshot: " + screenshotFile + " | Dump: " + hierarchyFile;
    }

    public String getEventDescription() {
        if (!("".equals(description))) {
            return description;
        }
        coords = getInputCoordinates();

        if (coords.size() > 1) {
            description += "Multitouch";
        }
        else if (coords.valueAt(0).size() < 3) {
            int[] coord = coords.valueAt(0).get(0);
            description += "Click at X:" + coord[0] + " |Y: " + coord[1];
        }
        else {
            int[] start = coords.valueAt(0).get(0);
            int[] end = coords.valueAt(0).get(coords.valueAt(0).size() - 1);
            description += "Swipe from X: " + start[0] + " |Y: " + start[1] + " | to X: " + end[0] + " |Y: " + end[1];
        }
        return description;
    }


    public Screenshot getScreenshot() {
        return screenshot;
    }

    public HierarchyDump getHierarchy() {
        return hierarchy;
    }

    public ArrayList<GetEvent> getInputEvents() {
        return inputList;
    }


    public long getStartTime() {
        return event_start_time;
    }

    public long getDuration() {
        return event_end_time - event_start_time;
    }

    public String getDevice() {
        return device;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setStartTime(Long time) {
        event_start_time = time;
    }

    public void setEndTime(Long time) {
        event_end_time = time;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    /**
     * Retrieve a list of input traces, accounts for a lack of X || Y in the output of specific ABS reports
     * @return a list of lists, where each list contains the coordinates for one touch input
     */
    public SparseArray<ArrayList<int[]>> getInputCoordinates() {
        if (coords != null) {
            return coords;
        }

        coords = new SparseArray<ArrayList<int[]>>();
        int NOT_FOUND = -1;
        int CLEAN = 0;
        int DIRTY = 1;

        /**
         * Each classification needs a different style of parsing. For typeB we can rely on the previous
         * ABS_MT_SLOT instance to designate which trace we are concerned with, using CLEAN and DIRTY variables
         * to denote the lack of an X or Y report of ABS_MT_POSITION_X||Y. Dirty is when there is not one,
         * clean is when there is.
         */
        if (GetEventDeviceInfo.getInstance().isMultiTouchB()) {
            SparseArray<int[]> trace = new SparseArray<int[]>();
            SparseIntArray slots = new SparseIntArray();
            int activeSlot = 0;

            trace.put(activeSlot, new int[]{-1, -1});
            coords.put(activeSlot, new ArrayList<int[]>());

            for (GetEvent e : inputList) {
                if (slot(e)) {
                    activeSlot = e.getValue();
                    if (slots.get(activeSlot, NOT_FOUND) == NOT_FOUND) {
                        trace.put(activeSlot, new int[]{-1, -1});
                        coords.put(activeSlot, new ArrayList<int[]>());
                    }
                }
                if (xPos(e)) {
                    trace.get(activeSlot)[0] = e.getValue();
                    slots.put(activeSlot, DIRTY);
                } else if (yPos(e)) {
                    trace.get(activeSlot)[1] = e.getValue();
                    coords.get(activeSlot).add(trace.get(activeSlot).clone());
                    slots.put(activeSlot, CLEAN);
                } else if (slots.get(activeSlot) == DIRTY) {
                    coords.get(activeSlot).add(trace.get(activeSlot).clone());
                    slots.put(activeSlot, CLEAN);
                }
            }
        }
        /**
         * Uses the same Clean/Dirty to handle lack of reports, taking the 'previous' x or y position as the one
         * to associate with the coordinate values. This method however cannot rely on slots, so we use distance
         * tracking to match certain traces to other traces. Can run into errors IF the user can put the pointers
         * at the exact SAME location, but that is a limitation of Type A devices.
         */
        else if (GetEventDeviceInfo.getInstance().isMultiTouchA() || GetEventDeviceInfo.getInstance().isTypeSingleTouch()) {
            int state = CLEAN;
            int[] coord = new int[2];

            for (GetEvent e : inputList) {
                if (down(e)) {
                    coords.put(coords.size(), new ArrayList<int[]>());
                } else if (coords.size() == 0) {
                    continue;
                } else if (xPos(e)) {
                    coord[0] = e.getValue();
                    state = DIRTY;
                } else {
                    if (yPos(e)) {
                        coord[1] = e.getValue();
                        state = DIRTY;
                    }
                    if (state == DIRTY) {
                        ArrayList<int[]> base = coords.valueAt(0);
                        for (int trace = 0; trace < coords.size(); trace++) {
                            ArrayList<int[]> other = coords.valueAt(trace);
                            if (other.size() == 0) {
                                base = other;
                                break;
                            } else {
                                int[] baseCoord = base.get(base.size() - 1);
                                int[] otherCoord = other.get(other.size() - 1);
                                if (distance(baseCoord[0], baseCoord[1], coord[0], coord[1]) > distance(otherCoord[0], otherCoord[1], coord[0], coord[1])) {
                                    base = other;
                                }
                            }
                        }
                        base.add(coord.clone());
                        state = CLEAN;
                    }
                }
            }

        }

        return coords;
    }


    private int distance(int startX, int startY, int endX, int endY) {
        return (int) (Math.sqrt(Math.pow(startX - endX, 2) + Math.pow(startY - endY, 2)));
    }

    /**
     * Methods that take the code from getEvent and determine whether or not the code matches a 'trigger'
     *  --down: Start of a report event trace
     *  --slot: slot ID of a trace
     *  --xPos: whether or not its a X position
     *  --yPos: whether or not its a Y position
     * @param e
     * @return
     */
    private boolean down(GetEvent e) {
        boolean res;
        try {
            res = e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_MT_TRACKING_ID") && e.getValue() != -1;
        } catch (Exception exc) {
            res = e.getCode() == GetEventDeviceInfo.getInstance().get_code("BTN_TOUCH") && e.getValue() == 1;
        }
        return res;
    }

    private boolean slot(GetEvent e) {
        try{
            return e.getType() == EV_ABS && (e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_MT_SLOT"));
        } catch (Exception exc) {return false;}
    }

    private boolean xPos(GetEvent e) {
        try {
            return e.getType() == EV_ABS && (e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_MT_POSITION_X") || e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_X"));
        } catch (Exception exc) {return false;}
    }

    private boolean yPos(GetEvent e) {
        try {
            return e.getType() == EV_ABS && (e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_MT_POSITION_Y") || e.getCode() == GetEventDeviceInfo.getInstance().get_code("ABS_Y"));
        } catch (Exception exc) {return false;}
    }
}
