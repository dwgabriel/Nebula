package nebula.nebulaserver;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Created by Daryl Wong on 7/21/2019.
 */
public class BlenderTask implements Serializable {

    public int totalNumberOfSubtasks;                                                        // Total number of subtasks across ALL Frames
    public int frameCount;
    private ArrayList<blenderFrameTask> frameTasks = new ArrayList<>();
    private LinkedHashMap<String, String> taskParamsMap;

    public BlenderTask (LinkedHashMap<String, String> taskParamsMap,
                        File subtaskDir) throws IOException {

        this.taskParamsMap = taskParamsMap;

        createFrameTasks(taskParamsMap.get("taskID"),
                taskParamsMap.get("frameCategory"),
                Integer.parseInt(taskParamsMap.get("startFrame")),
                Integer.parseInt(taskParamsMap.get("endFrame")),
                taskParamsMap.get("application"),
                taskParamsMap.get("renderOutputType"),
                taskParamsMap.get("renderfileName"),
                Integer.parseInt(taskParamsMap.get("divisionRate")),
                subtaskDir);
    }

    public int createFrameTasks (String taskID,
                                 String frameCategory,
                                 int startFrame,
                                 int endFrame,
                                 String application,
                                 String renderOutputType,
                                 String renderfileName,
                                 int divisionRate,
                                 File subtaskDir
    ) throws IOException {

        frameCount = endFrame - startFrame + 1;
        int multiplier = calculateMultiplier(divisionRate);
        int subtaskCountPerFrame = multiplier*multiplier;
        setTotalNumberOfSubtasks(subtaskCountPerFrame * frameCount);  // (number of subtasks per frame) * number of frames

        System.out.println("BLEND TASK | Generating tile scripts for " + taskID);

        for (int i = startFrame; i < endFrame + 1; i++) {
            String frameID = String.format("%s_%03d_%03d", taskID, Integer.valueOf(frameCount), i);
            File[] tileScripts = generateTileSplitScript(subtaskDir, frameID, renderOutputType, multiplier);
            System.out.println("BLEND TASK | TILE SCRIPTS : " + tileScripts.length);
            blenderFrameTask frameTask = new blenderFrameTask(frameID,
                    taskID,
                    frameCategory,
                    i,
                    subtaskCountPerFrame,
                    application,
                    renderfileName,
                    tileScripts);

            frameTasks.add(frameTask);
            System.out.println("BLEND TASK | " + frameID + " has been added to frameTasks. frameTasks Size : " + frameTasks.size());
        }
        System.out.println("BLEND TASK | Subtasks Created.");

        return frameCount;
    }

    public int calculateMultiplier(int divisionRate) {
//            long optimalSize = 100000; // 100 kb for optimal file transfer and downloading between Node and Server
//        long sizePerFrame = renderfile.length() / frameCount;
//            long optimized = sizePerFrame / (sizePerFrame / computeRate);

        int multiplier = 0;
        if (taskParamsMap.get("renderOutputType").equals("targa")) {
            multiplier = 1;
        } else {
            if (divisionRate >= 1) {
                multiplier = (int) Math.sqrt(divisionRate);
            }
        }
        return multiplier;
    }

    public File[] generateTileSplitScript(File outputDir, String frameID, String renderOutputType, int multiplier)   // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
            throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders
        TileBorder[] tileBorders = calcTileBorders(multiplier);
        File[] allScripts = new File[tileBorders.length];

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("%s_%03d_%s", frameID, tileCounter++, "TS").concat(".txt");
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

    public TileBorder[] calcTileBorders(int multiplier) {

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

    public ArrayList<blenderFrameTask> getFrameTaskQueue() {
        return frameTasks;
    }

    public void setTotalNumberOfSubtasks(int numberOfSubtasks) {
        this.totalNumberOfSubtasks = numberOfSubtasks;
    }

    public class blenderFrameTask {
        private String frameID;
        private String taskID;
        private String frameCategory;
        private int frame;
        private int subtaskCount;
        private String application;
        private String renderfileName;
        private File[] tileScripts;
        private ArrayList<blenderSubtask> subtaskQueue = new ArrayList<>();

        public blenderFrameTask(String frameID,
                                String taskID,
                                String frameCategory,
                                int frame,
                                int subtaskCount,
                                String application,
                                String renderfileName,
                                File[] tileScripts) throws IOException {

            this.frameID = frameID;
            this.taskID = taskID;
            this.frameCategory = frameCategory;
            this.frame = frame;
            this.subtaskCount = subtaskCount;
            this.application = application;
            this.renderfileName = renderfileName;
            this.tileScripts = tileScripts;
            createSubtasks();
        }

        public void createSubtasks() throws IOException {
            int counter = 1;

            System.out.println("BLEND TASK | Creating Subtasks . . .");
            // Based on the number of subtasks, the tileScripts are generated accordingly for Nodes to render.
            for (int i = 0; i < subtaskCount; i++) {
                String subtaskID = String.format("%s_%03d", frameID, counter);
                blenderSubtask subtask = new blenderSubtask(taskID,
                        subtaskID,
                        frameCategory,
                        frame,
                        renderfileName,
                        application,
                        tileScripts[i].getAbsoluteFile());

                subtaskQueue.add(subtask);
                counter++;
            }
        }

        public ArrayList<blenderSubtask> getSubtaskQueue() {
            return subtaskQueue;
        }

        public int getSubtaskCount() {
            return subtaskCount;
        }
    } // END OF FRAME_TASK CLASS

    public class blenderSubtask {

        private String taskID;
        private String subtaskID;
        private String frameCategory;
        private int frame;  // Frame to be rendered
        private String renderfileName;
        private String application;
        private File tileScript;

        public blenderSubtask(String taskID,
                              String subtaskID,
                              String frameCategory,
                              int frame,
                              String renderfileName,
                              String application,
                              File tileScript
        ) {
            this.taskID = taskID;
            this.subtaskID = subtaskID;
            this.frameCategory = frameCategory;
            this.frame = frame;
            this.renderfileName = renderfileName;
            this.application = application;
            this.tileScript = tileScript;
        }

        public String getSubtaskID() {
            return subtaskID;
        }

        public String getRenderfileName() {
            return renderfileName;
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