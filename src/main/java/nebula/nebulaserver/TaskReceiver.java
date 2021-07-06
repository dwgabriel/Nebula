package nebula.nebulaserver;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;


@WebServlet(
        name = "TaskReceiver",
        urlPatterns = {"/upload"}
)
public class TaskReceiver extends HttpServlet {

    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    private String application;
    private String userEmail;
    private String jsonTaskParams;

    String schedulerServlet = "https://nebula-server.herokuapp.com/scheduler";
    Deque<Task> taskQueue = new LinkedList<>();

    // This class acts as the reception to receive new tasks from the web-app. Parameters of the new task should include the Application to be used and Data of the task to be parallelized and distributed to Nodes.

    // putTask sends a PUT request to Scheduler class to update the SubtaskPackageQueue with new TaskIDs for scheduling.
    private void putTask(Task task) {
        String taskID = task.getTaskID();
        String computeRate = task.getTaskParamsMap().get("computeRate");

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpUriRequest request = RequestBuilder
                    .put(schedulerServlet)
                    .build();

            request.setHeader("Task-Identity", taskID);
            request.setHeader("Compute-Rate", computeRate);

            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            System.out.println("TASK RECEIVER | Executing request " + request.getRequestLine() + " | Status : " + status);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void put_failedTaskToNebula(Task task) {

        try{
            String put_failedURL = "https://www.nebula.my/_functions/Failed";
            String testURL = "https://www.nebula.my/_functions-dev/Failed/";
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpUriRequest request = new HttpPost(put_failedURL);
            request.addHeader("task-identity", task.getTaskID());

            CloseableHttpResponse response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            System.out.println("TASK RECEIVER | Executing request " + request.getRequestLine() + " | Status : " + status);
            if (status != 200) {
                System.out.println("RESULT RECEIVER | Failed to update nebula.my.");
            }

            httpClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ClientResponse emailNewTask(String taskID) {
        final String from = "admin@nebula.my";
        final String toEmail = "chris_kee@hotmail.com";
        final String ccEmail = "gizmo.chriskee@gmail.com";
        final String ccEmail2 = "darylgabrielwong@gmail.com";

        Client client = Client.create();
        client.addFilter(new HTTPBasicAuthFilter("api", Server.mailGunApiKey));
        WebResource webResource = client.resource("https://api.mailgun.net/v3/nebula.my/messages");
        MultivaluedMapImpl formData = new MultivaluedMapImpl();

        String msg = "NEW RENDER -- TASK ID : " + taskID;

        formData.add("from", from);
        formData.add("to", "<" + toEmail + ">");
        formData.add("cc","<" + ccEmail + ">");
//        formData.add("cc","<" + ccEmail2 + ">");
        formData.add("subject", msg);
        formData.add("text", msg);

        return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formData);
    }

    private ClientResponse emailFailedRender(String taskID, String userEmail) {
        final String from = "admin@nebula.my";
        final String startRenderingURL = "https://www.nebula.my/start-rendering";

        Client client = Client.create();
        client.addFilter(new HTTPBasicAuthFilter("api", Server.mailGunApiKey));
        WebResource webResource = client.resource("https://api.mailgun.net/v3/nebula.my/messages");
        MultivaluedMapImpl formData = new MultivaluedMapImpl();

            // Your render is complete. You can download it from the link below :

            String msg = "Your render (Task ID : " + taskID + ") has failed. \r\n" +
                    "Please ensure that your your Renderfile is in the correct format and packaged with all its assets, textures and materials. Once you do, click the link below to try again! \r\n" +
                    "\r\n" +
                    "Start Rendering : " + startRenderingURL + "\r\n" +
                    "\r\n" +
                    "If the problem still persists, it's probably our fault! Respond to this email and we'll get on it right away.\r\n" +
                    "\r\n" +
                    "This is an auto-generated message from Nebula, but feel free to respond to this email with any queries you may have. We're always ready to help.   \r\n";

        formData.add("from", from);
        formData.add("to", "<" + userEmail + ">");
        formData.add("subject", "Your render (Task ID : " + taskID + ") has failed.");
        formData.add("text", msg);

        return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formData);
    }

    // The doPost method will be called from the web-app to receive parameters/information of the selected Application and Data to be parallelized.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LinkedHashMap<String, String> taskParamsMap = readJsonTaskParams(request.getReader());

        try {
            Task task = new Task(taskParamsMap);
            if (task.getTaskSetupStatus()) {
                putTask(task);
                emailNewTask(task.getTaskID());
            } else {
                put_failedTaskToNebula(task);
                emailFailedRender(task.getTaskID(), task.getTaskParamsMap().get("userEmail"));
            }
            System.out.println("TASK RECEIVER | " + task.getTaskID() + " added to Scheduler queue.");
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("Access-Control-Allow-Origin", "https://www.nebula.my");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinkedHashMap<String, String> readJsonTaskParams (BufferedReader reader) throws IOException {
        StringBuffer jsonString = new StringBuffer();
        LinkedHashMap<String, String> taskParams;
        String line = null;
        try {
            BufferedReader bufferedReader = reader;
            while ((line = reader.readLine()) != null)
                jsonString.append(line);

            JSONObject jsonObject =  HTTP.toJSONObject(jsonString.toString());
            taskParams = extractJsonTaskParams(jsonString.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new IOException("Error parsing JSON request string");
        }

        return taskParams;
    }

    //  The extractJsonTaskParams is paramount to ensuring the right task information is passed to create the right tasks.
    //  The method will extract and define the 'application' parameter to decide what Key and Value are to be added into the taskParamsMap to be passed to the Task Class.
    private LinkedHashMap<String, String> extractJsonTaskParams (String jsonParam) {
        ArrayList<String> params = new ArrayList<>();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        String s = jsonParam.replaceAll("[\"{}]", "");

        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter=0;
        while (scanner.hasNext()) {
            params.add(counter, scanner.next());
            counter++;
        }

        // Scan the jsonParam String to identify what application and task is uploaded. Then add Key and Value of parameters to the map respectively.
        // New Application Task Types are to be added here.
        if (jsonParam.contains("vray")) {
            map.put("taskID", params.get(0));
            map.put("userEmail", params.get(1));
            map.put("application", params.get(2));
            map.put("frameCategory", params.get(3));
            map.put("startFrame", params.get(4));
            map.put("endFrame", params.get(5));
            map.put("renderOutputType", params.get(6));
            map.put("computeRate", params.get(7));
            map.put("divisionRate", params.get(8));
            map.put("renderfileName", params.get(9));
            map.put("packedSkpName", params.get(10));
            map.put("renderHeight", params.get(11));
            map.put("renderWidth", params.get(12));
            map.put("uploadfileName", params.get(13));
            map.put("userSubscription", params.get(14));
            map.put("userAllowance", params.get(15));

        } else if (jsonParam.contains("blender")) {
            map.put("taskID", params.get(0));
            map.put("userEmail", params.get(1));
            map.put("application", params.get(2));
            map.put("frameCategory", params.get(3));
            map.put("startFrame", params.get(4));
            map.put("endFrame", params.get(5));
            map.put("renderOutputType", params.get(6));
            map.put("computeRate", params.get(7));
            map.put("divisionRate", params.get(8));
            map.put("renderfileName", params.get(9));
            map.put("uploadfileName", params.get(10));
            map.put("userSubscription", params.get(11));
            map.put("userAllowance", params.get(12));
        }

        map.forEach((key, value) -> System.out.println("TASK RECEIVER | " + key + ":" + value));

        scanner.close();

        return map;
    }
}
