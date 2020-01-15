package nebula.nebulaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


@SuppressWarnings("serial")
public class Controller extends HttpServlet {

    static final String DATA_COLLECTION_JSP="/WEB-INF/jsp/data_collection.jsp";
    static ServletContext sc;
    Logger log;
    // private
    // "/WEB-INF/app.properties" also works...
    private static final String PROPERTIES_PATH = "WEB-INF/app.properties";
    private Properties properties;

    @Override
    public void init() throws ServletException {
        super.init();
        // synchronize !
        if (sc == null) sc = getServletContext();
        log = LoggerFactory.getLogger(this.getClass());
        try {
            loadProperties();
        } catch (IOException e) {
            throw new RuntimeException("Can't load properties file", e);
        }
    }

    private void loadProperties() throws IOException {
        try(InputStream is= sc.getResourceAsStream(PROPERTIES_PATH)) {
            if (is == null)
                throw new RuntimeException("Can't locate properties file");
            properties = new Properties();
            properties.load(is);
        }
    }

    String property(final String key) {
        return properties.getProperty(key);
    }
}
