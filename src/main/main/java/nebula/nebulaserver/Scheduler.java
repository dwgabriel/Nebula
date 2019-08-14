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
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
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

    final File tasksDir = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\");
    final File schedulerCache = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\schedulercache\\");

    private String application;
    private String taskID;
    private String subtaskID;
    private String blenderCL;
    private String subtaskLength;
    String[] filesToZip = new String[2];
    private ArrayList<String> taskQueue;
    public Task task;

    public String getInfo() throws IOException { // Retrieves information from server. (Works)
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpUriRequest request = RequestBuilder
                .get("http://localhost:8080/clientReceiver")
                .build();
        String taskIdentity;
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            taskIdentity = response.getFirstHeader("Task-Identity").getValue();

        } finally  {
            response.close();
        }
        int status = response.getStatusLine().getStatusCode();
        System.out.println("Status Code for POST : " + status);
        return taskIdentity;
    }

//    protected void doGet(HttpServletRequest request, HttpServletResponse response)                                      // doGet to send Subtask Queue & Application info
//            throws ServletException , IOException {
//
//
//
//        if (application != null) {
//
////            response.setHeader("Application", application);
////            response.setHeader("Task-Identity", taskID);
//            taskQueue.add(request.getAttribute("Task-Queue"));
//
//
//            int status = response.getStatus();
//            System.out.println("STATUS CODE : " + status);
//            System.out.println("___________________________________");
//            System.out.println("Task Queue : " + taskQueue.get(0));
//        } else {
//            System.out.println("Problem's here.");
//            response.sendError(response.SC_BAD_REQUEST,
//                    "Bad Request | Parameter Required");
//        }
//    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { // com.nebula.Server Nodes will post Node information (Node name, job queue, node creditScore) to com.nebula.Server as request for more jobs.
        String nodeIdentity = request.getParameter("username");                                                      // Retrieve <input type="text" name="username"> - Username of Node Supplier User
        String deviceIdentity = request.getParameter("deviceID");                                                    // Retrieve <input type="text" name="deviceID"> - Identity of device being supplied by Supply User
        String[] nodeQueue = request.getParameterValues("queue");                                                    // Retrieve <input type="text" name="queue"> -  Status of device job queue
        String nodeScore = request.getParameter("score");                                                            // Retrieve <input type="text" name"score"> - Identifies Node device's reliability

        boolean name = false;
        boolean device = false;
        boolean score = false;
        boolean queue = false;
        boolean valid = false;

        ServletOutputStream out = response.getOutputStream();

        if (nodeIdentity == null) {                                                                                     // Checks nodeIdentity Parameter and if validated, moves on to deviceIdentity Parameter.
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
            taskID = getInfo();
            int index = 0;
            System.out.println("Index : " + index);
            File originalTaskDir = new File(tasksDir + "\\" + taskID + "\\originaltask");
            File subtaskDir = new File(tasksDir + "\\" + taskID + "\\subtasks");

            if (subtaskDir.listFiles() != null && originalTaskDir.listFiles()!=null) {
                File[] filesArray = subtaskDir.listFiles();
                File[] originalTaskArray = originalTaskDir.listFiles();

                if (index < filesArray.length) {
                    try {
                        File originalTaskFile = originalTaskArray[0].getAbsoluteFile();
                        File tileScript = filesArray[index].getAbsoluteFile();
                        System.out.println("tileScript : " + tileScript.getName());
                        File fileDecode = new File(tileScript, URLDecoder.decode(tileScript.getAbsolutePath(), "UTF-8"));
                        String contentType = getServletContext().getMimeType(fileDecode.getName());

                        application = getMetadata(tileScript.toPath(), "Application");
                        blenderCL = getMetadata(tileScript.toPath(), "Blender-CL");
                        subtaskID = getMetadata(tileScript.toPath(), "Subtask-Identity");
                        subtaskLength = getMetadata(tileScript.toPath(), "Subtask-Length");

                        response.reset();
//                        response.setContentType(contentType);                                                           // Content Type        : Type of renderfile - e.g. Renderfile.blend / Renderfile.3dm
                        response.setHeader("Tile-Script", tileScript.getName());                                     // Content Length      : Renderfile size
                        response.setHeader("Subtask-Identity", subtaskID);                                           // Content Disposition : Renderfile name
                        response.setHeader("Task-Identity", taskID);                                                 // Task Identity       : Task ID for consistency
                        response.setHeader("Application", application);                                              // Application         : Application ID for Docker container - nebula/blender from DockerHub
                        response.setHeader("Blender-CL", blenderCL);                                                 // Blender-CL          : Blender Command Line to execute Python Script and Blender to initiate Rendering.
                        response.setHeader("Subtask-Length", subtaskLength);
                        response.addHeader("Task-Package", "attachment; filename=\"test-arc.zip\"");

                        response.setContentType("application/zip");
                        response.setStatus(HttpServletResponse.SC_OK);
//
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

                        index++;                                                                                        // Index : For Loop to loop through the file array of Split tasks.
                        System.out.println("New Index : " + index);

                    } catch (IOException io) {
                        io.printStackTrace();
                    }
                } else if (index >= filesArray.length) {
                    System.out.println("All Subtasks has been scheduled.");
                    out.write("There are no tasks at this moment.".getBytes("UTF-8"));
                    out.flush();
                    out.close();
                }
            }
        }
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
            System.out.println("TEST | Attribute : " + metaName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }
}


