package nebula.nebulaserver;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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
import java.text.SimpleDateFormat;
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
    private String taskReceiverServlet = "https://nebula-server.herokuapp.com/upload";
    private String resultsServer = "https://nebula-server.herokuapp.com/complete";

    private final String latestNodeVersion = "1.3.15";
    private final String[] filesToZip = new String[1];
    private Deque<File> subtaskQueue = new ArrayDeque<>();
    private LinkedHashMap<String, ArrayList<String>> scheduledMap = new LinkedHashMap<>();

    // doGet receives requests from the Rescheduler class to update the SubtaskQueue with tasks that needs to be re-scheduled.
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String taskID = req.getHeader("Task-Identity");
        String subtaskID = req.getHeader("Subtask-Identity");
        String nodeEmail = req.getHeader("Node-Email");
        String userEmail = req.getHeader("User-Email");
        String ipAddress = req.getHeader("IP-Address");

        System.out.println("------------ RE-SCHEDULE REQUEST from " + nodeEmail + " (SCHEDULER - doGet) ------------");

        if (taskID == null) {
            System.out.println("SCHEDULER | Nothing to re-schedule.");

        } else {
            File taskDir = new File(taskDatabase, taskID);
            File subtaskDir = new File(taskDir, "subtasks");
            File[] subtaskArray = subtaskDir.listFiles();
            Arrays.sort(subtaskArray);

            if (subtaskArray.length > 0) {

                System.out.println("SCHEDULER | Re-scheduling . . .");

                for (int i = 0; i < subtaskArray.length; i++) {
                    File file = subtaskArray[i].getAbsoluteFile();
                    if (file.getName().contains(subtaskID)
                            && !subtaskQueue.contains(file)) {

                            subtaskQueue.addFirst(subtaskArray[i].getAbsoluteFile());
                            updateScheduledMap(userEmail, ipAddress, "remove");
                            System.out.println("SCHEDULER | " + subtaskID + " has been re-added to the Task Queue - Due for re-scheduling immediately.");
                            break;
                    }
                }

                checkSubtaskQueue();
            } else {
                System.out.println("SCHEDULER | Subtask Directory is empty. Files : " + subtaskArray.length);
            }
            System.out.println(" ---- (SCHEDULER - doGet) ----");
        }
    }

    // doPut receives requests from the TaskReceiver class to update the subtaskQueue with new TaskIDs for scheduling.
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        String taskID = req.getHeader("Task-Identity");

        if (taskID != null) {
            System.out.println("SCHEDULER | New Task Request - Task Identity : " + taskID);
            File taskDir = new File(taskDatabase, taskID);
            File subtaskDir = new File(taskDir, "subtasks");
            File[] subtaskArray = subtaskDir.listFiles();
            Arrays.sort(subtaskArray);

            for (int i = 0; i < subtaskDir.listFiles().length; i++) {
                subtaskQueue.add(subtaskArray[i].getAbsoluteFile());
            }
        } else {
            System.out.println("[ERROR] TaskID not given in request. ");
        }
    }

    // doDelete takes request from the ResultReceiver class to delete a task from the Scheduler queue one the result has been received - specifically Re-scheduled tasks.
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean taskRemoved = false;
        String subtaskID = req.getHeader("Subtask-Identity");
        String taskID = req.getHeader("Task-Identity");
        String userEmail = req.getHeader("User-Email");
        String ipAddress = req.getHeader("IP-Address");

        File taskDir = new File(taskDatabase, taskID);
        File subtaskDir = new File(taskDir, "subtasks");
        File[] subtaskArray = subtaskDir.listFiles();
        Arrays.sort(subtaskArray);

        for (int i=0; i<subtaskArray.length; i++) {

            File file = subtaskArray[i].getAbsoluteFile();

            if (file.getName().contains(subtaskID)) {
                if (subtaskQueue.contains(file)) {
                    subtaskQueue.remove(file);
                    taskRemoved = true;
                    System.out.println("SCHEDULER | " + file.getName() + " removed from Subtask Queue.");

                } else {
                    System.out.println("SCHEDULER | " + file.getName() + " doesn't exist in Subtask Queue.");
                }
            }
        }
        updateScheduledMap(userEmail, ipAddress, "remove");

        resp.setHeader("Task-Removed", String.valueOf(taskRemoved));

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
            System.out.println("SCHEDULER | Node Invalid");
        } else {
                    if (subtaskQueue.size() == 0) {
                        System.out.println("SCHEDULER | There are no tasks to compute at this time.");
                        out.write("SCHEDULER | There are no tasks to compute at this time.".getBytes("UTF-8"));
                        response.addHeader("Subtask-Params", "null");
                    } else {
                        try {
                            Iterator<File> queueIterator = subtaskQueue.iterator();

                            if (queueIterator.hasNext()) {
                                File tileScript = queueIterator.next();

                                String subtaskParams = getMetadata(tileScript.toPath(), "Subtask-Params");
                                String subtaskID = getMetadata(tileScript.toPath(), "Subtask-ID");
                                String userEmail = getMetadata(tileScript.toPath(), "User-Email");
                                int computeRate = Integer.parseInt(getMetadata(tileScript.toPath(), "Compute-Rate"));

                                if (checkUserSchedulingLimit(userEmail, ipAddress, computeRate)) {
                                    subtaskQueue.remove(tileScript);

                                    response.reset();
                                    response.addHeader("Subtask-Params", subtaskParams);
                                    response.addHeader("Task-Package", "attachment; filename=\"test-arc.zip\"");
                                    response.setContentType("application/zip");
                                    response.setStatus(HttpServletResponse.SC_OK);

                                    filesToZip[0] = tileScript.getAbsolutePath();
                                    File zippedSubtaskPackage = zipFiles(subtaskID, filesToZip);
                                    BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(zippedSubtaskPackage));              // InputStream of TileScript
                                    byte[] buffer = new byte[(int) zippedSubtaskPackage.length()];
                                    int length;

                                    while ((length = inputStream.read(buffer)) > 0) {                                                        // Output of renderfile to POST-Responses from Node
                                        out.write(buffer, 0, length);
                                    }
                                    inputStream.close();
                                    out.flush();
                                    out.close();

                                    putPendingResults(subtaskID, ipAddress, userEmail);
                                }
                            } else {
                                System.out.println("SCHEDULER | Error - Subtask Queue already empty.");
                            }
                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    }
            }
        System.out.println("----------------------- (SCHEDULER - doPost) -----------------------");
    }

    public boolean updateScheduledMap(String userEmail, String ipAddress, String action) {
        boolean updated = false;

        if (scheduledMap.containsKey(userEmail)) {
            ArrayList<String> scheduledPCs = scheduledMap.get(userEmail);

            if (action.equals("add")) {
                scheduledPCs.add(ipAddress);
            } else if (action.equals("remove")) {
                scheduledPCs.remove(ipAddress);
            }
            System.out.println("SCHEDULER | " + userEmail + "'s Scheduled PCs List has been updated. Size : " + scheduledPCs.size());
            updated = true;
        } else if (!scheduledMap.containsKey(userEmail)) {
            System.out.println("SCHEDULER | " + userEmail + " could not be found on the Scheduled Map. Update failed.");
        }

        return updated;
    }

    public boolean checkUserSchedulingLimit(String userEmail, String ipAddress, int computeRate) {
        boolean canAssign = false;

        if (scheduledMap.containsKey(userEmail)) {
            ArrayList<String> scheduledPCs = scheduledMap.get(userEmail);

            if (scheduledPCs.size() < computeRate) {
                System.out.println("SCHEDULER | " + userEmail + " has more room for scheduling. Subscription : " + computeRate + " | Scheduled : " + scheduledPCs.size());

                scheduledPCs.add(ipAddress);
                canAssign = true;
            }
        } else {
            System.out.println("SCHEDULER | " + userEmail + " not found in Scheduling Map. Ready for scheduling.");
            ArrayList<String> scheduledPCs = new ArrayList<>();
            scheduledPCs.add(ipAddress);

            scheduledMap.put(userEmail, scheduledPCs);
            canAssign = true;
        }

        return canAssign;
    }

    public void putPendingResults(String subtaskID, String nodeIp, String userEmail) {
        String currentTime = new SimpleDateFormat("HH h.MM m.SS s").format(new Date());

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            HttpUriRequest request = RequestBuilder
                    .put(resultsServer)
                    .build();

            request.setHeader("Subtask-Identity", subtaskID);
            request.setHeader("Time-Scheduled", currentTime);
            request.setHeader("IP-Address", nodeIp);
            request.setHeader("User-Email", userEmail);

            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            log("[LOG] Executing request " + request.getRequestLine() + " | Status : " + status);
            httpClient.close();
            response.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean verifyNode (NodeUser.Node node, ServletOutputStream outputStream) throws IOException {
        boolean verified = false;

        System.out.println("---- Node Verification for " + node.getNodeEmail() + " (SCHEDULER - verifyNode) -----------------");

        if (node.getNodeEmail() == null || node.getNodeEmail().isEmpty() || node.getNodeEmail().equals("kilrah@andrebernet.ch") || node.getNodeEmail().equals("vv@labordei.com")) {                                                                                     // Checks nodeEmail Parameter and if validated, moves on to deviceIdentity Parameter.
            System.out.println("SCHEDULER | VERIFY - 1. Node Email invalid. Email : " + node.getNodeEmail());
            outputStream.write("Your username is invalid.".getBytes("UTF-8"));

        } else {
            System.out.println("SCHEDULER | VERIFY - 1. Node Email : " + node.getNodeEmail());

            if (!node.getProductVersion().contains(latestNodeVersion)) {
                System.out.println("SCHEDULER | VERIFY - 2. Node Product Version is outdated. Node Version : " + node.getProductVersion());
                String updateMessage = String.format("SERVER | Your Node Version is outdated. Please install the latest update - (Version : " + latestNodeVersion + ")");
                outputStream.write(updateMessage.getBytes("UTF-8"));

            } else if (node.getProductVersion().contains(latestNodeVersion)) {
                System.out.println("SCHEDULER | VERIFY - 2. Node Product Version is updated. Node Version : " + node.getProductVersion());

                if (node.getDeviceID() == null || node.getDeviceID().isEmpty()) { // || node.getDeviceID().contains("O.E.M")) {                                                                          // Checks deviceIdentity Parameter and if validated, moves on to nodeScore Parameter.
                    System.out.println("SCHEDULER | VERIFY - 3. Device Identity invalid.");
                    outputStream.write("SERVER | Your Device ID is invalid.".getBytes("UTF-8"));

                } else {
                    System.out.println("SCHEDULER | VERIFY - 3. Device Identity : " + node.getDeviceID());

                    if (!InetAddressUtils.isIPv4Address(node.getIpAddress()) && !InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        System.out.println("SCHEDULER | VERIFY - 4. Node IP Address invalid.");
                        outputStream.write("SERVER | Your IP Address is invalid.".getBytes("UTF-8"));

                    } else if (InetAddressUtils.isIPv4Address(node.getIpAddress()) || InetAddressUtils.isIPv6Address(node.getIpAddress())) {
                        System.out.println("SCHEDULER | VERIFY - 4. IP Address : " + node.getIpAddress());


                    if (Integer.valueOf(node.getScore()) < 70) {                                             // Checks nodeScore Parameter and if validated, moves on to nodeQueue Parameter.
                        System.out.println("SCHEDULER | VERIFY - 5. Node Score invalid.");
                        outputStream.write("SERVER | Your Node Score is invalid / too low.".getBytes("UTF-8"));

                    } else if (Integer.valueOf(node.getScore()) >= 70) {
                        System.out.println("SCHEDULER | VERIFY - 5. Node Score : " + node.getScore());

                            if (node.getNodeQueue() > 0) {                                                    // Checks nodeQueue Parameter and if validated, all necessary gateway Parameters are valid and Subtasks can be scheduled to Node.
                                System.out.println("SCHEDULER | VERIFY - 6. Queue invalid. " + node.getNodeEmail() + "'s Queue Size : " + node.getNodeQueue());
                                outputStream.write("SERVER | Your Device Queue is full.".getBytes("UTF-8"));
                                outputStream.flush();
                                outputStream.close();

                            } else {
                                    verified = true;
                                    System.out.println("SCHEDULER | VERIFY - 6. Node Queue : " + node.getNodeQueue());
                                    System.out.println("SCHEDULER | VERIFY - " + node.getNodeEmail() + " is verified.");
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
        Iterator iterator = subtaskQueue.iterator();
        int i=1;

//        System.out.println("SUBTASK_QUEUE_CHECK ---");
        System.out.println("SCHEDULER | SUBTASK QUEUE SIZE : " + subtaskQueue.size());
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
                System.out.println("SCHEDULER | " + i + ". " + filePaths[i]);
            }
            System.out.println("SCHEDULER | Zip File Name : " + zipFileName);
            zippedFile = new File(schedulerCache, zipFileName);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zippedFile)));

            for (String aFile : filePaths) {
                File file = new File(aFile);
                FileInputStream fis = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(file.getName());
                zos.putNextEntry(zipEntry);
                int length;
                byte[] bytes = Files.readAllBytes(Paths.get(aFile));
//                byte[] buffer = new byte[1024];

                while ((length = fis.read(bytes)) > 0) {
                    zos.write(bytes, 0, length);
                }
                fis.close();
                zos.closeEntry();
            }
            zos.finish();
            zos.close();

        } catch (FileNotFoundException ex) {
            System.err.println("[ERROR] A file does not exist: " + ex);
        } catch (IOException ex) {
            System.err.println("[ERROR] I/O error: " + ex);
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
        System.out.println("SCHEDULER | FILES IN DIR : " + dir.getName());
        for (int i=0; i<files.length; i++) {
            System.out.println("SCHEDULER | " + dir.getName() + " | " + i + ". " + files[i].getName());
        }
        System.out.println(" --- print end ---");
    }

    public void printTaskInfo(String taskID, String subtaskID, String nodeEmail) {
        System.out.println("---- TASK INFO (SCHEDULER - printTaskInfo) ----");
        System.out.println("SCHEDULER | Task ID : " + taskID);
        System.out.println("SCHEDULER | Subtask ID :" + subtaskID);
        System.out.println("SCHEDULER | Node-Email : " + nodeEmail);
        System.out.println("---------------------------");
    }

//    public class SubtaskPackage {
//        File script;
////        File renderfile;
//        boolean assigned = false;
//
//        public SubtaskPackage(File script) {
//            this.script = script;
////            this.renderfile = renderfile;
//            this.assigned = false;
//        }
//
//        public void setAssigned(boolean assign) {
//            assigned = assign;
//        }
//
//        public boolean getAssigned() {
//            return assigned;
//        }
//
//        public String getScriptName() {
//            return script.getName();
//        }
//
//
//    }
}


