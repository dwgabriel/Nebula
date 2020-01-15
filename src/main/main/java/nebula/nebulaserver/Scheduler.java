package nebula.nebulaserver;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Daryl Wong on 3/15/2019.
 */
@WebServlet(
        name = "SchedulerServlet",
        urlPatterns = {"/scheduler"}
)

public class Scheduler extends HttpServlet {

    private static String RootPath = new File("").getAbsolutePath();
    private static File rootDir = new File(RootPath);
    private static File database = new File(rootDir, "nebuladatabase");
    final File taskDatabase = new File(database, "tasks");
    final File schedulerCache = new File(database, "schedulercache");
    File taskDir;

    String uploadServlet = "https://nebula-server.herokuapp.com/upload";

    private String userEmail;
    private String application;
    private String taskID;
    private String subtaskID;
    private String subtaskLength;
    private final String[] filesToZip = new String[2];
    private Deque<File> taskQueue = new ArrayDeque<>();
    private ArrayList<Node> activeNodes = new ArrayList<>();
    int index = 0;

    public String getInfo() throws IOException { // Retrieves information from server. (Works)
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpUriRequest request = RequestBuilder
                .get(uploadServlet)
                .build();
        String taskIdentity;
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            taskIdentity = response.getFirstHeader("Task-Identity").getValue();

        } finally  {
            response.close();
        }
        int status = response.getStatusLine().getStatusCode();
        System.out.println("Status Code for GET : " + status);
        return taskIdentity;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        System.out.println("Re-scheduling in 3 . . . ");
        String this_taskID = req.getHeader("Task-Identity");
        String this_subtaskID = req.getHeader("Subtask-Identity");
        String this_tileScript = req.getHeader("Tile-Script");
        String this_nodeEmail = req.getHeader("Node-Email");

        System.out.println("Re-scheduling in 2 . . . ");
        taskDir = new File(taskDatabase, this_taskID);
        File originalTaskDir = new File(taskDir, "originaltask");
        File subtaskDir = new File(taskDir, "subtasks");

        System.out.println("Re-scheduling in 1 . . . ");
        File[] filesArray = subtaskDir.listFiles();
        for (int i=0; i<filesArray.length; i++) {
            if (filesArray[i].getName().equals(this_tileScript)) {
                taskQueue.addFirst(filesArray[i].getAbsoluteFile());
                System.out.println(this_subtaskID + "has been re-added to the Task Queue - Due for re-scheduling immediately.");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException { // Nodes will post Node information (Node name, job queue, node creditScore) to Server as request for more jobs.

        String nodeEmail = request.getParameter("Node-Email");                                                      // Retrieve <input type="text" name="username"> - Username of Node Supplier User
        String deviceIdentity = request.getParameter("Device-Identity");                                                    // Retrieve <input type="text" name="deviceID"> - Identity of device being supplied by Supply User
        String nodeScore = request.getParameter("Score");                                                            // Retrieve <input type="text" name"score"> - Identifies Node device's reliability

        Node node = new Node(nodeEmail, deviceIdentity, Integer.parseInt(nodeScore));
        addTester(node);

        String[] nodeQueue = request.getParameterValues("Queue");                                                    // Retrieve <input type="text" name="queue"> -  Status of device job queue
        System.out.println("------ Task Request from " + nodeEmail + " ------");
        boolean name = false;
        boolean device = false;
        boolean score = false;
        boolean queue = false;
        boolean valid = false;

        ServletOutputStream out = response.getOutputStream();

        if (nodeEmail == null) {                                                                                     // Checks nodeEmail Parameter and if validated, moves on to deviceIdentity Parameter.
            System.out.println("1. Name invalid.");
            out.write("Your username is invalid.".getBytes("UTF-8"));
            out.flush();
            out.close();

        } else {
            name = true;
            System.out.println("1. Name valid.");

            if (deviceIdentity == null) {                                                                               // Checks deviceIdentity Parameter and if validated, moves on to nodeScore Parameter.
                System.out.println("2. Device invalid.");
                out.write("Your Device ID is invalid.".getBytes("UTF-8"));
                out.flush();
                out.close();
            } else {
                device = true;
                System.out.println("2. Device valid.");

                if (nodeScore == null || Integer.valueOf(nodeScore) < 70) {                                             // Checks nodeScore Parameter and if validated, moves on to nodeQueue Parameter.
                    System.out.println("3. Score invalid.");
                    out.write("Your Device Score is invalid / too low.".getBytes("UTF-8"));
                    out.flush();
                    out.close();
                } else if (nodeScore != null && Integer.valueOf(nodeScore) >= 70) {
                    score = true;
                    System.out.println("3. Score valid.");

                    if (nodeQueue == null || nodeQueue.length > 3) {                                                    // Checks nodeQueue Parameter and if validated, all necessary gateway Parameters are valid and Subtasks can be scheduled to Node.
                        System.out.println("4. Queue invalid.");
                        out.write("Your Device Queue is full.".getBytes("UTF-8"));
                        out.flush();
                        out.close();
                    } else {
                        while (nodeQueue.length <= 3) {
                            queue = true;
                            valid = true;
                            System.out.println("4. Everything is valid.");
                            break;
                        }
                    }
                }
            }
        }
        if (valid == true) {                                                                                            // If all criterias approved and are valid, then and only then will Subtasks be scheduled to the Node.
            ///// Scheduler needs to supply Docker Blender Image/Container & Tile Script to Node.

            if (taskID == null) {
                taskID = getInfo();
            }

                taskDir = new File(taskDatabase, taskID);
                File originalTaskDir = new File(taskDir, "originaltask");
                File subtaskDir = new File(taskDir, "subtasks");

                if (subtaskDir.listFiles() != null && originalTaskDir.listFiles() != null) {

                    File[] filesArray = subtaskDir.listFiles();
                    Arrays.sort(filesArray);

                     for (int i=0; i<subtaskDir.listFiles().length; i++) {
                        taskQueue.add(filesArray[i].getAbsoluteFile());
                     }

                    File[] originalTaskArray = originalTaskDir.listFiles();

                    if (index < filesArray.length) {
                        try {
                            File originalTaskFile = originalTaskArray[0].getAbsoluteFile();
                            File tileScript = taskQueue.peek().getAbsoluteFile();

                            userEmail = getMetadata(originalTaskFile.toPath(), "User-Email");
                            application = getMetadata(tileScript.toPath(), "Application");
                            subtaskID = getMetadata(tileScript.toPath(), "Subtask-Identity");
                            subtaskLength = getMetadata(tileScript.toPath(), "Subtask-Length");

                            response.reset();
                            response.setHeader("User-Email", userEmail);
                            response.setHeader("Tile-Script", tileScript.getName());                                     // Content Length      : Renderfile size
                            response.setHeader("Task-Name", originalTaskFile.getName());
                            response.setHeader("Subtask-Identity", subtaskID);                                           // Content Disposition : Renderfile name
                            response.setHeader("Task-Identity", taskID);                                                 // Task Identity       : Task ID for consistency
                            response.setHeader("Application", application);                                              // Application         : Application ID for Docker container - nebula/blender from DockerHub
                            response.setHeader("Subtask-Length", subtaskLength);
                            response.addHeader("Task-Package", "attachment; filename=\"test-arc.zip\"");

                            printTaskInfo(taskID, subtaskID, nodeEmail);
                            printActiveNodes();

                            response.setContentType("application/zip");
                            response.setStatus(HttpServletResponse.SC_OK);

                            filesToZip[0] = originalTaskFile.getAbsolutePath();
                            filesToZip[1] = tileScript.getAbsolutePath();
                            File taskPackage = zipFiles(taskID, filesToZip);

                            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(taskPackage));              // InputStream of Renderfile
                            byte[] buffer = new byte[(int) taskPackage.length()];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {                                                        // Output of renderfile to POST-Responses from Node
                                out.write(buffer, 0, length);
                            }
                            inputStream.close();
                            out.flush();
                            out.close();

                            index++;
                            taskQueue.remove();
                            System.out.println("New Index : " + index);

                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    } else if (index >= filesArray.length) {
                        System.out.println("All Subtasks of " + taskID + " has been scheduled.");
                        out.write("There are no tasks at this moment.".getBytes("UTF-8"));
                        out.flush();
                        out.close();
                        taskID = null;
                        index = 0;
                    }
                } else {
                    System.out.println("Something's wrong here.");
                }
            }

        System.out.println("----------------------------------------------------------------------");
    }

    private File zipFiles(String taskID, String[] filePaths) {
        File zippedFile = null;
        try {
            String zipFileName = taskID.concat(".zip");
            System.out.println("Zip File Name : " + zipFileName);
            zippedFile = new File(schedulerCache, zipFileName);

            FileOutputStream fos = new FileOutputStream(zippedFile);
            ZipOutputStream zos = new ZipOutputStream(fos);

            for (String aFile : filePaths) {
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
        return zippedFile;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }

    public boolean checkNodeID(Node node) {
        boolean exists = false;
        if (!activeNodes.isEmpty()) {
            for (int i=0; i<activeNodes.size(); i++) {
                if (activeNodes.get(i).deviceID == node.deviceID) {
                    exists = true;
                } else {
                    exists = false;
                }
            }
        } else {
            exists = false;
        }

        return exists;
    }

    public void addTester(Node node) {
        if (checkNodeID(node)) {
            System.out.println(node.deviceID + " already exists.");
        } else {
            activeNodes.add(node);
            System.out.println(node.deviceID + " added to Active Nodes.");
        }
    }

    public void printActiveNodes () {
        System.out.println("---- ACTIVE NODES ----");
        for (int i=0; i<activeNodes.size(); i++) {
            System.out.println(i + ". " + activeNodes.get(i).deviceID);
        }
        System.out.println("------------------------");
    }

    public void printTaskInfo(String taskID, String subtaskID, String nodeEmail) {
        System.out.println("---- TASK INFO ----");
        System.out.println("Task ID : " + taskID);
        System.out.println("Subtask ID :" + subtaskID);
        System.out.println("Node-Email : " + nodeEmail);
        System.out.println("------------------------");
    }

    public class Node {

        private String nodeEmail;
        private String deviceID;
        private int nodeScore;

        public Node(String nodeEmail, String deviceID, int nodeScore) {
            this.nodeEmail = nodeEmail;
            this.deviceID = deviceID;
            this.nodeScore = nodeScore;
        }

        public String getNodeEmail() {
            return nodeEmail;
        }

        public String getDeviceID() {
            return deviceID;
        }

        public int getNodeScore() {
            return nodeScore;
        }
    }
}


