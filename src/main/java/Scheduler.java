import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;

/**
 * Created by Daryl Wong on 3/15/2019.
 */
@WebServlet(
        name = "JobServlet",
        urlPatterns = {"/jobsplease"}
)

public class Scheduler extends HttpServlet {
    int index = 0;
    String application;

//    public void doGet(HttpServletRequest request , HttpServletResponse response) throws ServletException, IOException {
//
//        System.out.println("This is also called.");
//        application = request.getHeader("application").toString();
//        response.setStatus(response.getStatus());
//        System.out.println("Application (Scheduler | doGet) : "+ application);
//    }

    public void getInfo() throws IOException { // Retrieves information from server. (Works)

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpUriRequest request = RequestBuilder
                .get("http://localhost:8080/clientReceiver")
                .build();

        CloseableHttpResponse response = httpClient.execute(request);

        try {
            application = response.getFirstHeader("application").getValue();
            System.out.println("Application : " + application);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                long length = entity.getContentLength();
                if (length != -1) {
                    String content = EntityUtils.toString(entity, "UTF-8");
                    System.out.println(content);
                }
            }
        } finally {
            response.close();
        }

        System.out.println("Executing request " + request.getRequestLine());
        int status = response.getStatusLine().getStatusCode();
        System.out.println("Status Code for POST : " + status);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { // Server Nodes will post Node information (Node name, job queue, node creditScore) to Server as request for more jobs.
        System.out.println("Application (Scheduler | doPost) : " + application);
        getInfo();
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
        System.out.println("Application check 3 : " + application);


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
                                                                                                                        ///// Scheduler needs to supply Docker Blender Image/Container & Renderfile (Processed) to Node.

                                                                                                                        // Processed Sub-tasks need to be obtained from Database and Scheduled to "Valid" Nodes.
            File schedulercache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\schedulercache");
            System.out.println("Index : " + index);


            if (schedulercache.listFiles() != null) {
                // 1. Iterate to next task when one is scheduled.
                File[] filesArray = schedulercache.listFiles();

                if (index < filesArray.length) {
                    try {
                        File renderfile = filesArray[index].getAbsoluteFile();
                        File fileDecode = new File(renderfile, URLDecoder.decode(renderfile.getAbsolutePath(), "UTF-8"));
                        String contentType = getServletContext().getMimeType(fileDecode.getName());
                        String fileName = String.format(renderfile.getName());
                        String taskID = fileName.substring(4);
                        System.out.println("Application check : " + application);

                        response.reset();
                        response.setContentType(contentType);                                                           // Content Type        : Type of renderfile - e.g. Renderfile.blend / Renderfile.3dm
                        response.setHeader("Content-Length", String.valueOf(renderfile.length()));                   // Content Length      : Renderfile size
                        response.setHeader("Content-Name", renderfile.getName());                                    // Content Disposition : Renderfile name
                        response.setHeader("Task-Identity", taskID);                                                 // Task Identity       : Task ID for consistency
                        response.setHeader("Application", application);                                               // Application         : Application ID for Docker container - nebula/blender from DockerHub

                        BufferedInputStream in = new BufferedInputStream(new FileInputStream(renderfile));              // InputStream of Renderfile
                        byte[] buffer = new byte[(int) renderfile.length()];
                        int length;
                        while ((length = in.read(buffer)) > 0) {                                                        // Output of renderfile to POST-Responses from Node
                            out.write(buffer, 0, length);
                        }
                        in.close();
                        out.flush();
                        out.close();

                        System.out.println("Testing that this works.");
                        index++;                                                                                        // Index : For Loop to loop through the file array of Split tasks.
                        System.out.println("Index : " + index);

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
}


