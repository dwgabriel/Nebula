package nebula.nebulaserver;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@SuppressWarnings("serial")
@MultipartConfig
@WebServlet(
        name = "com.nebula.DCS",
        urlPatterns = {"/dataservlet"}
)

public final class DataCollectionServlet extends Controller {


    private static final String UPLOAD_LOCATION_PROPERTY_KEY="upload.location";
    private String uploadsDirName;
    File file = new File("C:\\Users\\Daryl\\Desktop\\nebuladatabase\\tasks");

    @Override
    public void init() throws ServletException {
        super.init();
        uploadsDirName = property(UPLOAD_LOCATION_PROPERTY_KEY);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // ...
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Part taskFile = req.getPart("renderfile");
        InputStream fileContent = taskFile.getInputStream();


            File save = new File(uploadsDirName, taskFile.getSubmittedFileName());
            final String absolutePath = save.getAbsolutePath();
            log.debug(absolutePath);
        Files.copy(fileContent, save.toPath());
            resp.setHeader("STATUS", save.getName() + " is saved to : " + save.getAbsolutePath());
        }
    }

