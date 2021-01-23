package nebula.nebulaserver; /**
 * Created by Daryl Wong on 3/14/2019.
 */

import org.json.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@WebServlet(
        name = "UploadReceiver",
        urlPatterns = {"/upload"}
        )
@MultipartConfig
public class UploadReceiver extends HttpServlet {

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");

//    private static Part taskFile;
//    private static File taskDir;
//    private static String taskFileName;
//    private static String taskID;
//    private static String subtaskID;
//    private static String subtaskLength;

//    String userEmail;
//    String application;

    Deque<BlenderTask> blenderTaskQueue = new LinkedList<>();
    Iterator<BlenderTask> taskIterator = blenderTaskQueue.iterator();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)                                      // doGet to send Subtask Queue & Application info
            throws IOException {
        System.out.println("---- Task Information Request (UPLOAD RECEIVER - doGet) ----");

       if (request == null) {
           System.out.println("Problem's here (UploadReceiver - doGet). Request Null.");
           response.sendError(response.SC_BAD_REQUEST,
                   "Bad Request | Parameter Required");
       } else if (request != null && blenderTaskQueue.size() > 0) {
           System.out.println("Task Queue Size : " + blenderTaskQueue.size());
           BlenderTask blenderTask = blenderTaskQueue.peek();
           blenderTaskQueue.remove();

           printTaskInfo(blenderTask);
           String taskID = blenderTask.getTaskID();
               response.setHeader("Task-Identity", taskID);
           System.out.println("New Task Queue Size : " + blenderTaskQueue.size());

       } else if (request != null && blenderTaskQueue.size() <= 0) {                                                         // NO TASKS
           System.out.println("UPLOAD RECEIVER (doGet) : There are no tasks to compute at this moment.");
           System.out.println("TaskQueue Size : " + blenderTaskQueue.size());
           response.setHeader("Task-Identity", "null");

        }

       int status = response.getStatus();
       System.out.println("STATUS CODE : " + status);
       System.out.println("-------- (UPLOAD RECEIVER - doGet) --------");
        }

      protected void doPost (HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException  {
          StringBuffer jsonString = new StringBuffer();
          HashMap<String, String> renderParamsMap = readJsonRenderParams(jsonString, request.getReader());

          String taskID = renderParamsMap.get("taskID");
          String userEmail = renderParamsMap.get("userEmail");
          String shareLink = renderParamsMap.get("shareLink");
          String application = renderParamsMap.get("application");
          String frameCategory = renderParamsMap.get("frameCategory");
          String startFrame = renderParamsMap.get("startFrame");
          String endFrame = renderParamsMap.get("endFrame");
          String renderOutputType = renderParamsMap.get("renderOutputType");

//           Creates a main directory for the new task.
            File taskDir = new File(taskDatabase, taskID);
            taskDir.mkdir();

            // Creating all sub-directories needed :
            Collection<File> subdirCollection = new LinkedList<>();
            File clientCollectionDir = new File(taskDir, "clientcollection");
            File subtaskDir = new File(taskDir, "subtasks");
            File originalTaskDir = new File(taskDir, "originaltask");
            File nodeResultsDir = new File(taskDir, "noderesults");
            File finalResultsDir = new File(nodeResultsDir, "finalresults");

            clientCollectionDir.mkdir();
            subtaskDir.mkdir();
            originalTaskDir.mkdir();
            nodeResultsDir.mkdir();
            finalResultsDir.mkdir();

            // subdirCollection keeps track of all Task Subdirectories.
            subdirCollection.add(nodeResultsDir);
            subdirCollection.add(clientCollectionDir);
            subdirCollection.add(subtaskDir);
            subdirCollection.add(originalTaskDir);

          //            File originalTaskFile = new File(originalTaskDir, originalTaskFileName);
            File renderFile = downloadBlendFileFromURL(shareLink, taskID, originalTaskDir);          //  USER SELECTED RENDERFILE TO BE DOWNLOADED FROM THE SHARE LINK URL (DROPBOX / GOOGLE DRIVE)
            System.out.println("RENDERFILE SIZE : " + renderFile.length());

            BlenderTask blenderTask = new BlenderTask(taskID,
                    userEmail,
                    application,
                    frameCategory,
                    Integer.valueOf(startFrame),
                    Integer.valueOf(endFrame),
                    renderOutputType,
                    renderFile,
                    subtaskDir);                               // Creates new Task object to contain crucial information.

            System.out.println("TASK CREATED : " + blenderTask.getTaskID() + " | SUBTASK QUEUE : " + blenderTask.getTotalNumberOfSubtasks());
            BlenderTask.blenderFrameTask frameTask;
            BlenderTask.blenderSubtask subtask;
            blenderTask.setOriginalTaskDir(originalTaskDir.toPath());                                                              // Links the Task object to originalTaskFile Path.
            blenderTaskQueue.add(blenderTask);
            printTaskInfo(blenderTask);

            setMetadata("User-Email", blenderTask.getUserEmail(), renderFile.toPath());                                 // Setting Basic Data into original task file in case needed. (Premature optimization)
            setMetadata("Task-Identity", taskID, renderFile.toPath());
            setMetadata("Application", application, renderFile.toPath());
            setMetadata("Start-Frame", startFrame, renderFile.toPath());
            setMetadata("End-Frame", endFrame, renderFile.toPath());
            setMetadata("Frame-Count", String.valueOf(blenderTask.getFrameCount()), renderFile.toPath());
            setMetadata("Output-Type", renderOutputType, renderFile.toPath());
            setMetadata("Blend-URL", shareLink, renderFile.toPath());
//
            for (int j = 0; j< blenderTask.getFrameTaskQueue().size(); j++) {
                frameTask = blenderTask.getFrameTaskQueue().get(j);

                for (int i = 0; i < frameTask.getSubtaskQueue().size(); i++) {
                    subtask = frameTask.getSubtaskQueue().get(i);
                    String this_originalTaskFileName = subtask.getOriginalTaskFile().getName();
                    String this_application = subtask.getApplication();
                    String this_subtaskID = subtask.getSubtaskID();
                    String this_frameCategory = blenderTask.getFrameCategory();
                    String this_renderFrame = String.valueOf(subtask.getFrame());
                    String this_subtaskCount = Integer.toString(frameTask.getSubtaskCount());
                    Path scriptPath = subtask.getTileScript().toPath();

                    setMetadata("Original-Task", this_originalTaskFileName, scriptPath);
                    setMetadata("Application", this_application, scriptPath);
                    setMetadata("Task-Identity", taskID, scriptPath);
                    setMetadata("Subtask-Identity", this_subtaskID, scriptPath);
                    setMetadata("Frame-Category", this_frameCategory, scriptPath);
                    setMetadata("Frame", this_renderFrame, scriptPath);
                    setMetadata("Subtask-Count", this_subtaskCount, scriptPath);
                }
            }
//            printSubtaskQueue(task);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        System.out.println("---- (UPLOAD RECEIVER - doPost) ----");

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
        map.put("renderOutputType", params[7]);

        System.out.println("UPLOAD PARAMS : ");
        map.forEach((key, value) -> System.out.println(key + ":" + value));


        scanner.close();

        return map;
    }

    public static File downloadBlendfileFromDbox(String url, String taskID, File originalTaskDir, int renderfileLength) {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String downloadURL = url.replace("?dl=0", "?dl=1");
        String filename = String.format(taskID + ".blend");
        File renderFile = new File(originalTaskDir.getAbsolutePath(), filename);
        try{
            URL download=new URL(downloadURL);
            System.out.println("DOWNLOAD (DBOX) : " + renderFile.getName());
            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
            InputStream in = new BufferedInputStream((download.openStream()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int n = 0;
            int updateFileSize = out.size();
            int totalFileSize = renderfileLength;
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

    public static File downloadBlendfileFromGDrive(String url, String taskID, File originalTaskDir, int renderfileLength) {
        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
        String filename = String.format(taskID + ".blend");
        File renderFile = new File(originalTaskDir.getAbsolutePath(), filename);
        try{
            URL download=new URL(gdriveURL);
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

    public static File downloadBlendFileFromURL(String url, String taskID, File originalTaskDir) throws MalformedURLException {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String filename = String.format(taskID + ".blend");
        File renderFile = new File(originalTaskDir.getAbsolutePath(), filename);
        int renderfileLength = getFileSizeInKB(new URL(url));
        int renderFileLimit = 20000;

        if (url.contains("google") && renderfileLength <= renderFileLimit) {
            System.out.println("Google Drive share link detected. Downloading from GDrive URL . . . ");
            downloadBlendfileFromGDrive(url, taskID, originalTaskDir, renderfileLength);
        } else if (url.contains("dropbox") && renderfileLength <= renderFileLimit) {
            System.out.println("DropBox share link detected. Downloading from DropBox URL . . .");
            downloadBlendfileFromDbox(url, taskID, originalTaskDir, renderfileLength);
        } else {
            System.out.println("ERROR : INVALID / UNKNOWN URL");
        }

        return renderFile;
    }

    private static int getFileSizeInKB(URL url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.getInputStream();
            return conn.getContentLength()/1024;
        } catch (IOException e) {
            return -1;
        } finally {
            conn.disconnect();
        }
    }

    public void printTaskQueue() {
        Iterator<BlenderTask> iterator = blenderTaskQueue.iterator();
        System.out.println("Task Queue : ");
        int i=1;
        while (iterator.hasNext()) {
            System.out.println(i + ". " + iterator.next().getTaskID());
            i++;
        }
    }


    public void printSubtaskQueue(BlenderTask blenderTask) {
        Iterator<BlenderTask.blenderFrameTask> frameTaskIterator = blenderTask.getFrameTaskQueue().iterator();

        System.out.println(blenderTask.getTaskID() + "'s Subtask Queue :");
        int i=1;
        while (frameTaskIterator.hasNext()) {
            Iterator<BlenderTask.blenderSubtask> subtaskIterator = frameTaskIterator.next().getSubtaskQueue().iterator();
            System.out.println(i + ". " + subtaskIterator.next().getSubtaskID());
            i++;
        }
    }

    public void printTaskInfo(BlenderTask blenderTask) {
        System.out.println("---------- TASK INFORMATION ----------");       // Task Information Checkpoint
        System.out.println("Task Identity : " + blenderTask.getTaskID());
        System.out.println("Uploaded by : " + blenderTask.getUserEmail());
        System.out.println("Application : " + blenderTask.getApplication());
//        System.out.println("Frame Category : " + task.getFrameCategory());
        System.out.println("Start Frame : " + blenderTask.getStartFrame());
        System.out.println("End Frame : " + blenderTask.getEndFrame());
        System.out.println("Frame Count : " + blenderTask.getFrameCount());
        System.out.println("Subtasks / Frame : " + blenderTask.getTotalNumberOfSubtasks() / blenderTask.getFrameCount());
        System.out.println("Output Type : " + blenderTask.getRenderOutputType());
        System.out.println("Task File : " + blenderTask.getOriginalTaskFile().getName());
        System.out.println("Task Queue : " + blenderTaskQueue.size());
        System.out.println("Subtask Queue : " + blenderTask.getTotalNumberOfSubtasks());
        System.out.println("----------- TASK DIRECTORY ------------");
        System.out.println("Original Task File Path : " + blenderTask.getOriginalTaskFile().getAbsolutePath());
        System.out.println("Original Task Dir : " + blenderTask.getOriginalTaskDir());
        System.out.println("Task Database : " + taskDatabase.getAbsolutePath());
        System.out.println("----------------------------------------");

    }

//
//    public void refreshQueue() {
//
//        File[] taskArray = taskDatabase.listFiles();
//        System.out.println("--- Task Queue ---");
//        for (int i = 0; i < taskArray.length; i++) {
//            System.out.println("Tasks " + i + " : " + taskArray[i].getName());
//            if (!taskQueue.contains(taskArray[i])) {
//                taskQueue.add(taskArray[i]);
//            } else {
//                System.out.println("Task : " + taskArray[i].getName() + " is already in queue.");
//            }
//        }
//        System.out.println("--- End of Queue ---");
//    }

    public static String taskIdentity(int frames) {

        int[] randomNum = new int[6];
        int min = 0;
        int max = 9;
        String frameType;

        if (frames > 1) {
            frameType = "M";
        } else {
            frameType = "S";
        }

        for (int i = 0; i < randomNum.length; i++) {
            randomNum[i] = ThreadLocalRandom.current().nextInt(min, max + 1);
        }

        String ID = String.format(frameType
                + Integer.toString(randomNum[0])
                + Integer.toString(randomNum[1])
                + Integer.toString(randomNum[2])
                + Integer.toString(randomNum[3])
                + Integer.toString(randomNum[4])
                + Integer.toString(randomNum[5]));
        return ID;
    }

    public void setMetadata(String metaName, String metaValue, Path metaPath) {                                                     // Setting Application info as Subtask attribute for retrieval later by ComputeTask.
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            view.write(metaName, Charset.defaultCharset().encode(metaValue));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMetadata(Path metaPath, String metaName) {                                       // Getting Application info from Subtask attributes for ComputeTask.
        String metaValue = null;
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            ByteBuffer buffer = ByteBuffer.allocate(view.size(metaName));
            view.read(metaName, buffer);
            buffer.flip();
            metaValue = Charset.defaultCharset().decode(buffer).toString();
            System.out.println("(CHECK) | Attribute : " + metaValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }
}
