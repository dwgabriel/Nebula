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

    String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {  // Receives request from Nodes to re-schedule incomplete subtasks
        nodeEmail = request.getParameter("Node-Email");
        deviceID = request.getParameter("Device-Identity");
        ipAddress = request.getParameter("IP-Address");
        taskID = request.getParameter("Task-Identity");
        subtaskID = request.getParameter("Subtask-Identity");

        System.out.println("---- RE-SCHEDULE REQUEST (RESCHEDULER - doPost) ----"
                + "\n Task Identity : " + taskID
                + "\n Subtask Identity : " + subtaskID
                + "\n Node Email : " + nodeEmail
                + "\n Device Identity : " + deviceID
                + "\n IP Address : " + ipAddress);

        getReschedule(taskID, subtaskID, nodeEmail);
        System.out.println("-------------- (RESCHEDULER - doPost) --------------");
    }

    public void getReschedule(String taskID, String subtaskID, String nodeEmail) throws IOException {      // Sends request to Scheduler to re-schedule incomplete subtask.
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpUriRequest request = RequestBuilder
                .get(schedulerServlet)
                .build();

        request.setHeader("Task-Identity", taskID);
        request.setHeader("Subtask-Identity", subtaskID);
        request.setHeader("Node-Email", nodeEmail);

        CloseableHttpResponse response = httpClient.execute(request);

        int status = response.getStatusLine().getStatusCode();
        System.out.println("Status Code for GET : " + status);
    }
}
