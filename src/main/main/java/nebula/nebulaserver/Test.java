package nebula.nebulaserver;


import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

//import javax.servlet.http.HttpServlet;

/**
 * Created by Daryl Wong on 3/29/2019.
 */


@WebServlet(
        name = "Test",
        urlPatterns = {"/test"}
)

@MultipartConfig

public class Test extends HttpServlet {

    private static String uniqueID = null;
    private static String deviceID = null;
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";

        public static void main(String[] args) {
            uniqueID = UUID.randomUUID().toString();
            deviceID = UUID.randomUUID().toString();
            System.out.println("Unique ID : " + uniqueID);
            System.out.println("Device ID : " + deviceID);


            Connection connection = null;
            Statement statement = null;
            ResultSet resultSet = null;

        }

    }



