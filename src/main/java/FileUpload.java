import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Daryl Wong on 3/12/2019.
 */
public class FileUpload extends HttpServlet {

//    private final static Logger LOGGER =
//            Logger.getLogger(FileUpload.class.getCanonicalName());

    protected void processRequest(HttpServletRequest request)
            throws Exception {

        boolean isMultipart = ServletFileUpload.isMultipartContent(request);

        // Create a factory for disk-based file items
        DiskFileItemFactory factory = new DiskFileItemFactory();


// Configure a repository (to ensure a secure temp location is used)
        ServletContext servletContext = this.getServletConfig().getServletContext();
        File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
        factory.setRepository(repository);

// Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        final int uploadSize = 10 * 1024 * 1024;
        upload.setFileSizeMax(uploadSize);


// Parse the request
        List<FileItem> items = upload.parseRequest(request);

        // Process the uploaded items
        Iterator<FileItem> iter = items.iterator();
        while (iter.hasNext()) {
            FileItem item = iter.next();

            if (item.isFormField()) {
                String name = item.getFieldName();
                String value = item.getString();
                System.out.println("Name : " + name + "\n"
                        + "Value : " + value);
            } else if (!item.isFormField()) {
                String fieldName = item.getFieldName();
                String fileName = item.getName();
                String contentType = item.getContentType();
                boolean isInMemory = item.isInMemory();
                long sizeInBytes = item.getSize();

                File uploadedFile = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\database");
                item.write(uploadedFile); // Writing to memory could be faster for access, however OS buffers are well optimized for read/writes to disks (never use both as it cancels each other out)
                System.out.println(fileName + " has been uploaded to database.\n"
                        + "(" + fieldName + " - " + contentType + " : " + sizeInBytes + ")");
            }
        }
    }
}
//        -------------------------------------------------------------------------------------------------------------
//        ORACLE JAVA EE6 - File Upload Example Application (Incomplete)


//        response.setContentType("text/html;charset=UTF-8");
//
//        // Create path components to save the file
//        final String path = request.getParameter("destination");
//        final Part filePart = request.getPart("file");
//        final String fileName = getFileName(filePart);
//
//        OutputStream out = null;
//        InputStream filecontent = null;
//        final PrintWriter writer = response.getWriter();
//
//        try {
//            out = new FileOutputStream(new File(path + File.separator
//                    + fileName));
//            filecontent = filePart.getInputStream();
//
//            int read = 0;
//            final byte[] bytes = new byte[1024];
//
//            while ((read = filecontent.read(bytes)) != -1) {
//                out.write(bytes, 0, read);
//            }
//            writer.println("New file " + fileName + " created at " + path);
//            LOGGER.log(Level.INFO, "File{0}being uploaded to {1}",
//                    new Object[]{fileName, path});
//        } catch (FileNotFoundException fne) {
//            writer.println("You either did not specify a file to upload or are "
//                    + "trying to upload a file to a protected or nonexistent "
//                    + "location.");
//            writer.println("<br/> ERROR: " + fne.getMessage());
//
//            LOGGER.log(Level.SEVERE, "Problems during file upload. Error: {0}",
//                    new Object[]{fne.getMessage()});
//        } finally {
//            if (out != null) {
//                out.close();
//            }
//            if (filecontent != null) {
//                filecontent.close();
//            }
//            if (writer != null) {
//                writer.close();
//            }
//        }
//    }
//
//    private String getFileName(final Part part) {
//        final String partHeader = part.getHeader("content-disposition");
//        LOGGER.log(Level.INFO, "Part Header = {0}", partHeader);
//        for (String content : part.getHeader("content-disposition").split(";")) {
//            if (content.trim().startsWith("filename")) {
//                return content.substring(
//                        content.indexOf('=') + 1).trim().replace("\"", "");
//            }
//        }
//        return null;
//    }
