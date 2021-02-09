package nebula.nebulaserver;

import javax.imageio.ImageIO;
import javax.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompletedRenderTask {

    String taskID;
    String userEmail;
    String application;
    int frameCount;
    String startFrame;
    String endFrame;
    String renderOutputType;
    int subtaskCount;
    String frameCategory;
    String computeMinutes;
    String cost;
    private LinkedHashMap<String, String> resultParamsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, ArrayList> taskCosts = new LinkedHashMap<>();

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    private File taskDir;
    private File resultsDir;
    private File finalResultsDir;
    private File frameResultsDir;
    private File frameDir;

    public CompletedRenderTask(LinkedHashMap<String, String> resultParamsMap) {

        this.taskID = resultParamsMap.get("taskID");
        this.userEmail = resultParamsMap.get("userEmail");
        this.application = resultParamsMap.get("application");
        this.frameCount = Integer.valueOf(resultParamsMap.get("frameCount"));
        this.startFrame = resultParamsMap.get("startFrame");
        this.endFrame = resultParamsMap.get("endFrame");
        this.renderOutputType = resultParamsMap.get("renderOutputType");
        this.subtaskCount = Integer.valueOf(resultParamsMap.get("subtaskCount"));
        this.frameCategory = resultParamsMap.get("frameCategory");
        this.computeMinutes = resultParamsMap.get("computeMinutes");
        this.cost = resultParamsMap.get("cost");
        this.resultParamsMap = resultParamsMap;

        taskDir = new File(taskDatabase, taskID);
        resultsDir = new File(taskDir, "results");
        finalResultsDir = new File(resultsDir, "finalResults");
        frameResultsDir = new File(resultsDir, "frameResults");
    }

    public boolean checkResultsDatabase(String taskID, String frameID) {
        boolean resultDatabaseChecked = false;
        frameDir = new File(frameResultsDir, frameID);

        if (taskDir.exists() && resultsDir.exists() && finalResultsDir.exists() && frameResultsDir.exists()) {

            if (!frameDir.exists()) {
                frameDir.mkdir();
                resultDatabaseChecked = true;
                System.out.println(taskID + " | " + frameDir.getName() + " directory created. Path : " + frameDir.getAbsolutePath());
            } else {
                resultDatabaseChecked = true;
                System.out.println(taskID + " | " + frameDir.getName() + " already exists. Path : " + frameDir.getAbsolutePath());
            }
        } else {
            System.out.println("[ERROR] Task Directories not created properly. Please check.");
            System.out.println("[ERROR] Task Dir : " + taskDir.getAbsolutePath());
            System.out.println("[ERROR] Results Dir : " + resultsDir.getAbsolutePath());
            System.out.println("[ERROR] Final Results Dir : " + finalResultsDir.getAbsolutePath());
            System.out.println("[ERROR] Frame Results Dir : " + frameResultsDir.getAbsolutePath());
        }

        return resultDatabaseChecked;
    }

    public File addToResults(Part resultFilePart, String fileName, String frameID) throws IOException {
        File finalResult = null;
        System.out.println("CompletedRenderTask | CHECK 1");
        if (checkResultsDatabase(taskID, frameID)) {
            copyFileToResultsDir(resultFilePart, frameDir, finalResultsDir, fileName);
            finalResult = resultsCheckpoint(taskID, frameID, frameDir, finalResultsDir);
        } else {
            System.out.println("[ERROR] Results Database not created properly.");
        }

        return finalResult;
    }

    private void copyFileToResultsDir(Part resultFile, File frameDir, File finalResults, String fileName) throws IOException {
        System.out.println("CompleteRenderTask | CHECK 2");
        File result = null;
        InputStream inputStream = resultFile.getInputStream();

        if (subtaskCount == 1) {
            if (finalResults.length() == 0 || !checkIfExists(fileName, listOfResultFiles(finalResults))) {
                result = new File(finalResults, fileName);
            } else {
                System.out.println("[ERROR] " + fileName + " already exists in the Final Results directory. Existing file will be replaced.");
            }

        } else if (subtaskCount > 1) {
            if (frameDir.length() == 0 || !checkIfExists(fileName, listOfResultFiles(frameDir))) {
                result = new File(frameDir, fileName);
            } else {
                System.out.println("[ERROR] " + fileName + " already exists in the Frame Results directory. Existing file will be replaced.");
            }
        }
        Files.copy(inputStream, result.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println(fileName + " saved to results directory.");

    }

    private boolean checkIfExists(String fileName, List<File> fileList) {
        boolean exists = false;

        if (!fileList.isEmpty()) {
            for (int i = 0; i < fileList.size(); i++) {
                String name = fileList.get(i).getName();

                if (name.equals(fileName)) {
                    exists = true;
                }
            }
        }
        return exists;
    }

    private File zipFiles(String taskID, List<File> finalResultList, File fileDest) {
        File zippedFile = null;
        if (!finalResultList.isEmpty()) {
            try {
                String[] filesToZip = new String[finalResultList.size()];
                for (int i = 0; i < finalResultList.size(); i++) {               // TaskID_TotalFrame_Frame_TileCount
                    File frameFile = finalResultList.get(i).getAbsoluteFile();
                    filesToZip[i] = frameFile.getAbsolutePath();
                    System.out.println(frameFile.getName() + " added to Zip File Array");
                }

                String zipFileName = taskID.concat(".zip");
                System.out.println("Zip File Name : " + zipFileName);
                zippedFile = new File(fileDest.getAbsolutePath(), zipFileName);

                FileOutputStream fos = new FileOutputStream(zippedFile);
                ZipOutputStream zos = new ZipOutputStream(fos);

                for (String aFile : filesToZip) {
                    zos.putNextEntry(new ZipEntry(new File(aFile).getName()));

                    byte[] bytes = Files.readAllBytes(Paths.get(aFile));
                    zos.write(bytes, 0, bytes.length);
                    zos.closeEntry();
                }
                zos.close();

            } catch (FileNotFoundException ex) {
                System.err.println("A file does not exist: " + ex);
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex);
            }
        } else {
            System.out.println("[ERROR] Final Result List empty.");
        }
        return zippedFile;
    }

    public List<File> listOfResultFiles(File results) {
        File[] files;
        if (results.length() > 0) {
            files = results.listFiles();
            System.out.println("CompletedRenderTask | listOfResultFiles_length : " + files.length);
            Arrays.sort(files);
        } else {
            System.out.println("CompletedRenderTask | listOfResultFiles_length : 0" );
            return null;
        }

        return Arrays.asList(files);
    }

    public File compileFinalResult(File finalResultsDir) {
        List<File> finalResultsDirList = listOfResultFiles(finalResultsDir);

        String[] filesToZip = new String[finalResultsDirList.size()];
        for (int i = 0; i < finalResultsDirList.size(); i++) {               // TaskID_TotalFrame_Frame_TileCount
            File resultFile = finalResultsDirList.get(i).getAbsoluteFile();
            filesToZip[i] = resultFile.getAbsolutePath();
            System.out.println(resultFile.getName() + " added to Zip File Array");
        }
        File finalResult = zipFiles(resultParamsMap.get("taskID"), finalResultsDirList, resultsDir);

        return finalResult;
    }

    public File resultsCheckpoint (String taskID, String frameID, File frameDir, File finalResultsDir) {
        File finalResult = null;
        System.out.println("CompletedRenderTask | Results CheckPoint");

            if (resultParamsMap.get("frameCategory").equals("multiFrame") && subtaskCount == 1) {
                if (finalResultsCheckpoint(taskID, finalResultsDir)) {
                    finalResult = compileFinalResult(finalResultsDir);
                    System.out.println("CompletedRenderTask | Final Result : " + finalResult.getAbsolutePath());
                }

            } else if (resultParamsMap.get("frameCategory").equals("multiFrame") && subtaskCount > 1) {

                if (frameResultsCheckpoint(frameID, frameDir)) {
                    compositeTiles(frameDir,
                            subtaskCount,
                            finalResultsDir,
                            frameID,
                            resultParamsMap.get("renderOutputType"));

                    if (finalResultsCheckpoint(taskID, finalResultsDir)){
                        if (finalResultsCheckpoint(taskID, finalResultsDir)) {
                            finalResult = compileFinalResult(finalResultsDir);
                            System.out.println("CompletedRenderTask | Final Result : " + finalResult.getAbsolutePath());
                        }
                    }
                }

            } else if (resultParamsMap.get("frameCategory").equals("singleFrame")) {

                if (frameResultsCheckpoint(frameID, frameDir)) {
                    compositeTiles(frameDir,
                            subtaskCount,
                            finalResultsDir,
                            taskID,
                            resultParamsMap.get("renderOutputType"));

                    finalResult = compileFinalResult(finalResultsDir);
                    System.out.println("CompletedRenderTask | Final Result : " + finalResult.getAbsolutePath());
                }
            } else {
                System.out.println("[ERROR] Frame Category undetected.");
            }
        //

        return finalResult;
    }

    public boolean frameResultsCheckpoint (String frameID, File frameDir) {
        boolean frameResultsChecked = false;
        List<File> frameDirList = listOfResultFiles(frameDir);

        System.out.println(" FRAME RESULT LIST CHECKPOINT -----------------------------");

        if (frameDirList.size() > Integer.valueOf(resultParamsMap.get("subtaskCount"))) {
            System.out.println("Frame Result List : " + frameDirList.size());
            System.out.println("ERROR : TOO MANY TILES (Frame Result list is greater than expected Subtask Count).");

        } else if (frameDirList.size() < Integer.valueOf(resultParamsMap.get("subtaskCount"))) {
            System.out.println("Frame Result List : " + frameDirList.size());
            System.out.println("Number of TILES to go before compositing FRAME (" + frameID + ") : " + (Integer.valueOf(resultParamsMap.get("subtaskCount")) - frameDirList.size()));

        } else if (frameDirList.size() == Integer.valueOf(resultParamsMap.get("subtaskCount"))) {
            System.out.println("Frame Result List : " + frameDirList.size());
            frameResultsChecked = true;

        }

        return frameResultsChecked;
    }

    public boolean finalResultsCheckpoint(String taskID, File finalResultsDir) {
        System.out.println(" FINAL RESULT LIST CHECKPOINT -----------------------------");
        boolean finalResultsChecked = false;
        List<File> finalResultsDirList = listOfResultFiles(finalResultsDir);

        if (finalResultsDirList.size() > Integer.valueOf(resultParamsMap.get("frameCount"))) {
            System.out.println("Final Result List : " + finalResultsDirList.size());
            System.out.println("ERROR : TOO MANY FRAMES (Final Result list is greater than expected Frame Count).");

        } else if (finalResultsDirList.size() < Integer.valueOf(resultParamsMap.get("frameCount"))) {
            System.out.println("Final Result List : " + finalResultsDirList.size());
            System.out.println("Number of FRAMES to go before completing TASK (" + taskID + ") : " + (Integer.valueOf(resultParamsMap.get("frameCount")) - finalResultsDirList.size()));

        } else if (finalResultsDirList.size() == Integer.valueOf(resultParamsMap.get("frameCount"))) {
            System.out.println("Final Result List : " + finalResultsDirList.size());
            System.out.println(resultParamsMap.get("taskID") + " IS COMPLETED. RETURNING RESULTS TO " + resultParamsMap.get("userEmail") + " NOW . . . ");

            finalResultsChecked = true;
        }

        return finalResultsChecked;
    }

    public String calculateTotalCost(String taskID) {
        double totalCost = 0;
        DecimalFormat costFormat = new DecimalFormat("##.##");
        ArrayList<ResultReceiver.SubtaskCosts> subtaskCosts = taskCosts.get(taskID);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalCost += subtaskCosts.get(i).cost;
            System.out.println(subtaskCosts.get(i).subtaskID + " : " + subtaskCosts.get(i).cost);
        }

        return costFormat.format(totalCost);
    }

    public String calculateTotalComputeTime(String taskID) {
        double totalComputeTime = 0;
        DecimalFormat timeFormat = new DecimalFormat("#.##");
        ArrayList<ResultReceiver.SubtaskCosts> subtaskCosts = taskCosts.get(taskID);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalComputeTime += Double.parseDouble(subtaskCosts.get(i).computeMinutes);
        }

        return timeFormat.format(totalComputeTime);
    }

    public File compositeTiles(File frameDir,
                               int tileCount,
                               File outputFile,
                               String outputFileName,
                               String renderOutputType) {

        File compositedFrame = null;

        System.out.println("Compositing tiles . . . ");
        long start = System.currentTimeMillis();
        // first check that it has all images
        File[] inputFiles = frameDir.listFiles();
        Arrays.sort(inputFiles);
        for (int i = 0; i < tileCount; i++) {
            System.out.println("Compositing tile " + i + " : " + inputFiles[i].getName());
            if (!inputFiles[i].isFile()) {

                System.out.println("[ERROR] Expected tile file doesn't exist: " + inputFiles[i].getAbsolutePath());
                return null;
            }
        }

        try {
            int divisions = (int)Math.sqrt(inputFiles.length);
            int numImage = 0;

            // create an array of BufferedImages from the input files inverting the order in the rows
            // (it's cropped from bottom to top but it's composited from top to bottom)
            System.out.println("INPUT FILES LENGTH : " + inputFiles.length + " (compositeTiles - Line 545)");
            BufferedImage[] bufferedImages = new BufferedImage[inputFiles.length];
            for (int row = divisions - 1; row >= 0; row--)
                for (int order = 0; order < divisions; order++)
                    bufferedImages[numImage++] = ImageIO.read(inputFiles[row*divisions + order]);

            BufferedImage image = combineImages(bufferedImages);
            compositedFrame = new File(outputFile,  outputFileName + "." + renderOutputType);
            boolean writeComplete = false;
            while (!writeComplete) {
                System.out.println("Writing image to " + outputFile + " . . . ");
                writeComplete = ImageIO.write(image, renderOutputType, compositedFrame);
            }
            System.out.println("COMPOSITION CHECK | Composited Frame File Size : " + compositedFrame.length());


        } catch (IOException ex) {
            System.out.println("[ERROR] Failed during tile compositing: "  + ex.getMessage());
            return null;
        }
        cleanup(frameDir, inputFiles);

        System.out.println("Composited " + Integer.toString(tileCount) +
                " tiles in (ms): " +
                Long.toString(System.currentTimeMillis() - start));
        return compositedFrame;
    }

    private BufferedImage combineImages(BufferedImage bufferedImages[]) {
        int divisions = (int)Math.sqrt(bufferedImages.length);
        int actualImage = 0;
        // first we establish the width and height of the final image
        int finalWidth = 0;
        int finalHeight = 0;
        System.out.println("BUFFERED IMAGE LENGTH : " + bufferedImages.length + " (combineImages - Line 613)");

        for (int i = 0; i < divisions; i++) {
            finalWidth += bufferedImages[i].getWidth();
            finalHeight += bufferedImages[i * divisions].getHeight();
        }

        System.out.println("Combining images . . . ");
        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
        System.out.println("Combining images (0) . . . ");
        int rowWidth = 0;
        int rowHeight = 0;

        try {
            for (int heightImage = 0; heightImage < divisions; heightImage++) {
                for (int widthImage = 0; widthImage < divisions; widthImage++) {
                    // check every image
                    if (bufferedImages[actualImage] == null) {
                        System.out.println("[ERROR] BufferedImages element has null parameter");
                        return null;
                    }
                    // adding to the final image
                    finalImg.createGraphics().drawImage(bufferedImages[actualImage], rowWidth, rowHeight, null);
                    rowWidth += bufferedImages[actualImage].getWidth();
                    actualImage++;
                }
                System.out.println("Combining images (" + heightImage + ") . . . ");
                // after processing the row we get the height of the last processed image
                // (it's the same for all in the row) and locate at the begining of the row
                rowHeight += bufferedImages[actualImage - 1].getHeight();
                rowWidth = 0;
            }

            System.out.println("COMBINING IMAGES COMPLETE.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return finalImg;
    }

    private void cleanup(File tileDir, File[] inputFiles) {
        for(File f: inputFiles) {
            if(!f.delete()) {
                System.out.println("[ERROR] Unable to delete tmp tile file: " +
                        f.getAbsolutePath());
            }
        }
        if(!tileDir.delete()) {
            System.out.println("[ERROR] Unable to delete tmp tile dir: " +
                    tileDir.getAbsolutePath());
        }
    }
}
