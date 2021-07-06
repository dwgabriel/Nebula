package nebula.nebulaserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class VrayTask {

    public int totalNumberOfSubtasks;                                                        // Total number of subtasks across ALL Frames
    public int frameCount;

    private ArrayList<vrayFrameTask> frameTasks = new ArrayList<>();
    private LinkedHashMap<String, String> taskParamsMap;

    public VrayTask(LinkedHashMap<String, String> taskParamsMap,
                    File subtaskDir) throws IOException {

        this.taskParamsMap = taskParamsMap;
        createFrameTasks(taskParamsMap.get("taskID"),
                taskParamsMap.get("frameCategory"),
                Integer.parseInt(taskParamsMap.get("startFrame")),
                Integer.parseInt(taskParamsMap.get("endFrame")),
                taskParamsMap.get("application"),
                taskParamsMap.get("renderOutputType"),
                taskParamsMap.get("renderfileName"),
                subtaskDir,
                Integer.parseInt(taskParamsMap.get("divisionRate")));
    }

    public int createFrameTasks (String taskID,
                                 String frameCategory,
                                 int startFrame,
                                 int endFrame,
                                 String application,
                                 String renderOutputType,
                                 String renderfileName,
                                 File subtaskDir,
                                 int divisionRate
    ) throws IOException {

        frameCount = endFrame - startFrame + 1;
        int multiplier = (int) Math.sqrt(divisionRate);
        int subtaskCountPerFrame = multiplier*multiplier;
        setTotalNumberOfSubtasks(subtaskCountPerFrame * frameCount);  // (number of subtasks per frame) * number of frames

        System.out.println("VRAY TASK | Generating tile scripts for " + taskID);
        for (int i = startFrame; i < endFrame + 1; i++) {
            String frameID = String.format("%s_%03d_%03d", taskID, Integer.valueOf(frameCount), i);
            File[] tileScripts = generateTileSplitScript(subtaskDir, frameID, multiplier);
            vrayFrameTask frameTask = new vrayFrameTask(frameID,
                    taskID,
                    frameCategory,
                    renderOutputType,
                    i,
                    subtaskCountPerFrame,
                    application,
                    renderfileName,
                    tileScripts);

            frameTasks.add(frameTask);
            System.out.println("VRAY TASK | " + frameID + " has been added to frameTasks. frameTasks Size : " + frameTasks.size());
        }
        System.out.println("VRAY TASK | Subtasks Created.");

        return frameCount;
    }

    public File[] generateTileSplitScript(File outputDir, String frameID, int multiplier)
            throws IOException {
        TileBorder[] tileBorders = calcTileBorders(multiplier);
        File[] allScripts = new File[tileBorders.length];

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("%s_%03d_%s", frameID, tileCounter++, "TS").concat(".txt");
            File script = new File(outputDir, scriptName);

            float top = tileBorder.getTop();
            float left = tileBorder.getLeft();
            float right = tileBorder.getRight();
            float bottom = tileBorder.getBottom();

            PrintWriter fout = new PrintWriter(new FileWriter(script));
            fout.println(top + "," + left + "," + right + "," + bottom);
            fout.flush();
            fout.close();
            allScripts[i] = script;
        }
        return allScripts;
    }

    public TileBorder[] calcTileBorders(int multiplier) {
        System.out.println("MULTIPLIER : " + multiplier);
        TileBorder[] tileBorders = new TileBorder[multiplier * multiplier];
        System.out.println("TILE BORDER LENGTH : " + tileBorders.length);
        float chunk = (float) 1 / (float) multiplier;

        int t = 0;
        float left, bottom, right, top;
//        float leftV = 0, bottomV = 0, rightV = 0, topV = 0;
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
//                topV = (float) VrayTask.round(top*renderHeight, 0);
//                leftV = (float) VrayTask.round(left*renderWidth, 0);
//                rightV = (float) VrayTask.round(right*renderWidth, 0);
//                bottomV = (float) VrayTask.round(bottom*renderHeight, 0);

//                System.out.println("top : " + top + ", left : " + left + ", right : " + right + ", bottom : " + bottom);
//                System.out.println("topV : " + topV + ", leftV : " + leftV + ", rightV : " + rightV + ", bottomV : " + bottomV);
//                System.out.println("-----------------------------------------------------");

                tileBorders[t] = new TileBorder(left, right, bottom, top);
                t++;
            }
        }
        return tileBorders;
    }


    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public ArrayList<vrayFrameTask> getFrameTaskQueue() {
        return frameTasks;
    }

    public void setTotalNumberOfSubtasks(int numberOfSubtasks) {
        this.totalNumberOfSubtasks = numberOfSubtasks;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public class vrayFrameTask {
        private String frameID;
        private String taskID;
        private String frameCategory;
        private String renderOutputType;
        private int frame;
        private int subtaskCount;
        private String application;
        private String renderfileName;
        private File[] tileScripts;
        private ArrayList<vraySubtask> subtaskQueue = new ArrayList<>();

        public vrayFrameTask(String frameID,
                                String taskID,
                                String frameCategory,
                                String renderOutputType,
                                int frame,
                                int subtaskCount,
                                String application,
                                String renderfileName,
                                File[] tileScripts) throws IOException {

            this.frameID = frameID;
            this.taskID = taskID;
            this.frameCategory = frameCategory;
            this.renderOutputType = renderOutputType;
            this.frame = frame;
            this.subtaskCount = subtaskCount;
            this.application = application;
            this.renderfileName = renderfileName;
            this.tileScripts = tileScripts;
            createSubtasks();
        }

        public void createSubtasks() throws IOException {
            int counter = 1;

            System.out.println("VRAY TASK | Creating Subtasks . . .");
            // Based on the number of subtasks, the tileScripts are generated accordingly for Nodes to render.
            for (int i = 0; i < subtaskCount; i++) {
                String subtaskID = String.format("%s_%03d", frameID, counter);
                vraySubtask subtask = new vraySubtask(taskID,
                        subtaskID,
                        frameCategory,
                        renderOutputType,
                        frame,
                        renderfileName,
                        application,
                        tileScripts[i].getAbsoluteFile());

                subtaskQueue.add(subtask);
                counter++;
            }
        }

        public ArrayList<vraySubtask> getSubtaskQueue() {
            return subtaskQueue;
        }

        public int getSubtaskCount() {
            return subtaskCount;
        }
    } // END OF FRAME_TASK CLASS

    public class vraySubtask {

        private String taskID;
        private String subtaskID;
        private String frameCategory;
        private String renderOutputType;
        private int frame;  // Frame to be rendered
        private String renderfileName;
        private String application;
        private File tileScript;

        public vraySubtask (String taskID,
                              String subtaskID,
                              String frameCategory,
                              String renderOutputType,
                              int frame,
                              String renderfileName,
                              String application,
                              File tileScript
        ) {
            this.taskID = taskID;
            this.subtaskID = subtaskID;
            this.frameCategory = frameCategory;
            this.renderOutputType = renderOutputType;
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
