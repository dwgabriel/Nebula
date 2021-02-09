package nebula.nebulaserver;


import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.dropbox.core.v2.users.FullAccount;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Deque;
import java.util.HashMap;
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
    static File renderfileDir = new File(taskDir, "renderfile");
    static File desktop = new File("C:\\Users\\Daryl\\Desktop\\testScript");

    static Deque<Integer> stringDeque = new LinkedList<>();

    static int renderHeight = 450;
    static int renderWidth = 850;

//    static Iterator<String> stringIterator = stringDeque.iterator();
    static HashMap<String, String> taskCosts = new HashMap<>();

    public static void main(String[] args) {
        try {
            String userShareLink = "https://www.dropbox.com/s/6qlnydq175s9dsa/sketchupDemo.vrscene?dl=0";
            File testFile = new File(desktop, "testFile.vrscene");
            File renderfile = downloadRenderfile(userShareLink, "vray", "testFile");

            String url = uploadRenderfileToDropbox(renderfile);
            System.out.println("Sharelink : " + url);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static File downloadRenderfile(String url, String application, String taskID) throws MalformedURLException {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String filename = null;
        if (application.contains("blender")) {
            filename = String.format(taskID + ".blend");
        } else if (application.contains("vray")) {
            filename = String.format(taskID + ".vrscene");
        }

        File renderFile = new File(desktop.getAbsolutePath(), filename);
        int renderfileLength = Task.getFileSizeInKB(new URL(url));
        int renderFileLimit = 150000;

        if (url.contains("google") && renderfileLength <= renderFileLimit) {
            System.out.println("Google Drive share link detected. Downloading from GDrive URL . . . ");
            renderFile = downloadRenderfileFromGDrive(url, desktop, filename);
        } else if (url.contains("dropbox") && renderfileLength <= renderFileLimit) {
            System.out.println("DropBox share link detected. Downloading from DropBox URL . . .");
            renderFile = downloadRenderfileFromDbx(url, desktop, filename);
        } else {
            System.out.println("ERROR : INVALID / UNKNOWN URL");
        }

        return renderFile;
    }

    public static String uploadRenderfileToDropbox(File renderfile) { // Uploads Result to Dropbox for users to view/download whenever they want through the dropbox link. NEGATIVE - Growing expense to host user renders.
        String path;
        String shareLink;
        try {
            FullAccount account = client.users().getCurrentAccount();
            System.out.println("Dbx Acc : " + account.getName().getDisplayName());

            InputStream in = new FileInputStream(renderfile);
            FileMetadata metadata = client.files().uploadBuilder("/render/" + renderfile.getName())
                    .uploadAndFinish(in);

            path = metadata.getPathLower();
            shareLink = getShareLink(path);
//            shareLink = "test";

            return shareLink;
        } catch (Exception e) {
            System.out.println("Error in uploading renderfile to Dropbox.");
            e.printStackTrace();
            return null;
        }
    }

    public static String getShareLink(String path) {
        try {
            String url;

                SharedLinkMetadata metadata = client.sharing().createSharedLinkWithSettings(path);
                url = metadata.getUrl();
                return url.replace("?dl=0", "?dl=1"); //?raw=1

        }  catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static File[] generateTileSplitScript(File outputDir, String frameID, int computeRate)   // TODO -  Generates a general python script with instructions and information on rendering subtasks with Blender
            throws IOException {                                                                                        // TODO - 1. Takes a multiplier to calculate TileBorders
        TileBorder[] tileBorders = calcTileBorders(computeRate, renderHeight, renderWidth);
        File[] allScripts = new File[tileBorders.length];

        int tileCounter = 1;

        for (int i=0; i<tileBorders.length; i++) {
            TileBorder tileBorder = tileBorders[i];
            String scriptName = String.format("testTilescript.txt");
            File script = new File(outputDir, scriptName);

            int top = (int) tileBorder.getTop();
            int left = (int) tileBorder.getLeft();
            int right = (int) tileBorder.getRight();
            int bottom = (int) tileBorder.getBottom();

            System.out.println("topp : " + tileBorder.getTop() + ", leftt : " + tileBorder.getLeft() + ", rightt : " + tileBorder.getRight() + ", bottomm : " + tileBorder.getBottom());
            System.out.println("top : " + top + ", left : " + left + ", right : " + right + ", bottom : " + bottom);
            System.out.println("--------------------------------------------------------------------------------------");

            PrintWriter fout = new PrintWriter(new FileWriter(script));
            fout.println(top + "," + left + "," + right + "," + bottom);
//            fout.println("vray -sceneFile=" + );

            fout.flush();
            fout.close();
            allScripts[i] = script;
        }
        return allScripts;
    }

    public static TileBorder[] calcTileBorders(int computeRate, int renderHeight, int renderWidth) {
        TileBorder[] tileBorders = new TileBorder[computeRate];
        float chunk = (float) 1 / (float) computeRate;

        int t = 0;
        float left, bottom, right, top;
        for (int y = 1; y < computeRate + 1; y++) {
            for (int x = 1; x < computeRate + 1; x++) {

                //x coordinates
                if (x == 1) {  //left border tile\
                    left = 0.0F;
                    right = chunk;
                } else if (x == computeRate) { //right border tile
                    left = chunk * (computeRate - 1);
                    right = 1.0F;
                } else {    //tile not on left or right border...
                    left = chunk * (float) (x - 1);
                    right = chunk * (float) x;
                }

                //y coordinates
                if (y == 1) {  //bottom border tile
                    bottom = 0.0F;
                    top = chunk;
                } else if (y == computeRate) { //top border tile
                    bottom = chunk * (computeRate - 1);
                    top = 1.0F;
                } else {    //tile not on bottom or top border...
                    bottom = chunk * (float) (y - 1);
                    top = chunk * (float) y;
                }

                float topV = (float) round(top*renderHeight, 0);
                float leftV = (float) round(left*renderWidth, 0);
                float rightV = (float) round(right*renderWidth, 0);
                float bottomV = (float) round(bottom*renderHeight, 0);

//                int topp = (int) topV;
//                int leftt = (int) leftV;
//                int rightt = (int) rightV;
//                int bottomm = (int) bottomV;

//                System.out.println("top : " + topp + ", left : " + leftt + ", right : " + rightt + ", bottom : " + bottomm);
//                System.out.println("topp : " + topV + ", leftt : " + leftV + ", rightt : " + rightV + ", bottomm : " + bottomV);
//                System.out.println("--------------------------------------------------------------------------------------");

                tileBorders[t] = new TileBorder(leftV, rightV, bottomV, topV);
            }
            t++;
        }

        return tileBorders;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static File downloadRenderfileFromDbx(String url, File renderfileDir, String filename) {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String downloadURL = url.replace("?dl=0", "?dl=1");
        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
        try{
            URL download=new URL(downloadURL);
            System.out.println("DOWNLOAD (DBOX) : " + renderFile.getName());
            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
//            InputStream in = new BufferedInputStream((download.openStream()));
//            ByteArrayOutputStream out = new ByteArrayOutputStream();

            FileOutputStream fileOut = new FileOutputStream(renderFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderFile;
    }

    public static File downloadRenderfileFromGDrive(String url, File renderfileDir, String filename) {
        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
        File renderFile = new File(renderfileDir.getAbsolutePath(), filename);
        try{
            URL download=new URL(gdriveURL);
            System.out.println("DOWNLOAD (GDRIVE) : " + download.openStream());
            ReadableByteChannel rbc= Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(renderFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderFile;
    }


}



