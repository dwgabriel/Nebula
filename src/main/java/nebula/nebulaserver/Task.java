package nebula.nebulaserver;

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
    File originalTaskDir;
    File resultsDir;
    File finalResultsDir;
    File frameResultsDir;

    private LinkedHashMap<String, String> taskParamsMap;
    Collection<File> subdirCollection = new LinkedList<>();

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
        originalTaskDir = new File(taskDir, "originaltask");
        resultsDir = new File(taskDir, "results");
        finalResultsDir = new File(resultsDir, "finalResults");
        frameResultsDir = new File(resultsDir, "frameResults");

        subtaskDir.mkdir();
        originalTaskDir.mkdir();
        resultsDir.mkdir();
        finalResultsDir.mkdir();
        frameResultsDir.mkdir();

        System.out.println(taskID + " | taskDir : " + taskDir.exists());
        System.out.println(taskID + " | subtaskDir : " + subtaskDir.exists());
        System.out.println(taskID + " | originalTaskDir : " + originalTaskDir.exists());
        System.out.println(taskID + " | resultsDir : " + resultsDir.exists());
        System.out.println(taskID + " | finalResultsDir : " + finalResultsDir.exists());
        System.out.println(taskID + " | frameResultsDir : " + frameResultsDir.exists());
    }

    // This method checks for the chosen application map and create the relative tasks accordingly with the right nested task class.
    // Created tasks should package subtasks in taskDirectory to be ready for scheduling.
    public void createTasks(LinkedHashMap<String, String> taskParamsMap) throws IOException {
        try {
            System.out.println("TASK | Checking application . . .");
            System.out.println("TASK | Application : " + taskParamsMap.get("application"));
            String application = taskParamsMap.get("application");
            if (application.contains("blender")) {
                System.out.println("TASK | Blender detected. Creating Blender Task . . .");
                createBlenderTask(taskParamsMap);
            } else {
                // 2. Create VRAY Task Objects for reference
                // createVrayTask(taskParamsMap);
                // - Download VrayTaskFile from URL or WebUpload

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                subtaskDir);

        BlenderTask.blenderFrameTask blenderFrameTask;
        BlenderTask.blenderSubtask blenderSubtask;

        try {
//            setMetadata("Task-Params", blendFile.toPath());
            // The jsonTaskParams is the same JsonString sent from nebula.my that includes these Task Parameters for Blender :
            // Params : TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType

            for (int j = 0; j< blenderTask.getFrameTaskQueue().size(); j++) {
                blenderFrameTask = blenderTask.getFrameTaskQueue().get(j);

                for (int i = 0; i < blenderFrameTask.getSubtaskQueue().size(); i++) {
                    blenderSubtask = blenderFrameTask.getSubtaskQueue().get(i);
                    String this_originalTaskFileName = blenderSubtask.getOriginalTaskFile().getName();
                    String this_tileScriptName = blenderSubtask.getTileScript().getName();
                    String this_subtaskID = blenderSubtask.getSubtaskID();
                    String this_renderFrame = String.valueOf(blenderSubtask.getFrame());
                    String this_subtaskCount = Integer.toString(blenderFrameTask.getSubtaskCount());
                    String this_frameCount = String.valueOf(blenderTask.frameCount);

                    StringBuilder blenderSubtaskParams = new StringBuilder(this_originalTaskFileName +
                                                                            "," + this_tileScriptName +
                                                                            "," + taskParamsMap.get("taskID") +
                                                                            "," + taskParamsMap.get("application") +
                                                                            "," + taskParamsMap.get("userEmail") +
                                                                            "," + taskParamsMap.get("shareLink") +
                                                                            "," + taskParamsMap.get("frameCategory") +
                                                                            "," + taskParamsMap.get("startFrame") +
                                                                            "," + taskParamsMap.get("endFrame") +
                                                                            "," + taskParamsMap.get("renderOutputType") +
                                                                            "," + this_subtaskID +
                                                                            "," + this_renderFrame +
                                                                            "," + this_frameCount +
                                                                            "," + this_subtaskCount);

                    if (blenderSubtask.getTileScript() != null) {
                        System.out.println("TASK | Tile Script Path : " + blenderSubtask.getTileScript().getAbsolutePath());
                        setMetadata("Subtask-Params", blenderSubtaskParams.toString(), blenderSubtask.getTileScript().toPath());
                        setMetadata("Subtask-ID", this_subtaskID, blenderSubtask.getTileScript().toPath());
                    } else {
                        System.out.println("[ERROR] Tile script null");
                    }

                    // The blenderSubtaskParams will operate much like the jsonTaskParams above, stringing together all subtask-specific parameters for Node to compute with.
                    // Params : OriginalTaskFileName (*insert TaskID*.blend), TaskID, UserEmail, ShareLink, Application, FrameCategory, StartFrame, EndFrame, RenderOutputType, SubtaskID, RenderFrame, SubtaskCount
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return blenderTask;
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

    private int getFileSizeInKB(URL url) {
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

    // ***** THE CODE BELOW IS A NESTED CLASS THAT CREATES RENDERING TASKS FOR BLENDER3D SOFTWARE
    public class BlenderTask implements Serializable {

        // COMMON PARAMETERS :
//    private final int multiplier = 0;        //TODO - DYNAMIC MULTIPLIER CALCULATOR                                                                           // Multiple that decides number of tiles. Fed as a parameter to calcTileBorders();
        private final String taskID;
        private final String userEmail;
        private final String application;

        // NEW TASK PARAMETERS :
        private int totalNumberOfSubtasks;                                                        // Total number of subtasks across ALL Frames
        private final int startFrame;
        private final int endFrame;
        private final int frameCount;
        private int computeRate;
        private String frameCategory;
        private String renderOutputType;
        private String shareLink;
        private final File originalTaskFile;
        private ArrayList<Task.BlenderTask.blenderFrameTask> frameTasks = new ArrayList<>();

        // NEW TASK         - TaskID, UserEmail, Application, numberOfSubtasks, frame, original_TaskFile, tileScripts, originalTaskDir, subtaskDir
        // Completed TASK   - TaskID, UserEmail, Application, numberOfSubtasks, frame, renderResults, Cost, ComputeHours

        public BlenderTask (String taskID,
                            String userEmail,
                            String application,
                            String frameCategory,
                            int startFrame,
                            int endFrame,
                            String renderOutputType,
                            String shareLink,
                            int computeRate,
                            File subtaskDir) throws IOException {

            this.taskID = taskID;
            this.userEmail = userEmail;
            this.application = application;
            this.frameCategory = frameCategory;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
            this.renderOutputType = renderOutputType;
            this.shareLink = shareLink;
            this.originalTaskFile = downloadBlendFileFromURL(shareLink, taskID, originalTaskDir);
            this.frameCount = this.endFrame - this.startFrame + 1;
            this.computeRate = computeRate;
            createFrameTasks(taskID, frameCategory, startFrame, endFrame, application, renderOutputType, originalTaskFile, subtaskDir, computeRate); //
        }

        public File downloadBlendfileFromDbox(String url, String taskID, File originalTaskDir, int renderfileLength) {
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

        public File downloadBlendfileFromGDrive(String url, String taskID, File originalTaskDir, int renderfileLength) {
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

        public File downloadBlendFileFromURL(String url, String taskID, File originalTaskDir) throws MalformedURLException {
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

        public String getFrameCategory() { return frameCategory; }

        public void setTotalNumberOfSubtasks(int numberOfSubtasks) {
            this.totalNumberOfSubtasks = numberOfSubtasks;
        }

        public ArrayList<Task.BlenderTask.blenderFrameTask> getFrameTaskQueue() {
            return frameTasks;
        }

        public int createFrameTasks (String taskID,
                                     String frameCategory,
                                     int startFrame,
                                     int endFrame,
                                     String application,
                                     String renderOutputType,
                                     File originalTaskFile,
                                     File subtaskDir,
                                     int computeRate
        ) throws IOException {

            int frameCount = endFrame - startFrame + 1;
//            int multiplier = calculateMultiplier(originalTaskFile, frameCount, computeRate);
            int subtaskCountPerFrame = computeRate;
            setTotalNumberOfSubtasks(subtaskCountPerFrame * frameCount);  // (number of subtasks per frame) * number of frames

            System.out.println("TASK | Generating tile scripts for " + taskID);
            for (int i = startFrame; i < endFrame + 1; i++) {
                String frameID = String.format("%s_%03d_%03d", taskID, Integer.valueOf(frameCount), i);
                File[] tileScripts = generateTileSplitScript(subtaskDir, frameID, i, renderOutputType, computeRate);
//                System.out.println("TASK | Tile Scripts : " + tileScripts.length + " | Path : " + tileScripts[i].getAbsolutePath());
                Task.BlenderTask.blenderFrameTask frameTask = new Task.BlenderTask.blenderFrameTask(frameID,
                        taskID,
                        frameCategory,
                        i,
                        subtaskCountPerFrame,
                        application,
                        originalTaskFile,
                        tileScripts);

                frameTasks.add(frameTask);
                System.out.println(frameID + " has been added to frameTasks. frameTasks Size : " + frameTasks.size());
            }
            System.out.println("Subtasks Created.");

            return frameCount;
        }

        public int calculateMultiplier(File originalTaskFile, int frameCount, int computeRate) {
//            long optimalSize = 100000; // 100 kb for optimal file transfer and downloading between Node and Server
            long sizePerFrame = originalTaskFile.length() / frameCount;
//            long optimized = sizePerFrame / (sizePerFrame / computeRate);

            int multiplier = (int) Math.sqrt(1);
            if (renderOutputType.equals("targa")) {
                multiplier = (int) Math.sqrt(1);
            } else {
                if (computeRate >= 1) {
                    multiplier = (int) Math.sqrt(computeRate);
                }
            }
            return multiplier;
        }

        public File[] generateTileSplitScript(File outputDir, String frameID, int frame, String renderOutputType, int computeRate)   // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
                throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders
            TileBorder[] tileBorders = calcTileBorders(computeRate);
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
//            fout.println("def enable_gpus(device_type, use_cpus=False):");
//            fout.println("    preferences = bpy.context.preferences");
//            fout.println("    cycles_preferences = preferences.addons[\"cycles\"].preferences");
//            fout.println("    cuda_devices, opencl_devices = cycles_preferences.get_devices()");
//            fout.println("    if device_type == \"CUDA\":");
//            fout.println("        devices = cuda_devices");
//            fout.println("    elif device_type == \"OPENCL\":");
//            fout.println("        devices = opencl_devices");
//            fout.println("    else:");
//            fout.println("        raise RuntimeError(\"Unsupported device type\")");
//            fout.println("    activated_gpus = []");
//            fout.println("    for device in devices:");
//            fout.println("        if device.type == \"CPU\":");
//            fout.println("          device.use = use_cpus");
//            fout.println("        else:");
//            fout.println("          device.use = True");
//            fout.println("          activated_gpus.append(device.name)");
//            fout.println("    cycles_preferences.compute_device_type = device_type");
//            fout.println("    bpy.context.scene.cycles.device = \"GPU\"");
//            fout.println("    return activated_gpus");
//            fout.println("enable_gpus(\"CUDA\")");

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

        public TileBorder[] calcTileBorders(int computeRate) {
            TileBorder[] tileBorders = new TileBorder[computeRate];
            float chunk = (float) 1 / (float) computeRate;

            int t = 0;
            float left, bottom, right, top;
            for (int y = 1; y < computeRate + 1; y++) {
                for (int x = 1; x < computeRate + 1; x++) {

                    //x coordinates
                    if (x == 1) {  //left border tile\
                        left = 0.0F;
                        right = chunk;
                    } else if (x == computeRate) { //right border tile
                        left = chunk * (computeRate - 1);
                        right = 1.0F;
                    } else {    //tile not on left or right border...
                        left = chunk * (float) (x - 1);
                        right = chunk * (float) x;
                    }

                    //y coordinates
                    if (y == 1) {  //bottom border tile
                        bottom = 0.0F;
                        top = chunk;
                    } else if (y == computeRate) { //top border tile
                        bottom = chunk * (computeRate - 1);
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

        public class blenderFrameTask {
            private String frameID;
            private String taskID;
            private String frameCategory;
            private int frame;
            private int subtaskCount;
            private String application;
            private File originalTaskFile;
            private File[] tileScripts;
            private ArrayList<Task.BlenderTask.blenderSubtask> subtaskQueue = new ArrayList<>();

            public blenderFrameTask(String frameID,
                                    String taskID,
                                    String frameCategory,
                                    int frame,
                                    int subtaskCount,
                                    String application,
                                    File originalTaskFile,
                                    File[] tileScripts) throws IOException {

                this.frameID = frameID;
                this.taskID = taskID;
                this.frameCategory = frameCategory;
                this.frame = frame;
                this.subtaskCount = subtaskCount;
                this.application = application;
                this.originalTaskFile = originalTaskFile;
                this.tileScripts = tileScripts;
                createSubtasks();
            }

            public ArrayList<Task.BlenderTask.blenderSubtask> getSubtaskQueue() {
                return subtaskQueue;
            }

            public int getSubtaskCount() {
                return subtaskCount;
            }

            public void createSubtasks() throws IOException {
                int counter = 1;

                System.out.println("Creating Subtasks . . .");
                // Based on the number of subtasks, the tileScripts are generated accordingly for Nodes to render.
                for (int i = 0; i < subtaskCount; i++) {
                    String subtaskID = String.format("%s_%03d", frameID, counter);
                    Task.BlenderTask.blenderSubtask subtask = new Task.BlenderTask.blenderSubtask(taskID,
                            subtaskID,
                            frameCategory,
                            frame,
                            originalTaskFile,
                            application,
                            tileScripts[i].getAbsoluteFile());

                    subtaskQueue.add(subtask);
                    counter++;
                }
            }
        }

        public class blenderSubtask {

            private String taskID;
            private String subtaskID;
            private String frameCategory;
            private int frame;  // Frame to be rendered
            private File originalTaskFile;
            private String application;
            private File tileScript;

            public blenderSubtask(String taskID,
                                  String subtaskID,
                                  String frameCategory,
                                  int frame,
                                  File originalTaskFile,
                                  String application,
                                  File tileScript
            ) {
                this.taskID = taskID;
                this.subtaskID = subtaskID;
                this.frameCategory = frameCategory;
                this.frame = frame;
                this.originalTaskFile = originalTaskFile;
                this.application = application;
                this.tileScript = tileScript;
            }

            public String getSubtaskID() {
                return subtaskID;
            }

            public File getOriginalTaskFile() {
                return originalTaskFile;
            }

            public String getApplication() {
                return application;
            }

            public File getTileScript() {
                return tileScript;
            }

            public String getFrameCategory() {
                return frameCategory;
            }

            public int getFrame() {
                return frame;
            }
        }
    }
    // ***** THE CODE ABOVE IS A NESTED CLASS THAT CREATES RENDERING TASKS FOR BLENDER3D SOFTWARE


}
