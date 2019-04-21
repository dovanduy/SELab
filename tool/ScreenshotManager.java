package semeru.odbr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Richard Bonett on 4/23/16.
 *
 * The ScreenshotManager provides functionality for taking a screenshot. To take a screenshot,
 * simply call takeScreenshot() and receive a Screenshot Object (below)
 */
public class ScreenshotManager {
    private ExecutorService service;
    private Future currentTask;
    private String directory;
    private String filename;
    private int screenshot_index;
    private Process process;
    private BlockingQueue<Runnable> taskQueue;


    public ScreenshotManager(String directory) {
        screenshot_index = 0;
        this.directory = directory;
        filename = "screenshot" + screenshot_index + ".png";
        File dir = new File(directory);
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                f.delete();
            }
        }
        else {
            dir.mkdirs();
        }
    }


    public void initialize() {
        taskQueue = new ArrayBlockingQueue<Runnable>(1);
        service = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, taskQueue);
        try {
            process = Runtime.getRuntime().exec("su", null, null);
        } catch (Exception e) {Log.e("ScreenshotTask", "Could not start process! Check su permissions.");}
    }


    public void destroy() {
        try {
            service.shutdown();
            process.destroy();
        } catch (Exception e) {Log.e("ScreenshotManager", "Could not destroy process: " + e.getMessage());}
    }

    /**
     * If already busy processing a Screenshot, returns a reference to the in process Screenshot.
     * Otherwise, starts to process a new Screenshot, returning a reference to this new one.
     * @return Most recently processed Screenshot object
     */
    public Screenshot takeScreenshot() {
        if (taskQueue.isEmpty()) {
            screenshot_index += 1;
            filename = "screenshot" + screenshot_index + ".png";
            currentTask = service.submit(new ScreenshotTask(directory + filename));
        }
        return new Screenshot(directory + filename);

    }

    /**
     * Runnable task to actually start the screenshot process, stores it in png file
     */
    class ScreenshotTask implements Runnable {

        private String file;
        private int MIN_INTERVAL = 300;

        public ScreenshotTask(String filename) {
            this.file = filename;
        }

        @Override
        public void run() {
            OutputStream os = process.getOutputStream();
            try {
                os.write(("/system/bin/screencap -p " + file + " & \n").getBytes("ASCII"));
                os.flush();
                do {
                    Thread.sleep(MIN_INTERVAL);
                }
                while (!(new File(file).exists()));
            } catch (Exception e) {
                Log.e("ScreenshotTask", "Error taking screenshot.");
            }
        }

    }
}

/**
 * A Screenshot object consists of a filename and functionality to access the image
 */
class Screenshot {
    private String filename;

    public Screenshot(String saveFile) {
        filename = saveFile;
    }


    public Bitmap getBitmap() {
        File screenshotFile = new File(filename);
        Log.d("ScreenshotManager", "File: " + filename + "|" + screenshotFile.exists());
        if (!screenshotFile.exists()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(screenshotFile.getAbsolutePath(), options).copy(Bitmap.Config.ARGB_8888, true);
    }


    public String getFilename() {
        return filename;
    }


}