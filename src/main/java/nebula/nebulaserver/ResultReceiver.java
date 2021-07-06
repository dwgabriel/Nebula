package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteResult;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.dropbox.core.v2.users.FullAccount;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;


/**
 * Created by Daryl Wong on 4/3/2019.
 */

@WebServlet(
        name = "ReceiverServlet",
        urlPatterns = {"/complete"}
)

@MultipartConfig
public class ResultReceiver extends HttpServlet {

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    public LinkedHashMap<String, String> pendingResults = new LinkedHashMap<>();
    public LinkedHashMap<String, Integer> pendingResultsRescheduled = new LinkedHashMap<>();
    String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

    private String resultParams;

    private int score;

    private LinkedHashMap<String, ArrayList> taskCostsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, String> resultParamsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, NodeUser> nodeUsersMap = new LinkedHashMap<>();
    private LinkedHashMap<String, CompletedRenderTask> completedTasksMap = new LinkedHashMap<>();
    private final Logger log = Logger.getLogger("nebula.nebulaserver.ResultReceiver");
    private String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";
    private String rescheduleServer = "https://nebula-server.herokuapp.com/reschedule";

    final DbxRequestConfig config = Server.config;
    final DbxClientV2 client = Server.client;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            if (request.getHeader("PROMPT").equals("EARNINGS")) {
                System.out.println("RESULT RECEIVER | ACTIVE NODE_USER SIZE : " + nodeUsersMap.size());
                if (nodeUsersMap.size() > 0) {
                    Iterator<NodeUser> iterator = nodeUsersMap.values().iterator();
                    int counter = 1;
                    while (iterator.hasNext()) {
                        System.out.println("RESULT RECEIVER | " + counter + ". Emailing Earnings Summary for Node : " + nodeUsersMap.get("nodeEmail"));
                        emailEarningsSummary(iterator.next());
                    }
                }

            } else if (request.getHeader("PROMPT").equals("PENDING")) {
                if (!pendingResults.isEmpty()) {
                    checkPendingResults();
                } else {
                    System.out.println("RESULT RECEIVER | No Pending Results.");
                }
            }

        } catch (MessagingException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String subtaskID = request.getHeader("Subtask-Identity");
        String timeScheduled = request.getHeader("Time-Scheduled");
        String ipAddress = request.getHeader("IP-Address");
        String userEmail = request.getHeader("User-Email");
        String pendingResultValue = ipAddress + " - " + timeScheduled + "-" + userEmail;

        if (!pendingResults.containsKey(subtaskID)) {
            pendingResults.put(subtaskID, pendingResultValue);
        } else {
            pendingResults.put(subtaskID, pendingResultValue+" - Re-scheduled");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File finalResult;

        try {
            String resultParams = request.getParameter("Result-Params");
            Part resultFilePart = request.getPart("Render-Result");
            String fileName = Paths.get(resultFilePart.getSubmittedFileName()).getFileName().toString();

                if (resultParams != null && resultFilePart.getSize() > 0) {
                    response.setHeader("Result-Received", "true");
                    resultParamsMap = extractResultParams(resultParams);

                    //  COST MANAGEMENT SECTION :
                    SubtaskCosts subtaskCost = new SubtaskCosts(resultParamsMap.get("nodeEmail"),
                            resultParamsMap.get("ipAddress"),
                            resultParamsMap.get("subtaskID"),
                            resultParamsMap.get("cost"),
                            resultParamsMap.get("computeSeconds"));
                    addSubtaskCostToTaskCostsMap(subtaskCost);

                    // NODE USER & NODE DEVICE MANAGEMENT :
                    NodeUser nodeUser = checkNodeUser(resultParamsMap.get("nodeEmail"));
                    nodeUser.addToCompletedSubtasks(resultParamsMap.get("deviceID"), resultParamsMap.get("ipAddress"), subtaskCost);

                    // REMOVE & UPDATE PENDING RESULTS LIST AND SCHEDULER QUEUE.
                    pendingResults.remove(resultParamsMap.get("subtaskID"));
                    pendingResultsRescheduled.remove(resultParamsMap.get("subtaskID"));
                    deleteReceivedTasks(resultParamsMap.get("subtaskID"), resultParamsMap.get("userEmail"), resultParamsMap.get("ipAddress"));

                    // TILE RESULT RECEPTION & COMPILATION OF FINAL RESULT
                    finalResult = compileFinalResult(resultFilePart, fileName);

                    // RETURNING OF FINAL RESULT TO USER
                    if (finalResult != null) {
                        handleFinalResult(finalResult, resultParamsMap.get("userEmail"), resultParamsMap.get("taskID"), resultParamsMap.get("renderfileName"));
                    }
                } else {
                    response.setHeader("Result-Received", "false");
                    System.out.println("[ERROR] Results received from " + resultParamsMap.get("nodeEmail") + " is null. ");
                }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteReceivedTasks (String subtaskID, String userEmail, String ipAddress) {

        String taskID = subtaskID.substring(0, 12);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            HttpUriRequest request = RequestBuilder
                    .delete(schedulerServlet)
                    .build();

            request.setHeader("Task-Identity", taskID);
            request.setHeader("Subtask-Identity", subtaskID);
            request.setHeader("User-Email", userEmail);
            request.setHeader("IP-Address", ipAddress);
            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            System.out.println("RESULT RECEIVER Executing request " + request.getRequestLine() + " | Status : " + status);
            boolean taskRemoved = Boolean.parseBoolean(response.getFirstHeader("Task-Removed").getValue());
            System.out.println("RESULT RECEIVER | " + subtaskID + " DELETE STATUS : " + taskRemoved);
            httpClient.close();
            response.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void postReschedule(String subtaskID, String userEmail, String ipAddress) throws IOException {

        String taskID = subtaskID.substring(0, 12);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpEntity data = EntityBuilder.create()                                                                    // Build entity to inform Server that this Node called STOP, and needs to re-schedule its subtask.
                    .setContentEncoding("UTF-8")                                                                        // Entity includes original Task Identity, Subtask Identity and TileScript
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("Node-Email", "admin"),
                            new BasicNameValuePair("Device-Identity", "admin"),
                            new BasicNameValuePair("Task-Identity", taskID)
                            , new BasicNameValuePair("Subtask-Identity", subtaskID)
                            , new BasicNameValuePair("User-Email", userEmail)
                            , new BasicNameValuePair("IP-Address", ipAddress), new BasicNameValuePair("Reason", "(admin). Overtime."))
                    .build();

            HttpUriRequest request = new HttpPost(rescheduleServer);
            ((HttpPost) request).setEntity(data);
            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            log("[LOG] Executing request " + request.getRequestLine() + " | Status : " + status);
            httpClient.close();
            response.close();

            if (pendingResultsRescheduled.containsKey(subtaskID)) {
                int timesRescheduled = pendingResultsRescheduled.get(subtaskID) + 1;
                pendingResultsRescheduled.put(subtaskID, timesRescheduled);
            } else {
                pendingResultsRescheduled.put(subtaskID, 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // This method checks for results of tasks that has been scheduled and calculates how long has passed since the task has been scheduled and results haven't been received.
    // If the result isn't received 30 minutes after the task has been scheduled, a request for re-scheduling is called as a failsafe in case task failed to render and became idle.
    // This helps prevent task congestion when all results are back and a single subtask stops the pipeline of renders.
    public void checkPendingResults() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH h.MM m.SS s");

        try {
            System.out.println("PENDING RESULTS : " + pendingResults.size());
                for (Map.Entry<String, String> entry : pendingResults.entrySet()) {
                    String currentTime = new SimpleDateFormat("HH h.MM m.SS s").format(new Date());
                    Date parsedCurrentTime = dateFormat.parse(currentTime);

                    String[] pendingResultValues = entry.getValue().split("-"); // pendingResults Values include 2 values - ipAddress and assignedTime. This splits them with "-" and ipAddress is first, followed by assignedTime.
                    String ipAddress = pendingResultValues[0];
                    String scheduledTime = pendingResultValues[1];
                    String userEmail = pendingResultValues[2];
                    Date parsedScheduledTime = dateFormat.parse(scheduledTime);

                    long elapsed = (parsedCurrentTime.getTime() - parsedScheduledTime.getTime()) / 1000;        // Calculates the elapsed time between ScheduledTime and CurrentTime in Seconds.
                    System.out.println("RESULT RECEIVER | " + entry.getKey() + " - Elapsed Time (S): " + elapsed + " | Assigned to : " + ipAddress + " | Times-Rescheduled : " + pendingResultsRescheduled.get(entry.getKey()));

                    if (!pendingResultsRescheduled.containsKey(entry.getKey()) || pendingResultsRescheduled.get(entry.getKey()) < 3) {
                        if (elapsed >= 1800) { // 50 mins - 3000 seconds | 30 minutes - 1800 seconds | 10 minutes - 600 seconds
                            System.out.println("RESULT RECEIVER | " + entry.getKey() + " results not received within 30 minutes. Calling Re-schedule request.");
                            postReschedule(entry.getKey(), userEmail, ipAddress);
                        }
                    }
                }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public NodeUser checkNodeUser(String nodeEmail) {
        NodeUser nodeUser;
        if (nodeUsersMap.get(nodeEmail) == null) {
            nodeUser = new NodeUser(nodeEmail);
            nodeUsersMap.put(nodeUser.getNodeEmail(), nodeUser);
        } else {
            nodeUser = nodeUsersMap.get(nodeEmail);
        }

        return nodeUser;
    }

    public File compileFinalResult(Part resultFilePart, String fileName) throws IOException {
        String taskID = resultParamsMap.get("taskID");
        CompletedRenderTask renderTask = null;
        File finalResult = null;

            String frameID = String.format("%s_%03d_%03d",
                    resultParamsMap.get("taskID"),
                    Integer.valueOf(resultParamsMap.get("frameCount")),
                    Integer.valueOf(resultParamsMap.get("renderFrame")));

            if (completedTasksMap.get(taskID) == null) {
                renderTask = new CompletedRenderTask(resultParamsMap);
                completedTasksMap.put(taskID, renderTask);
            } else {
                renderTask = completedTasksMap.get(taskID);
            }

            finalResult = renderTask.addToResults(resultFilePart, fileName, frameID);

        return finalResult;
    }

    public void addSubtaskCostToTaskCostsMap(SubtaskCosts subtaskCost) {
        String taskID = resultParamsMap.get("taskID");
        String subtaskID = resultParamsMap.get("subtaskID");

        if (taskCostsMap.get(taskID) != null) {
            taskCostsMap.get(taskID).add(subtaskCost);
            System.out.println("RESULT RECEIVER | " + taskID + " SubtaskCostList exist. " + subtaskID + " added to the list.");
        } else {
            taskCostsMap.put(taskID, new ArrayList());
            taskCostsMap.get(taskID).add(subtaskCost);
            System.out.println("RESULT RECEIVER | " + taskID + " SubtaskCostList created.");
        }
    }

    private LinkedHashMap<String, String> extractResultParams (String paramsString) {
        ArrayList<String> params = new ArrayList<>();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        String s = paramsString.replaceAll("[\"{}]", "");
        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter=0;
        while (scanner.hasNext()) {
            params.add(counter, scanner.next());
            counter++;
        }

        // Scan the resultParams String to identify what application and task is uploaded. Then add Key and Value of parameters to the map respectively.
        // New Application Task Types are to be added here.

            map.put("nodeEmail", params.get(0));
            map.put("deviceID", params.get(1));
            map.put("ipAddress", params.get(2));
            map.put("taskID", params.get(3));
            map.put("subtaskID", params.get(4));
            map.put("userEmail", params.get(5));
            map.put("computeSeconds", params.get(6));
            map.put("computeMinutes", params.get(7));
            map.put("cost", params.get(8));
            map.put("subtaskCount", params.get(9));
            map.put("frameCount", params.get(10));
            map.put("frameCategory", params.get(11));
            map.put("application", params.get(12));
            map.put("renderfileName", params.get(13));
            map.put("renderOutputType", params.get(14));
            map.put("renderFrame", params.get(15));
            map.put("uploadfileName", params.get(16));
            map.put("userSubscription", params.get(17));
            map.put("userAllowance", params.get(18));
            map.put("computeRate", params.get(19));

        System.out.println("RESULT RECEIVER | UPLOAD PARAMS : ");
        map.forEach((key, value) -> System.out.println(key + ":" + value));

        scanner.close();

        return map;
    }

    public String calculateTotalCost(String taskID) {
        double totalCost = 0;
        DecimalFormat costFormat = new DecimalFormat("##.##");
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);

        System.out.println("RESULT RECEIVER | " + taskID + " Costs : ");
        for (int i=0; i<subtaskCosts.size(); i++) {
            totalCost += subtaskCosts.get(i).cost;
            System.out.println(subtaskCosts.get(i).subtaskID + " : " + subtaskCosts.get(i).cost);
        }

        return costFormat.format(totalCost);
    }

    public String calculateTotalComputeTime(String taskID) {
        double totalComputeSeconds = 0;
        DecimalFormat timeFormat = new DecimalFormat("#.##");
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalComputeSeconds += Double.parseDouble(subtaskCosts.get(i).computeSeconds);
        }

        return timeFormat.format(totalComputeSeconds);
    }

//    public String calculateAdditionalCharge(double totalComputeSeconds, double userAllowance) {
//        String additionalCost = "0";
//        double vrayAddCostRate = 0.25;
//        double blendAddCostRate = 0.2;
//        double ratePerHour = 1.00;
//        double totalComputeHours = totalComputeSeconds / 3600; // Convert compute time from seconds to hours.
//
//        if (userAllowance > totalComputeSeconds) {
//            double remainder = userAllowance - totalComputeSeconds
//        }
//
//        double remainder = totalComputeHours - userAllowance;
//        if (remainder > 0) {
//            double remainderCost = round((remainder * ratePerHour), 2);
//            additionalCost = String.valueOf(remainderCost);
//        }
//
//        return additionalCost;
//    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void handleFinalResult(File finalResult,
                                  String userEmail,
                                  String taskID,
                                  String renderfileName) {

        String totalCost = calculateTotalCost(taskID);
        String totalComputeSeconds = calculateTotalComputeTime(taskID);
//        String additionalCost = calculateAdditionalCharge(Double.parseDouble(totalComputeSeconds), Double.parseDouble(resultParamsMap.get("userAllowance"))); // TODO

        try {
            System.out.println("RESULT RECEIVER | UPLOADING RENDER TO DROPBOX . . . " );
            String renderDropBoxPath = uploadResultToDropbox(finalResult);
            String renderDropboxURL = getShareLink(renderDropBoxPath);

            System.out.println("RESULT RECEIVER | UPLOADING RENDER TO NEBULA.MY . . .");
            if (!postRenderToNebula(userEmail,
                    renderfileName,
                    taskID,
                    totalCost,
                    totalComputeSeconds,
                    renderDropboxURL)) {
                postRenderToNebula(userEmail, renderfileName, taskID, totalCost, totalComputeSeconds, renderDropboxURL);
            }

            ClientResponse resultResponse = emailResults(userEmail, renderDropboxURL);
            System.out.println("RESULT_RECEIVER | Result Response : " + resultResponse.toString());

            ClientResponse paymentsResponse = emailPayments(userEmail, totalCost, taskID);
            System.out.println("RESULT_RECEIVER | Payments Response : " + paymentsResponse.toString());

            removeTaskDir(taskID);
            removeRenderfileDbx(taskID, resultParamsMap.get("application"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeTaskDir(String taskID) {
        File[] taskArray = taskDatabase.listFiles();

        System.out.println("RESULT RECEIVER | Clear TaskDir");
        for (int i=0; i<taskArray.length; i++) {
            File taskDir = taskArray[i];
            if (taskDir.getName().contains(taskID)) {
                System.out.println(taskID + " is being deleted from the Task Database.");
                taskDir.getAbsoluteFile().delete();
            }
        }
    }

    public void removeRenderfileDbx(String taskID, String application) {

        try {
            // Find and delete renderfile (.vrscene) from dropbox/render/
            String renderfileName = taskID;
            if (application.contains("vray")) {
                renderfileName = renderfileName + ".vrscene";
            } else if (application.contains("blender")) {
                renderfileName = renderfileName + ".blend";
            }
            String renderfilePath = "/render/" + renderfileName;
            DeleteResult deleteRenderfile = client.files().deleteV2(renderfilePath);
            String renderfile = deleteRenderfile.getMetadata().getName();

            // Find and delete packedSkp from dropbox/render/
            String packedSkpName = taskID + "_packedSKP".concat(".zip");
            String packedSkpPath = "/render/" + packedSkpName;
            DeleteResult deletePackedSkp = client.files().deleteV2(packedSkpPath);
            String packedSkp = deletePackedSkp.getMetadata().getName();
            System.out.println("RESULT RECEIVER | " + renderfile + " & " + packedSkp + " has been deleted from Dropbox/render.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean postRenderToNebula(String userEmail,
                                          String renderfileName,
                                          String taskId,
                                          String cost,
                                          String computeTime,
                                          String renderDropboxURL) throws IOException {
        boolean postSuccess = false;

        String postRenderURL = "https://www.nebula.my/_functions/Render";
        String testURL = "https://www.nebula.my/_functions-dev/Render/";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpUriRequest request = new HttpPost(postRenderURL);
            request.addHeader("renderfile-name", renderfileName);
            request.addHeader("task-identity", taskId);
            request.addHeader("cost", cost);
            request.addHeader("user-email", userEmail);
            request.addHeader("compute-time", computeTime);
            request.addHeader("render-url", renderDropboxURL);

            CloseableHttpResponse response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            System.out.println("RESULT RECEIVER | Executing request " + request.getRequestLine() + " | Status : " + status);

            if (status == 200) {
                postSuccess = true;
            } else {
                System.out.println("RESULT RECEIVER | Failed to update nebula.my.");
            }

            httpClient.close();
            response.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return postSuccess;
    }

    private String uploadResultToDropbox(File render) { // Uploads Result to Dropbox for users to view/download whenever they want through the dropbox link. NEGATIVE - Growing expense to host user renders.
        String path;
        try {
            FullAccount account = client.users().getCurrentAccount();
            System.out.println("RESULT RECEIVER | " + account.getName().getDisplayName());

            InputStream in = new FileInputStream(render);
                FileMetadata metadata = client.files().uploadBuilder("/completedRenders/" + render.getName())
                        .uploadAndFinish(in);

                path = metadata.getPathLower();
                return path;
        } catch (Exception e) {
            System.out.println("[ERROR] Uploading results to Dropbox.");
            e.printStackTrace();
            return null;
        }
    }

    public String getShareLink(String path) {
        try {
            String url;

            SharedLinkMetadata metadata = client.sharing().createSharedLinkWithSettings(path);
            url = metadata.getUrl();
            return url.replace("?dl=0", "?dl=1"); //?raw=1

        }  catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void listCompletedRenders() {
        try {
            ListFolderResult result = client.files().listFolder("/completedRenders");

            System.out.println("RESULT RECEIVER | List Renders : ");
            while (true) {
                for (Metadata metadata : result.getEntries()) {
                    System.out.println(metadata.getPathLower());
                }

                if (!result.getHasMore()) {
                    break;
                }
                result = client.files().listFolderContinue(result.getCursor());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ClientResponse emailResults(String userEmail,
                                        String renderDropboxURL)
            throws IOException, MessagingException {

        final String from = "admin@nebula.my";
        final String paymentUrl = "https://www.nebula.my/pay";
        final String myJobsUrl = "https://www.nebula.my/account/myjobs";

        Client client = Client.create();
        client.addFilter(new HTTPBasicAuthFilter("api", Server.mailGunApiKey));
        WebResource webResource = client.resource("https://api.mailgun.net/v3/nebula.my/messages");
        MultivaluedMapImpl formData = new MultivaluedMapImpl();

        try {
            // Your render is complete. You can download it from the link below :
            String msg = null;

//            if (Double.parseDouble(additionalCost) == 0) {
//
//                msg = "Your render is complete. You can download it from the link below : " + "\r\n" +
//                        "Render Download Link : " + renderDropboxURL + "\r\n" +
//                        "\r\n" +
//                        "You can also find your final render in the 'My Jobs' page of your account profile. My Jobs : " + myJobsUrl + "\r\n" +
//                        "\r\n" +
//                        "Render Details" +
//                        "\r\n" +
//                        "Upload File : " + resultParamsMap.get("uploadfileName") + "\r\n" +
//                        "Task ID : " + resultParamsMap.get("taskID") + "\r\n" +
//                        "Application : " + resultParamsMap.get("application") + "\r\n" +
//                        "Frame Count : " + resultParamsMap.get("frameCount") + "\r\n" +
//                        "User Subscription : " + resultParamsMap.get("userSubscription") + "\r\n" +
//                        "Compute Rate : " + resultParamsMap.get("computeRate") + "\r\n" +
//                        "Unlimited Render Time : " + resultParamsMap.get("userAllowance") + "\r\n" +
//                        "Total Render Time : " + resultParamsMap.get("computeMinutes") + "\r\n" +
//                        "Additional Charge : " + additionalCost + "\r\n" +
//                        "\r\n" +
//                        "\r\n" +
//                        "This is an auto-generated message from Nebula, but feel free to respond to this email with any queries you may have. We're always ready to help.   \r\n";
//            } else {
                msg = "Your render is complete. Please find your render details below : " + "\r\n" +
                        "\r\n" +
                        "Render Details" +
                        "\r\n" +
                        "Upload File : " + resultParamsMap.get("uploadfileName") + "\r\n" +
                        "Task ID : " + resultParamsMap.get("taskID") + "\r\n" +
                        "Application : " + resultParamsMap.get("application") + "\r\n" +
                        "Frame Count : " + resultParamsMap.get("frameCount") + "\r\n" +
                        "User Subscription : " + resultParamsMap.get("userSubscription") + "\r\n" +
                        "Compute Rate (No. of PCs) : " + resultParamsMap.get("computeRate") + "\r\n" +
                        "Total Render Time : " + resultParamsMap.get("computeMinutes") + "\r\n" +
//                        "Additional Charge : " + additionalCost + "\r\n" +
                        "\r\n" +
                        "Please make all your outstanding payments here (if any) : " + paymentUrl + "\r\n" +
                        "\r\n" +
                        "You can download your render result at the 'My Jobs' page once all outstanding payments have been made in full. " + "\r\n" +
                        "My Jobs : " + myJobsUrl + "\r\n" +
                        "\r\n" +
                        "\r\n" +
                        "This is an auto-generated message from Nebula, but feel free to respond to this email with any queries you may have. We're always ready to help. \r\n";
//            }

            if (from != null && userEmail != null && msg != null) {
                formData.add("from", from);
                formData.add("to", "<" + userEmail + ">");
                formData.add("subject", "Your render is complete! - " + resultParamsMap.get("uploadfileName"));
                formData.add("text", msg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formData);
    }

    private ClientResponse emailPayments(String userEmail, String totalCost, String taskID) throws IOException, MessagingException {
        final String from = "admin@nebula.my";
        final String pass = "DWGabriel4";
        String toEmail = "darylgabrielwong@gmail.com";
        String ccEmail = "chris_kee@hotmail.com";
        String ccEmail2 = "gizmo.chriskee@gmail.com";
        Date date = new Date();

        Client client = Client.create();
        client.addFilter(new HTTPBasicAuthFilter("api", Server.mailGunApiKey));
        WebResource webResource = client.resource("https://api.mailgun.net/v3/nebula.my/messages");
        MultivaluedMapImpl formData = new MultivaluedMapImpl();

        StringBuilder costList = new StringBuilder();
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);
        double totalComputeSeconds = 0;

        for (int i=0; i<subtaskCosts.size(); i++)
        {
            String subtask = subtaskCosts.get(i).subtaskID;
            String nodeEmail = subtaskCosts.get(i).nodeEmail;
            String ipAddress = subtaskCosts.get(i).getIpAddress();
            double cost = subtaskCosts.get(i).cost;
            String computeSeconds = subtaskCosts.get(i).computeSeconds;
            totalComputeSeconds += Double.parseDouble(computeSeconds);
            String string = i + ". " + date + " | " + subtask + " | " + nodeEmail + " (" + ipAddress + ") | COST : USD" + cost + " | Compute Second(s) : " + computeSeconds + ".   \r\n";
            costList.append(string);
        }

        String msg = "Completed Render for " + userEmail + " | Date : " + date + "\r\n" +
                "Total Compute Time (S) : " + totalComputeSeconds + " | Total Cost = USD" + totalCost + "\r\n" +
                costList;

        formData.add("from", from);
        formData.add("to", "<" + toEmail + ">");
        formData.add("cc","<" + ccEmail + ">");
        formData.add("cc","<" + ccEmail2 + ">");
        formData.add("subject", taskID + " | Completed Render for " + userEmail);
        formData.add("text", msg);

        return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formData);
    }

//    private void emailPayments(String userEmail, String totalCost, String taskID) throws IOException, MessagingException {
//        final String from = "admin@nebula.my";
//        final String pass = "DWGabriel4";
//
//        Address[] addresses = new Address[2];
//        addresses[0] = new InternetAddress("darylgabrielwong@gmail.com");
//        addresses[1] = new InternetAddress("chris_kee@hotmail.com");
//
//        Properties prop = new Properties();
//        prop.put("mail.transport.protocol", "smtp");
//        prop.put("mail.smtp.user", from);
//        prop.put("mail.smtp.auth", "true");
//        prop.put("mail.smtp.starttls.enable", "true");
//        prop.put("mail.smtp.startssl.enable", "true");
//        prop.put("mail.smtp.host", "smtp.gmail.com");
//        prop.put("mail.smtp.port", "587");
//        prop.put("mail.smtp.ssl.trust", "smtp.gmail.com");
//        prop.put("mail.smtp.debug", "true");
//
//        Authenticator auth = new Authenticator() {
//            @Override
//            protected PasswordAuthentication getPasswordAuthentication() {
//                return new PasswordAuthentication(from, pass);
//            }
//        };
//
//        Session session = Session.getInstance(prop, auth);
//        session.setDebug(true);
//
//        Message message = new MimeMessage(session);
//        message.setFrom(new InternetAddress(from));
//        message.setRecipient(Message.RecipientType.TO, new InternetAddress("darylgabrielwong@gmail.com"));
////        message.setRecipients(Message.RecipientType.TO, addresses);
//        message.setSubject("Completed Render for " + userEmail + " .   \r\n");
//
//        StringBuilder costList = new StringBuilder();
//        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);
//        double totalComputeSeconds = 0;
//
//        for (int i=0; i<subtaskCosts.size(); i++)
//        {
//            String subtask = subtaskCosts.get(i).subtaskID;
//            String nodeEmail = subtaskCosts.get(i).nodeEmail;
//            String ipAddress = subtaskCosts.get(i).getIpAddress();
//            double cost = subtaskCosts.get(i).cost;
//            String computeSeconds = subtaskCosts.get(i).computeSeconds;
//            totalComputeSeconds += Double.parseDouble(computeSeconds);
//            Date date = new Date();
//            String string = i + ". " + date + " | " + subtask + " | " + nodeEmail + " (" + ipAddress + ") | COST : USD" + cost + " | Compute Second(s) : " + computeSeconds + ".   \r\n" + " <p/>";
//            costList.append(string);
//        }
//
//        String msg = "Total Compute Time (S) : " + totalComputeSeconds + " | Total Cost = USD" + totalCost + "\r\n" + "<p/>"
//                + costList;
//
//        MimeBodyPart mimeBodyPart = new MimeBodyPart();
//        mimeBodyPart.setContent(msg, "text/html");
//
//        Multipart multipart = new MimeMultipart();
//        multipart.addBodyPart(mimeBodyPart);
//
//        message.setContent(multipart);
//        Transport.send(message);
//    }

    private void emailEarningsSummary(NodeUser nodeUser) throws MessagingException {                                        // todo - TO BE RECTIFIED
        try {
            Collection<SubtaskCosts> collection = nodeUser.getAllCompletedSubtasks().values();
            Iterator<SubtaskCosts> iterator = collection.iterator();
//            ArrayList<CompletedTask.CompletedSubtask> nodeUserAllCompletedSubtasks = nodeUser.getAllCompletedSubtasks();

            EarningsBook earningsBook = new EarningsBook(nodeUser);
//            EarningsBook earningsBook = new EarningsBook(node, nodeEmail, nodes);
            System.out.println("RESULT RECEIVER | Writing Earnings Book . . . ");
            File earningsExcel = earningsBook.writeEarnings();

            final String from = "admin@nebula.my";
            final String pass = "DWGabriel4";

            Properties prop = new Properties();
            prop.put("mail.transport.protocol", "smtp");
            prop.put("mail.smtp.user", from);
            prop.put("mail.smtp.auth", "true");
            prop.put("mail.smtp.starttls.enable", "true");
            prop.put("mail.smtp.startssl.enable", "true");
            prop.put("mail.smtp.host", "smtp.gmail.com");
            prop.put("mail.smtp.port", "587");
            prop.put("mail.smtp.ssl.trust", "smtp.gmail.com");
            prop.put("mail.smtp.debug", "true");

            Authenticator auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(from, pass);
                }
            };

            Session session = Session.getInstance(prop, auth);
            session.setDebug(true);

            InternetAddress[] toAddresses = {
                    new InternetAddress("darylgabrielwong@gmail.com"),
                    new InternetAddress(nodeUser.getNodeEmail())};

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, toAddresses);
            message.setSubject("Earnings Summary for  " + nodeUser.getNodeEmail() + "\r\n");

            double totalProfit = 0;
            double totalComputeHours = 0;
            int counter = 1;

            StringBuilder costList = new StringBuilder();
            System.out.println("RESULT RECEIVER | Cost & Profit Summary : ");
            while (iterator.hasNext()) {
                String subtaskID = iterator.next().subtaskID;
                double computeHours = Double.parseDouble(iterator.next().computeSeconds);
                double profit = iterator.next().cost;
                totalProfit += profit;
                totalComputeHours += computeHours;
                String string = counter + ". " + subtaskID + " | Node Profit : RM " + profit + " | Compute Hour(s) : " + computeHours + "\r\n";
                System.out.println(string);
                costList.append(string);
                counter++;
            }

            LocalDateTime localDateTime = LocalDateTime.now();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

            String msg = "Total Earnings Today " + dtf.format(localDateTime) + " : " + totalProfit + " | Total Compute Hour(s) : " + totalComputeHours + "\r\n"
                    + "Your earnings will be paid in full to you by end of the week.";

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(msg, "text/html");

            MimeBodyPart earningsPart = new MimeBodyPart();
            earningsPart.attachFile(earningsExcel);

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(earningsPart);

            message.setContent(multipart);

            Transport.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class SubtaskCosts {
        String nodeEmail;
        String ipAddress;
        String subtaskID;
        String computeSeconds;
        double cost;

        public SubtaskCosts(String nodeEmail, String ipAddress, String subtaskID, String cost, String computeSeconds) {
            this.nodeEmail = nodeEmail;
            this.ipAddress = ipAddress;
            this.subtaskID = subtaskID;
            this.cost = Double.parseDouble(cost);
            this.computeSeconds = computeSeconds;
        }

        public String getNodeEmail() {
            return nodeEmail;
        }

        public String getSubtaskID() {
            return subtaskID;
        }

        public String getComputeSeconds() {
            return computeSeconds;
        }

        public double getCost() {
            return cost;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
}

