package semeru.odbr;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.SparseArray;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Rich on 2/10/16.
 */
public class Globals {
    public static ArrayList<Sensor> sensors;
    public static SensorManager sMgr;
    public static boolean recording;
    public static int width;
    public static int height;
    public static int availableHeightForImage;
    public static boolean event_active = true;
    public static String databaseURL = "http://23.92.18.210:5984/odbr/"; //CouchDB URL
    public static String baseDirectory = "sdcard" + File.separator + "ODBR";
    public static String hierarchyDumpDirectory = baseDirectory + File.separator + "HierarchyDumps" + File.separator;
    public static String screenshotDirectory = baseDirectory + File.separator + "Screenshots" + File.separator;

    /**
     * Description of the data contained within each sensor's float[]
     */
    public static SparseArray<String[]> sensorDescription = new SparseArray<String[]>();
    static {

        sensorDescription.put(Sensor.TYPE_ACCELEROMETER, new String[]
                {"Acceleration on x-axis (m/s^2)",
                 "Acceleration on y-axis (m/s^2)",
                 "Acceleration on z-axis (m/s^2)"});

        sensorDescription.put(Sensor.TYPE_MAGNETIC_FIELD, new String[]
                {"Strength of ambient magnetic field in x-axis (uT)",
                 "Strength of ambient magnetic field in y-axis (uT)",
                 "Strength of ambient magnetic field in z-axis (uT)"});

        sensorDescription.put(Sensor.TYPE_GYROSCOPE, new String[]
                {"Angular speed around x-axis (radians/second)",
                 "Angular speed around y-axis (radians/second)",
                 "Angular speed around z-axis (radians/second)"});

        sensorDescription.put(Sensor.TYPE_LIGHT, new String[]
                {"Ambient light level (lux units)"});

        sensorDescription.put(Sensor.TYPE_PRESSURE, new String[]
                {"Atmospheric pressure (hPa)"});

        sensorDescription.put(Sensor.TYPE_PROXIMITY, new String[]
                {"Distance from nearest object (cm)"});

        sensorDescription.put(Sensor.TYPE_GRAVITY, new String[]
                {"Gravity force in x-axis (m/s^2)",
                 "Gravity force in y-axis (m/s^2)",
                 "Gravity force in z-axis (m/s^2)"});

        sensorDescription.put(Sensor.TYPE_LINEAR_ACCELERATION, new String[]
                {"Acceleration minus gravity on x-axis (m/s^2)",
                 "Acceleration minus gravity on y-axis (m/s^2)",
                 "Acceleration minus gravity on z-axis (m/s^2)"});

        sensorDescription.put(Sensor.TYPE_ROTATION_VECTOR, new String[]
                {"Rotation around the x-axis",
                 "Rotation around the y-axis",
                 "Rotation around the z-axis",
                 "cos(theta/2), where theta is the angle of rotation",
                 "Estimated heading accuracy (radians)"});

        sensorDescription.put(Sensor.TYPE_ORIENTATION, new String[]
                {"Azimuth (angle between magnetic north and y-axis)",
                 "Pitch (rotation around x-axis)",
                 "Roll (rotation around y-axis)"});

        sensorDescription.put(Sensor.TYPE_RELATIVE_HUMIDITY, new String[]
                {"Relative ambient air humidity in percent"});

        sensorDescription.put(Sensor.TYPE_AMBIENT_TEMPERATURE, new String[]
                {"Ambient room temperature (degrees Celsius)"});

        sensorDescription.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, new String[]
                {"Uncalibrated strength of magnetic field in x-axis (uT)",
                 "Uncalibrated strength of magnetic field in y-axis (uT)",
                 "Uncalibrated strength of magnetic field in z-axis (uT)",
                 "Estimated hard iron calibration in x-axis (uT)",
                 "Estimated hard iron calibration in y-axis (uT)",
                 "Estimated hard iron calibration in z-axis (uT)"});

        sensorDescription.put(Sensor.TYPE_GAME_ROTATION_VECTOR, new String[]
                {"Rotation around the x-axis",
                 "Rotation around the y-axis",
                 "Rotation around the z-axis",
                 "cos(theta/2) where theta is the angle of rotation",
                 "Estimated heading accuracy (radians)"});

        sensorDescription.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, new String[]
                {"Angular speed w/o drift compensation in x-axis (radians/s)",
                 "Angular speed w/o drift compensation in y-axis (radians/s)",
                 "Angular speed w/o drift compensation in z-axis (radians/s)",
                 "Estimated drift around x-axis (radians/s)",
                 "Estimated drift around y-axis (radians/s)",
                 "Estimated drift around z-axis (radians/s)"});

        sensorDescription.put(Sensor.TYPE_STEP_COUNTER, new String[]
                {"Number of steps taken since last reboot"});
    };
}
