package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by Daryl Wong on 3/11/2019.
 */
public class Server {

    private static File nebuladatabase;
    private static String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private static File rootDir = new File(RootPath);
    public static String dbxToken = "EYpuJvW1YhgAAAAAAAAAAXN9mMRDO-r12iIhI_LF9vxNQNHXFu2nI-Yz01QnFjG7";
    public static String mailGunApiKey = "4537a625cbcbe9a8aff6cddca77f2de1-4b1aa784-683105fb";
    final static DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/nebula-render").build();
    final static DbxClientV2 client = new DbxClientV2(config, Server.dbxToken);

    public static void main(String[] args) throws Exception {
       startServer();
        System.out.println("SERVER | Server down.");
    }

    private static void createDatabase() {
        nebuladatabase = new File(rootDir, "nebuladatabase");
        if (!nebuladatabase.getAbsoluteFile().exists()) {
            nebuladatabase.mkdir();

            File result = new File(nebuladatabase, "result");
            File schedulerCache = new File(nebuladatabase, "schedulercache");
            File tasks = new File(nebuladatabase, "tasks");
            File earnings = new File(nebuladatabase, "earnings");
            result.mkdir();
            schedulerCache.mkdir();
            tasks.mkdir();
            earnings.mkdir();
        } else {
            System.out.println("SERVER | Database already exists. ");
        }
    }

    private static void startEmailTimer() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT+8"));
        ZonedDateTime nextRun = now.withHour(23).withMinute(59).withSecond(0);
        if(now.compareTo(nextRun) > 0)
            nextRun = nextRun.plusDays(1);

        System.out.println("SERVER | " + dtf.format(now));

        Duration duration = Duration.between(now, nextRun);
        long initialDelay = duration.getSeconds();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    promptResultsReceiver("EARNINGS");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(runnable,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS);
    }

    private static void startResultCheckTimer() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    promptResultsReceiver("PENDING");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(runnable,
                60,
                60,
                TimeUnit.SECONDS);
    }
    private static void startServer() throws Exception {
        startEmailTimer();
        startResultCheckTimer();
        String homepageDirLocation = "src/main/webapp";
        Tomcat tomcat = new Tomcat();
        // Requires connection to Front End : Webpage, web-app, UI/UX
        String webPort = System.getenv("PORT");
        if(webPort == null) {
            webPort = "8080";
        }

        tomcat.setPort(Integer.valueOf(webPort));
        StandardContext ctx = (StandardContext) tomcat.addWebapp("/", new File(homepageDirLocation).getAbsolutePath());

        System.out.println("SERVER | Configuring app with basedir: " + new File(homepageDirLocation).getAbsolutePath());

        File additionWebInfClasses = new File("target/classes");
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources,"/WEB-INF/classes",
                additionWebInfClasses.getAbsolutePath(), "/"));
        ctx.setResources(resources);
        createDatabase();

        tomcat.start();
        System.out.println("SERVER | Server started.");
        tomcat.getServer().await();
    }

    public static void promptResultsReceiver(String prompt) throws IOException {
        String receiverServlet = "https://nebula-server.herokuapp.com/complete";
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpUriRequest request = RequestBuilder
                .get(receiverServlet)
                .build();

        request.addHeader("PROMPT", prompt);

        CloseableHttpResponse response = httpClient.execute(request);
        int status = response.getStatusLine().getStatusCode();
        System.out.println("SERVER | Prompt ResultReceiver (" + prompt + ") - STATUS : " + status);
    }
}
