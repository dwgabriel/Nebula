package nebula.nebulaserver;


import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.*;
import com.dropbox.core.v2.users.FullAccount;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.StandardCopyOption;
import java.util.*;

//import javax.servlet.http.HttpServlet;

/**
 * Created by Daryl Wong on 3/29/2019.
 */


@WebServlet(
        name = "Test",
        urlPatterns = {"/testUpload"}
)

@MultipartConfig

public class Test extends HttpServlet {

    private static DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/nebula-render").build();
    private static DbxClientV2 client = new DbxClientV2(config, "rM8fF-GuUNAAAAAAAAAAK6ksJER9acjYeF1krFbX63InD8wn_Iq-5fDlV_1YM6gh");

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    File taskDir = new File(taskDatabase, "test_123");
    File originalTaskDir = new File(taskDir, "originaltask");
    static Deque<Integer> stringDeque = new LinkedList<>();

//    static Iterator<String> stringIterator = stringDeque.iterator();
    static HashMap<String, ArrayList> taskCosts = new HashMap<>();

    public static void main(String[] args) {
        Random random = new Random();
            int distance_meter = random.nextInt();


            // -x-

            if (distance_meter < 1) {
                social_distance();
            } else {
                System.out.println("Thanks, we're a little safer now.");
            }



    }

    public static void social_distance () {

    }

    public static void copyFileToResultsDir(InputStream resultFile, File frameResults, String fileName) throws IOException {

        File resultDestFile = new File(frameResults, fileName);

        java.nio.file.Files.copy(resultFile, resultDestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static List<File> listOfResultFiles(File results) {
        File[] files = results.listFiles();
        System.out.println("CompletedRenderTask | listOfResultFiles_length : " + results.listFiles().length);
        if (results.listFiles().length > 1) {
            Arrays.sort(files);         
        }

        return Arrays.asList(files);
    }

    public static File downloadBlendfileFromGDrive(String url, String taskID, File originalTaskDir) {
        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
        String filename = String.format(taskID + ".blend");
        File renderFile = null;
        try{
            URL download=new URL(gdriveURL);
//            download.getFile();
            renderFile = new File(originalTaskDir.getAbsolutePath(), download.getFile());
            System.out.println("DOWNLOAD (GDRIVE) : " + download.openStream());
            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(renderFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderFile;
    }

    public static TileBorder[] calcTileBorders(int multiplier) {
        TileBorder[] tileBorders = new TileBorder[multiplier * multiplier];
        float chunk = (float) 1 / (float) multiplier;

        int t = 0;
        float left, bottom, right, top;
        for (int y = 1; y < multiplier + 1; y++) {
            for (int x = 1; x < multiplier + 1; x++) {

                //x coordinates
                if (x == 1) {  //left border tile\
                    left = 0.0F;
                    right = chunk;
                } else if (x == multiplier) { //right border tile
                    left = chunk * (multiplier - 1);
                    right = 1.0F;
                } else {    //tile not on left or right border...
                    left = chunk * (float) (x - 1);
                    right = chunk * (float) x;
                }

                //y coordinates
                if (y == 1) {  //bottom border tile
                    bottom = 0.0F;
                    top = chunk;
                } else if (y == multiplier) { //top border tile
                    bottom = chunk * (multiplier - 1);
                    top = 1.0F;
                } else {    //tile not on bottom or top border...
                    bottom = chunk * (float) (y - 1);
                    top = chunk * (float) y;
                }

                tileBorders[t] = new TileBorder(left, right, bottom, top);
                t++;
            }
        }
        return tileBorders;
    }

    public static File[] generateTileSplitScript(File outputDir, String frameID, int frame, String renderOutputType, int multiplier)   // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
            throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders
        TileBorder[] tileBorders = calcTileBorders(multiplier);
        File[] allScripts = new File[tileBorders.length];

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("%s_%s_%03d", frameID, "TS", tileCounter++).concat(".py");
            File script = new File(outputDir, scriptName);

            PrintWriter fout = new PrintWriter(new FileWriter(script));
            fout.println("import bpy");
            fout.println("import os");

//            bpy.context.preferences.addons['cycles'].preferences.compute_device_type = 'CUDA'
//            bpy.context.preferences.addons['cycles'].preferences.devices[0].use = True

            // THIS SECTION OF THE TASK SCRIPT ENABLES GPU RENDERING IF POSSIBLE
            fout.println("def enable_gpus(device_type, use_cpus=False):");
            fout.println("    preferences = bpy.context.preferences");
            fout.println("    cycles_preferences = preferences.addons[\"cycles\"].preferences");
            fout.println("    cuda_devices, opencl_devices = cycles_preferences.get_devices()");
            fout.println("    if device_type == \"CUDA\":");
            fout.println("        devices = cuda_devices");
            fout.println("    elif device_type == \"OPENCL\":");
            fout.println("        devices = opencl_devices");
            fout.println("    else:");
            fout.println("        raise RuntimeError(\"Unsupported device type\")");
            fout.println("    activated_gpus = []");
            fout.println("    for device in devices:");
            fout.println("        if device.type == \"CPU\":");
            fout.println("          device.use = use_cpus");
            fout.println("        else:");
            fout.println("          device.use = True");
            fout.println("          activated_gpus.append(device.name)");
            fout.println("    cycles_preferences.compute_device_type = device_type");
            fout.println("    bpy.context.scene.cycles.device = \"GPU\"");
            fout.println("    return activated_gpus");
            fout.println("enable_gpus(\"CUDA\")");

            // THIS SECTION OF THE TASK SCRIPT SPLITS THE TASK INTO SUBTASKS BY SPECIFYING WHICH PORTION OF THE ORIGINAL IMAGE IS TO BE RENDERED
            fout.println("left = " + Float.toString(tileBorder.getLeft()));
            fout.println("right = " + Float.toString(tileBorder.getRight()));
            fout.println("bottom = " + Float.toString(tileBorder.getBottom()));
            fout.println("top = " + Float.toString(tileBorder.getTop()));
            fout.println("scene  = bpy.context.scene");
            fout.println("render = scene.render");
            fout.println("render.use_border = True");
            fout.println("render.use_crop_to_border = True");
            fout.println("render.image_settings.file_format = " + "'" + renderOutputType.toUpperCase() + "'");
//            fout.println("render.image_settings.color_mode = 'RGB'");
            fout.println("render.use_file_extension = True");
            fout.println("render.border_max_x = right");
            fout.println("render.border_min_x = left");
            fout.println("render.border_max_y = top");
            fout.println("render.border_min_y = bottom");
            // from the location of the fetched file (blendcache) to ../tmp/
//            fout.println("scene.frame_start = " + startFrame);          // startFrame parameter does nothing for singleFrame Tasks
//            fout.println("scene.frame_end = " + endFrame);              // endFrame parameter does nothing for multiFrame Tasks
            // if it's not used this line, the scene uses their original parameters...
//            fout.println("bpy.ops.render.render(animation=True)");
            fout.flush();
            fout.close();
            allScripts[i] = script;
        }
        return allScripts;
    }

    public String checkNodeUpdateConfigURL() {
        String url = null;
        try {
            ListFolderResult listing = client.files().listFolderBuilder("/Node Update").start();

            for (Metadata child : listing.getEntries()) {
                System.out.println(child.getName());

                if (child.getName().equals("node-update-config.txt")) {
                    String path = child.getPathLower();
                    url = getShareLink(path);
                    System.out.println("Node Update URL : " + url);
                } else {
                    System.out.println("Node Update URL not found.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return url;
    }

    static BufferedImage ensureOpaque(BufferedImage bi) {
        if (bi.getTransparency() == BufferedImage.OPAQUE)
            return bi;
        int w = bi.getWidth();
        int h = bi.getHeight();
        int[] pixels = new int[w * h];
        bi.getRGB(0, 0, w, h, pixels, 0, w);
        BufferedImage bi2 = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        bi2.setRGB(0, 0, w, h, pixels, 0, w);
        return bi2;
    }

    public static int calculateMultiplier(File originalTaskFile) {
        long optimalSize = 100000; // 100 kb for optimal file transfer and downloading between Node and Server
        long divided = originalTaskFile.length() / optimalSize;
        int multiplier = (int)Math.sqrt(divided);
        System.out.println("Original Size : " + originalTaskFile.length());
        System.out.println("Divided : " + divided);
        System.out.println("Multiplier : " + multiplier);

        return multiplier;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static double calculateCost(String taskId) {
        double totalCost = 0;

        ArrayList<SubtaskCosts> subtaskCosts = taskCosts.get(taskId);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalCost += subtaskCosts.get(i).cost;
            System.out.println(subtaskCosts.get(i).subtaskID + " : " + subtaskCosts.get(i).cost);
        }
        return totalCost;
    }

    public static class SubtaskCosts {
        String nodeEmail;
        String subtaskID;
        String computeMinutes;
        double cost;

        public SubtaskCosts(String nodeEmail, String subtaskID, String computeMinutes, double cost) {
            this.nodeEmail = nodeEmail;
            this.subtaskID = subtaskID;
            this.computeMinutes = computeMinutes;
            this.cost = cost;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("WIX FETCH POST TEST : ");
        System.out.println("WIX HEADER : " + request.getHeader("Content-Type"));

//        System.out.println("getParameter - Name : " + request.getParameter("task-file"));           // GETS THE PARAMETER - WORKS
//        System.out.println("getParameter - Size : " + request.getParameter("task-file").length());  // GETS PARAMETER SIZE
//        System.out.println("getPart - Name : " + request.getPart("task-file").getName());
//        System.out.println("getPart - Size : " + request.getPart("task-file").getSize());
//
//        Collection<Part> parts = request.getParts();
//
//        System.out.println("getParts - Part : " +  parts.iterator().next());
//        System.out.println("getParts - Part getName : " + parts.iterator().next().getName());
//        System.out.println("getParts - Part inputStream(int)"  + parts.iterator().next().getInputStream().read());
//        System.out.println("getParts - Part getSize : " + parts.iterator().next().getSize());
//        System.out.println("allParts Size : " + parts.size());

//        File originalTaskFile = new File(originalTaskDir, renderFileName);
////
//        InputStream inputStream = parts.iterator().next().getInputStream();
//        Files.copy(inputStream, originalTaskFile.toPath());

        StringBuffer jsonString = new StringBuffer();
        HashMap<String, String> renderParamsMap = readJsonRenderParams(jsonString, request.getReader());

        renderParamsMap.forEach((k, v) -> {
            System.out.format("%s : %s \n", k, v);
        });

        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("FETCH", "SUCCESS");
    }

    private static LinkedHashMap<String, String> readJsonRenderParams (StringBuffer jsonString, BufferedReader reader) throws IOException {
        LinkedHashMap<String, String> renderParams;
        String line = null;
        try {
            BufferedReader bufferedReader = reader;
            while ((line = reader.readLine()) != null)
                jsonString.append(line);
        } catch (Exception e) { /*report an error*/ }

        try {
            JSONObject jsonObject =  HTTP.toJSONObject(jsonString.toString());
            System.out.println("JB STRING : " + jsonString);
            renderParams = renderJsonParamExtract(jsonString.toString());
            System.out.println("JSON OBJECT : " + jsonObject);
        } catch (JSONException e) {
            throw new IOException("Error parsing JSON request string");
        }

        return renderParams;
    }

    private static LinkedHashMap<String, String> renderJsonParamExtract (String jsonParam) {
        String[] params = new String[10];
//        String s = jsonParam.replaceAll("[^a-zA-Z0-9]", "");
        String s = jsonParam.replaceAll("[\"{}]", "");

        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter=0;
        while(scanner.hasNext()) {
            params[counter] = scanner.next();
            counter++;
        }

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("taskID", params[0]);
        map.put("userEmail", params[1]);
        map.put("shareLink", params[2]);
        map.put("application", params[3]);
        map.put("frameCategory", params[4]);
        map.put("startFrame", params[5]);
        map.put("endFrame", params[6]);
        map.put("renderOutput", params[7]);

        scanner.close();

        return map;
    }

    private static String uploadResultToDropbox(File render) {
        String url;
        try {
            FullAccount account = client.users().getCurrentAccount();
            System.out.println(account.getName().getDisplayName());

            InputStream in = new FileInputStream(render);
            FileMetadata metadata = client.files().uploadBuilder("/completedRenders/" + render.getName())
                    .uploadAndFinish(in);

            url = metadata.getPathLower();
            return url;
        } catch (Exception e) {
            System.out.println("Error in uploading results to Dropbox.");
            e.printStackTrace();
            return null;
        }
    }

    public static String getShareLink(String path) {
        try {
            String url;

            DbxUserSharingRequests share = client.sharing();
            ListSharedLinksResult linksResult = client.sharing().listSharedLinksBuilder()
                    .withPath(path)
//                    .withDirectOnly(true)
                    .start();

            List<SharedLinkMetadata> links = linksResult.getLinks();

            if (links.size() > 0) {
                url = links.get(0).getUrl();
            } else {
                SharedLinkSettings settings = new SharedLinkSettings(RequestedVisibility.PUBLIC, null, null, null, RequestedLinkAccessLevel.VIEWER);
                SharedLinkMetadata metadata = share.createSharedLinkWithSettings(path, settings);
                url = metadata.getUrl();
            }
            return url.replace("?dl=0", "?raw=1");

        }  catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static String getUpload() throws IOException {
        String upload = null;
        String testURL = "https://www.nebula.my/_functions-dev/upload/";
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpUriRequest request = RequestBuilder
                .get(testURL)
                .build();

        CloseableHttpResponse response = httpClient.execute(request);
        try {
            upload = response.getFirstHeader("ID").getValue();
            System.out.println("Response 1 : " + response.getEntity().getContent().toString());
            System.out.println("Response 2 : " + response.getEntity().toString());
            System.out.println("Response 3 : " + response.getAllHeaders().length);
            if (upload.equals("null")) {
                return null;
            } else {
                return upload;
            }

        } finally  {
            response.close();
        }
    }

}



