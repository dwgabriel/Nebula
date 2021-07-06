package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.sharing.ListSharedLinksResult;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
    static boolean taskSetup = false;

    public Task(LinkedHashMap<String, String> taskParamsMap) throws IOException {
        this.taskParamsMap = taskParamsMap;

        if (createTasks(taskParamsMap)) {
            taskSetup = true;
        }
    }

    public boolean getTaskSetupStatus() {
        return taskSetup;
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

        System.out.println("TASK | " + taskID + " | taskDir : " + taskDir.exists());
        System.out.println("TASK | " + taskID + " | subtaskDir : " + subtaskDir.exists());
        System.out.println("TASK | " + taskID + " | renderfileDir : " + renderfileDir.exists());
        System.out.println("TASK | " + taskID + " | resultsDir : " + resultsDir.exists());
        System.out.println("TASK | " + taskID + " | finalResultsDir : " + finalResultsDir.exists());
        System.out.println("TASK | " + taskID + " | frameResultsDir : " + frameResultsDir.exists());
    }

    // This method checks for the chosen application map and create the relative tasks accordingly with the right nested task class.
    // Created tasks should package subtasks in taskDirectory to be ready for scheduling.
    public boolean createTasks(LinkedHashMap<String, String> taskParamsMap) {
        boolean tasksCreated = false;

        try {
            System.out.println("TASK | Application : " + taskParamsMap.get("application"));
            String application = taskParamsMap.get("application");

            if (setupTaskFiles(application, taskParamsMap.get("taskID"))) {
                if (application.contains("blender")) {
                    System.out.println("TASK | Blender detected. Creating Blender Task . . .");
                    if (createBlenderTask(taskParamsMap) != null) {
                        tasksCreated = true;
                    }
                } else if (application.contains("vray")) {
                    System.out.println("TASK | V-ray SketchUp detected. Creating V-ray SketchUp Task . . .");
                    if (createVraySketchTask(taskParamsMap) != null) {
                        tasksCreated = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return tasksCreated;
    }

    public VrayTask createVraySketchTask(LinkedHashMap<String, String> taskParamsMap) throws IOException {

        VrayTask vrayTask = new VrayTask(taskParamsMap, subtaskDir);

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
                    String this_frameCount = String.valueOf(vrayTask.getFrameCount());

                    // The subtaskParams are essential parameters needed for nebula_node to compute and keep track of Tasks, FrameTasks and Subtasks.
                    // To ensure these parameters are passed to nebula_node, we set them as Metadata to the TileScript.
                    // The subtaskParams will operate much like the jsonTaskParams above, stringing together all subtask-specific parameters for Node to compute with.
                    // Params : RenderfileName (*insert TaskID*.blend), TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType, SubtaskID, RenderFrame, SubtaskCount
                    String subtaskParams = buildSubtaskParams(this_renderfileName,
                                                            this_tileScriptName,
                                                            this_subtaskID,
                                                            this_renderFrame,
                                                            this_frameCount,
                                                            this_subtaskCount);

                    if (vraySubtask.getTileScript() != null) {
                        System.out.println("TASK | Tile Script : " + vraySubtask.getTileScript().getName());
                        setMetadata("Subtask-Params", subtaskParams, vraySubtask.getTileScript().toPath());
                        setMetadata("Subtask-ID", this_subtaskID, vraySubtask.getTileScript().toPath());
                        setMetadata("User-Email", taskParamsMap.get("userEmail"), vraySubtask.getTileScript().toPath());
                        setMetadata("Compute-Rate", taskParamsMap.get("computeRate"), vraySubtask.getTileScript().toPath());

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

        BlenderTask blenderTask = new BlenderTask(taskParamsMap, subtaskDir);

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
                    String subtaskParams = buildSubtaskParams(this_renderfileName,
                                                            this_tileScriptName,
                                                            this_subtaskID,
                                                            this_renderFrame,
                                                            this_frameCount,
                                                            this_subtaskCount);

                    if (blenderSubtask.getTileScript() != null) {
                        setMetadata("Subtask-Params", subtaskParams, blenderSubtask.getTileScript().toPath());
                        setMetadata("Subtask-ID", this_subtaskID, blenderSubtask.getTileScript().toPath());
                        setMetadata("User-Email", taskParamsMap.get("userEmail"), blenderSubtask.getTileScript().toPath());
                        setMetadata("Compute-Rate", taskParamsMap.get("computeRate"), blenderSubtask.getTileScript().toPath());

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

    public String buildSubtaskParams(String renderfileName,
                                     String tileScriptName,
                                     String subtaskID,
                                     String renderFrame,
                                     String frameCount,
                                     String subtaskCount) {

        String subtaskParams = null;

        if (taskParamsMap.get("application").contains("vray")) {

            subtaskParams = new StringBuilder(
                    renderfileName +
                            "," + tileScriptName +
                            "," + taskParamsMap.get("packedSkpName") +
                            "," + taskParamsMap.get("taskID") +
                            "," + taskParamsMap.get("application") +
                            "," + taskParamsMap.get("userEmail") +
                            "," + taskParamsMap.get("frameCategory") +
                            "," + taskParamsMap.get("startFrame") +
                            "," + taskParamsMap.get("endFrame") +
                            "," + taskParamsMap.get("renderOutputType") +
                            "," + subtaskID +
                            "," + renderFrame +
                            "," + frameCount +
                            "," + subtaskCount +
                            "," + taskParamsMap.get("renderfileURL") +
                            "," + taskParamsMap.get("packedSkpURL") +
                            "," + taskParamsMap.get("uploadfileName") +
                            "," + taskParamsMap.get("userSubscription") +
                            "," + taskParamsMap.get("userAllowance") +
                            "," + taskParamsMap.get("computeRate"))
                            .toString();
        } else if (taskParamsMap.get("application").contains("blender")) {

            subtaskParams = new StringBuilder(
                    renderfileName +
                            "," + tileScriptName +
                            "," + taskParamsMap.get("taskID") +
                            "," + taskParamsMap.get("application") +
                            "," + taskParamsMap.get("userEmail") +
                            "," + taskParamsMap.get("frameCategory") +
                            "," + taskParamsMap.get("startFrame") +
                            "," + taskParamsMap.get("endFrame") +
                            "," + taskParamsMap.get("renderOutputType") +
                            "," + subtaskID +
                            "," + renderFrame +
                            "," + frameCount +
                            "," + subtaskCount +
                            "," + taskParamsMap.get("renderfileURL")+
                            "," + taskParamsMap.get("uploadfileName") +
                            "," + taskParamsMap.get("userSubscription") +
                            "," + taskParamsMap.get("userAllowance") +
                            "," + taskParamsMap.get("computeRate"))
                    .toString();
        }

        return subtaskParams;
    }

    // This method downloads the Renderfile submitted by users from their GDrive/Dbox to our Dbox.
    // Once the renderfile is moved to our Dbox, we get its sharelink to distribute to nebula_node for computing.
    // This method fulfilles 2 purposes.
    // (1) nebula_node downloads from our sharelink instead of user's sharelink to protect their privacy.
    // (2) Dbox free account has download limits, meaning limited nebula_nodes can download from it.
    private boolean setupTaskFiles(String application, String taskID) {
        boolean renderfileSetup = false;
        try {
            createTaskDatabase(taskID);

            if (application.contains("vray")) {

                renderfileSetup = setupVrayTaskFiles(taskID);

//                File renderfile = downloadFromDbx(renderfileDir, taskParamsMap.get("renderfileName"));
//                System.out.println("TASK | CHECKPOINT 3 - RENDERFILE : " + renderfile.getName() + " | SIZE : " + renderfile.length());


//                if (renderfile.length() > 0) {
//                    System.out.println("TASK | CHECKPOINT 4");

//                    File packedSkp = downloadFromDbx(renderfileDir, taskParamsMap.get("packedSkpName"));
//                    System.out.println("TASK | CHECKPOINT 5 - PACKED SKP : " + packwwedSkp.getName() + " | SIZE : "  + packedSkp.length());

//                    if (packedSkp.length() > 0) {
//                        System.out.println("TASK | CHECKPOINT 6");
//
//                        renderfileSetup = setupVrayTaskFiles(taskID);
//                        System.out.println("TASK | CHECKPOINT 7 - RENDERFILE SETUP : " + renderfileSetup);

//                    } else {
//                        System.out.println("[ERROR] Renderfile (.vrscene) or Packed Sketchup File not downloaded properly. Renderfile : " + renderfile.length() + " | Packed SketchUp File : " + packedSkp.length());
//                    }
//                }


            } else if (application.contains("blender")) {
                renderfileSetup = setupBlenderTaskFiles(taskID);

//                File renderfile = downloadFromDbx(renderfileDir, taskParamsMap.get("renderfileName"));

//                if (renderfile.length() > 0) {
//                    renderfileSetup = setupBlenderTaskFiles(taskID);
//                } else {
//                    System.out.println("[ERROR] Renderfile (.blend) file not downloaded properly. Renderfile : " + renderfile.length());
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return renderfileSetup;
    }

    private String[] createFilesToZip(File dir) {
        File[] files = dir.listFiles();
        String[] filesToZip = new String[files.length];

        for (int i=0; i<files.length; i++) {
            File f = files[i];

            if (f.getName().contains(".skp") || f.getName().contains(".zip")) {
                f.delete();
            } else {
                filesToZip[i] = f.getAbsolutePath();
            }
        }

        return filesToZip;
    }

    private boolean setupBlenderTaskFiles(String taskID) {

        boolean blenderTaskSetup = false;

//        String[] filesToZip = createFilesToZip(renderfileDir);
//        String taskPackageName = taskID + ".zip";
//        File taskPackage = zipFiles(taskPackageName, filesToZip);
//        String renderfileURL = uploadRenderfileToDropbox(renderfilePackage);
        String renderfileURL = getShareLink("/render/" + taskParamsMap.get("renderfileName"));

        if (renderfileURL != null) {
            taskParamsMap.put("renderfileURL", renderfileURL);
//            taskParamsMap.put("taskPackageName", taskPackageName);
            blenderTaskSetup = true;
        }

        return blenderTaskSetup;
    }

    private boolean setupVrayTaskFiles(String taskID) {
        boolean vrayTaskSetup = false;

        try {
//                if (unzip(packedSkp, renderfileDir)) {

//            String[] filesToZip = createFilesToZip(renderfileDir);

//            String taskPackageName = taskID + ".zip";
//            File taskPackage = zipFiles(taskPackageName, filesToZip);
//          String renderfilePackageLink = uploadRenderfileToDropbox(taskPackage);
            String renderfileURL = getShareLink("/render/" + taskParamsMap.get("renderfileName"));
            String packedSkpURL = getShareLink("/render/" + taskParamsMap.get("packedSkpName"));

            System.out.println("TASK | CHECKPOINT - Renderfile URL : " + renderfileURL);
            System.out.println("TASK | CHECKPOINT - PackedSKP URL : " + packedSkpURL);


            if (renderfileURL != null && packedSkpURL != null) {
                taskParamsMap.put("renderfileURL", renderfileURL);
                taskParamsMap.put("packedSkpURL", packedSkpURL);
//                taskParamsMap.put("taskPackageName", taskPackageName);
                vrayTaskSetup = true;
            } else {
                System.out.println("[ERROR] TASK | " + taskParamsMap.get("renderfileName") + " or " + taskParamsMap.get("packedSkpName") + " failed to upload to Dbx for Scheduling.");
            }
//                } else {
//                    System.out.println("[ERROR] TASK | " + packedSkp.getName() + " failed to unzip. Size : " + packedSkp.length());
//                }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return vrayTaskSetup;
    }

//    private String uploadRenderfileToDropbox(File renderfilePackage) { // Uploads Result to Dropbox for users to view/download whenever they want through the dropbox link. NEGATIVE - Growing expense to host user renders.
//        String path;
//        String shareLink;
//        try {
//            FullAccount account = client.users().getCurrentAccount();
//
//            InputStream in = new FileInputStream(renderfilePackage);
//            FileMetadata metadata = client.files().uploadBuilder("/render/" + renderfilePackage.getName())
//                    .uploadAndFinish(in);
//
//            path = metadata.getPathLower();
//            shareLink = createShareLink(path);
//
//            return shareLink;
//        } catch (Exception e) {
//            System.out.println("[ERROR] Error in uploading renderfile to Dropbox.");
//            e.printStackTrace();
//            return null;
//        }
//    }

    public String createShareLink(String path) {
        try {
            SharedLinkMetadata metadata = client.sharing().createSharedLinkWithSettings(path);
            String url = metadata.getUrl();
            return url.replace("?dl=0", "?dl=1");

        }  catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String getShareLink(String path) {
        String url = null;
        try {
            ListSharedLinksResult sharedLinksResult = client.sharing().listSharedLinksBuilder()
                    .withPath(path)
                    .withDirectOnly(true)
                    .start();

            for (SharedLinkMetadata sharedLinkMetadata : sharedLinksResult.getLinks()){
                url = sharedLinkMetadata.getUrl().replace("?dl=0", "?dl=1");
            }

            if (url == null) {
                url = createShareLink(path);
            }

        }  catch (Exception ex) {
            ex.printStackTrace();
        }
        return url;
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

    private File zipFiles(String zipFileName, String[] filePaths) {
        File zippedFile = null;
        try {
            System.out.println("TASK | Zip File Name : " + zipFileName);
            zippedFile = new File(renderfileDir, zipFileName);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zippedFile)));

            for (String aFile : filePaths) {
                if (aFile != null) {
                    File file = new File(aFile);
//                    FileInputStream fis = new FileInputStream(file);
                    InputStream is = new FileInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(is);
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    int length;
//                    byte[] bytes = Files.readAllBytes(Paths.get(aFile));
                    byte[] buffer = new byte[1048576];

                    while ((length = bis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    bis.close();
                    zos.closeEntry();
                }
            }
            zos.finish();
            zos.close();

        } catch (FileNotFoundException ex) {
            System.err.println("[ERROR] A file does not exist: " + ex);
        } catch (IOException ex) {
            System.err.println("[ERROR] I/O error: " + ex);
        }
        return zippedFile;
    }

    private boolean unzip(File zipFile, File destDir) {
        boolean unzipped = false;
        FileInputStream fis;
        byte[] buffer = new byte[(int) zipFile.length()];
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            int idx = 1;
            while (ze != null) {

                String fileName = new String(ze.getName().getBytes(StandardCharsets.UTF_8));
                System.out.println("TASK | UNZIPPING - " + idx + ". File Name : " + fileName);
                idx++;
                File newFile = new File(destDir + File.separator + fileName);
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            fis.close();

            unzipped = true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return unzipped;
    }

    public File downloadFromDbx(File outputDir, String filename) {
        System.out.println("TASK | CHECK - Output Dir : " + outputDir.getAbsolutePath() + " | File : " + filename);
        File outfile = new File(outputDir.getAbsolutePath(), filename);
        try {
            OutputStream outputStream = new FileOutputStream(outfile);
            FileMetadata metadata = client.files().downloadBuilder("/render/" + filename)
                    .download(outputStream);
            System.out.println("TASK | METADATA - " + metadata.getName() + " / Size : " + metadata.getSize() + " / Downloadble : " + metadata.getIsDownloadable() + " / Path : " + metadata.getPathLower());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return outfile;
    }

//    public static int getFileSizeInKB(URL url) {
//        HttpURLConnection conn = null;
//        try {
//            conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("HEAD");
//            conn.getInputStream();
//            return conn.getContentLength()/1024;
//        } catch (IOException e) {
//            return -1;
//        } finally {
//            conn.disconnect();
//        }
//    }

    public LinkedHashMap<String, String> getTaskParamsMap() {
        return taskParamsMap;
    }

    public String getTaskID() {
        return taskParamsMap.get("taskID");
    }

//    public File downloadRenderfile(String url, String application, String taskID) throws MalformedURLException {
////        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
//        String filename = null;
//        url = formatURL(url);
//        if (application.contains("blender")) {
//            filename = String.format(taskID + ".blend");
//        } else if (application.contains("vray")) {
//            filename = String.format(taskID + ".vrscene");
//        }
//
//        File renderFile = null;
//        int renderfileLength = getFileSizeInKB(new URL(url));
//        int renderfileMin = 100;
//        int renderfileMax = 150000000;
//
//        System.out.println("CHECK FILE SIZE : " + renderfileLength);
//        if (url.contains("google") && renderfileLength >= renderfileMin && renderfileLength <= renderfileMax) {
//            System.out.println("TASK | Google Drive share link detected. Downloading from GDrive URL . . . ");
//            createTaskDatabase(taskID);
//            renderFile = downloadRenderfileFromGDrive(url, renderfileDir, filename);
//        } else if (url.contains("dropbox") && renderfileLength >= renderfileMin && renderfileLength <= renderfileMax) {
//            System.out.println("TASK | DropBox share link detected. Downloading from DropBox URL . . .");
//            createTaskDatabase(taskID);
//            renderFile = downloadRenderfileFromDbx(url, renderfileDir, filename);
//        } else {
//            System.out.println("[ERROR] INVALID URL OR UNAUTHORIZED TO DOWNLOAD FROM URL");
//        }
//
//        return renderFile;
//    }
//
//    public String formatURL(String url) {
//
//        if (url.contains("dropbox")) {
//            url = url.replace("?dl=0", "?dl=1");
//        } else if (url.contains("google")) {
//            url = url.replace("file/d/", "uc?export=download&id=");
//            url = url.replace("/view?usp=sharing", "");
//        }
//
//    return url;
//    }
//
//    public File downloadRenderfileFromDbx(String url, File renderfileDir, String filename) {
////        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
//        String downloadURL = url.replace("?dl=0", "?dl=1");
//        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
//        try{
//            URL download=new URL(downloadURL);
//            System.out.println("TASK | DOWNLOAD (DBOX) : " + renderFile.getName());
//            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
//            InputStream in = new BufferedInputStream((download.openStream()));
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//
//            FileOutputStream fileOut = new FileOutputStream(renderFile);
//            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//            fileOut.flush();
//            fileOut.close();
//            rbc.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return renderFile;
//    }
//
//    public File downloadRenderfileFromGDrive(String url, File renderfileDir, String filename) {
//        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
//        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
//        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
//        try{
//            URL download=new URL(gdriveURL);
//            System.out.println("TASK | DOWNLOAD (GDRIVE) : " + download.openStream());
//            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
//            FileOutputStream fileOut = new FileOutputStream(renderFile);
//            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//            fileOut.flush();
//            fileOut.close();
//            rbc.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return renderFile;
//    }
}
