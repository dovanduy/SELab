package semeru.odbr;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


/**
 * The HierarchyDumpManager takes hierarchy dump requests, returning HierarchyDump objects and
 * calling uiautomator to create a dump at the file location.
 */
public class HierarchyDumpManager {
    private ThreadPoolExecutor service;
    private Process process;
    private String directory;
    private String filename;
    private int dump_index;
    private BlockingQueue<Runnable> taskQueue;


    public HierarchyDumpManager(String directory) {
        dump_index = 0;
        this.directory = directory;
        filename = "dump" + dump_index + ".xml";
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
        } catch (Exception e) {Log.e("HierarchyDumpTask", "Could not start process! Check su permissions.");}
    }


    public void destroy() {
        try {
            service.shutdown();
            process.destroy();
        } catch (Exception e) {Log.e("HierarchyDumpManager", "Could not destroy process: " + e.getMessage());}
    }

    /**
     * Returns a new HierachyDump of the current view hierarchy if initialized,
     * otherwise throws an exception
     * @return dump of the current view hierarchy
     * @throws Exception : manager is not initialized
     */
    public HierarchyDump takeHierarchyDump() throws Exception {
        if (service == null) {
            throw new Exception("HierarchyDumpManager not initialized");
        }
        if (taskQueue.isEmpty()) {
            dump_index += 1;
            filename = "dump" + dump_index + ".xml";
            service.submit(new HierarchyDumpTask(directory + filename));
        }
        return new HierarchyDump(directory + filename);
    }


    /**
     * Uses uiautomator to take a dump of the view hierarchy, saving the xml at filename
     */
    class HierarchyDumpTask implements Runnable {

        private String filename;
        private int MIN_INTERVAL = 300;

        public HierarchyDumpTask(String filename) {
            this.filename = filename;
        }

        @Override
        public void run() {
            try {
                OutputStream os = process.getOutputStream();
                Log.v("HDM", "Starting " + filename + " " + System.currentTimeMillis());
                os.write(("/system/bin/uiautomator dump " + filename + " >/dev/null & \n").getBytes("ASCII"));
                os.flush();
                do {
                    Thread.sleep(MIN_INTERVAL);
                }
                while (!(new File(filename).exists()));
                Log.v("HDM", "Finished " + filename + " " + System.currentTimeMillis());
            } catch (Exception e) {
                Log.e("HierarchyDumpTask", "Error taking Hierarchy Dump: " + e.getMessage());
            }
        }
    }
}


/**
 * A HierarchyDump contains a String handle to a file storing an xml of the view hierarchy, as well
 * as methods to interpret this hierarchy
 */
class HierarchyDump {

    private String filename;

    public HierarchyDump(String filename) {
        this.filename = filename;
    }


    public String getFilename() {
        return filename;
    }


    /**
     * Returns the deepest view containing the coordinates as a Node
     * @param x the x coordinate
     * @param y the y coordinate
     * @return Node for the deepest view encapsulating the input coordinates
     */
    public Node getViewAtCoordinates(int x, int y) {
        Node node = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(new File(filename));
            node = getNodeBetweenBounds(document.getDocumentElement(), x, y);
        } catch (Exception e) {}
        return node;
    }


    /*
     * Helper function for getViewAtCoordinates, returns deepest node containing x and y
     */
    private Node getNodeBetweenBounds(Node node, int x, int y) {
        boolean childBetweenBounds = true;
        while (childBetweenBounds) {
            childBetweenBounds = false;
            NodeList nodeList = node.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (isBetweenBounds(x, y, getBounds(nodeList.item(i)))) {
                    childBetweenBounds = true;
                    node = nodeList.item(i);
                    break;
                }
            }
        }
        return node;
    }


    /*
     * Helper function for getNodeBetweenBounds, returns node's bounds as int[]
     */
    private int[] getBounds(Node node) {
        String[] boundsAttr = (((Element) node).getAttribute("bounds")).replace("[","").split("[^0-9]");
        int[] bounds = new int[boundsAttr.length];
        for (int i = 0; i < boundsAttr.length; i++) {
            bounds[i] = Integer.parseInt(boundsAttr[i]);
        }
        return bounds;
    }


    /*
     * Helper function for getNodeBetweenBounds, returns true if the x and y coordinates are
     * within the bounds, false otherwise
     */
    private boolean isBetweenBounds(int x, int y, int[] bounds) {
        return x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3];
    }
}