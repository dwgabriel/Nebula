package nebula.nebulaserver;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;


/**
 * Created by Daryl Wong on 4/3/2019.
 */

@WebServlet(
        name = "ReceiverServlet",
        urlPatterns = {"/complete"}
)

@MultipartConfig
public class Receiver extends HttpServlet {
    // Center for Receiving completed results from Nodes for merging and verification.
    // 1. doPost Servlet for receiving completed results from Nodes. (Checked)
    // 2. Merge and verify results from Nodes in chronological order. ***** (Checked)
    // 3. Complete and verified results shall be sent to Client Collection storage for returning to clients. (Checked)
    // 4. doPost Servlet for receiving Client ID as request to return results. (Checked)
    // 5. doPost Servlet for receiving verified and accurate Client ID as request to return results of submitted workload. (Checked) ****

    File taskDatabase = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\");

    String userID = new String();
    String deviceID = new String();
    String taskID = new String();
    String subtaskID = new String();
    int tileCount = 16;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        userID = request.getParameter("username");                                                                   // Retrieves <input type="text" name="username">
        deviceID = request.getParameter("deviceID");                                                                 // Retrieves <input type="text" name="deviceID">
        taskID = request.getParameter("taskidentity");                                                               // Retrieves <input type="text" name="subtaskidentity">
        subtaskID = request.getParameter("subtaskidentity");                                                         // Retrieves <input type="text" name="taskidentity">
        Part filePart = request.getPart("file");                                                                     // Retrieves <input type="file" name="file">
        String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();                          // MSIE fix. Ensures that the file name itself is returned, not the entire file path.
        InputStream fileContent = filePart.getInputStream();

        System.out.println("TaskTest ID: " + taskID);

        File nodeResults = new File(taskDatabase + taskID + "\\noderesults");
        File clientCollection = new File(taskDatabase + taskID + "\\clientCollection");
        System.out.println("node results : " + nodeResults.listFiles().length);

        File file = new File(nodeResults, fileName);
            try {
                Files.copy(fileContent, file.toPath());
                System.out.println(fileName + " is received.");
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (listOfFilesToMerge(nodeResults).size() > tileCount) {
                System.out.println("Error : Tile Count and Node Result list does not match!");
                System.out.println("Node Result : " + listOfFilesToMerge(nodeResults).size());
                System.out.println("Tile Count : " + tileCount);
            } else if (listOfFilesToMerge(nodeResults).size() < tileCount) {
                System.out.println("Number of tiles to go before composition : " + (tileCount - listOfFilesToMerge(nodeResults).size()));
            } else {
                compositeTiles(nodeResults, tileCount, clientCollection);
                System.out.println(fileName +  " has been merged and verified. Ready for collection.");
            }
    }

    public static List<File> listOfFilesToMerge(File nodeResults) {

        File[] files = nodeResults.getAbsoluteFile().listFiles();
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        System.out.println("File Size: " + files.length);
        return Arrays.asList(files);
    }

    public static boolean compositeTiles(File tileDir, int tileCount,
                                         File outputFile) {

        long start = System.currentTimeMillis();

        // first check that it has all images
        File[] inputFiles = new File[tileCount];
        for (int i = 0; i < tileCount; i++) {
            inputFiles[i] = new File(tileDir, Integer.toString(i) + ".png");
            if (!inputFiles[i].isFile()) {
                log.warning("expected tile file doesn't exist: " +
                        inputFiles[i].getAbsolutePath());
                return false;
            }
        }

        try {
            int divisions = (int)Math.sqrt(inputFiles.length);
            int numImage = 0;
            // create an array of BufferedImages from the input files inverting the order in the rows
            // (it's cropped from bottom to top but it's composited from top to bottom)
            BufferedImage[] bufferedImages = new BufferedImage[inputFiles.length];
            for (int row = divisions - 1; row >= 0; row--)
                for (int order = 0; order < divisions; order++)
                    bufferedImages[numImage++] = ImageIO.read(inputFiles[row*divisions + order]);

            BufferedImage image = combineImages(bufferedImages);
            ImageIO.write(image, "png", outputFile);

        } catch (IOException ex) {
            log.warning("failed during tile compositing: "  + ex.getMessage());
            return false;
        }
        cleanup(tileDir, inputFiles, tileCount);

        log.fine("composited " + Integer.toString(tileCount) +
                " tiles in (ms): " +
                Long.toString(System.currentTimeMillis() - start));
        return true;
    }

    private static BufferedImage combineImages(BufferedImage bufferedImages[]) {
        int divisions = (int)Math.sqrt((double)bufferedImages.length);
        int actualImage = 0;
        // first we stablish the width and height of the final image
        int finalWidth = 0;
        int finalHeight = 0;
        for (int i = 0; i < divisions; i++){
            finalWidth += bufferedImages[i].getWidth();
            finalHeight += bufferedImages[i*divisions].getHeight();
        }
//        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, bufferedImages[0].getType());
        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);

        int rowWidth = 0;
        int rowHeight = 0;
        for (int heightImage = 0; heightImage < divisions; heightImage++) {
            for (int widthImage = 0; widthImage < divisions; widthImage++) {
                // check every image
                if (bufferedImages[actualImage] == null) {
                    log.warning("bufferedImages element has null parameter");
                    return null;
                }
                // adding to the final image
                finalImg.createGraphics().drawImage(bufferedImages[actualImage], rowWidth, rowHeight, null);
                rowWidth += bufferedImages[actualImage].getWidth();
                actualImage++;
            }
            // after processing the row we get the height of the last processed image
            // (it's the same for all in the row) and locate at the begining of the row
            rowHeight += bufferedImages[actualImage - 1].getHeight();
            rowWidth = 0;
        }

        return finalImg;
    }

    private static void cleanup(File tileDir, File[] inputFiles, int tileCount) {
        for(File f: inputFiles) {
            if(!f.delete()) {
                log.warning("unable to delete tmp tile file: " +
                        f.getAbsolutePath());
            }
        }
        if(!tileDir.delete()) {
            log.warning("unable to delete tmp tile dir: " +
                    tileDir.getAbsolutePath());
        }
    }

    private static final String className =
            "net.whn.loki.master.ImageHelper";
    private static final Logger log = Logger.getLogger(className);

}


//    public static void mergeFiles(List<File> files, String taskID) throws IOException {
//        File clientCollection = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\" + taskID + "\\clientcollection\\");
//        File completedFile = new File(clientCollection, taskID + "_Complete");
//
//        try {
//            BufferedOutputStream mergingStream = new BufferedOutputStream(new FileOutputStream(completedFile));
//            for (File f : files) {
//                Files.copy(f.toPath(), mergingStream);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
