package nebula.nebulaserver;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.imageio.ImageIO;
import javax.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

    // Creates the necessary databases for result Sorting, Compositing and Compiling.
    // The Results Database sits within the Task Dir and comprises of the Final Results Dir, Frame Results Dir, and the Frame Dir which sits within the Frame Results Dir and is not to be confused for one another.
    // Frame Results Dir serves as the database for all collective Frames, and the Frame Dir serves as the database of each rendered tile/subtask of a specified Frame.
    // This delegation is critical for Multi-Frame Renders.
    public boolean checkResultsDatabase(String taskID, String frameID) {
        boolean resultDatabaseChecked = false;
        frameDir = new File(frameResultsDir, frameID);

        if (taskDir.exists() && resultsDir.exists() && finalResultsDir.exists() && frameResultsDir.exists()) {

            if (!frameDir.exists()) {
                frameDir.mkdir();
                resultDatabaseChecked = true;
                System.out.println("COMPLETED RENDER | " + taskID + " - " + frameDir.getName() + " directory created. Path : " + frameDir.getAbsolutePath());
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
        if (checkResultsDatabase(taskID, frameID)) {
            copyFileToResultsDir(resultFilePart, frameDir, finalResultsDir, fileName);

            finalResult = resultsCheckpoint(taskID, frameID, frameDir, finalResultsDir);
        } else {
            System.out.println("[ERROR] Results Database not created properly.");
        }

        return finalResult;
    }

    // Copies the HttpRequest File Part to the respective Directory for processing later. The Directory is chosen on the basis of whether the Task has multiple Subtasks or not - .TGA tasks from Blender are not splittable.
    // Tasks with a single subtask is copied directly to the FinalResults Directory as there would be no further processing/compositing needed.
    // Tasks with multiple subtasks are copied to the FrameDirectory respective to the subtasks of the specific Frame to be composited into the final Frame later.
    private void copyFileToResultsDir(Part resultFile, File frameDir, File finalResultsDir, String fileName) throws IOException {
        File result = null;
        InputStream inputStream = resultFile.getInputStream();

        if (subtaskCount == 1) {
            if (finalResultsDir.length() == 0 || !checkIfExists(fileName, listOfResultFiles(finalResultsDir))) {
                result = new File(finalResultsDir, fileName);

                Files.copy(inputStream, result.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (unzip(result, finalResultsDir)) {
                    System.out.println("COMPLETED RENDER | " + fileName + " unzipped to results directory.");
                }
            } else {
                System.out.println("[ERROR] " + fileName + " already exists in the Final Results directory. Existing file will be replaced.");
            }
        } else if (subtaskCount > 1) {
            if (frameDir.length() == 0 || !checkIfExists(fileName, listOfResultFiles(frameDir))) {
                result = new File(frameDir, fileName);

                Files.copy(inputStream, result.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (unzip(result, frameDir)) {
                    System.out.println("COMPLETED RENDER | " + fileName + " unzipped to results directory.");
                }
            } else {
                System.out.println("[ERROR] " + fileName + " already exists in the Frame Results directory. Existing file will be replaced.");
            }
        }
    }

    private static boolean unzip(File file, File destDir) {
        boolean unzipped = false;
        FileInputStream fis;
        Charset utf8 = Charset.forName("UTF-8");
        Charset csLatin1 = Charset.forName("IBM437");

        byte[] buffer = new byte[(int) file.length()];
        try {
            ZipFile zipFile = new ZipFile(file, csLatin1);

            Enumeration<ZipEntry> entry = (Enumeration<ZipEntry>) zipFile.entries();
            while (entry.hasMoreElements()) {
                ZipEntry ze = entry.nextElement();
                String fileName = ze.getName();
                File newFile = new File(destDir, fileName);
                new File(newFile.getParent()).mkdirs();

                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(ze));
                FileOutputStream fos = new FileOutputStream(newFile);
                int length;
                while ((length = bis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.flush();
                fos.close();
            }

            unzipped = true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return unzipped;
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
                    System.out.println("COMPLETED RENDER | " + frameFile.getName() + " added to Zip File Array");
                }

                String zipFileName = taskID.concat(".zip");
                System.out.println("COMPLETED RENDER | Zip File Name : " + zipFileName);
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
                System.err.println("[ERROR] A file does not exist: " + ex);
            } catch (IOException ex) {
                System.err.println("[ERROR] I/O error: " + ex);
            }
        } else {
            System.out.println("[ERROR] Final Result List empty.");
        }
        return zippedFile;
    }

    public List<File> listOfResultFiles(File results) {
        LinkedList<File> files;

        if (results.length() > 0) {
            files = new LinkedList<>(Arrays.asList(results.listFiles()));
            Iterator<File> filesIterator = files.iterator();
            ArrayList<File> filesToRemove = new ArrayList<>();
            int idx = 1;

            if (files != null) {
                while (filesIterator.hasNext()) {
                    File f = filesIterator.next();
                    if (f.getName().contains(".zip")) {
                        filesToRemove.add(f);
                    }
                    idx++;
                }
                System.out.println("-----------------------------------------------------------------------------------");
                files.removeAll(filesToRemove);
            }

            System.out.println("COMPLETED RENDER | listOfResultFiles_length : " + files.size());
        } else {
            System.out.println("COMPLETED RENDER | listOfResultFiles_length : 0" );
            return null;
        }

        return files;
    }

    public File compileFinalResult(File finalResultsDir) {
        List<File> finalResultsDirList = listOfResultFiles(finalResultsDir);

        String[] filesToZip = new String[finalResultsDirList.size()];
        for (int i = 0; i < finalResultsDirList.size(); i++) {               // TaskID_TotalFrame_Frame_TileCount
            File resultFile = finalResultsDirList.get(i).getAbsoluteFile();
            filesToZip[i] = resultFile.getAbsolutePath();
            System.out.println("COMPLETED RENDER | " + resultFile.getName() + " added to Zip File Array");
        }
        File finalResult = zipFiles(resultParamsMap.get("taskID"), finalResultsDirList, resultsDir);

        return finalResult;
    }

    // The ResultsCheckpoint is what its name suggests. It checks for the results within its respective Directories & Level - Frame Results or Final Results.
    // If the expected results meet the expected volume of Frames OR Subtasks, it triggers the Compilation of Frames OR Composition of Subtasks into a Frame.
    // Certain results come with more than a single File, but they all share the same Subtask ID.
    // So a differentiation of each Result File - e.g. : ".jpeg", ".Denoiser.jpeg", ".FakeSunlight.jpeg" - is needed through the 'extractExtensions' method. See the comments on the method for more information.
    public File resultsCheckpoint (String taskID, String frameID, File frameDir, File finalResultsDir) {
        File finalResult = null;
        System.out.println("COMPLETED RENDER | Results CheckPoint");
        ArrayList<String> extensions;

            if (resultParamsMap.get("frameCategory").equals("multiFrame") && subtaskCount == 1) {
                extensions = extractExtensions(finalResultsDir.listFiles());

                if (finalResultsCheckpoint(taskID, finalResultsDir, extensions.size())) {
                    finalResult = compileFinalResult(finalResultsDir);
                    System.out.println("COMPLETED RENDER | Final Result : " + finalResult.getAbsolutePath());
                }

            } else if (resultParamsMap.get("frameCategory").equals("multiFrame") && subtaskCount > 1) {
                extensions = extractExtensions(frameDir.listFiles());

                if (frameResultsCheckpoint(frameID, frameDir, extensions.size())) {
                    compositeTiles(frameDir,
                            subtaskCount,
                            finalResultsDir,
                            frameID,
                            resultParamsMap.get("renderOutputType"),
                            resultParamsMap.get("application"),
                            extensions);

                        if (finalResultsCheckpoint(taskID, finalResultsDir, extensions.size())) {
                            finalResult = compileFinalResult(finalResultsDir);
                            System.out.println("COMPLETED RENDER | Final Result : " + finalResult.getAbsolutePath());
                        }
                }

            } else if (resultParamsMap.get("frameCategory").equals("singleFrame")) {
                extensions = extractExtensions(frameDir.listFiles());

                if (frameResultsCheckpoint(frameID, frameDir, extensions.size())) {
                    compositeTiles(frameDir,
                            subtaskCount,
                            finalResultsDir,
                            taskID,
                            resultParamsMap.get("renderOutputType"),
                            resultParamsMap.get("application"),
                            extensions);

                    finalResult = compileFinalResult(finalResultsDir);
                    System.out.println("COMPLETED RENDER | Final Result : " + finalResult.getAbsolutePath());
                }
            } else {
                System.out.println("[ERROR] Frame Category undetected.");
            }

        return finalResult;
    }

    // As the name suggests, checks if a specified Frame's subtasks have all been received and need composition. This returns a Boolean value to trigger Composition.
    public boolean frameResultsCheckpoint (String frameID, File frameDir, int totalExtensions) {
        boolean frameResultsChecked = false;
        List<File> frameDirList = listOfResultFiles(frameDir);
        int subtaskCount = Integer.valueOf(resultParamsMap.get("subtaskCount"));
        int expectedFrameResultsDirSize = subtaskCount*totalExtensions;
        int tilesToGo = (expectedFrameResultsDirSize - frameDirList.size()) / totalExtensions;

        System.out.println(" --------------------- FRAME RESULT LIST CHECKPOINT ---------------------");

        if (frameDirList.size() > expectedFrameResultsDirSize) {
            System.out.println("COMPLETED RENDER | Frame Result List : " + frameDirList.size());
            System.out.println("[ERROR] TOO MANY TILES (Frame Result list is greater than expected Subtask Count).");

        } else if (frameDirList.size() < expectedFrameResultsDirSize) {
            System.out.println("COMPLETED RENDER | Frame Result List : " + frameDirList.size());
            System.out.println("COMPLETED RENDER | Number of TILES to go before compositing FRAME (" + frameID + ") : " + tilesToGo);

            if (!updateRenderStatus(taskID, String.valueOf(frameDirList.size()), String.valueOf(expectedFrameResultsDirSize))) {
                updateRenderStatus(taskID, String.valueOf(frameDirList.size()), String.valueOf(expectedFrameResultsDirSize));
            }

        } else if (frameDirList.size() == expectedFrameResultsDirSize) {
            System.out.println("COMPLETED RENDER | Frame Result List : " + frameDirList.size());
            frameResultsChecked = true;
        }

        return frameResultsChecked;
    }

    // As the name suggests, checks if all the Frames of the Task has been composited and needs Compilation. This returns a Boolean value to trigger Compilation.
    public boolean finalResultsCheckpoint(String taskID, File finalResultsDir, int totalExtensions) {
        System.out.println(" --------------------- FINAL RESULT LIST CHECKPOINT ---------------------");
        boolean finalResultsChecked = false;
        List<File> finalResultsDirList = listOfResultFiles(finalResultsDir);
        int frameCount = Integer.valueOf(resultParamsMap.get("frameCount"));
        int expectedFinalResultsDirSize = frameCount * totalExtensions;
        int framesToGo = (expectedFinalResultsDirSize - finalResultsDirList.size()) / totalExtensions;


        if (finalResultsDirList.size() > expectedFinalResultsDirSize) {
            System.out.println("COMPLETED RENDER | Final Result List : " + finalResultsDirList.size());
            System.out.println("[ERROR] TOO MANY FRAMES (Final Result list is greater than expected Frame Count).");

        } else if (finalResultsDirList.size() < expectedFinalResultsDirSize) {
            System.out.println("COMPLETED RENDER | Final Result List : " + finalResultsDirList.size());
            System.out.println("COMPLETED RENDER | Number of FRAMES to go before completing TASK (" + taskID + ") : " + framesToGo);

            if (!updateRenderStatus(taskID, String.valueOf(finalResultsDirList.size()), String.valueOf(expectedFinalResultsDirSize))) {
                updateRenderStatus(taskID, String.valueOf(finalResultsDirList.size()), String.valueOf(expectedFinalResultsDirSize));
            }

        } else if (finalResultsDirList.size() == expectedFinalResultsDirSize) {
            System.out.println("COMPLETED RENDER | Final Result List : " + finalResultsDirList.size());
            System.out.println("COMPLETED RENDER | " + resultParamsMap.get("taskID") + " IS COMPLETED. RETURNING RESULTS TO " + resultParamsMap.get("userEmail") + " NOW . . . ");

            finalResultsChecked = true;
        }

        return finalResultsChecked;
    }

    public boolean updateRenderStatus(String taskID, String completed, String total) {
        boolean updated = false;
        String renderUpdateURL = "https://www.nebula.my/_functions/Update";
        String testURL = "https://www.nebula.my/_functions-dev/Update/";

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            HttpUriRequest request = RequestBuilder
                    .put(renderUpdateURL)
                    .build();

            request.setHeader("task-identity", taskID);
            request.setHeader("completed", completed);
            request.setHeader("total", total);
            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            System.out.println("COMPLETED TASK | Executing request " + request.getRequestLine() + " | Status : " + status);
            if (status == 200) {
                updated = true;
            } else {
                System.out.println("COMPLETED TASK | Failed to update nebula.my.");
            }
            httpClient.close();
            response.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return updated;
    }

    // This method is required for Tasks that returns multiple results - e.g. VRAY Renders - ".jpeg", ".Denoiser.jpeg", ".FakeSunlight.jpeg"
    // Though multiple results, they all share the same SubtaskID and this method differentiates each results using its extensions beyond the SubtaskID
    // This is critical in determining the Total Expected Volume of results in FrameDir or FinalResultsDir for Composition / Compilation.
    // This is also paramount to Composition in ensuring it composites all the results and not just the main output.
    public ArrayList<String> extractExtensions (File[] inputFiles) {
        Arrays.sort(inputFiles);

        ArrayList<String> extensions = new ArrayList<>();

        for (int i=0; i<inputFiles.length; i++) {

            File f = inputFiles[i].getAbsoluteFile();
            String extension = f.getName().substring(24);
            String split[] = extension.split("\\.");
            extension = "." + split[1];
            if (!extensions.contains(extension) && !extension.contains("zip")) {
                System.out.println("COMPLETED RENDER | Extension : " + extension);
                extensions.add(extension);
            }
        }

        return extensions;
    }

    // This method works alongside the 'extractExtensions' method to collect all the various results needed for Composition.
    public LinkedHashMap<String, ArrayList<File>> collectToComposite (ArrayList<String> extensions, List<File> inputFiles) {
        LinkedHashMap<String, ArrayList<File>> toComposite = new LinkedHashMap<>();

        for (int i=0; i<extensions.size(); i++) {
            String extension = extensions.get(i);

            ArrayList<File> toCompositeArray = new ArrayList<>();

            for (int j=0; j<inputFiles.size(); j++) {
                File f = inputFiles.get(j).getAbsoluteFile();
                String f_extension = f.getName().substring(24);

                if (f_extension.equals(extension)) {
                    toCompositeArray.add(f);
                }
            }
            Collections.sort(toCompositeArray);
            toComposite.put(extension, toCompositeArray);
        }

        return toComposite;
    }

    // The Composition. This method composites all the gathered rendered tiles (subtasks) into a single frame to be Compiled.
    public File compositeTiles(File frameDir,
                               int tileCount,
                               File outputDir,
                               String outputId,
                               String renderOutputType,
                               String application,
                               ArrayList<String> extensions) {

        File compositedFrame = null;

        System.out.println("COMPLETED RENDER | Compositing tiles . . . ");
        long start = System.currentTimeMillis();

        // first check that it has all images
        List<File> inputFiles = listOfResultFiles(frameDir);
        LinkedHashMap<String, ArrayList<File>> toComposite = collectToComposite(extensions, inputFiles);

        for (int i=0; i<extensions.size(); i++) {

            String extension = extensions.get(i);
            ArrayList<File> toCompositeArray = toComposite.get(extension);
            String outputfileName = outputId + extension;

            try {
                // create an array of BufferedImages from the input files inverting the order in the rows
                // (it's cropped from bottom to top but it's composited from top to bottom)

                BufferedImage[] bufferedImages = bufferImages(toCompositeArray, application);
                BufferedImage image = combineImages(bufferedImages);
                compositedFrame = new File(outputDir,  outputfileName);
                boolean writeComplete = false;

                while (!writeComplete) {
                    writeComplete = ImageIO.write(image, renderOutputType, compositedFrame);
                }

            } catch (IOException ex) {
                System.out.println("[ERROR] Failed during tile compositing: "  + ex.getMessage());
                return null;
            }
        }

//        while (iterator.hasNext()) {
//            String extension = iterator.next().getKey();
//            System.out.println("CHECK | Extensions (composite) : " + extension);
//            Map.Entry entry = iterator.next();
//            ArrayList<File> toCompositeArray = ;
//            String outputfileName = outputId + extension;
//
//            try {
//                // create an array of BufferedImages from the input files inverting the order in the rows
//                // (it's cropped from bottom to top but it's composited from top to bottom)
//
//                BufferedImage[] bufferedImages = bufferImages(toCompositeArray, application);
//                BufferedImage image = combineImages(bufferedImages);
//                compositedFrame = new File(outputDir,  outputfileName);
//                boolean writeComplete = false;
//
//                while (!writeComplete) {
//                    System.out.println("COMPLETED RENDER | Writing image to " + outputDir + " . . . ");
//                    writeComplete = ImageIO.write(image, renderOutputType, compositedFrame);
//                }
//                System.out.println("COMPLETED RENDER | Composited Frame File Size : " + compositedFrame.length());
//
//            } catch (IOException ex) {
//                System.out.println("[ERROR] Failed during tile compositing: "  + ex.getMessage());
//                return null;
//            }
//        }

        cleanup(frameDir, inputFiles);

        System.out.println("COMPLETED RENDER | Composited " + tileCount +
                " tiles in (ms): " +
                (System.currentTimeMillis() - start));

        return compositedFrame;
    }

    // This method works within the Composition method to buffer the tiles into a stream in a specified order to ensure the Composed Frame is accurate to the user's specified dimensions and without overlap.
    private BufferedImage[] bufferImages(ArrayList<File> inputFiles, String application) throws IOException {
        BufferedImage[] bufferedImages = new BufferedImage[inputFiles.size()];
        int divisions = (int)Math.sqrt(inputFiles.size());
        int numImage = 0;

        // Blender : Create an array of BufferedImages from the input files inverting the order in the rows
        // Vray : Create an array of BufferedImages from the input files in regular order
        // (it's cropped from bottom to top but it's composited from top to bottom)
        if (application.contains("blender")) {
            for (int row = divisions - 1; row >= 0; row--)
                for (int order = 0; order < divisions; order++)
                    bufferedImages[numImage++] = ImageIO.read(inputFiles.get(row*divisions + order));

        } else if (application.contains("vray")) {
            for (int i=0; i<inputFiles.size(); i++) {
                bufferedImages[i] = ImageIO.read(inputFiles.get(i));
            }
        } else {
            System.out.println("[ERROR] Unknown application : " + application);
        }

        return bufferedImages;
    }

    // This method works hand-in-hand with the 'bufferImages' method as part of the Composition to finally stitch all the BufferedImages together in its specified dimensions without overlap.
    private BufferedImage combineImages(BufferedImage bufferedImages[]) {
        int divisions = (int)Math.sqrt(bufferedImages.length);
        int actualImage = 0;
        // first we establish the width and height of the final image
        int finalWidth = 0;
        int finalHeight = 0;
        System.out.println("COMPLETED RENDER | Buffered Image Length : " + bufferedImages.length + "");

        for (int i = 0; i < divisions; i++) {
            finalWidth += bufferedImages[i].getWidth();
            finalHeight += bufferedImages[i * divisions].getHeight();
        }

        System.out.println("COMPLETED RENDER | Combining images . . . ");
        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
        System.out.println("COMPLETED RENDER | Combining images (0) . . . ");
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
                System.out.println("COMPLETED RENDER | Combining images (" + heightImage + ") . . . ");
                // after processing the row we get the height of the last processed image
                // (it's the same for all in the row) and locate at the begining of the row
                rowHeight += bufferedImages[actualImage - 1].getHeight();
                rowWidth = 0;
            }

            System.out.println("COMPLETED RENDER | Images combined.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return finalImg;
    }

    // Cleans up whichever Directory needs cleaning up.
    private void cleanup(File tileDir, List<File> inputFiles) {
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
