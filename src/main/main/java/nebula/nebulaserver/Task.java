package nebula.nebulaserver;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Created by Daryl Wong on 7/21/2019.
 */
public class Task implements Serializable {

    private final int multiplier = 4;                                                                                   // Multiple that decides number of tiles. Fed as a parameter to calcTileBorders();
    private final String taskID;
    private final String username;
    private final String application;
    private int numberOfSubtasks = (multiplier * multiplier);                                                           // Also defined as number of tiles.
    private final int frame;
    private final File original_taskFile;
    private File[] tileScripts;
    private Path originalTaskDir;

    public Task(String taskID,
                String username,
                String application,
                int frame,
                File original_taskFile) {

        this.taskID = taskID;
        this.username = username;
        this.application = application;
        this.frame = frame;
        this.original_taskFile = original_taskFile;
    }

    public void setOriginalTaskDir(Path originalTaskDir) {
        this.originalTaskDir = originalTaskDir;
    }

    public Path getOriginalTaskDir() {
        return originalTaskDir;
    }

    public File getOriginal_taskFile() {
        return original_taskFile;
    }

    public String getTaskID() {
        return taskID;
    }

    public String getUsername() {
        return username;
    }

    public String getApplication() {
        return application;
    }

    public int getNumberOfSubtasks() {
        return numberOfSubtasks;
    }

    public int getFrame() {
        return frame;
    }

    public int getMultiplier() {return multiplier;}

    public File[] getTileScripts() {return tileScripts;}

    public void setNumberOfSubtasks(int numberOfSubtasks) {
        this.numberOfSubtasks = numberOfSubtasks;
    }

    public ArrayList<Subtask> createSubtasks (File taskDatabase, File outputDir) throws IOException {
        int subtask_counter = 1;
        ArrayList<Subtask> subtaskQueue = new ArrayList<>();
        ArrayList<String> allBlenderCL = generateBlenderCL(taskDatabase,outputDir);                                                                     // TODO - Join Subtasks with Scripts for Scheduling ********************************

        for (int i=0; i<numberOfSubtasks; i++) {
            String subtaskID = String.format("%s_%03d",taskID, subtask_counter++);

            Subtask subtask = new Subtask(subtaskID, i, original_taskFile, application, tileScripts[i], allBlenderCL.get(i));
            subtaskQueue.add(subtask);
        }
        return subtaskQueue;
    }

    public ArrayList<String> generateBlenderCL(File taskDatabase, File outputDir) throws IOException {

        tileScripts = generateBlenderScript(outputDir);
        File resultDir = new File(taskDatabase + "\\noderesults");

//        docker run -it --rm -v ~/desktop/test:/test ikester/blender test/bmwgpu.blend
//        --python test/thescript.py -o /test/frame_### -f 1

//        ----------------------------------------------------------------------------------------------------------------

//        docker run -it -v ~/Desktop/Nebula/Code/nebulaserver/nebuladatabase/tasks/054873/originaltask:/originaltask ikester/blender originaltask/blendertest.blend --python originaltask/054873_tileScript__001.py -o ~/Desktop/Nebula/Code/nebula
//        server/nebuladatabase/tasks/054873/noderesults/tile__001 -f 1

        ArrayList<String> alLBlenderCL = new ArrayList<>();

        for (int i=0; i<tileScripts.length; i++) {
            ArrayList<String> blenderCL = new ArrayList<>();
            String outputName = String.format("%s_%03d", "tile_", (i+1));
            File renderOutput = new File(resultDir, outputName);

            String nodeDir = "/c/Users/Daryl/desktop/nebula/code/nebulanode/taskcache";        // Bind Volume for TaskCache and  Results directory
            String destinationDir = "/results/";
            String bindVolume = String.format(nodeDir + ":" + destinationDir);

            blenderCL.add( "bin/bash");
            blenderCL.add( "-it");
            blenderCL.add( "--rm");
            blenderCL.add( "-v");
            blenderCL.add( bindVolume);
            blenderCL.add( "--rm");
            blenderCL.add( original_taskFile.getAbsolutePath());
            blenderCL.add( "--python");
            blenderCL.add( tileScripts[i].getAbsolutePath());
            blenderCL.add( "-o");
            blenderCL.add( renderOutput.getAbsolutePath());
            blenderCL.add( "-f");
            blenderCL.add( "1");                                                                       // TODO - Implement Multi-frame feature *****************************************

            String commandLine = String.format(blenderCL.iterator().toString());
//            String commandLine = String.format(blenderCL.get(0) + blenderCL.get(1) + blenderCL.get(2) + blenderCL.get(3) + blenderCL.get(4) + blenderCL.get(5) + blenderCL.get(6) + blenderCL.get(7) + blenderCL.get(8) +);
            System.out.println("Command Line : " + commandLine);
            alLBlenderCL.add(commandLine);
        }
        return alLBlenderCL;
    }

    public File[] generateBlenderScript (File outputDir)                                                               // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
            throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders

        TileBorder[] tileBorders = calcTileBorders(multiplier);
        File[] allScripts = new File[tileBorders.length];
        setNumberOfSubtasks(tileBorders.length);

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("%s_%s_%03d", taskID, "ts",  tileCounter++).concat(".py");
            File script = new File(outputDir, scriptName);

            PrintWriter fout = new PrintWriter(new FileWriter(script));
            fout.println("import bpy");
            fout.println("import os");
            fout.println("left = " + Float.toString(tileBorder.getLeft()));
            fout.println("right = " + Float.toString(tileBorder.getRight()));
            fout.println("bottom = " + Float.toString(tileBorder.getBottom()));
            fout.println("top = " + Float.toString(tileBorder.getTop()));
            fout.println("scene  = bpy.context.scene");
            fout.println("render = scene.render");
            fout.println("render.use_border = True");
            fout.println("render.use_crop_to_border = True");
            fout.println("render.image_settings.file_format = 'PNG'");
            fout.println("render.image_settings.color_mode = 'RGBA'");
            fout.println("render.use_file_extension = True");
            fout.println("render.border_max_x = right");
            fout.println("render.border_min_x = left");
            fout.println("render.border_max_y = top");
            fout.println("render.border_min_y = bottom");
            // from the location of the fetched file (blendcache) to ../tmp/
            fout.println("scene.frame_start = " + frame);
            fout.println("scene.frame_end = " + frame);
            // if it's not used this line, the scene uses their original parameters...
            fout.println("bpy.ops.render.render(animation=True)");
            fout.flush();
            fout.close();
            allScripts[i] = script;
        }
        return allScripts;
    }

    public static TileBorder[] calcTileBorders(int multiplier) {
        TileBorder[] tileBorders = new TileBorder[multiplier * multiplier];
        float chunk = (float) 1 / (float) multiplier;

        int t = 0;
        float left, bottom, right, top;
        for (int y = 1; y < multiplier + 1; y++) {
            for (int x = 1; x < multiplier + 1; x++) {

                //x coordinates
                if (x == 1) {  //left border tile
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

    public class Subtask {

        private String subtaskID;
        private int tileNumber;
        private File originalTaskFile;
        private String application;
        private File tileScript;
        private String blenderCL;


        public Subtask(String subtaskID,
                       int tileNumber,
                       File originalTaskFile,
                       String application,
                       File tileScript,
                       String blenderCL
                       ) {

            this.subtaskID = subtaskID;
            this.tileNumber = tileNumber;
            this.originalTaskFile = originalTaskFile;
            this.application = application;
            this.tileScript = tileScript;
            this.blenderCL = blenderCL;
        }

        public String getSubtaskID() {
            return subtaskID;
        }

        public int getTileNumber() {
            return tileNumber;
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

        public String getBlenderCL() {
            return blenderCL;
        }
    }
}
