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
    private String taskID;
    private String subtaskID;
    private String tileScriptName;

    String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        nodeEmail = request.getParameter("Node-Email");
        taskID = request.getParameter("Task-Identity");
        subtaskID = request.getParameter("Subtask-Identity");
        tileScriptName = request.getParameter("Tile-Script");

        System.out.println("Received request for re-scheduling " + subtaskID + " | " + tileScriptName + " of " + taskID + " by " + nodeEmail);

        getInfo();
    }

    public void getInfo() throws IOException { // Retrieves information from server. (Works)
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpUriRequest request = RequestBuilder
                .get(schedulerServlet)
                .build();

        request.setHeader("Task-Identity", taskID);
        request.setHeader("Subtask-Identity", subtaskID);
        request.setHeader("Tile-Script", tileScriptName);
        request.setHeader("Node-Email", nodeEmail);

        CloseableHttpResponse response = httpClient.execute(request);

        int status = response.getStatusLine().getStatusCode();
        System.out.println("Status Code for GET : " + status);
    }
}
