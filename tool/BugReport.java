package semeru.odbr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.TargetApi;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

/**
 * Created by Richard Bonett on 2/11/16.
 * Singleton class containing all information for a specific bug report.
 * The BugReport contains a list of the events, a list for each sensor's data, as well as
 * descriptions useful for the report.
 */
public class BugReport {
    public static transient int colors[] = {Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.YELLOW, Color.MAGENTA};
    public static transient String[] orientationStrings = {"portrait", "landscape_left", "reverse", "landscape_right"};
    private transient HashMap<String, Bitmap> sensorGraphs = new HashMap<String, Bitmap>();

    private HashMap<String, SensorDataList> sensorData = new HashMap<String, SensorDataList>();
    private List<ReportEvent> eventList = new ArrayList<ReportEvent>();
    private LinkedHashMap<Long, Integer> orientations = new LinkedHashMap<Long, Integer>();
    private int startOrientation;
    private transient int orientation = -1; // current device orientation
    private String app_name;
    private String package_name;
    private String device_type = android.os.Build.MODEL;
    private String description_actual_outcome = "";
    private String description_desired_outcome = "";
    private String name = "";
    private String title = "";
    private int os_version = android.os.Build.VERSION.SDK_INT;
    private Screenshot startScreenshot;
    private Screenshot lastScreenshot;
    private Screenshot endScreenshot;

    private static transient BugReport ourInstance = new BugReport();


    public static BugReport getInstance() {
        return ourInstance;
    }

    private BugReport() {
        clearReport();
    }

    //resets the data, called after report is submitted
    public void clearReport() {
        sensorData.clear();
        sensorGraphs.clear();
        eventList.clear();
        title = "";
        name = "";
        description_desired_outcome = "";
        description_actual_outcome = "";
    }


    public void addEvent(int ndx, ReportEvent e) {
        eventList.add(ndx, e);
    }

    public void addEvent(ReportEvent e) {
        eventList.add(e);
        if (e.type != ReportEvent.TYPE_ORIENTATION) {
            lastScreenshot = e.getScreenshot();
        }
    }

    //adds a sensor 'event' to a specific sensor
    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    public void addSensorData(Sensor s, SensorEvent e) {
        if (!sensorData.containsKey(s.getName())) {
            sensorData.put(s.getName(), new SensorDataList(s.getType()));
        }
        sensorData.get(s.getName()).addData(e.timestamp, e.values.clone());
    }

    public void addOrientationChange(long time, int orientation) {
        String orString;
        try {
            orString = orientationStrings[orientation];
        } catch (Exception e) {
            Log.e("BugReport", "Could not match orientation '" + orientation + "': " + e.getMessage());
            return;
        }
        if (this.orientation == -1) {
            this.orientation = orientation;
            return;
        }
        if (this.orientation == orientation) {
            return;
        }
        orientations.put(time, orientation);
        ReportEvent orientationChange = new ReportEvent(null);
        orientationChange.type = ReportEvent.TYPE_ORIENTATION;
        orientationChange.setOrientation(orientation);
        orientationChange.setStartTime(time);
        orientationChange.setEndTime(time + 100);

        String descriptor = orientationStrings[this.orientation] + "_to_" + orString;
        orientationChange.setScreenshot(new Screenshot("@drawable/" + descriptor));
        orientationChange.setDescription("Rotate device from " + orientationStrings[this.orientation] + " to " + orString);
        addEvent(orientationChange);

        this.orientation = orientation;
    }

    public int getCurrentOrientation() {
        return orientation;
    }

    public void setCurrentOrientation(int orientation) {
        this.orientation = orientation;
    }

    public void setOrientations(LinkedHashMap<Long, Integer> orientations) {
        this.orientations = orientations;
    }

    public void setDescription_desired_outcome(String s) {
        description_desired_outcome = s;
    }

    public void setDescription_actual_outcome(String s) {
        description_actual_outcome = s;
    }

    public void setTitle(String s) {
        title = s;
    }

    public void setName(String s) {
        name = s;
    }

    public void setAppName(String s) {
        app_name = s;
    }

    public void setPackageName(String s) {
        package_name = s;
    }

    public void setStartScreenshot(Screenshot s) {
        startScreenshot = s;
        lastScreenshot = s;
    }

    public void setLastScreenshot(Screenshot s) {
        lastScreenshot = s;
    }

    public void setEndScreenshot(Screenshot s) {
        endScreenshot = s;
        lastScreenshot = s;
    }

    public void setStartOrientation(int orientation) {
        startOrientation = orientation;
        this.orientation = orientation;
    }

    /**
     * Returns a Bitmap representing the sensor's data over the course of the report. The graph
     * is formatted with a horizontal line representing the mean value and other lines representing
     * the deviation from the mean at any given time during the report
     * @param s the sensor
     * @return Bitmap of the sensor data
     */
    public Bitmap drawSensorData(String s) {
        if (sensorGraphs.containsKey(s)) {
            return sensorGraphs.get(s);
        }

        int height = Globals.height / 2;
        Bitmap b = Bitmap.createBitmap(Globals.width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        SensorDataList data = sensorData.get(s);
        if (data == null) {
            return null;
        }
        Paint color = new Paint();
        color.setColor(Color.BLACK);
        color.setStrokeWidth(5);
        c.drawARGB(255, 200, 200, 200);
        c.drawLine(0, 0, 0, height, color);
        c.drawLine(0, height / 2, Globals.width, height / 2, color);
        color.setStrokeWidth(3);

        long timeMod = data.getElapsedTime(data.numItems() - 1) / Globals.width;
        timeMod = timeMod > 0 ? timeMod : 1;
        for (int k = 0; k < data.numTraces() && k < colors.length; k++) {
            float valueMod = data.meanValue(k) / (height / 2);
            valueMod = valueMod > 0 ? valueMod : 1;
            color.setColor(colors[k]);
            float startX = data.getElapsedTime(0) / timeMod;
            float startY = data.getValues(0)[k] / valueMod;
            for (int i = 1; i < data.numItems(); i++) {
                float endX = data.getElapsedTime(i) / timeMod;
                float endY = data.getValues(i)[k] / valueMod;
                c.drawLine(startX, startY, endX, endY, color);
                startX = endX;
                startY = endY;
            }
        }
        sensorGraphs.put(s, b);
        return b;
    }

    /* Getters */
    public long getStartTime() {
        if (eventList == null || eventList.isEmpty()) {
            return 0;
        }
        return eventList.get(0).getStartTime();
    }
    public String getName() {
        return name;
    }
    public String getTitle() {
        return title;
    }
    public String getAppName() {
        return app_name;
    }
    public String getPackageName() {
        return package_name;
    }
    public String getDescription_desired_outcome(){
        return description_desired_outcome;
    }
    public String getDescription_actual_outcome(){
        return description_actual_outcome;
    }
    public List<ReportEvent> getEventList() {
        return eventList;
    }
    public int numEvents() {
        return eventList.size();
    }
    public ReportEvent getEventAtIndex(int ndx) {
        return eventList.get(ndx);
    }
    public HashMap<String, SensorDataList> getSensorData() {
        return sensorData;
    }
    public HashMap<Long, Integer> getOrientations() {
        return orientations;
    }
    public Screenshot getStartScreenshot() {
        return startScreenshot;
    }
    public Screenshot getLastScreenshot() {
        return lastScreenshot;
    }
    public Screenshot getEndScreenshot() {
        return endScreenshot;
    }
    public int getStartOrientation() {
        return startOrientation;
    }
}


/**
 * A SensorDataList contains the values of a particular sensor over time
 */
class SensorDataList {
    private ArrayList<SensorDataContainer> values;
    private String[] valueDescriptions;
    private transient float[] valueSums = null;

    public SensorDataList(int sensorType) {
        values = new ArrayList<SensorDataContainer>();
        valueDescriptions = Globals.sensorDescription.get(sensorType, new String[] {"", "", "", ""});
    }

    public void addData(long timestamp, float[] value) {
        values.add(new SensorDataContainer(timestamp, value));
        if (valueSums == null) {
            valueSums = new float[value.length];
        }
        for (int i = 0; i < value.length; i++) {
            valueSums[i] += value[i];
        }
    }

    public long getTime(int index) {
        if (index >= values.size() || index < 0) {
            return 0;
        }
        return values.get(index).time;
    }

    public long getElapsedTime(int index) {
        if (index >= values.size() || index < 0) {
            return 0;
        }
        return values.get(index).time - values.get(0).time;
    }

    public float meanValue(int index) {
        if (index >= valueSums.length || index < 0) {
            return 0;
        }
        return valueSums[index] / values.size();
    }

    public float[] getValues(int index) {
        if (index >= values.size() || index < 0) {
            return null;
        }
        return values.get(index).values;
    }

    public int numItems() {
        return values.size();
    }

    public int numTraces() {
        return valueSums.length;
    }

    class SensorDataContainer {
        public long time;
        public float[] values;
        public SensorDataContainer(long time, float[] values) {
            this.time = time;
            this.values = values;
        }
    }
}