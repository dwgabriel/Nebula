package nebula.nebulaserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class VrayTask {

    // COMMON PARAMETERS :
//    private final int multiplier = 0;        //TODO - DYNAMIC MULTIPLIER CALCULATOR                                                                           // Multiple that decides number of tiles. Fed as a parameter to calcTileBorders();
    private final String taskID;
    private final String userEmail;
    private final String application;

    // NEW TASK PARAMETERS :
    public int totalNumberOfSubtasks;                                                        // Total number of subtasks across ALL Frames
    public final int startFrame;
    public final int endFrame;
    public final int frameCount;
    public int computeRate;
    public String frameCategory;
    public String renderOutputType;
    public String shareLink;
    public String renderfileName;
    public int renderHeight;
    public int renderWidth;
    private ArrayList<vrayFrameTask> frameTasks = new ArrayList<>();

    public VrayTask(String taskID,
                    String userEmail,
                    String application,
                    String frameCategory,
                    int startFrame,
                    int endFrame,
                    String renderOutputType,
                    String shareLink,
                    int computeRate,
                    String renderfileName,
                    String renderHeight,
                    String renderWidth,
                    File subtaskDir) throws IOException {

        this.taskID = taskID;
        this.userEmail = userEmail;
        this.application = application;
        this.frameCategory = frameCategory;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.renderOutputType = renderOutputType;
        this.shareLink = shareLink;
        this.renderfileName = renderfileName;
        this.frameCount = this.endFrame - this.startFrame + 1;
        this.computeRate = computeRate;
        this.renderHeight = Integer.parseInt(renderHeight);
        this.renderWidth = Integer.parseInt(renderWidth);
        createFrameTasks(taskID,
                frameCategory,
                startFrame,
                endFrame,
                application,
                renderOutputType,
                renderfileName,
                subtaskDir,
                computeRate);
    }

    public int createFrameTasks (String taskID,
                                 String frameCategory,
                                 int startFrame,
                                 int endFrame,
                                 String application,
                                 String renderOutputType,
                                 String renderfileName,
                                 File subtaskDir,
                                 int computeRate
    ) throws IOException {

        int frameCount = endFrame - startFrame + 1;
//            int multiplier = calculateMultiplier(renderfile, frameCount, computeRate);
        int subtaskCountPerFrame = computeRate;
        setTotalNumberOfSubtasks(subtaskCountPerFrame * frameCount);  // (number of subtasks per frame) * number of frames

        System.out.println("TASK | Generating tile scripts for " + taskID);
        for (int i = startFrame; i < endFrame + 1; i++) {
            String frameID = String.format("%s_%03d_%03d", taskID, Integer.valueOf(frameCount), i);
            File[] tileScripts = generateTileSplitScript(subtaskDir, frameID, computeRate);
                System.out.println("TASK | Tile Scripts : " + tileScripts.length + " | Path : " + tileScripts[i].getAbsolutePath());
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
            System.out.println(frameID + " has been added to frameTasks. frameTasks Size : " + frameTasks.size());
        }
        System.out.println("Subtasks Created.");

        return frameCount;
    }

    public File[] generateTileSplitScript(File outputDir, String frameID, int computeRate)   // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
            throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders
        TileBorder[] tileBorders = calcTileBorders(computeRate);
        File[] allScripts = new File[tileBorders.length];

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("%s_%s_%03d", frameID, "TS", tileCounter++).concat(".txt");
            File script = new File(outputDir, scriptName);

            int top = (int) tileBorder.getTop();
            int left = (int) tileBorder.getLeft();
            int right = (int) tileBorder.getRight();
            int bottom = (int) tileBorder.getBottom();

            PrintWriter fout = new PrintWriter(new FileWriter(script));
            fout.println(top + "," + left + "," + right + "," + bottom);
//            fout.println("vray -sceneFile=" + );

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
                float topV = (float) round(top*renderHeight, 0);
                float leftV = (float) round(left*renderWidth, 0);
                float rightV = (float) round(right*renderWidth, 0);
                float bottomV = (float) round(bottom*renderHeight, 0);

                tileBorders[t] = new TileBorder(leftV, rightV, bottomV, topV);
            }
            t++;
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

            System.out.println("Creating Subtasks . . .");
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
