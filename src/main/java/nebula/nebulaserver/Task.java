package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.sharing.*;
import com.dropbox.core.v2.users.FullAccount;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;

public class Task {
    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");

    File taskDir;
    File subtaskDir;
    File renderfileDir;
    File resultsDir;
    File finalResultsDir;
    File frameResultsDir;

    private LinkedHashMap<String, String> taskParamsMap;
    final DbxRequestConfig config = Server.config;
    final DbxClientV2 client = Server.client;

    public Task(LinkedHashMap<String, String> taskParamsMap) throws IOException {
        this.taskParamsMap = taskParamsMap;

        createTaskDatabase(taskParamsMap.get("taskID"));
        createTasks(taskParamsMap);
    }

    public void createTaskDatabase(String taskID) {
        // Creates a main directory for the new task.
        taskDir = new File(taskDatabase, taskID);
        taskDir.mkdir();

        // Creating all sub-directories needed :
        subtaskDir = new File(taskDir, "subtasks");
        renderfileDir = new File(taskDir, "renderfile");
        resultsDir = new File(taskDir, "results");
        finalResultsDir = new File(resultsDir, "finalResults");
        frameResultsDir = new File(resultsDir, "frameResults");

        subtaskDir.mkdir();
        renderfileDir.mkdir();
        resultsDir.mkdir();
        finalResultsDir.mkdir();
        frameResultsDir.mkdir();

        System.out.println(taskID + " | taskDir : " + taskDir.exists());
        System.out.println(taskID + " | subtaskDir : " + subtaskDir.exists());
        System.out.println(taskID + " | renderfileDir : " + renderfileDir.exists());
        System.out.println(taskID + " | resultsDir : " + resultsDir.exists());
        System.out.println(taskID + " | finalResultsDir : " + finalResultsDir.exists());
        System.out.println(taskID + " | frameResultsDir : " + frameResultsDir.exists());
    }

    // This method checks for the chosen application map and create the relative tasks accordingly with the right nested task class.
    // Created tasks should package subtasks in taskDirectory to be ready for scheduling.
    public void createTasks(LinkedHashMap<String, String> taskParamsMap) throws IOException {
        try {
            System.out.println("TASK | Application : " + taskParamsMap.get("application"));
            String application = taskParamsMap.get("application");

            if (setupRenderfile(taskParamsMap.get("shareLink"), application, taskParamsMap.get("taskID"), renderfileDir)) {
                if (application.contains("blender")) {
                    System.out.println("TASK | Blender detected. Creating Blender Task . . .");
                    createBlenderTask(taskParamsMap);

                } else if (application.contains("vray")) {
                    System.out.println("TASK | V-ray SketchUp detected. Creating V-ray SketchUp Task . . .");
                    createVraySketchTask(taskParamsMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public VrayTask createVraySketchTask(LinkedHashMap<String, String> taskParamsMap) throws IOException {

        VrayTask vrayTask = new VrayTask(taskParamsMap.get("taskID"),
                taskParamsMap.get("userEmail"),
                taskParamsMap.get("application"),
                taskParamsMap.get("frameCategory"),
                Integer.parseInt(taskParamsMap.get("startFrame")),
                Integer.parseInt(taskParamsMap.get("endFrame")),
                taskParamsMap.get("renderOutputType"),
                taskParamsMap.get("shareLink"),
                Integer.parseInt(taskParamsMap.get("computeRate")),
                taskParamsMap.get("renderfileName"),
                taskParamsMap.get("renderHeight"),
                taskParamsMap.get("renderWidth"),
                subtaskDir);

        VrayTask.vrayFrameTask vrayFrameTask;
        VrayTask.vraySubtask vraySubtask;

        try {
            // setMetadata("Task-Params", blendFile.toPath());
            // The jsonTaskParams is the same JsonString sent from nebula.my that includes these Task Parameters for Blender :
            // Params : TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType

            // Loop through all blenderFrameTasks to then loop through blenderSubtasks of each blenderFrameTask
            for (int j = 0; j< vrayTask.getFrameTaskQueue().size(); j++) {
                vrayFrameTask = vrayTask.getFrameTaskQueue().get(j);

                for (int i = 0; i < vrayFrameTask.getSubtaskQueue().size(); i++) {
                    vraySubtask = vrayFrameTask.getSubtaskQueue().get(i);
                    String this_renderfileName = vraySubtask.getRenderfileName();
                    String this_tileScriptName = vraySubtask.getTileScript().getName();
                    String this_subtaskID = vraySubtask.getSubtaskID();
                    String this_renderFrame = String.valueOf(vraySubtask.getFrame());
                    String this_subtaskCount = Integer.toString(vrayFrameTask.getSubtaskCount());
                    String this_frameCount = String.valueOf(vrayTask.frameCount);

                    // The subtaskParams are essential parameters needed for nebula_node to compute and keep track of Tasks, FrameTasks and Subtasks.
                    // To ensure these parameters are passed to nebula_node, we set them as Metadata to the TileScript.
                    // The subtaskParams will operate much like the jsonTaskParams above, stringing together all subtask-specific parameters for Node to compute with.
                    // Params : RenderfileName (*insert TaskID*.blend), TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType, SubtaskID, RenderFrame, SubtaskCount
                    String subtaskParams = buildSubtaskParamsString(this_renderfileName,
                                                                    this_tileScriptName,
                                                                    this_subtaskID,
                                                                    this_renderFrame,
                                                                    this_frameCount,
                                                                    this_subtaskCount);

                    if (vraySubtask.getTileScript() != null) {
                        System.out.println("TASK | Tile Script Path : " + vraySubtask.getTileScript().getName());
                        setMetadata("Subtask-Params", subtaskParams, vraySubtask.getTileScript().toPath());
                        setMetadata("Subtask-ID", this_subtaskID, vraySubtask.getTileScript().toPath());

                    } else {
                        System.out.println("[ERROR] Tile script null");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return vrayTask;
    }

    public BlenderTask createBlenderTask(LinkedHashMap<String, String> taskParamsMap) throws IOException {

        BlenderTask blenderTask = new BlenderTask(taskParamsMap.get("taskID"),
                taskParamsMap.get("userEmail"),
                taskParamsMap.get("application"),
                taskParamsMap.get("frameCategory"),
                Integer.parseInt(taskParamsMap.get("startFrame")),
                Integer.parseInt(taskParamsMap.get("endFrame")),
                taskParamsMap.get("renderOutputType"),
                taskParamsMap.get("shareLink"),
                Integer.parseInt(taskParamsMap.get("computeRate")),
                taskParamsMap.get("renderfileName"),
                subtaskDir);

        BlenderTask.blenderFrameTask blenderFrameTask;
        BlenderTask.blenderSubtask blenderSubtask;

        try {
            // setMetadata("Task-Params", blendFile.toPath());
            // The jsonTaskParams is the same JsonString sent from nebula.my that includes these Task Parameters for Blender :
            // Params : TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType

            // Loop through all blenderFrameTasks to then loop through blenderSubtasks of each blenderFrameTask
            for (int j = 0; j< blenderTask.getFrameTaskQueue().size(); j++) {
                blenderFrameTask = blenderTask.getFrameTaskQueue().get(j);

                for (int i = 0; i < blenderFrameTask.getSubtaskQueue().size(); i++) {
                    blenderSubtask = blenderFrameTask.getSubtaskQueue().get(i);
                    String this_renderfileName = blenderSubtask.getRenderfileName();
                    String this_tileScriptName = blenderSubtask.getTileScript().getName();
                    String this_subtaskID = blenderSubtask.getSubtaskID();
                    String this_renderFrame = String.valueOf(blenderSubtask.getFrame());
                    String this_subtaskCount = Integer.toString(blenderFrameTask.getSubtaskCount());
                    String this_frameCount = String.valueOf(blenderTask.frameCount);

                    // The subtaskParams are essential parameters needed for nebula_node to compute and keep track of Tasks, FrameTasks and Subtasks.
                    // To ensure these parameters are passed to nebula_node, we set them as Metadata to the TileScript.
                    // The subtaskParams will operate much like the jsonTaskParams above, stringing together all subtask-specific parameters for Node to compute with.
                    // Params : RenderfileName (*insert TaskID*.blend), TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType, SubtaskID, RenderFrame, SubtaskCount
                    String subtaskParams = buildSubtaskParamsString(this_renderfileName,
                                                                    this_tileScriptName,
                                                                    this_subtaskID,
                                                                    this_renderFrame,
                                                                    this_frameCount,
                                                                    this_subtaskCount);

                    if (blenderSubtask.getTileScript() != null) {
                        System.out.println("TASK | Tile Script Path : " + blenderSubtask.getTileScript().getAbsolutePath());
                        setMetadata("Subtask-Params", subtaskParams, blenderSubtask.getTileScript().toPath());
                        setMetadata("Subtask-ID", this_subtaskID, blenderSubtask.getTileScript().toPath());

                    } else {
                        System.out.println("[ERROR] Tile script null");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return blenderTask;
    }

    public String buildSubtaskParamsString(String renderfileName,
                                          String tileScriptName,
                                          String subtaskID,
                                          String renderFrame,
                                          String frameCount,
                                          String subtaskCount) {


        StringBuilder subtaskParams = new StringBuilder(
                            renderfileName +
                        "," + tileScriptName +
                        "," + taskParamsMap.get("taskID") +
                        "," + taskParamsMap.get("application") +
                        "," + taskParamsMap.get("userEmail") +
                        "," + taskParamsMap.get("shareLink") +
                        "," + taskParamsMap.get("frameCategory") +
                        "," + taskParamsMap.get("startFrame") +
                        "," + taskParamsMap.get("endFrame") +
                        "," + taskParamsMap.get("renderOutputType") +
                        "," + subtaskID +
                        "," + renderFrame +
                        "," + frameCount +
                        "," + subtaskCount);

        return subtaskParams.toString();
    }

    // This method downloads the Renderfile submitted by users from their GDrive/Dbox to our Dbox.
    // Once the renderfile is moved to our Dbox, we get its sharelink to distribute to nebula_node for computing.
    // This method fulfilles 2 purposes.
    // (1) nebula_node downloads from our sharelink instead of user's sharelink to protect their privacy.
    // (2) Dbox free account has download limits, meaning limited nebula_nodes can download from it.
    private boolean setupRenderfile (String userShareLink, String application, String taskID, File renderfileDir) {
        boolean renderfileSetup = false;
        try {
            File renderfile = downloadRenderfile(userShareLink, application, taskID);
//            String downloadURL = userShareLink.replace("?dl=0", "?dl=1");

            String renderfileLink = uploadRenderfileToDropbox(renderfile);
            System.out.println("Renderfile Link : " + renderfileLink);

            if (renderfile.length() > 0 && renderfileLink != null) {
                taskParamsMap.put("shareLink", renderfileLink);
                taskParamsMap.put("renderfileName", renderfile.getName());
                renderfileSetup = true;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return renderfileSetup;
    }

    private String uploadRenderfileToDropbox(File renderfile) { // Uploads Result to Dropbox for users to view/download whenever they want through the dropbox link. NEGATIVE - Growing expense to host user renders.
        String path;
        String shareLink;
        try {
            FullAccount account = client.users().getCurrentAccount();
            System.out.println(account.getName().getDisplayName());

            InputStream in = new FileInputStream(renderfile);
            FileMetadata metadata = client.files().uploadBuilder("/render/" + renderfile.getName())
                    .uploadAndFinish(in);

            path = metadata.getPathLower();
            shareLink = getShareLink(path);

            return shareLink;
        } catch (Exception e) {
            System.out.println("Error in uploading renderfile to Dropbox.");
            e.printStackTrace();
            return null;
        }
    }

    public String getShareLink(String path) {
        try {
            String url;

            SharedLinkMetadata metadata = client.sharing().createSharedLinkWithSettings(path);
            url = metadata.getUrl();
            return url.replace("?dl=0", "?dl=1"); //?raw=1

        }  catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

//    public String getShareLink(String path) {
//        try {
//            String url;
//
//            DbxUserSharingRequests share = client.sharing();
//            ListSharedLinksResult linksResult = client.sharing().listSharedLinksBuilder()
//                    .withPath(path)
////                    .withDirectOnly(true)
//                    .start();
//
//            List<SharedLinkMetadata> links = linksResult.getLinks();
//
//            if (links.size() > 0) {
//                url = links.get(0).getUrl();
//            } else {
//                SharedLinkSettings settings = new SharedLinkSettings(RequestedVisibility.PUBLIC, null, null, null, RequestedLinkAccessLevel.VIEWER);
//                SharedLinkMetadata metadata = share.createSharedLinkWithSettings(path, settings);
//                url = metadata.getUrl();
//            }
//            return url.replace("?dl=0", "?dl=1"); //?raw=1
//
//        }  catch (Exception ex) {
//            ex.printStackTrace();
//            return null;
//        }
//    }

    public File downloadRenderfile(String url, String application, String taskID) throws MalformedURLException {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String filename = null;
        if (application.contains("blender")) {
            filename = String.format(taskID + ".blend");
        } else if (application.contains("vray")) {
            filename = String.format(taskID + ".vrscene");
        }

        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
        int renderfileLength = getFileSizeInKB(new URL(url));
        int renderFileLimit = 150000;

        if (url.contains("google") && renderfileLength <= renderFileLimit) {
            System.out.println("Google Drive share link detected. Downloading from GDrive URL . . . ");
            renderFile = downloadRenderfileFromGDrive(url, renderfileDir, filename);
        } else if (url.contains("dropbox") && renderfileLength <= renderFileLimit) {
            System.out.println("DropBox share link detected. Downloading from DropBox URL . . .");
            renderFile = downloadRenderfileFromDbx(url, renderfileDir, filename);
        } else {
            System.out.println("ERROR : INVALID / UNKNOWN URL");
        }

        return renderFile;
    }

    public File downloadRenderfileFromDbx(String url, File renderfileDir, String filename) {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String downloadURL = url.replace("?dl=0", "?dl=1");
        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
        try{
            URL download=new URL(downloadURL);
            System.out.println("DOWNLOAD (DBOX) : " + renderFile.getName());
            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
//            InputStream in = new BufferedInputStream((download.openStream()));
//            ByteArrayOutputStream out = new ByteArrayOutputStream();

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

    public File downloadRenderfileFromGDrive(String url, File renderfileDir, String filename) {
        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
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

    public void printTaskInfo(Task task) {
        System.out.println("---------- TASK INFORMATION ----------");       // Task Information Checkpoint
        LinkedHashMap<String, String> map = task.getTaskParamsMap();
        Iterator iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry)iterator.next();
            System.out.println(pair.getKey() + " : " + pair.getValue());
            iterator.remove();
        }
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

    public static int getFileSizeInKB(URL url) {
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

    public LinkedHashMap<String, String> getTaskParamsMap() {
        return taskParamsMap;
    }

    public String getTaskID() {
        return taskParamsMap.get("taskID");
    }

}
