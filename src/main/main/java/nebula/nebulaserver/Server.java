package nebula.nebulaserver;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;



/**
 * Created by Daryl Wong on 3/11/2019.
 */
public class Server {

    private static File nebuladatabase;
    private static String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private static File rootDir = new File(RootPath);

    public static void main(String[] args) throws Exception {
       startServer();
        System.out.println("Server down.");

    }

    public static void createDatabase() {
        nebuladatabase = new File(rootDir, "nebuladatabase");
        if (!nebuladatabase.getAbsoluteFile().exists()) {
            nebuladatabase.mkdir();

            File result = new File(nebuladatabase, "result");
            File schedulerCache = new File(nebuladatabase, "schedulercache");
            File tasks = new File(nebuladatabase, "tasks");
            result.mkdir();
            schedulerCache.mkdir();
            tasks.mkdir();
        } else {
            System.out.println("Database already exists. ");
        }
    }

    public static void startServer() throws Exception {
        String homepageDirLocation = "src/main/webapp";
        Tomcat tomcat = new Tomcat();
        // Requires connection to Front End : Webpage, web-app, UI/UX
        String webPort = System.getenv("PORT");
        if(webPort == null) {
            webPort = "8080";
        }

        tomcat.setPort(Integer.valueOf(webPort));
        StandardContext ctx = (StandardContext) tomcat.addWebapp("/", new File(homepageDirLocation).getAbsolutePath());

        System.out.println("Configuring app with basedir: " + new File(homepageDirLocation).getAbsolutePath());

        File additionWebInfClasses = new File("target/classes");
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                additionWebInfClasses.getAbsolutePath(), "/"));
        ctx.setResources(resources);
        createDatabase();

        tomcat.start();
        System.out.println("Server started.");
        tomcat.getServer().await();
    }
}
