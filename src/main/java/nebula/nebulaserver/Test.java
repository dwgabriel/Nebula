package nebula.nebulaserver;


import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;

//import javax.servlet.http.HttpServlet;

/**
 * Created by Daryl Wong on 3/29/2019.
 */


@WebServlet(
        name = "Test",
        urlPatterns = {"/testUpload"}
)

@MultipartConfig

public class Test extends HttpServlet {

    private static DbxRequestConfig config = Server.config;
    private static DbxClientV2 client = Server.client;

    static String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    static final File rootDir = new File(RootPath);
    static File taskDatabase = new File(rootDir, "/nebuladatabase/tasks");
    static File taskDir = new File(taskDatabase, "test_123");
    static File uploadDir = new File(rootDir, "/testUpload");
    static File renderfileDir = new File(taskDir, "renderfile");
    static File testFiles = new File("C:\\Users\\Daryl\\Desktop\\Misc\\mountainrange.jpg");
    private LinkedHashMap<String, ArrayList> taskCostsMap = new LinkedHashMap<>();
    static LinkedHashMap<String, String> resultParamsMap = new LinkedHashMap<>();


    static Deque<Integer> stringDeque = new LinkedList<>();

    static int renderHeight = 2160;
    static int renderWidth = 3940;

    static LinkedHashMap<String, String> subtaskQueue = new LinkedHashMap<>();
    static ArrayList<String> user000_scheduledNodes = new ArrayList<>();
    static ArrayList<String> user001_scheduledNodes = new ArrayList<>();
    static LinkedHashMap<String, ArrayList<String>> schedulingMap = new LinkedHashMap<>();

    public static void main(String[] args) {

        try {

            System.out.println("FINAL DIV : " + calculateDivisionRate(25, 2000*1000));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int calculateDivisionRate (int computeRate, int size) {
        double divisionRate = 0;
        double multiplier = 1.025;
        double minComputeRate = 25;

        int division = size / 5000;
        divisionRate = minComputeRate * multiplier;
        System.out.println("Initial Div : " + (int)divisionRate);

        for (int i=1; i<=division; i++) {
            double div = divisionRate * multiplier;
            divisionRate = div;
        }

        if (divisionRate < computeRate) {
            divisionRate = computeRate;
        }

        return (int)divisionRate;
    }

    private static ClientResponse emailNewTask(String taskID) {
        final String from = "admin@nebula.my";
        final String toEmail = "darylgabrielwong@gmail.com";
        final String ccEmail2 = "chris_kee@hotmail.com";
        final String ccEmail = "gizmo.chriskee@gmail.com";

        Client client = Client.create();
        client.addFilter(new HTTPBasicAuthFilter("api", Server.mailGunApiKey));
        WebResource webResource = client.resource("https://api.mailgun.net/v3/nebula.my/messages");
        MultivaluedMapImpl formData = new MultivaluedMapImpl();

        String msg = "NEW RENDER -- TASK ID : " + taskID;

        formData.add("from", from);
        formData.add("to", "<" + toEmail + ">");
        formData.add("cc","<" + ccEmail + ">");
        formData.add("cc","<" + ccEmail2 + ">");
        formData.add("subject", msg);
        formData.add("text", msg);

        return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formData);
    }



}



