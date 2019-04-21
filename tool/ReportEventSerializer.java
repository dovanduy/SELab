package semeru.odbr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.SparseArray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * Created by Richard Bonett on 1/24/17.
 */
public class ReportEventSerializer implements JsonSerializer<ReportEvent> {
    private Context context;

    public ReportEventSerializer(Context context) {
        this.context = context;
    }

    private String readFileToString(String path) {
        StringBuilder text = new StringBuilder();
        File f = new File(path);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line + '\n');
            }
            reader.close();
        } catch (Exception e) {}
        return text.toString();
    }


    @Override
    public JsonElement serialize(ReportEvent src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject root = new JsonObject();
        root.addProperty("description", src.getEventDescription());
        root.addProperty("event_start_time", src.getStartTime());
        root.addProperty("event_end_time", src.getStartTime() + src.getDuration());

        if (src.type == ReportEvent.TYPE_USER_EVENT) {
            JsonObject screenshot = new JsonObject();
            screenshot.addProperty("title", src.getScreenshot().getFilename());
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            src.getScreenshot().getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream);
            screenshot.addProperty("bitmap_string", Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT));
            root.add("screenshot", screenshot);

            JsonObject hierarchy = new JsonObject();
            hierarchy.addProperty("title", src.getHierarchy().getFilename());
            hierarchy.addProperty("text", readFileToString(src.getHierarchy().getFilename()));
            root.add("hierarchy", hierarchy);

            JsonArray inputList = new JsonArray();
            SparseArray<ArrayList<int[]>> inputCoords = src.getInputCoordinates();
            for (int i = 0; i < inputCoords.size(); ++i) {
                JsonArray input = new JsonArray();
                for (int[] coords : inputCoords.get(i)) {
                    JsonArray coord = new JsonArray();
                    for (int c : coords) {
                        coord.add(new JsonPrimitive(c));
                    }
                    input.add(coord);
                }
                inputList.add(input);
            }
            root.add("inputList", inputList);
        }
        else {
            JsonObject screenshot = new JsonObject();
            screenshot.addProperty("title", src.getScreenshot().getFilename().split("/")[1]);
            int imageResource = this.context.getResources().getIdentifier(src.getScreenshot().getFilename(), null, this.context.getPackageName());
            Drawable img = this.context.getResources().getDrawable(imageResource);
            Bitmap bitmap = ((BitmapDrawable) img).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            screenshot.addProperty("bitmap_string", Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT));
            root.add("screenshot", screenshot);
        }

        return root;
    }
}
