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
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

@WebServlet(
        name = "UploadReceiver",
        urlPatterns = {"/upload"}
        )
@MultipartConfig
public class UploadReceiver extends HttpServlet {

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");

    Part taskFile;
    File taskDir;
    String taskFileName;
    String taskID;
    String userEmail;
    String application;

    Queue<File> taskQueue = new LinkedList<File>();
    ArrayList<Task.Subtask> subtaskQueue = new ArrayList<Task.Subtask>();

    public Queue<File> getTaskQueue() {
        return taskQueue;
    }
    public ArrayList<Task.Subtask> getSubtaskQueue() {
        return subtaskQueue;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)                                      // doGet to send Subtask Queue & Application info
            throws IOException {

        if (request != null && taskQueue.size() <= 0 ) {
            refreshQueue();
            System.out.println("TaskID from Queue : " + taskQueue.peek().getName());
            response.setHeader("Task-Identity", taskQueue.peek().getName());
            taskQueue.remove();

            int status = response.getStatus();
            System.out.println("STATUS CODE : " + status);
            System.out.println("___________________________________");
        } else if (request != null && taskQueue.size() > 0) {
                System.out.println("TaskQueue Size : " + taskQueue.size());
                System.out.println("TaskID from Queue : " + taskQueue.peek().getName());
                response.setHeader("Task-Identity", taskQueue.peek().getName());
                taskQueue.remove();

                int status = response.getStatus();
                System.out.println("STATUS CODE : " + status);
                System.out.println("___________________________________");
        } else {
                System.out.println("Problem's here. Request Null.");
                response.sendError(response.SC_BAD_REQUEST,
                        "Bad Request | Parameter Required");
            }
        }


    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        userEmail = request.getParameter("user-email");                                                                   // Retrieves Username of Client
        application = request.getParameter("application");                                                           // Retrieves ID of Client-selected application for rendering
        taskFile = request.getPart("task-file");                                                                    // Retrieves Renderfile from Client <input type="file" name="file">
        taskFileName = Paths.get(taskFile.getSubmittedFileName()).getFileName().toString();                             // Decoding Received File's Name : MSIE fix. Ensures that the file name itself is returned, not the entire file path.

        try {
        InputStream fileContent = taskFile.getInputStream();
        String fileExt = FilenameUtils.getExtension(taskFileName);                                                      // File decode to retrieve file extension (file type - e.g. renderfile.blend / renderfile.3dm)

        taskID = String.format(taskIdentity());                                                                         // Encodes task with identity and info - ID_User_FileName.extension | 0123_johnDoe_johnsdesign.blend
        taskDir = new File(taskDatabase, taskID);                                                                       // Creates a new storage directory for the new task.
        taskDir.mkdir();
        System.out.println("Task Dir : " + taskDir.getAbsolutePath());
        // Creating all sub-directories needed :
            Collection<File> subdirCollection = new LinkedList<>();
            File nodeResultsDir = new File(taskDir, "noderesults");
            File clientCollectionDir = new File(taskDir, "clientcollection");
            File subtaskDir = new File(taskDir, "subtasks");
            File originalTaskDir = new File(taskDir, "originaltask");

            nodeResultsDir.mkdir();
            clientCollectionDir.mkdir();
            subtaskDir.mkdir();
            originalTaskDir.mkdir();

            subdirCollection.add(nodeResultsDir);
            subdirCollection.add(clientCollectionDir);
            subdirCollection.add(subtaskDir);
            subdirCollection.add(originalTaskDir);

        File originalTaskFile = new File(originalTaskDir, taskFileName);                                                // Creates the originalTaskFile for writing to.
        Task task = new Task(taskID, userEmail, application, 1, originalTaskFile);                                  // Creates new Task object to contain crucial information.
        task.setOriginalTaskDir(originalTaskDir.toPath());                                                              // Links the Task object to originalTaskFile Path.
        subtaskQueue = task.createSubtasks(subtaskDir);                                                        // Creates a queue of subtasks for the respective Task, and populates it with Subtask information (Blender-CL, TileScript, TaskID, etc.)

        System.out.println("------------------------------------ CHECK ----------------------------------------");
            System.out.println(nodeResultsDir.getAbsolutePath());
            System.out.println(clientCollectionDir.getAbsolutePath());
            System.out.println(subtaskDir.getAbsolutePath());
            System.out.println(originalTaskDir.getAbsolutePath());
        System.out.println("Task Queue : " + taskQueue.size());
        System.out.println("Subtask Queue : " + subtaskQueue.size());
        System.out.println("Tile Scripts : " + task.getTileScripts().length);
        System.out.println("-----------------------------------------------------------------------------------");

        for (int i = 0; i < subtaskQueue.size(); i++) {                                                                 // Quick check of Subtask Queue to ensure tally.
            System.out.println(i + 1 + ". Subtask Queue : " + subtaskQueue.get(i).getSubtaskID());
        }
            Files.copy(fileContent, originalTaskFile.toPath());                                                         // Writes client uploaded originalTaskFile to originalTaskDir.

            setMetadata("User-Email", userEmail, originalTaskFile.toPath());                                 // Setting Basic Data into original task file in case needed. (Premature optimization)
            setMetadata("Task-Identity", taskID, originalTaskFile.toPath());
            setMetadata("Application", application, originalTaskFile.toPath());

            for (int i = 0; i < subtaskQueue.size(); i++) {
                                                                                                                        // Setting Render-crucial data to tileScripts in case needed.
                String subtaskID = subtaskQueue.get(i).getSubtaskID();
                String subtaskLength = Integer.toString(task.getNumberOfSubtasks());
                Path tileScriptPath = subtaskQueue.get(i).getTileScript().toPath();
                setMetadata("Task-Name", originalTaskFile.getName(), tileScriptPath);
                setMetadata("Application", application, tileScriptPath);
                setMetadata("Task-Identity", taskID, tileScriptPath);
                setMetadata("Subtask-Identity", subtaskID, tileScriptPath);
                setMetadata("Subtask-Length", subtaskLength, tileScriptPath);
            }
            refreshQueue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void refreshQueue() {
        File[] taskArray = taskDatabase.listFiles();
        System.out.println("--- Task Queue ---");
        for (int i = 0; i < taskArray.length; i++) {
            System.out.println("Tasks " + i + " : " + taskArray[i].getName());
            if (!taskQueue.contains(taskArray[i])) {
                taskQueue.add(taskArray[i]);
            } else {
                System.out.println("Task : " + taskArray[i].getName() + " is already in queue.");
            }
        }
        System.out.println("--- End of Queue ---");
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
