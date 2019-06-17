import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

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

    String taskID = new String();


    public static void main(String[] args) throws IOException {

        Receiver receiver = new Receiver();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        System.out.println("This has been called too.");
        String userID = request.getParameter("username");                                                            // Retrieves <input type="text" name="username">
        String deviceID = request.getParameter("deviceID");                                                          // Retrieves <input type="text" name="deviceID">
        taskID = request.getParameter("taskidentity");                                                               // Retrieves <input type="text" name="taskidentity">
        Part filePart = request.getPart("file");                                                                     // Retrieves <input type="file" name="file">
        String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();                          // MSIE fix. Ensures that the file name itself is returned, not the entire file path.
        InputStream fileContent = filePart.getInputStream();

        System.out.println("Task ID: " + taskID);

        File nodeResults = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\" + taskID + "\\noderesults");
        File originalSplit = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\" + taskID + "\\originalsplit");
        System.out.println("node results : " + nodeResults.listFiles().length);
        System.out.println("original split : " + originalSplit.listFiles().length);

        File file = new File(nodeResults, fileName);
            try {
                Files.copy(fileContent, file.toPath());
                System.out.println(fileName + " is received.");

            } catch (Exception e) {
                e.printStackTrace();
            }

        if (nodeResults.listFiles().length == originalSplit.listFiles().length) {

            mergeFiles(listOfFilesToMerge(nodeResults), taskID);
            System.out.println("File : " + fileName.substring(4) + " has been merged and verified. Ready for collection.");
        }
    }

    public static void mergeFiles(List<File> files, String taskID) throws IOException {
        File clientCollection = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\" + taskID + "\\clientcollection\\");
        File completedFile = new File(clientCollection, taskID + ".Complete");

        try {
            BufferedOutputStream mergingStream = new BufferedOutputStream(new FileOutputStream(completedFile));
            for (File f : files) {
                Files.copy(f.toPath(), mergingStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<File> listOfFilesToMerge(File nodeResults) {

        File[] files = nodeResults.getAbsoluteFile().listFiles();
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        System.out.println("File Size: " + files.length);
        return Arrays.asList(files);
    }
}

