package nebula.nebulaserver;

import org.apache.http.client.methods.CloseableHttpResponse;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;


@WebServlet(
        name = "TaskReceiver",
        urlPatterns = {"/newTask"}
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

    // The doGet method will be called from other classes to get parameters/information about the task request for parallelization.
//    @Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        if (request == null) {
//
//            System.out.println("[ERROR] - REQUEST NULL");
//            response.sendError(response.SC_BAD_REQUEST, "BAD REQUEST | PARAMETER REQUIRED");
//        } else if (request != null && taskQueue.size() > 0) {
//            System.out.println("TASK QUEUE SIZE : " + taskQueue.size());
//            Task task = taskQueue.peek();
//
//            response.setHeader("Task-Identity", task.getTaskID());
//            taskQueue.remove();
//
//        }  else if (request != null && taskQueue.size() <= 0) {                                                         // NO TASKS
//            System.out.println("TASK RECEIVER (doGet) : There are no tasks to compute at this moment.");
//            System.out.println("TASK QUEUE SIZE : " + taskQueue.size());
//            response.setHeader("Task-Identity", "null");
//        }
//
//        int status = response.getStatus();
//        System.out.println("STATUS CODE : " + status);
//        System.out.println("-------- (UPLOAD RECEIVER - doGet) --------");
//    }

    // putTask sends a PUT request to Scheduler class to update the SubtaskPackageQueue with new TaskIDs for scheduling.
    private void putTask(Task task) {
        String taskID = task.getTaskID();

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpUriRequest request = RequestBuilder
                    .put(schedulerServlet)
                    .build();

            request.setHeader("Task-Identity", taskID);

            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            System.out.println("TASK RECEIVER | PUT_TASK Status Code : " + status);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // The doPost method will be called from the web-app to receive parameters/information of the selected Application and Data to be parallelized.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LinkedHashMap<String, String> taskParamsMap = readJsonTaskParams(request.getReader());

//        application = taskParamsMap.get("application");
        Task task = new Task(taskParamsMap);
        putTask(task);
        System.out.println("TASK_RECEIVER | " + task.getTaskID() + " added to queue.");

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private static LinkedHashMap<String, String> readJsonTaskParams (BufferedReader reader) throws IOException {
        StringBuffer jsonString = new StringBuffer();
        LinkedHashMap<String, String> taskParams;
        String line = null;
        try {
            BufferedReader bufferedReader = reader;
            while ((line = reader.readLine()) != null)
                jsonString.append(line);
        } catch (Exception e) { /*report an error*/ }

        try {
            JSONObject jsonObject =  HTTP.toJSONObject(jsonString.toString());
            System.out.println("JB STRING : " + jsonString);
            taskParams = extractJsonTaskParams(jsonString.toString());
            System.out.println("JSON OBJECT : " + jsonObject);
        } catch (JSONException e) {
            throw new IOException("Error parsing JSON request string");
        }

        return taskParams;
    }

    //  The extractJsonTaskParams is paramount to ensuring the right task information is passed to create the right tasks.
    //  The method will extract and define the 'application' parameter to decide what Key and Value are to be added into the taskParamsMap to be passed to the Task Class.
    private static LinkedHashMap<String, String> extractJsonTaskParams (String jsonParam) {
        ArrayList<String> params = new ArrayList<>();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        String s = jsonParam.replaceAll("[\"{}]", "");

        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter=0;
        while(scanner.hasNext()) {
            params.add(counter, scanner.next());
            counter++;
        }

        // Scan the jsonParam String to identify what application and task is uploaded. Then add Key and Value of parameters to the map respectively.
        // New Application Task Types are to be added here.
        if (jsonParam.contains("blender")) {
            map.put("taskID", params.get(0));
            map.put("userEmail", params.get(1));
            map.put("shareLink", params.get(2));
            map.put("application", params.get(3));
            map.put("frameCategory", params.get(4));
            map.put("startFrame", params.get(5));
            map.put("endFrame", params.get(6));
            map.put("renderOutputType", params.get(7));
            map.put("computeRate", params.get(8));
        } else if (jsonParam.contains("vray")) {
            // 1. Extract Params for VRAY TASK
        }

        System.out.println("UPLOAD PARAMS : ");
        map.forEach((key, value) -> System.out.println(key + ":" + value));

        scanner.close();

        return map;
    }
}
