package nebula.nebulaserver;

import org.apache.http.conn.util.InetAddressUtils;

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
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;
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

    private String RootPath = new File("").getAbsolutePath();
    private File rootDir = new File(RootPath);
    private File database = new File(rootDir, "nebuladatabase");
    final File taskDatabase = new File(database, "tasks");
    final File schedulerCache = new File(database, "schedulercache");
    String taskServlet = "https://nebula-server.herokuapp.com/upload";

    private final String latestNodeVersion = "1.0.22";
    private String subtaskParams;
    private String taskID;
    private String subtaskID;
    private final String[] filesToZip = new String[1];
    private Deque<SubtaskPackage> subtaskPackageQueue = new ArrayDeque<>();
    private ArrayList<String> activeNodes = new ArrayList<>();

    // doGet receives requests from the Rescheduler class to update the SubtaskPackageQueue with tasks that needs to be re-scheduled.
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String this_taskID = req.getHeader("Task-Identity");
        String this_subtaskID = req.getHeader("Subtask-Identity");
        String this_nodeEmail = req.getHeader("Node-Email");

        System.out.println("------------ RE-SCHEDULE REQUEST from " + this_nodeEmail + " (SCHEDULER - doGet) ------------");

        if (this_taskID == null) {
            System.out.println("Nothing to re-schedule.");

        } else {
            System.out.println("Re-scheduling in 3 . . . ");

            File taskDir = new File(taskDatabase, this_taskID);
            File subtaskDir = new File(taskDir, "subtasks");
            File renderfileDir = new File(taskDir, "renderfile");
            System.out.println("Re-scheduling in 2 . . . ");

            File[] subtaskArray = subtaskDir.listFiles();
            File[] renderfileArray = renderfileDir.listFiles();
            if (subtaskArray.length > 0) {
                System.out.println("Re-scheduling in 1 . . . ");

                for (int i = 0; i < subtaskArray.length; i++) {
                    System.out.println("Re-scheduling . . .");
                    File file = subtaskArray[i].getAbsoluteFile();
                    if (file.getName().contains(this_subtaskID)) {
                        System.out.println("File : " + file.getName());
                        subtaskPackageQueue.addFirst(new SubtaskPackage(subtaskArray[i].getAbsoluteFile(), renderfileArray[0].getAbsoluteFile()));
                        System.out.println(this_subtaskID + " has been re-added to the Task Queue - Due for re-scheduling immediately.");
                    }
                }

                checkSubtaskQueue();
            } else {
                System.out.println("Subtask Directory is empty. Files : " + subtaskArray.length);
            }
            System.out.println(" ---- (SCHEDULER - doGet) ----");
        }
    }

    // doPut receives requests from the TaskReceiver class to update the SubtaskPackageQueue with new TaskIDs for scheduling.
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        String taskID = req.getHeader("Task-Identity");

        if (taskID != null) {
            System.out.println("SCHEDULER | New Task Request - Task Identity : " + taskID);
            File taskDir = new File(taskDatabase, taskID);
            File renderfileDir = new File(taskDir, "renderfile");
            File subtaskDir = new File(taskDir, "subtasks");
            File[] renderfileArray = renderfileDir.listFiles();
            File[] subtaskArray = subtaskDir.listFiles();

            Arrays.sort(subtaskArray);

            for (int i = 0; i < subtaskDir.listFiles().length; i++) {
                SubtaskPackage subtaskPackage = new SubtaskPackage(subtaskArray[i].getAbsoluteFile(), renderfileArray[0].getAbsoluteFile());
                subtaskPackageQueue.add(subtaskPackage);
            }
        } else {
            System.out.println("[ERROR] taskID not given in request. ");
        }
    }

    // doPost receives Node requests for jobs. Node information will be passed and verified, and if a task is available, assign to Node.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException { // Nodes will post Node information to Server as request for more jobs.

        String nodeEmail    = request.getParameter("Node-Email");                                                      // Retrieve <input type="text" name="username"> - Username of Node Supplier User
        String deviceID     = request.getParameter("Device-Identity");                                                    // Retrieve <input type="text" name="deviceID"> - Identity of device being supplied by Supply User
        String ipAddress    = request.getParameter("IP-Address");
        String nodeQueue    = request.getParameter("Queue");                                                    // Retrieve <input type="text" name="queue"> -  Status of device job queue
        String nodeVersion  = request.getParameter("Version");
        String nodeScore    = "95";

        NodeUser nodeUser = new NodeUser(nodeEmail);
        NodeUser.Node node = nodeUser.new Node(nodeEmail, nodeVersion, deviceID, nodeScore, ipAddress);
        node.setNodeQueue(Integer.valueOf(nodeQueue));

        ServletOutputStream out = response.getOutputStream();
        System.out.println("--------------------- Task Request from " + nodeEmail + " (SCHEDULER - doPost) ---------------------");

        if (!verifyNode(node, out)) {                                                                                            // If all criterias approved and are valid, then and only then will Subtasks be scheduled to the Node.
            System.out.println("[NODE UNVERIFIED]");
        } else {

//                    int taskExists = 0;                                                                                 // boolean taskExists lets Nodes know there is a task to process, otherwise continue pinging server.
                    if (subtaskPackageQueue.size() == 0) {
                        System.out.println("There are no tasks to compute at this time.");
                        out.write("There are no tasks to compute at this time.".getBytes("UTF-8"));
                        response.addHeader("Subtask-Params", "null");
//                        response.addHeader("Task-Exist", String.valueOf(taskExists));
                    } else {
                        try {
//                            taskExists = 1;
                            SubtaskPackage subtaskPackage = subtaskPackageQueue.peek();
                            System.out.println("SCHEDULER | Subtask Package : " + subtaskPackage.getScriptName());
                            subtaskPackageQueue.remove(subtaskPackage);                         // TODO - CHANGES MADE HERE
                            // Subtasks are packaged into subtaskPackages for scheduling, and placed into a queue. Job requests from Node will peek at the queue using the FIFO method to schedule.
                            // Scheduled jobs will set the 'assigned' boolean to true to avoid double-scheduling, but more importantly to ensure those 'unassigned' will continue to be scheduled if skipped.

                            // GET Subtask Metadata from Renderfile
                            subtaskParams = getMetadata(subtaskPackage.script.toPath(), "Subtask-Params");
                            subtaskID = getMetadata(subtaskPackage.script.toPath(), "Subtask-ID");
                            System.out.println("SCHEDULER | Subtask Params : " + subtaskParams);

                            // SET Headers to supply render-critical information to Nodes pinging the Server for jobs
                            response.reset();
//                            response.addHeader("Task-Exist", String.valueOf(taskExists));
                            response.addHeader("Subtask-Params", subtaskParams);
                            response.addHeader("Task-Package", "attachment; filename=\"test-arc.zip\"");

                            printTaskInfo(taskID, subtaskID, nodeEmail);
                            response.setContentType("application/zip");
                            response.setStatus(HttpServletResponse.SC_OK);

                            filesToZip[0] = subtaskPackage.script.getAbsolutePath();
                            // filesToZip[1] = subtaskPackage.renderfile.getAbsolutePath();
                            File zippedSubtaskPackage = zipFiles(subtaskID, filesToZip);
                            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(zippedSubtaskPackage));              // InputStream of Renderfile
                            byte[] buffer = new byte[(int) zippedSubtaskPackage.length()];
                            int length;

                            while ((length = inputStream.read(buffer)) > 0) {                                                        // Output of renderfile to POST-Responses from Node
                                out.write(buffer, 0, length);
                            }
                            inputStream.close();
                            out.flush();
                            out.close();
//                            taskExists = 0;

                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    }
            }
        System.out.println("----------------------- (SCHEDULER - doPost) -----------------------");
    }

    private boolean verifyNode (NodeUser.Node node, ServletOutputStream outputStream) throws IOException {
        boolean verified = false;

        System.out.println("---- Node Verification for " + node.getNodeEmail() + " (SCHEDULER - verifyNode) -----------------");

        if (node.getNodeEmail() == null || node.getNodeEmail().isEmpty()) {                                                                                     // Checks nodeEmail Parameter and if validated, moves on to deviceIdentity Parameter.
            System.out.println("1. Node Email invalid.");
            outputStream.write("Your username is invalid.".getBytes("UTF-8"));

        } else {
            System.out.println("1. Node Email : " + node.getNodeEmail());

            if (!node.getProductVersion().contains(latestNodeVersion)) {
                System.out.println("2. Node Product Version is outdated. Node Version : " + node.getProductVersion());
                String updateMessage = String.format("Your Node Version is outdated. Please install the latest update - (Version : " + latestNodeVersion + ")");
                outputStream.write(updateMessage.getBytes("UTF-8"));

            } else if (node.getProductVersion().contains(latestNodeVersion)) {
                System.out.println("2. Node Product Version is updated. Node Version : " + node.getProductVersion());

                if (node.getDeviceID() == null || node.getDeviceID().isEmpty() || node.getDeviceID().contains("O.E.M")) {                                                                          // Checks deviceIdentity Parameter and if validated, moves on to nodeScore Parameter.
                    System.out.println("3. Device Identity invalid.");
                    outputStream.write("Your Device ID is invalid.".getBytes("UTF-8"));

                } else {
                    System.out.println("3. Device Identity : " + node.getDeviceID());

                    if (!InetAddressUtils.isIPv4Address(node.getIpAddress()) && !InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        System.out.println("4. Node IP Address invalid.");
                        outputStream.write("Your IP Address is invalid.".getBytes("UTF-8"));

                    } else if (InetAddressUtils.isIPv4Address(node.getIpAddress()) || InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        System.out.println("4. IP Address : " + node.getIpAddress());


                    if (Integer.valueOf(node.getScore()) < 70) {                                             // Checks nodeScore Parameter and if validated, moves on to nodeQueue Parameter.
                        System.out.println("5. Node Score invalid.");
                        outputStream.write("Your Node Score is invalid / too low.".getBytes("UTF-8"));

                    } else if (Integer.valueOf(node.getScore()) >= 70) {
                        System.out.println("5. Node Score : " + node.getScore());

                            if (node.getNodeQueue() > 0) {                                                    // Checks nodeQueue Parameter and if validated, all necessary gateway Parameters are valid and Subtasks can be scheduled to Node.
                                System.out.println("6. Queue invalid. " + node.getNodeEmail() + "'s Queue Size : " + node.getNodeQueue());
                                outputStream.write("Your Device Queue is full.".getBytes("UTF-8"));
                                outputStream.flush();
                                outputStream.close();

                            } else {
                                    verified = true;
                                    System.out.println("6. Node Queue : " + node.getNodeQueue());
                                    System.out.println(node.getNodeEmail() + " is verified.");
                            }
                        }
                    }
                }
            }
        }
            System.out.println(" ------------------------------- (SCHEDULER - verifyNode) -------------------------------");
            return verified;
    }

    private void checkSubtaskQueue() {
        Iterator iterator = subtaskPackageQueue.iterator();
        int i=1;

//        System.out.println("SUBTASK_QUEUE_CHECK ---");
        System.out.println("SUBTASK QUEUE SIZE : " + subtaskPackageQueue.size());
//        while (iterator.hasNext()) {
//            System.out.println(i + ". " + iterator.next());
//            i++;
//        }
    }

    private File zipFiles(String subtaskID, String[] filePaths) {
        File zippedFile = null;
        try {
            String zipFileName = subtaskID.concat(".zip");
            for (int i=0; i<filePaths.length; i++) {
                System.out.println(i + ". " + filePaths[i]);
            }
            System.out.println("Zip File Name : " + zipFileName);
            zippedFile = new File(schedulerCache, zipFileName);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zippedFile)));

            for (String aFile : filePaths) {
                File file = new File(aFile);
                FileInputStream fis = new FileInputStream(file);
                zos.putNextEntry(new ZipEntry(new File(aFile).getName()));
                int length;
//                byte[] bytes = Files.readAllBytes(Paths.get(aFile));
                byte[] buffer = new byte[1024];

                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, buffer.length);
                }
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

    public void printFilesInDir(File dir) {
        File[] files = dir.listFiles();
        System.out.println(" FILES IN DIR : " + dir.getName());
        for (int i=0; i<files.length; i++) {
            System.out.println(dir.getName() + " | " + i + ". " + files[i].getName());
        }
        System.out.println(" --- print end ---");
    }

    public void printTaskInfo(String taskID, String subtaskID, String nodeEmail) {
        System.out.println("---- TASK INFO (SCHEDULER - printTaskInfo) ----");
        System.out.println("Task ID : " + taskID);
        System.out.println("Subtask ID :" + subtaskID);
        System.out.println("Node-Email : " + nodeEmail);
        System.out.println("--------- (SCHEDULER - printTaskInfo) ---------");
    }

    public class SubtaskPackage {
        File script;
        File renderfile;
        boolean assigned = false;

        public SubtaskPackage(File script, File renderfile) {
            this.script = script;
            this.renderfile = renderfile;
            this.assigned = false;
        }

        public void setAssigned(boolean assign) {
            assigned = assign;
        }

        public boolean getAssigned() {
            return assigned;
        }

        public String getScriptName() {
            return script.getName();
        }


    }
}


