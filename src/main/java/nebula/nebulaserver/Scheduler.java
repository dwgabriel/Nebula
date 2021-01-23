package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
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

    private static String RootPath = new File("").getAbsolutePath();
    private static File rootDir = new File(RootPath);
    private static File database = new File(rootDir, "nebuladatabase");
    final DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/nebula-update").build();
    final DbxClientV2 client = new DbxClientV2(config, "rM8fF-GuUNAAAAAAAAAAK6ksJER9acjYeF1krFbX63InD8wn_Iq-5fDlV_1YM6gh");
    final File taskDatabase = new File(database, "tasks");
    final File schedulerCache = new File(database, "schedulercache");
    String uploadServlet = "https://nebula-server.herokuapp.com/upload";
    String taskServlet = "https://nebula-server.herokuapp.com/newTask";

    private final String latestNodeVersion = "1.0.22";
    private String subtaskParams;
    private String taskID;
    private String subtaskID;
    private final String[] filesToZip = new String[1];
    private Deque<SubtaskPackage> subtaskPackageQueue = new ArrayDeque<>();
    private ArrayList<String> activeNodes = new ArrayList<>();

//    public String getInfo() throws IOException { // Retrieves information from server. (Works)
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//
//        HttpUriRequest request = RequestBuilder
//                .get(taskServlet)
//                .build();
//
//        String taskIdentity;
//        CloseableHttpResponse response = httpClient.execute(request);
//        try {
//            taskIdentity = response.getFirstHeader("Task-Identity").getValue();
//            System.out.println("TASK IDENTITY (getInfo) : " + taskIdentity);
//            if (taskIdentity.equals("null")) {
//                return null;
//            } else {
//                return taskIdentity;
//            }
//
//        } finally {
//            response.close();
//        }
//    }

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
            File originalTaskDir = new File(taskDir, "originaltask");
            System.out.println("Re-scheduling in 2 . . . ");

            File[] subtaskArray = subtaskDir.listFiles();
            File[] originalTaskArray = originalTaskDir.listFiles();
            System.out.println("Re-scheduling in 1 . . . ");
            if (subtaskDir != null && subtaskArray.length > 0) {
                for (int i = 0; i < subtaskArray.length; i++) {
                    if (subtaskArray[i].getName().contains(this_subtaskID)) {
                        subtaskPackageQueue.addFirst(new SubtaskPackage(subtaskArray[i].getAbsoluteFile(), originalTaskArray[0].getAbsoluteFile()));
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
            File originalTaskDir = new File(taskDir, "originaltask");
            File subtaskDir = new File(taskDir, "subtasks");
            File[] subtaskArray = subtaskDir.listFiles();
            System.out.println("SCHEDULER | Subtask Dir : " + subtaskDir.getAbsolutePath());
            System.out.println("SCHEDULER | Subtask Dir Length : " + subtaskDir.length());
            System.out.println("SCHEDULER | Subtask Array : " + subtaskArray.length);
            Arrays.sort(subtaskArray);
            File[] originalTaskArray = originalTaskDir.listFiles();

            for (int i = 0; i < subtaskDir.listFiles().length; i++) {
                System.out.println("SUBTASK_DIR | " + i + ". " + subtaskArray[i].getName());
                SubtaskPackage subtaskPackage = new SubtaskPackage(subtaskArray[i].getAbsoluteFile(), originalTaskArray[0].getAbsoluteFile());
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
        String nodeVersion  = request.getParameter("Version");
        String deviceID     = request.getParameter("Device-Identity");                                                    // Retrieve <input type="text" name="deviceID"> - Identity of device being supplied by Supply User
        String ipAddress    = request.getParameter("IP-Address");
        String nodeQueue    = request.getParameter("Queue");                                                    // Retrieve <input type="text" name="queue"> -  Status of device job queue
        String nodeScore    = "95";

        NodeUser nodeUser = new NodeUser(nodeEmail);
        NodeUser.Node node = nodeUser.new Node(nodeEmail, nodeVersion, deviceID, nodeScore, ipAddress);
        node.setNodeQueue(Integer.valueOf(nodeQueue));

        ServletOutputStream out = response.getOutputStream();
        System.out.println("--------------------- Task Request from " + nodeEmail + " (SCHEDULER - doPost) ---------------------");

        if (!verifyNode(node, out)) {                                                                                            // If all criterias approved and are valid, then and only then will Subtasks be scheduled to the Node.
            System.out.println("[NODE UNVERIFIED]");
        } else {

                    // TASK ID RETRIEVAL : This section of code calls the UploadReceiver with "getInfo" to obtain the next TaskID in queue for rendering.
                    // It checks the originalTask and Subtask Directories of the given TaskID for jobs.
                    // It only proceeds if the subtaskPackageQueue is empty, otherwise the Scheduler moves on to the next block of code to schedule jobs.
                    // Based on the TaskID given, it will determine if the assigned Task is a Multi-frame or Single-frame render and add it into the subtaskPackageQueue accordingly for scheduling.
                    int taskExists = 0;                                                                                 // boolean taskExists lets Nodes know there is a task to process, otherwise continue pinging server.
                    if (subtaskPackageQueue.size() == 0) {
                        System.out.println("There are no tasks to compute at this time.");
                        out.write("There are no tasks to compute at this time.".getBytes("UTF-8"));
                        response.addHeader("Task-Exist", String.valueOf(taskExists));
                    } else {
                        try {
                            taskExists = 1;
                            SubtaskPackage subtaskPackage = subtaskPackageQueue.peek();
                            System.out.println("SCHEDULER | Subtask Package : " + subtaskPackage.getScriptName());
                            subtaskPackageQueue.remove(subtaskPackage);                         // TODO - CHANGES MADE HERE
                            // Subtasks are packaged into subtaskPackages for scheduling, and placed into a queue. Job requests from Node will peek at the queue using the FIFO method to schedule.
                            // Scheduled jobs will set the 'assigned' boolean to true to avoid double-scheduling, but more importantly to ensure those 'unassigned' will continue to be scheduled if skipped.

                            // GET Subtask Metadata from OriginalTaskFile
                            subtaskParams = getMetadata(subtaskPackage.script.toPath(), "Subtask-Params");
                            subtaskID = getMetadata(subtaskPackage.script.toPath(), "Subtask-ID");
                            System.out.println("SCHEDULER | Subtask Params : " + subtaskParams);

                            // SET Headers to supply render-critical information to Nodes pinging the Server for jobs
                            response.reset();
                            response.addHeader("Task-Exist", String.valueOf(taskExists));
                            response.addHeader("Subtask-Params", subtaskParams);
                            response.addHeader("Task-Package", "attachment; filename=\"test-arc.zip\"");

                            printTaskInfo(taskID, subtaskID, nodeEmail);
                            response.setContentType("application/zip");
                            response.setStatus(HttpServletResponse.SC_OK);

                            filesToZip[0] = subtaskPackage.script.getAbsolutePath();
//                                        filesToZip[1] = subtaskPackage.originalTaskFile.getAbsolutePath();
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
                            taskExists = 0;

                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    }
            }
        System.out.println("----------------------- (SCHEDULER - doPost) -----------------------");
    }

    private boolean verifyNode (NodeUser.Node node, ServletOutputStream outputStream) throws IOException {
        boolean verified = false;
        boolean name = false;
        boolean device = false;
        boolean score = false;
        boolean latestVersion = false;
        boolean ipValid = false;
        boolean queue = false;
        System.out.println("---- Node Verification for " + node.getNodeEmail() + " (SCHEDULER - verifyNode) -----------------");

        if (node.getNodeEmail() == null || node.getNodeEmail().isEmpty()) {                                                                                     // Checks nodeEmail Parameter and if validated, moves on to deviceIdentity Parameter.
            System.out.println("1. Node Email invalid.");
            outputStream.write("Your username is invalid.".getBytes("UTF-8"));

        } else {
            name = true;
            System.out.println("1. Node Email : " + node.getNodeEmail());

            if (!node.getProductVersion().contains(latestNodeVersion)) {
                System.out.println("2. Node Product Version is outdated. Node Version : " + node.getProductVersion());
                String updateMessage = String.format("Your Node Version is outdated. Please install the latest update - (Version : " + latestNodeVersion + ")");
                outputStream.write(updateMessage.getBytes("UTF-8"));

            } else if (node.getProductVersion().contains(latestNodeVersion)) {
                latestVersion = true;
                System.out.println("2. Node Product Version is updated. Node Version : " + node.getProductVersion());

                if (node.getDeviceID() == null || node.getDeviceID().isEmpty()) { // || node.getDeviceID().contains("O.E.M")) {                                                                          // Checks deviceIdentity Parameter and if validated, moves on to nodeScore Parameter.
                    device = false;
                    System.out.println("3. Device Identity invalid.");
                    outputStream.write("Your Device ID is invalid.".getBytes("UTF-8"));

                } else {
                    device = true;
                    System.out.println("3. Device Identity : " + node.getDeviceID());

                    if (!InetAddressUtils.isIPv4Address(node.getIpAddress()) && !InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        System.out.println("4. Node IP Address invalid.");
                        outputStream.write("Your IP Address is invalid.".getBytes("UTF-8"));

                    } else if (InetAddressUtils.isIPv4Address(node.getIpAddress()) || InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        ipValid = true;
                        System.out.println("4. IP Address : " + node.getIpAddress());


                    if (Integer.valueOf(node.getScore()) < 70) {                                             // Checks nodeScore Parameter and if validated, moves on to nodeQueue Parameter.
                        System.out.println("5. Node Score invalid.");
                        outputStream.write("Your Node Score is invalid / too low.".getBytes("UTF-8"));

                    } else if (Integer.valueOf(node.getScore()) >= 70) {
                        score = true;
                        System.out.println("5. Node Score : " + node.getScore());

                            if (node.getNodeQueue() > 0) {                                                    // Checks nodeQueue Parameter and if validated, all necessary gateway Parameters are valid and Subtasks can be scheduled to Node.
                                System.out.println("6. Queue invalid. " + node.getNodeEmail() + "'s Queue Size : " + node.getNodeQueue());
                                outputStream.write("Your Device Queue is full.".getBytes("UTF-8"));
                                outputStream.flush();
                                outputStream.close();

                            } else {
                                while (node.getNodeQueue() < 1) {
                                    queue = true;
                                    verified = true;
                                    System.out.println("6. Node Queue : " + node.getNodeQueue());
                                    System.out.println(node.getNodeEmail() + " is verified.");
                                    break;
                                }
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


//            public void zipFile(File srcFile, File zipFile) throws IOException {
//                try (FileInputStream fis = new FileInputStream(srcFile);
//                     ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
//                    zos.putNextEntry(new ZipEntry(srcFile.getName()));
//                    int len;
//                    byte[] buffer = new byte[1024];
//                    while ((len = fis.read(buffer)) > 0) {
//                        zos.write(buffer, 0, len);
//                    }
//                    zos.closeEntry();
//                }
//            }

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

    public boolean checkNodeID(NodeUser.Node node) { /// TODO - DOES NOT WORK
        boolean exists = false;
        if (!activeNodes.isEmpty()) {
            for (int i=0; i<activeNodes.size(); i++) {
                String nodeEmailIdentifier = node.getNodeEmail() + "_" + i;
                if (activeNodes.get(i) == nodeEmailIdentifier) {
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

//    public void addTester(NodeUser.Node node) {  /// TODO - DOES NOT WORK
//        int nodeIndex = 1;
//        if (checkNodeID(node)) {
//            nodeIndex++;
////            System.out.println(node.deviceID + " already exists.");
//            activeNodes.add(node.getNodeEmail() + "_" + nodeIndex);
//        } else {
//            activeNodes.add(node.getNodeEmail() + "_" + nodeIndex);
//            System.out.println(node.getDeviceID() + " added to Active Nodes.");
//        }
//    }

//    public String checkNodeUpdateConfigURL() {
//        String url = null;
//        try {
//            ListFolderResult listing = client.files().listFolderBuilder("/Node Update").start();
//
//            for (Metadata child : listing.getEntries()) {
//                System.out.println(child.getName());
//
//                if (child.getName().equals("node-update-config.txt")) {
//                    String path = child.getPathLower();
//                    url = getShareLink(path);
//                    System.out.println("Node Update URL : " + url);
//                } else {
//                    System.out.println("Node Update URL not found.");
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return url;
//    }
//
//    public String getShareLink(String path) {
//        try {
//            String url;
//
//            DbxUserSharingRequests share = client.sharing();
//            ListSharedLinksResult linksResult = client.sharing().listSharedLinksBuilder()
//                    .withPath(path)
////                    .withDirectOnly(true)
//                    .start();
//
//            List<SharedLinkMetadata> links = linksResult.getLinks();
//
//            if (links.size() > 0) {
//                url = links.get(0).getUrl();
//            } else {
//                SharedLinkSettings settings = new SharedLinkSettings(RequestedVisibility.PUBLIC, null, null, null, RequestedLinkAccessLevel.VIEWER);
//                SharedLinkMetadata metadata = share.createSharedLinkWithSettings(path, settings);
//                url = metadata.getUrl();
//            }
//            return url.replace("?dl=0", "?dl=1");
//
//        }  catch (Exception ex) {
//            ex.printStackTrace();
//            return null;
//        }
//    }

    public void printActiveNodes () {
        System.out.println("---- ACTIVE NODES (SCHEDULER - printActiveNodes ----");
        for (int i=0; i<activeNodes.size(); i++) {
            System.out.println(i + ". " + activeNodes.get(i));
        }
        System.out.println("------ (SCHEDULER - printActiveNodes) ------");
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
        File originalTaskFile;
        boolean assigned = false;

        public SubtaskPackage(File script, File originalTaskFile) {
            this.script = script;
            this.originalTaskFile = originalTaskFile;
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


