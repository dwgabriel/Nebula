/**
 * Created by Daryl Wong on 3/14/2019.
 */

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

@WebServlet(
        name = "ClientReceiver",
        urlPatterns = {"/clientReceiver"}
        )
@MultipartConfig
public class ClientReceiver extends HttpServlet {
                                                                                                                        // Path directories
    File database = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\");
    File schedulercache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\schedulercache");
    File applibrary = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\applibrary"); // To be configured as private DockerImage registry.

    Part filePart;
    File taskDatabase;
    String fileName;
    String taskID;
    String userID;
    String application;
    String taskName;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)                                      // doGet to send "Application" through response.
            throws ServletException , IOException {

        System.out.println("Application (doGet) : " + application);

        if (application != null) {
            System.out.println("This is called. ");
            response.setHeader("application", application);
            int status = response.getStatus();
            System.out.println("STATUS CODE : " + status);
            System.out.println("___________________________________");
        } else {
            System.out.println("Problem's here.");
            response.sendError(response.SC_BAD_REQUEST,
                    "Application Required");
        }
    }

//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException , IOException {
//
//        application = request.getHeader("application").toString();
//        System.out.println("Application (doGet) : " + application);
//
//        RequestDispatcher rd = getServletConfig().getServletContext().getRequestDispatcher("/jobsplease");
//
//        if (application != null) {
//            System.out.println("This is called. ");
//            request.setAttribute("application", application);
//            rd.forward(request, response);
//            int status = response.getStatus();
//            System.out.println("STATUS CODE : " + status);
//            System.out.println("___________________________________");
//        } else {
//            System.out.println("Problem's here.");
//            response.sendError(response.SC_BAD_REQUEST,
//                    "Application Required");
//        }
//    }


    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        userID = request.getParameter("username");                                                                   // Retrieves Username of Client
        application = request.getParameter("application");                                                           // Retrieves ID of Client-selected application for rendering
        filePart = request.getPart("file");                                                                          // Retrieves Renderfile from Client <input type="file" name="file">
                                                                                                                        // Decoding Received File's Name :
        fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();                                 // MSIE fix. Ensures that the file name itself is returned, not the entire file path.
        InputStream fileContent = filePart.getInputStream();
        File fileDecode = new File(fileName, URLDecoder.decode(fileName, "UTF-8"));                                // File decode to retrieve file extension (file type - e.g. renderfile.blend / renderfile.3dm)
        String contentType = getServletContext().getMimeType(fileDecode.getName());   // DOESNT WORK #################

            //TESTS :
            System.out.println("Username : " + userID);
            System.out.println(fileName + "'s Content Type : "  + contentType);
            System.out.println("Application (doPost) : " + application);


        taskID = String.format(taskIdentity());                                                                     // Encodes task titles with identity and info - ID_User_FileName.extension | 0123_johnDoe_johnsdesign.blend
            taskName = String.format(taskID + "_" + userID + "_" + fileName + "." + contentType);

                taskDatabase = new File(database, taskID);                                                              // Creates a new storage directory for the new task.
                taskDatabase.mkdir();
                                                                                                                        // Creating all sub-directories needed :
                File nodeResults        = new File(taskDatabase, "noderesults");
                File clientCollection   = new File(taskDatabase, "clientcollection");
                File original           = new File(taskDatabase, "originaltask");
                File originalSplit      = new File(taskDatabase, "originalsplit");
                File appdir             = new File(taskDatabase, "appdir");

                nodeResults             .mkdir();
                clientCollection        .mkdir();
                original                .mkdir();
                originalSplit           .mkdir();
                appdir                  .mkdir();

            File originalTaskFile = new File(original, taskName);

            try {
                Files.copy(fileContent, originalTaskFile.toPath());                                                     // Copy of original task files as failsafe. In case Nodes fail and require re-scheduling.
                Split(originalTaskFile.getAbsoluteFile(), originalSplit);                                               // Splitting of tasks into optimal sizes - Requires industrial method of graphic parallelism : Equalizer ###################
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    public void Split(File originalFile, File originalSplit) throws IOException {                                       // DOESNT WORK - REQUIRES INTEGRATION OF EQUALIZER #########################

        int partCounter = 1;

        int sizeOfTask = 1024 * 1024; // by Megabytes - Configuration of TaskInstance sizes, and increments on partCounter.
        byte[] buffer = new byte[sizeOfTask];
        String taskName = originalFile.getName();

        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(originalFile));

            int bytesAmount = 0;
            while ((bytesAmount = bis.read(buffer)) > 0) {
                File fileDecode = new File(originalFile, URLDecoder.decode(originalFile.getAbsolutePath(), "UTF-8"));
                String contentType = getServletContext().getMimeType(fileDecode.getName());
                String taskInstanceID = String.format("%03d_%s", partCounter++, taskID, "." + contentType);

                File taskFile = new File(originalSplit, taskInstanceID);                                                // Creates a copy of subtasks in the original database as reference.
                File newSubtask = new File(schedulercache.getAbsolutePath(), taskInstanceID);                           // Creates a new subtask in the Scheduler Cache.

                FileOutputStream outputStream = new FileOutputStream(taskFile);                                         // Original Split
                FileOutputStream outputStreamTwo = new FileOutputStream(newSubtask);                                    // Scheduler Cache

                outputStream.write(buffer, 0, bytesAmount);                                                         // Original Split
                outputStreamTwo.write(buffer,0,bytesAmount);                                                        // Scheduler Cache
                }
            } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String taskIdentity () {

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
}
