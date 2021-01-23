package nebula.nebulaserver;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EarningsBook {

    private NodeUser nodeUser;
    private LinkedHashMap<String, NodeUser.Node> nodesMap;
    private final String RootPath = new File("").getAbsolutePath();                                                         // RootDir = server /app directory path
    private final File rootDir = new File(RootPath);
    private final File earningsDatabase = new File(rootDir, "/nebuladatabase/earnings");

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    LocalDateTime now = LocalDateTime.now();

    public EarningsBook(NodeUser nodeUser) {
        this.nodeUser = nodeUser;
        this.nodesMap = nodeUser.getNodesMap();
    }

    public File writeEarnings() {                                                                                                    // todo - TO BE RECTIFIED
        File earningsExcel = null;
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("Earnings Summary for " + nodeUser.getNodeEmail() + " | " + now);
            createHeaderRow(sheet);
            int rowCount = 0;
//            System.out.println("EARNINGS CHECK | NODE_EMAIL : " + nodeUser.getNodeEmail() + " | SIZE : " + node.getCompletedSubtasks().size());

            for (int i=0; i<nodesMap.size(); i++) {
                NodeUser.Node node = nodesMap.get(i);
                int counter = 1;
                LinkedHashMap<String, Receiver.SubtaskCosts> nodeCompletedSubtasksMap = node.getCompletedSubtasks();
                Iterator<Receiver.SubtaskCosts> iterator = nodeCompletedSubtasksMap.values().iterator();

                while (iterator.hasNext()) {
                    Receiver.SubtaskCosts subtaskCost = iterator.next();
                    Row row = sheet.createRow(++rowCount);
                    writeBook(node, subtaskCost, row);
                    System.out.println(counter + ". Earnings for Subtask : " +  subtaskCost.getSubtaskID());
                    counter++;
                }
//                ArrayList<CompletedTask.CompletedSubtask> completedSubtasks = node.getCompletedSubtasks();
//
//                for (int j=0; j<completedSubtasks.size(); j++) {
//                    CompletedTask.CompletedSubtask subtask = completedSubtasks.get(j);
//                    Row row = sheet.createRow(++rowCount);
//                    writeBook(node, subtask, row);
//                    System.out.println(i + ". Earnings for Subtask : " +  subtask.getSubtaskID());
//                }
            }
            String earningsFileName = String.format("[" + dtf.format(now) + "] Earnings Summary for " + nodeUser.getNodeEmail());
            earningsExcel = new File(earningsFileName + ".xlsx");
            try (FileOutputStream outputStream = new FileOutputStream(earningsExcel)) {
                workbook.write(outputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return earningsExcel;
    }

    private void writeBook(NodeUser.Node node, Receiver.SubtaskCosts subtaskCost, Row row) {                                            // todo - TO BE RECTIFIED
        Cell cell = row.createCell(0);
        cell.setCellValue(subtaskCost.getSubtaskID());

        cell = row.createCell(1);
        cell.setCellValue(subtaskCost.getComputeMinutes());

//        XSSFCellStyle curStyle = workbook.createCellStyle();
//        XSSFDataFormat df = workbook.createDataFormat();
//        curStyle.setDataFormat(df.getFormat("$#,#0.00"));
//        cell.setCellStyle(curStyle);
//        cell.setCellType(XSSFCell.CELL_TYPE_NUMERIC);
//        cell.setCellValue("2156820.54");

        cell = row.createCell(2);
        cell.setCellValue(subtaskCost.getCost());

        cell = row.createCell(3);
        cell.setCellValue(subtaskCost.getNodeEmail());

        cell = row.createCell(4);
        cell.setCellValue(node.getDeviceID());

        cell = row.createCell(5);
        cell.setCellValue(node.getIpAddress());

    }

    private static void createHeaderRow (Sheet sheet) {
        CellStyle cellStyle = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        cellStyle.setFont(font);

        Row row = sheet.createRow(0);

        Cell subtaskHeader = row.createCell(0);
        subtaskHeader.setCellStyle(cellStyle);
        subtaskHeader.setCellValue("Subtask ID");

        Cell profitHeader = row.createCell(1);
        profitHeader.setCellStyle(cellStyle);
        profitHeader.setCellValue("Profit (MYR)");


        Cell computeHeader = row.createCell(2);
        computeHeader.setCellStyle(cellStyle);
        computeHeader.setCellValue("Compute Hour(s)");

        Cell nodeIDHeader = row.createCell(3);
        nodeIDHeader.setCellStyle(cellStyle);
        nodeIDHeader.setCellValue("Node ID");

        Cell deviceIDHeader = row.createCell(4);
        deviceIDHeader.setCellStyle(cellStyle);
        deviceIDHeader.setCellValue("Device ID");

        Cell ipHeader = row.createCell(5);
        ipHeader.setCellStyle(cellStyle);
        ipHeader.setCellValue("IP Address");
    }
}


