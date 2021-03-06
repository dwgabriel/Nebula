package nebula.nebulaserver;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet(
        name = "UpdaterServlet",
        urlPatterns = {"/update"}
)

public class NodeUpdater extends HttpServlet {

    final DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/nebula-update").build();
    final DbxClientV2 client = new DbxClientV2(config, Server.dbxToken);
    private final String latestNodeVersion = "1.3.15";


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req == null) {
            System.out.println("UPDATER | Bad Request (NodeUpdater - doGet). Request Null.");
            resp.sendError(resp.SC_BAD_REQUEST, "Bad Request");
        } else if (req != null) {

            String ipAddress = req.getHeader("IP-ADDRESS");
            String nodeEmail = req.getHeader("NODE-EMAIL");
            String version = req.getHeader("NODE-VERSION");

            String nodeUpdateConfigURL = checkNodeUpdateConfigURL();
            resp.setHeader("Latest-Version", latestNodeVersion);
            resp.setHeader("Update-URL", nodeUpdateConfigURL);
            System.out.println("NODE UPDATER | Node Email : " + nodeEmail + " | IP-Address : " + ipAddress + " | Version : " + version);
        }
    }

    public String checkNodeUpdateConfigURL() {
        String url = null;
        try {
            ListFolderResult listing = client.files().listFolderBuilder("/Node Update").start();

            for (Metadata child : listing.getEntries()) {

                if (child.getName().equals("node-update-config.txt")) {
                    String path = child.getPathLower();
                    url = getShareLink(path);
                }
            }

            if (url == null) {
                System.out.println("UPDATER | Node Update Config not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return url;
    }

    public String getShareLink(String path) {
        try {
            String url;

            DbxUserSharingRequests share = client.sharing();
            ListSharedLinksResult linksResult = client.sharing().listSharedLinksBuilder()
                    .withPath(path)
                    .start();

            List<SharedLinkMetadata> links = linksResult.getLinks();

            if (links.size() > 0) {
                url = links.get(0).getUrl();
            } else {
                SharedLinkSettings settings = new SharedLinkSettings(RequestedVisibility.PUBLIC, null, null, null, RequestedLinkAccessLevel.VIEWER);
                SharedLinkMetadata metadata = share.createSharedLinkWithSettings(path, settings);
                url = metadata.getUrl();
            }
            return url.replace("?dl=0", "?dl=1");

        }  catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
