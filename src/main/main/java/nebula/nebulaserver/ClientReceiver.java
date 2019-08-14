package nebula.nebulaserver; /**
 * Created by Daryl Wong on 3/14/2019.
 */

import org.apache.commons.io.FilenameUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

@WebServlet(
        name = "ClientReceiver",
        urlPatterns = {"/clientReceiver"}
        )
@MultipartConfig
public class ClientReceiver extends HttpServlet {
    // Path directories
    File taskDatabase = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks");

    Queue<File> taskQueue = new LinkedList<File>();
    ArrayList<Task.Subtask> subtaskQueue = new ArrayList<Task.Subtask>();


    public Queue<File> getTaskQueue() {
        return taskQueue;
    }

    public ArrayList<Task.Subtask> getSubtaskQueue() {
        return subtaskQueue;
    }

    Part taskFile;
    File originalTaskDir;
    String taskFileName;
    String taskID;
    String userID;
    String application;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)                                      // doGet to send Subtask Queue & Application info
            throws ServletException, IOException {

        if (request != null) {
            refreshQueue();
            response.setHeader("Task-Identity", taskQueue.peek().getName());
            taskQueue.remove();

            int status = response.getStatus();
            System.out.println("STATUS CODE : " + status);
            System.out.println("___________________________________");
        } else {
            System.out.println("Problem's here." + taskQueue.peek().getName());
            response.sendError(response.SC_BAD_REQUEST,
                    "Bad Request | Parameter Required");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        userID = request.getParameter("username");                                                                   // Retrieves Username of Client
        application = request.getParameter("application");                                                           // Retrieves ID of Client-selected application for rendering
        taskFile = request.getPart("renderfile");                                                                    // Retrieves Renderfile from Client <input type="file" name="file">
        taskFileName = Paths.get(taskFile.getSubmittedFileName()).getFileName().toString();                             // Decoding Received File's Name : MSIE fix. Ensures that the file name itself is returned, not the entire file path.

        InputStream fileContent = taskFile.getInputStream();
        String fileExt = FilenameUtils.getExtension(taskFileName);                                                      // File decode to retrieve file extension (file type - e.g. renderfile.blend / renderfile.3dm)

        //TESTS :
        System.out.println("Username : " + userID);
        System.out.println(taskFileName + "'s Content Type : " + fileExt);
        System.out.println("Application (doPost) : " + application);

        taskID = String.format(taskIdentity());                                                                 // Encodes task with identity and info - ID_User_FileName.extension | 0123_johnDoe_johnsdesign.blend
        originalTaskDir = new File(taskDatabase, taskID);                                                              // Creates a new storage directory for the new task.
        originalTaskDir.mkdir();
        // Creating all sub-directories needed :
        File nodeResultsDir = new File(originalTaskDir, "noderesults");
        File clientCollectionDir = new File(originalTaskDir, "clientcollection");
        File subtaskDir = new File(originalTaskDir, "subtasks");
        File originalTaskDir = new File(this.originalTaskDir, "originaltask");

        nodeResultsDir.mkdir();
        clientCollectionDir.mkdir();
        subtaskDir.mkdir();
        originalTaskDir.mkdir();

        File originalTaskFile = new File(originalTaskDir, taskFileName);                                            // Creates the originalTaskFile for writing to.
        Task task = new Task(taskID, userID, application, 1, originalTaskFile);                              // Creates new Task object to contain crucial information.
        task.setOriginalTaskDir(originalTaskDir.toPath());                                                          // Links the Task object to originalTaskFile Path.
        subtaskQueue = task.createSubtasks(this.originalTaskDir, subtaskDir);                                               // Creates a queue of subtasks for the respective Task, and populates it with Subtask information (Blender-CL, TileScript, TaskID, etc.)
        refreshQueue();

        System.out.println("------------------------------------ CHECK ----------------------------------------");
        System.out.println("Task Queue : " + taskQueue.size());
        System.out.println("Subtask Queue : " + subtaskQueue.size());
        System.out.println("Tile Scripts : " + task.getTileScripts().length);
        System.out.println("-----------------------------------------------------------------------------------");

        for (int i = 0; i < subtaskQueue.size(); i++) {                                                                 // Quick check of Subtask Queue to ensure tally.
            System.out.println(i + 1 + ". Subtask Queue : " + subtaskQueue.get(i).getSubtaskID());
        }

        try {
            Files.copy(fileContent, originalTaskFile.toPath());                                                     // Writes client uploaded originalTaskFile to originalTaskDir.

            setMetadata("User-Identity", userID, originalTaskFile.toPath());                              // Setting Basic Data into original task file in case needed. (Premature optimization)
            setMetadata("Task-Identity", taskID, originalTaskFile.toPath());
            setMetadata("Application", application, originalTaskFile.toPath());

            for (int i = 0; i < subtaskQueue.size(); i++) {
                // Setting Render-crucial data to tileScripts in case needed.
                String subtaskID = subtaskQueue.get(i).getSubtaskID();
                String blenderCL = subtaskQueue.get(i).getBlenderCL();
                String subtaskLength = Integer.toString(task.getNumberOfSubtasks());
                Path tileScriptPath = subtaskQueue.get(i).getTileScript().toPath();
                setMetadata("Application", application, tileScriptPath);
                setMetadata("Blender-CL", blenderCL, tileScriptPath);
                setMetadata("Task-Identity", taskID, tileScriptPath);
                setMetadata("Subtask-Identity", subtaskID, tileScriptPath);
                setMetadata("Subtask-Length", subtaskLength, tileScriptPath);

                response.setHeader("RECEIVED", originalTaskFile.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void refreshQueue() {
        File[] taskArray = taskDatabase.listFiles();

        for (int i = 0; i < taskArray.length; i++) {
            System.out.println("Tasks " + i + " : " + taskArray[i].getName());
            if (!taskQueue.contains(taskArray[i])) {
                taskQueue.add(taskArray[i]);
            } else {
                System.out.println("Task : " + taskArray[i].getName() + " is already in queue.");
            }
        }
    }

    public static String taskIdentity() {

        int[] randomNum = new int[6];
        int min = 0;
        int max = 9;

        for (int i = 0; i < randomNum.length; i++) {
            randomNum[i] = ThreadLocalRandom.current().nextInt(min, max + 1);
        }

        String ID = String.format(Integer.toString(randomNum[0])
                + Integer.toString(randomNum[1])
                + Integer.toString(randomNum[2])
                + Integer.toString(randomNum[3])
                + Integer.toString(randomNum[4])
                + Integer.toString(randomNum[5]));
        return ID;
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

    public String getMetadata(Path metaPath, String metaName) {                                       // Getting Application info from Subtask attributes for ComputeTask.
        String metaValue = null;
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            ByteBuffer buffer = ByteBuffer.allocate(view.size(metaName));
            view.read(metaName, buffer);
            buffer.flip();
            metaValue = Charset.defaultCharset().decode(buffer).toString();
            System.out.println("TEST | Attribute : " + metaName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }
}


//    public void Split(File originalFile, File originalSplit) throws IOException {                                       // DOESNT WORK - REQUIRES INTEGRATION OF CAMERA SPLITTER #########################
//
//        int partCounter = 1;
//
//
//        int sizeOfTask = 1024 * 1024;                                                                                   // by Megabytes - Configuration of TaskInstance sizes, and increments on partCounter.
//        byte[] buffer = new byte[sizeOfTask];
//        String originalFileName = originalFile.getName();
//        System.out.println("SPLIT | Original FileName : " + originalFileName);
//
//        try {
//            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(originalFile));
//
//            int bytesAmount = 0;
//            while ((bytesAmount = bis.read(buffer)) > 0) {
//                String fileExt = FilenameUtils.getExtension(originalFileName);
//                String subtaskID = String.format("%03d_%s_%s", partCounter++, taskID, "TEST" + "." + fileExt);
//                System.out.println("Subtask ID : " + subtaskID);
//
//
//                File taskFile = new File(originalSplit, subtaskID);                                                     // Creates a copy of subtasks in the original database as reference.
//                File newSubtask = new File(schedulercache.getAbsolutePath(), subtaskID);                                // Creates a new subtask in the com.nebula.Scheduler Cache.
//
//                FileOutputStream outputStream = new FileOutputStream(taskFile);                                         // Original Split
//                FileOutputStream outputStreamTwo = new FileOutputStream(newSubtask);                                    // com.nebula.Scheduler Cache
//
//                outputStream.write(buffer, 0, bytesAmount);                                                         // Original Split
//                outputStreamTwo.write(buffer,0,bytesAmount);                                                        // com.nebula.Scheduler Cache
//
//                setMetadata(subtaskID, "Application", application, taskFile.toPath());
//                setMetadata(subtaskID, "User-Identity", userID, taskFile.toPath());
//
//                setMetadata(subtaskID, "Application", application, newSubtask.toPath());
//                setMetadata(subtaskID, "User-Identity", userID, newSubtask.toPath());
//            }
//            } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

