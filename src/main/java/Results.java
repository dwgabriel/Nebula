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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Created by Daryl Wong on 4/3/2019.
 */

@WebServlet(
        name = "ResultServlet",
        urlPatterns = {"/results"}
)
public class Results extends HttpServlet {

    // Collection of all Completed & Verified results ready for returning to Clients
    // 1. Receives Client ID as Post request to retrieve completed job results, if any.

    public static void main(String[] args) throws IOException {

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { // Receives Client ID as request for Job results, if any. (Every 5 min)


        String clientIdentity = request.getParameter("username");  // Retrieve <input type="text" name="username"> - Username of Client Demand User

        boolean name = false;

        ServletOutputStream out = response.getOutputStream();

        if (clientIdentity.length() == 0) { // Checks clientIdentity Parameter and if validated, moves on to deviceIdentity Parameter.
            System.out.println("1. Name invalid.");
            out.write("Your username is invalid.".getBytes("UTF-8"));
            out.flush();
            out.close();
        } else {
            name = true;
            System.out.println("1. Name valid. ");
        }

        if (name == true) {

            File clientCollection = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\752645\\clientcollection"); // HARD - CODE. NEEDS TO BE AUTOMATED.

            if (clientCollection.listFiles() != null) {
                try (Stream<Path> fileStream = Files.walk(Paths.get(clientCollection.getAbsolutePath()))) {
                    fileStream
                            .filter(Files::isRegularFile)
                            .map(Path::toFile)
                            .forEachOrdered(file -> {
                                // 1. Iterate to next task when one is scheduled.
                                try {

                                    File fileDecode = new File(file, URLDecoder.decode(file.getAbsolutePath(), "UTF-8"));
                                    String contentType = getServletContext().getMimeType(fileDecode.getName());
                                    String fileID = String.format(file.getName());

                                    response.reset();
                                    response.setContentType(contentType);
                                    response.setHeader("Task-size", String.valueOf(file.length()));
                                    response.setHeader("Task-ID", file.getName());

                                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                                    byte[] buffer = new byte[(int)file.length()];
                                    int length;
                                    while ((length = in.read(buffer)) > 0){
                                        out.write(buffer, 0, length);
                                    }
                                    in.close();
                                    out.flush();

                                    System.out.println("Testing that this works.");

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                }
            }
            if (clientCollection.listFiles() != null)
                try (Stream<Path> fileStream = Files.walk(Paths.get(clientCollection.getAbsolutePath()))) {
                    fileStream
                            .filter(Files::isRegularFile)
                            .map(Path::toFile)
                            .forEach(System.out::println);
                }
        }
    }
}
