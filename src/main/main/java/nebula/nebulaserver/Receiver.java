package nebula.nebulaserver;

import javax.imageio.ImageIO;
import javax.mail.*;
import javax.mail.internet.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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
public class Receiver extends HttpServlet {
    // Center for Receiving completed results from Nodes for merging and verification.
    // 1. doPost Servlet for receiving completed results from Nodes. (Checked)
    // 2. Merge and verify results from Nodes in chronological order. ***** (Checked)
    // 3. Complete and verified results shall be sent to Client Collection storage for returning to clients. (Checked)
    // 4. doPost Servlet for receiving Client ID as request to return results. (Checked)
    // 5. doPost Servlet for receiving verified and accurate Client ID as request to return results of submitted workload. (Checked) ****

    public class SubtaskCosts {
        String nodeEmail;
        String subtaskID;
        String computeHours;
        double cost;

        public SubtaskCosts(String nodeEmail, String subtaskID, double cost, String computeHours) {
            this.nodeEmail = nodeEmail;
            this.subtaskID = subtaskID;
            this.cost = cost;
            this.computeHours = computeHours;
        }
    }

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    private File originalTask;
    private File nodeResults;
    private File clientCollection;

    private String nodeEmail;
    private String deviceID;
    private String taskID;
    private String subtaskID;
    private String userEmail;
    private String computeHours;
    private double cost;
    int tileCount = 16; // SHOULD NOT BE HARDCODED.
//    private static HashMap<String, Double> allCosts = new HashMap<>();
    private static ArrayList<SubtaskCosts> subtaskCosts = new ArrayList<>();

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        nodeEmail = request.getParameter("Node-Email");                                                                   // Retrieves <input type="text" name="username">
        deviceID = request.getParameter("Device-Identity");                                                                 // Retrieves <input type="text" name="deviceID">
        taskID = request.getParameter("Task-Identity");                                                               // Retrieves <input type="text" name="subtaskidentity">
        subtaskID = request.getParameter("Subtask-Identity");                                                         // Retrieves <input type="text" name="taskidentity">
        userEmail = request.getParameter("User-Email");
        computeHours = request.getParameter("Compute-Time");
        cost = Double.parseDouble(request.getParameter("Cost"));

        SubtaskCosts subtaskCost = new SubtaskCosts(nodeEmail, subtaskID, cost, computeHours);                                         // Create SubtaskCost object for later reference to make payments to Supply User - contains SupplyUser email, completed subtask ID, cost of computing subtask.
        subtaskCosts.add(subtaskCost);

        Part filePart = request.getPart("subtask");                                                                     // Retrieves <input type="file" name="file">
        String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();                          // MSIE fix. Ensures that the file name itself is returned, not the entire file path.
        InputStream fileContent = filePart.getInputStream();

        System.out.println("TaskID: " + taskID);

        originalTask = new File (taskDatabase + "/" + taskID);
        nodeResults = new File(taskDatabase + "/" + taskID + "/noderesults");
        clientCollection = new File(taskDatabase + "/" + taskID + "/clientcollection");
        System.out.println("---------------- DIR CHECK ------------------");
        System.out.println(taskDatabase.getAbsolutePath());
        System.out.println(nodeResults.getAbsolutePath());
        System.out.println(clientCollection.getAbsolutePath());
        System.out.println("---------------------------------------------");

//        System.out.println("node results : " + nodeResults.listFiles().length);

        File file = new File(nodeResults, fileName);
            try {
                Files.copy(fileContent, file.toPath());
                System.out.println(fileName + " is received.");
                System.out.println("Subtask ID : " + subtaskID);


                if (listOfFilesToMerge(nodeResults).size() > tileCount) {
                System.out.println("Error : Tile Count and Node Result list does not match!");
                System.out.println("Node Result : " + listOfFilesToMerge(nodeResults).size());
                System.out.println("Tile Count : " + tileCount);

                } else if (listOfFilesToMerge(nodeResults).size() < tileCount) {
                System.out.println("Number of tiles to go before composition : " + (tileCount - listOfFilesToMerge(nodeResults).size()));

                } else if (listOfFilesToMerge(nodeResults).size() == tileCount) {
                    if (compositeTiles(nodeResults, tileCount, clientCollection, userEmail)) {
                        log(fileName + " has been merged and verified. Returning to customer . . .");
                        System.out.println(fileName +  " has been merged and verified. Returning to customer . . .");
                        originalTask.getAbsoluteFile().delete();
                    } else {
                        System.out.println("Error compositing tiles.");
                    }
                }
            } catch (Exception e) {
            e.printStackTrace();
            }
    }

    public static double calculateTotalCost(ArrayList<SubtaskCosts> subtaskCosts) {
        double totalCost = 0;
        for (int i=0; i<subtaskCosts.size(); i++) {
            totalCost += subtaskCosts.get(i).cost;
        }

        return totalCost;
    }

    public static List<File> listOfFilesToMerge(File nodeResults) {

        File[] files = nodeResults.getAbsoluteFile().listFiles();
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        System.out.println("File Size: " + files.length);
        return Arrays.asList(files);
    }

    public boolean compositeTiles(File tileDir, int tileCount,
                                         File outputFile, String userEmail) throws IOException, AddressException, MessagingException {
        log("Compositing tiles . . . ");
        long start = System.currentTimeMillis();
        // first check that it has all images
        File[] inputFiles = tileDir.listFiles();
        Arrays.sort(inputFiles);
        for (int i = 0; i < tileCount; i++) {
            log(inputFiles[i].getName());
//            inputFiles[i] = new File(tileDir, inputFiles[i].getName());
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
            log("Compositing tiles (1) . . .");
            BufferedImage[] bufferedImages = new BufferedImage[inputFiles.length];
            for (int row = divisions - 1; row >= 0; row--)
                for (int order = 0; order < divisions; order++)
                    bufferedImages[numImage++] = ImageIO.read(inputFiles[row*divisions + order]);

            log("Compositing tiles (2) . . .");
            BufferedImage image = combineImages(bufferedImages);

            log("Compositing tiles (3) . . .");
            File finalResult = new File(outputFile,  taskID + ".png");

            log("Compositing tiles (4) . . .");
            ImageIO.write(image, "png", finalResult);

            log ("Compositing tiles (5) . . . ");
            double totalCost = calculateTotalCost(subtaskCosts);
            emailResults(userEmail, finalResult, totalCost);
            emailSupplierProfit(userEmail, totalCost);

        } catch (IOException ex) {
            log.warning("Failed during tile compositing: "  + ex.getMessage());
            return false;
        }
        cleanup(tileDir, inputFiles, tileCount);

        log.fine("Composited " + Integer.toString(tileCount) +
                " tiles in (ms): " +
                Long.toString(System.currentTimeMillis() - start));

        return true;
    }

    private static BufferedImage combineImages(BufferedImage bufferedImages[]) {
        int divisions = (int)Math.sqrt(bufferedImages.length);
        int actualImage = 0;
        // first we establish the width and height of the final image
        int finalWidth = 0;
        int finalHeight = 0;
        for (int i = 0; i < divisions; i++){
            finalWidth += bufferedImages[i].getWidth();
            finalHeight += bufferedImages[i*divisions].getHeight();
        }
        System.out.println("Combining images . . . ");
//        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, bufferedImages[0].getType());
        BufferedImage finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
        System.out.println("Combining images (1) . . . ");
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
            System.out.println("Combining images (2) . . . ");
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
                log.warning("Unable to delete tmp tile file: " +
                        f.getAbsolutePath());
            }
        }
        if(!tileDir.delete()) {
            log.warning("Unable to delete tmp tile dir: " +
                    tileDir.getAbsolutePath());
        }
    }

    private static final Logger log = Logger.getLogger("nebula.nebulaserver.Receiver");

    private static void emailResults(String userEmail, File finalResult, double totalCost) throws IOException, MessagingException {
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
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(userEmail));
        message.setSubject("Completed Render for " + userEmail);

        String msg = "Your render is complete. Please find attached the final result of render. \r\n" +
                "This is an auto-generated message from Nebula. Please do not respond to this email. \r\n" +
                "<strong>The total amount of your render is</strong>" + " : RM" + totalCost + " \r\n";

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(msg, "text/html");

        MimeBodyPart renderResultPart = new MimeBodyPart();
        renderResultPart.attachFile(finalResult);

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);
        multipart.addBodyPart(renderResultPart);

        message.setContent(multipart);

        Transport.send(message);
    }

    private static void emailSupplierProfit (String userEmail, double totalCost) throws IOException, MessagingException {
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
        message.setSubject("Completed Render for " + userEmail + "\r\n");

        StringBuilder costList = new StringBuilder();
        for (int i=0; i<subtaskCosts.size(); i++)
        {
            String subtask = subtaskCosts.get(i).subtaskID;
            String nodeEmail = subtaskCosts.get(i).nodeEmail;
            double cost = subtaskCosts.get(i).cost;
            String computeHours = subtaskCosts.get(i).computeHours;
            String string = i + ". " + subtask + " | " + nodeEmail + " - RM" + cost + " | Compute Hour(s) : " + computeHours + "\r\n";
            costList.append(string);
        }

        String msg = "Total Cost = RM" + totalCost + "\r\n"
                + costList;

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(msg, "text/html");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        message.setContent(multipart);
        Transport.send(message);
    }

}

