package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.*;
import com.dropbox.core.v2.users.FullAccount;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

    private String resultParams;

    private int score;

    private LinkedHashMap<String, ArrayList> taskCostsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, String> resultParamsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, NodeUser> nodeUsersMap = new LinkedHashMap<>();
    private LinkedHashMap<String, CompletedRenderTask> completedTasksMap = new LinkedHashMap<>();
    private final Logger log = Logger.getLogger("nebula.nebulaserver.ResultReceiver");
    String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";
    final DbxRequestConfig config = Server.config;
    final DbxClientV2 client = Server.client;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("-------- RECEIVER | EMAIL EARNINGS SUMMARY --------");
        if (request.getHeader("PROMPT").equals("TRUE")) {
            try {
                System.out.println("RECEIVER | ACTIVE NODE_USER SIZE : " + nodeUsersMap.size());
                if (nodeUsersMap.size() > 0) {
                    Iterator<NodeUser> iterator = nodeUsersMap.values().iterator();
                    int counter = 1;
                    while (iterator.hasNext()) {
                        System.out.println(counter + ". Emailing Earnings Summary for Node : " + nodeUsersMap.get("nodeEmail"));
                        emailEarningsSummary(iterator.next());
                    }
                }
            } catch (MessagingException ex) {
                ex.printStackTrace();
            }
        } else {
            System.out.println("THERE IS SOMETHING NOT WORKING RIGHT HERE (RECEIVER - doGet)");
        }
        System.out.println("------------------- END (ResultReceiver - doGet) -------------------");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        File finalResult = null;
        try {
        resultParams = request.getParameter("Result-Params");
        Part resultFilePart = request.getPart("Render-Result");                                                                     // Retrieves <input type="file" name="file">
        String fileName = Paths.get(resultFilePart.getSubmittedFileName()).getFileName().toString();
        resultParamsMap = extractResultParams(resultParams);

        //  COST MANAGEMENT SECTION :
        SubtaskCosts subtaskCost = new SubtaskCosts(resultParamsMap.get("nodeEmail"),
                                                    resultParamsMap.get("subtaskID"),
                                                    resultParamsMap.get("cost"),
                                                    resultParamsMap.get("computeMinutes"));
        addSubtaskCostToTaskCostsMap(subtaskCost);

        // NODE USER & NODE DEVICE MANAGEMENT :
        NodeUser nodeUser;
        if (nodeUsersMap.get(resultParamsMap.get("nodeEmail")) == null) {
            nodeUser = new NodeUser(resultParamsMap.get("nodeEmail"));
            nodeUsersMap.put(nodeUser.getNodeEmail(), nodeUser);
        } else {
            nodeUser = nodeUsersMap.get(resultParamsMap.get("nodeEmail"));
        }
        nodeUser.addToCompletedSubtasks(resultParamsMap.get("deviceID"), resultParamsMap.get("ipAddress"), subtaskCost);

        // RESULT RECEPTION & COMPILATION OF FINAL RESULT
            System.out.println("RECEIVER | CHECK 1");
        finalResult = handleResult(resultFilePart, resultParamsMap.get("application"), fileName);
            System.out.println("RECEIVER | CHECK 2");


            // RETURNING OF FINAL RESULT TO USER
        if (finalResult != null) {
            handleFinalResult(finalResult, resultParamsMap.get("userEmail"), resultParamsMap.get("taskID"), resultParamsMap.get("renderfileName"));
        }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public File handleResult(Part resultFilePart, String application, String fileName) throws IOException {
        String taskID = resultParamsMap.get("taskID");
        CompletedRenderTask renderTask = null;
        File finalResult = null;

//        if (application.equals("blenderCycles")) {
            System.out.println("RECEIVER | CHECK 1(A)");
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

//        } else {
//            System.out.println("[ERROR] TASK APPLICATION DOESN'T EXIST");
//        }
        // else if (application.equals("insert application")) {
        // do smth else
        // }
        return finalResult;
    }

    public void addSubtaskCostToTaskCostsMap(SubtaskCosts subtaskCost) {
        String taskID = resultParamsMap.get("taskID");
        String subtaskID = resultParamsMap.get("subtaskID");

        if (taskCostsMap.get(taskID) != null) {
            taskCostsMap.get(taskID).add(subtaskCost);
            System.out.println(taskID + " SubtaskCostList exist. " + subtaskID + " added to the list.");
        } else {
            taskCostsMap.put(taskID, new ArrayList());
            taskCostsMap.get(taskID).add(subtaskCost);
            System.out.println(taskID + " SubtaskCostList created.");
        }
    }

    private LinkedHashMap<String, String> extractResultParams (String paramsString) {
        ArrayList<String> params = new ArrayList<>();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        String s = paramsString.replaceAll("[\"{}]", "");

        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter=0;
        while(scanner.hasNext()) {
            params.add(counter, scanner.next());
            counter++;
        }

        // Scan the jsonParam String to identify what application and task is uploaded. Then add Key and Value of parameters to the map respectively.
        // New Application Task Types are to be added here.
//        if (paramsString.contains("blender")) {
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
//        }

        System.out.println("UPLOAD PARAMS : ");
        map.forEach((key, value) -> System.out.println(key + ":" + value));

        scanner.close();

        return map;
    }

    public String calculateTotalCost(String taskID) {
        double totalCost = 0;
        DecimalFormat costFormat = new DecimalFormat("##.##");
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalCost += subtaskCosts.get(i).cost;
            System.out.println(subtaskCosts.get(i).subtaskID + " : " + subtaskCosts.get(i).cost);
        }

        return costFormat.format(totalCost);
    }

    public String calculateTotalComputeTime(String taskID) {
        double totalComputeTime = 0;
        DecimalFormat timeFormat = new DecimalFormat("#.##");
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);

        for (int i=0; i<subtaskCosts.size(); i++) {
            totalComputeTime += Double.parseDouble(subtaskCosts.get(i).computeMinutes);
        }

        return timeFormat.format(totalComputeTime);
    }

//    public static double round(double value, int places) {
//        if (places < 0) throw new IllegalArgumentException();
//
//        BigDecimal bd = BigDecimal.valueOf(value);
//        bd = bd.setScale(places, RoundingMode.HALF_UP);
//        return bd.doubleValue();
//    }

    public void handleFinalResult(File finalResult,
                                  String userEmail,
                                  String taskID,
                                  String renderfileName) {

        String totalCost = calculateTotalCost(taskID);
        String totalComputeTime = calculateTotalComputeTime(taskID);
        String finalResultName = finalResult.getName();
        try {
            System.out.println("UPLOADING RENDER TO DROPBOX . . . " );
            String renderDropBoxPath = uploadResultToDropbox(finalResult);
            String renderDropboxURL = getShareLink(renderDropBoxPath);

            System.out.println("UPLOADING RENDER TO NEBULA.MY . . .");
            postRenderToNebula(userEmail,
                    renderfileName,
                    taskID,
                    totalCost,
                    totalComputeTime,
                    renderDropboxURL);

            emailResults(userEmail, finalResult, totalCost, taskID, renderDropboxURL);
            emailPayments(userEmail, totalCost, taskID);
            clearTaskDir(taskID);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearTaskDir(String taskID) {
        File[] taskArray = taskDatabase.listFiles();

        for (int i=0; i<taskArray.length; i++) {
            File taskDir = taskArray[i];
            if (taskDir.getName().contains(taskID)) {
                System.out.println(taskID + " is being deleted from the Task Database.");
                taskDir.getAbsoluteFile().delete();
            }
        }
    }

//    private static void cleanup(File tileDir, File[] inputFiles) {
//        for(File f: inputFiles) {
//            if(!f.delete()) {
//                log.warning("Unable to delete tmp tile file: " +
//                        f.getAbsolutePath());
//            }
//        }
//        if(!tileDir.delete()) {
//            log.warning("Unable to delete tmp tile dir: " +
//                    tileDir.getAbsolutePath());
//        }
//    }

    public void postRenderToNebula(String userEmail,
                                          String renderfileName,
                                          String taskId,
                                          String cost,
                                          String computeTime,
                                          String renderDropboxURL) throws IOException {

        String postRenderURL = "https://www.nebula.my/_functions/Render";
        String testURL = "https://www.nebula.my/_functions-dev/Render/";
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpUriRequest request = new HttpPost(postRenderURL);
            request.addHeader("renderfile-name", renderfileName);
            request.addHeader("task-identity", taskId);
            request.addHeader("cost", cost);
            request.addHeader("user-email", userEmail);
            request.addHeader("compute-time", computeTime);
            request.addHeader("render-url", renderDropboxURL);

            CloseableHttpResponse response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            System.out.println("Executing request " + request.getRequestLine());
            System.out.println("Status Code for POST : " + status);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception : " + e.getMessage());
        } finally {
            System.out.println("---------- End of Request ----------");
            httpClient.close();
        }
    }

    private String uploadResultToDropbox(File render) { // Uploads Result to Dropbox for users to view/download whenever they want through the dropbox link. NEGATIVE - Growing expense to host user renders.
        String path;
        try {
            FullAccount account = client.users().getCurrentAccount();
            System.out.println(account.getName().getDisplayName());

            InputStream in = new FileInputStream(render);
                FileMetadata metadata = client.files().uploadBuilder("/completedRenders/" + render.getName())
                        .uploadAndFinish(in);

                path = metadata.getPathLower();
                return path;
        } catch (Exception e) {
            System.out.println("Error in uploading results to Dropbox.");
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
//            return url.replace("?dl=0", "?dl=1"); //?raw=1
//
//        }  catch (Exception ex) {
//            ex.printStackTrace();
//            return null;
//        }
//    }

    private void listCompletedRenders() {
        try {
            ListFolderResult result = client.files().listFolder("/completedRenders");

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

    private  boolean emailResults(String userEmail, File render, String totalCost, String taskID, String renderDropboxURL) throws IOException, MessagingException {
        final String from = "admin@nebula.my";
        final String pass = "DWGabriel4";
        final String paymentUrl = "https://www.nebula.my/pay";
        final String myJobsUrl = "https://www.nebula.my/account/myjobs";

        try {
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

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(userEmail));
            message.setSubject("Completed Render for " + userEmail);

            // Your render is complete. You can download it from the link below :

            String msg = "Your render is complete. You can download it from the link below : "+ "\r\n" + "<p/>" +
                    "FINAL RENDER : " + renderDropboxURL + "\r\n" + "<p/>" +
                    "\r\n" + "<p/>" +
                    "You can also find your final render in the 'My Jobs' page of your account profile. Alternatively, click here : " + myJobsUrl + "\r\n" +
                    "<strong>Task Identity</strong> : " + taskID + ".\r\n" +
                    "This is an auto-generated message from Nebula, but feel free to respond to this email with any queries you may have. We're always ready to help.   \r\n";

            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setContent(msg, "text/html");

//            MimeBodyPart renderResultPart = new MimeBodyPart();
//            renderResultPart.attachFile(render);

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(mimeBodyPart);
//            multipart.addBodyPart(renderResultPart);

            message.setContent(multipart);

            Transport.send(message);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true; // WRONG USE - HAS NO INDICATION OF SUCCESSFUL RESULT EMAIL
    }

    private void emailPayments(String userEmail, String totalCost, String taskID) throws IOException, MessagingException {
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

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("darylgabrielwong@gmail.com"));
        message.setSubject("Completed Render for " + userEmail + " .   \r\n");

        StringBuilder costList = new StringBuilder();
        ArrayList<SubtaskCosts> subtaskCosts = taskCostsMap.get(taskID);
        double totalComputeMin = 0;

        for (int i=0; i<subtaskCosts.size(); i++)
        {
            String subtask = subtaskCosts.get(i).subtaskID;
            String nodeEmail = subtaskCosts.get(i).nodeEmail;
            double cost = subtaskCosts.get(i).cost;
            String computeMinutes = subtaskCosts.get(i).computeMinutes;
            totalComputeMin += Double.parseDouble(computeMinutes);
            String string = i + ". " + subtask + " | " + nodeEmail + " - USD" + cost + " | Compute Minute(s) : " + computeMinutes + ".   \r\n" + " <p/>";
            costList.append(string);
        }

        String msg = "Total Compute Time : " + totalComputeMin + " | Total Cost = USD" + totalCost + "\r\n" + "<p/>"
                + costList;

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(msg, "text/html");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        message.setContent(multipart);
        Transport.send(message);
    }

    private void emailEarningsSummary(NodeUser nodeUser) throws MessagingException {                                        // todo - TO BE RECTIFIED
        try {
            Collection<SubtaskCosts> collection = nodeUser.getAllCompletedSubtasks().values();
            Iterator<SubtaskCosts> iterator = collection.iterator();
//            ArrayList<CompletedTask.CompletedSubtask> nodeUserAllCompletedSubtasks = nodeUser.getAllCompletedSubtasks();

            EarningsBook earningsBook = new EarningsBook(nodeUser);
//            EarningsBook earningsBook = new EarningsBook(node, nodeEmail, nodes);
            System.out.println("Writing Earnings Excel Book . . . ");
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
            while (iterator.hasNext()) {
                String subtaskID = iterator.next().subtaskID;
                double computeHours = Double.parseDouble(iterator.next().computeMinutes);
                double profit = iterator.next().cost;
                totalProfit += profit;
                totalComputeHours += computeHours;
                String string = counter + ". " + subtaskID + " | PROFIT : RM " + profit + " | Compute Hour(s) : " + computeHours + "\r\n";
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
        String subtaskID;
        String computeMinutes;
        double cost;

        public SubtaskCosts(String nodeEmail, String subtaskID, String cost, String computeMinutes) {
            this.nodeEmail = nodeEmail;
            this.subtaskID = subtaskID;
            this.cost = Double.parseDouble(cost);
            this.computeMinutes = computeMinutes;
        }

        public String getNodeEmail() {
            return nodeEmail;
        }

        public String getSubtaskID() {
            return subtaskID;
        }

        public String getComputeMinutes() {
            return computeMinutes;
        }

        public double getCost() {
            return cost;
        }
    }
}

