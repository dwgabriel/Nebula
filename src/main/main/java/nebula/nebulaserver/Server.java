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

    public static void main(String[] args) throws Exception {

//      -----------------------------------------------------------------------------------
//        HEROKU Java Web App Example using Embedded Tomcat

        String homepageDirLocation = "src/main/webapp";
        Tomcat tomcat = new Tomcat();
                                                                                                                        // Requires connection to Front End : Webpage, web-app, UI/UX
        String webPort = System.getenv("PORT");
        if(webPort == null) {
            webPort = "8080";
        }

        tomcat.setPort(Integer.valueOf(webPort));
        StandardContext ctx = (StandardContext) tomcat.addWebapp("/", new File(homepageDirLocation).getAbsolutePath());

        System.out.println("configuring app with basedir: " + new File(homepageDirLocation).getAbsolutePath());

        File additionWebInfClasses = new File("target/classes");
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                additionWebInfClasses.getAbsolutePath(), "/"));
        ctx.setResources(resources);

        tomcat.start();
        tomcat.getServer().await();
    }
}
