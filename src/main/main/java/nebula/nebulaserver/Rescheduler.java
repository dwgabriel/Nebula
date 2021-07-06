package nebula.nebulaserver;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@WebServlet(
        name = "ReschedulerServlet",
        urlPatterns = {"/reschedule"}
)

public class Rescheduler extends HttpServlet {

    private String nodeEmail;
    private String deviceID;
    private String ipAddress;
    private String taskID;
    private String subtaskID;
    private String userEmail;
    private String failReason;

    String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {  // Receives request from Nodes to re-schedule incomplete subtasks
        nodeEmail = request.getParameter("Node-Email");
        deviceID = request.getParameter("Device-Identity");
        taskID = request.getParameter("Task-Identity");
        subtaskID = request.getParameter("Subtask-Identity");
        userEmail = request.getParameter("User-Email");
        ipAddress = request.getParameter("IP-Address");
        failReason = request.getParameter("Reason");


        System.out.println("---- RE-SCHEDULE REQUEST (RESCHEDULER - doPost) ----"
                + "\n Task Identity : " + taskID
                + "\n Subtask Identity : " + subtaskID
                + "\n Node Email : " + nodeEmail
                + "\n Device Identity : " + deviceID
                + "\n IP Address : " + ipAddress
                + "\n Fail Reason : " + failReason);

        getReschedule(taskID, subtaskID, nodeEmail, userEmail, ipAddress);
        System.out.println("---------------------END OF REQUEST--------------------");
    }

//    @Override
//    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//       taskID = request.getParameter("Task-Identity");
//       subtaskID = request.getParameter("Subtask-Identity");
//       ipAddress = request.getParameter("IP-Address");
//       userEmail = request.getParameter("User-Email");
//       String nodeEmailReplacement = "RESULTS_RECEIVER_RESCHEDULE_REQUEST";
//
//        System.out.println("---- RE-SCHEDULE REQUEST (RESCHEDULER - doPut) ----"
//                + "\n Task Identity : " + taskID
//                + "\n Subtask Identity : " + subtaskID
//                + "\n Node Email : " + nodeEmail);
//
//       getReschedule(taskID, subtaskID, nodeEmailReplacement, userEmail, ipAddress);
//       System.out.println("---------------------END OF REQUEST--------------------");
//    }

    public void getReschedule(String taskID,
                              String subtaskID,
                              String nodeEmail,
                              String userEmail,
                              String ipAddress) throws IOException {      // Sends request to Scheduler to re-schedule incomplete subtask.

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpUriRequest request = RequestBuilder
                .get(schedulerServlet)
                .build();

        request.setHeader("Task-Identity", taskID);
        request.setHeader("Subtask-Identity", subtaskID);
        request.setHeader("Node-Email", nodeEmail);
        request.setHeader("User-Email", userEmail);
        request.setHeader("IP-Address", ipAddress);

        CloseableHttpResponse response = httpClient.execute(request);

        int status = response.getStatusLine().getStatusCode();
        System.out.println("RESCHEDULER | Executing request (getReschedule) | Status : " + status);
    }
}
