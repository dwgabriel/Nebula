import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by Daryl Wong on 3/29/2019.
 */


@WebServlet(
        name = "Test",
        urlPatterns = {"/test"}
)

public class Test extends HttpServlet {


    public static void main(String[] arg) throws IOException {

    }

    public void doGet(HttpServletRequest request ,
                      HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain");

        PrintWriter out = response.getWriter();

        String application = (String)request.getAttribute("application");

        out.println("The Application is "+ application);

    }


    public void doPost(HttpServletRequest request , HttpServletResponse response) throws ServletException , IOException {
        doGet(request,response);
    }





//    @Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        String application = "blender";
//
//        response.setHeader("application", application);
//        System.out.println("This was called.");
//    }

//    @Override
//    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        String application = "blender";
//        System.out.println("This was called. ");
//
//        String username = request.getParameter("username");
//        String tester = request.getParameter("TEST");
//        System.out.println("Username : " + username);
//        System.out.println("TEST : " + tester);
//
//            System.out.println("This was called too.");
//            response.setHeader("application", application);
//            System.out.println("Problem's here");
//            response.sendError(500, "NO USERNAME");
//
//    }
}
