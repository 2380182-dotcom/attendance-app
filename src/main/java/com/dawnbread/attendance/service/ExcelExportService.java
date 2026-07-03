package com.dawnbread.attendance.service;

import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.GeoFenceLog;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.GeoFenceLogRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private GeoFenceLogRepository geoFenceLogRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ByteArrayInputStream exportSalesRecords(List<SalesRecord> records) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sales Records");
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Agent", "Store", "Date", "Time", "Total Units", "Total Amount (PKR)", "Status"};

            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (SalesRecord record : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(record.getId());
                row.createCell(1).setCellValue(record.getAgent().getName());
                row.createCell(2).setCellValue(record.getStoreName() != null ? record.getStoreName() : record.getLocation());
                row.createCell(3).setCellValue(record.getSaleDate().format(DATE_FORMATTER));
                row.createCell(4).setCellValue(record.getSaleTime().toString());
                int units = record.getTotalUnits() != null ? record.getTotalUnits() :
                        record.getItems().stream().mapToInt(SaleItem::getQuantity).sum();
                row.createCell(5).setCellValue(units);
                row.createCell(6).setCellValue(record.getTotalAmount());
                row.createCell(7).setCellValue(record.getStatus() != null ? record.getStatus() : "PENDING");
            }

            autoSizeColumns(sheet, columns.length);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public ByteArrayInputStream exportAllSales() throws IOException {
        return exportSalesRecords(salesRecordRepository.findAll());
    }

    public ByteArrayInputStream exportAgentSales(Long agentId) throws IOException {
        return exportSalesRecords(salesRecordRepository.findByAgentIdOrderBySaleDateDescSaleTimeDesc(agentId));
    }

    public ByteArrayInputStream exportDepartmentSales(String department) throws IOException {
        return exportSalesRecords(salesRecordRepository.findByAgentDepartment(department));
    }

    public ByteArrayInputStream exportAllReports(LocalDate date, Long agentId, Integer year, Integer month) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            createDailyAttendanceSheet(workbook, date, agentId);
            createMonthlyAttendanceSheet(workbook, agentId, year, month);
            createLateArrivalsSheet(workbook, date, agentId);
            createSalesVisitsSheet(workbook, date, agentId);
            createGeoFenceActionsSheet(workbook, agentId);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public ByteArrayInputStream exportAgentReport(Long agentId, LocalDate startDate, LocalDate endDate) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Attendance History");
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Date", "Mart Name", "Check-in Time", "Check-out Time", "Status", "Distance (m)"};
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
            LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
            
            List<Attendance> attendances = attendanceRepository.findByAgentIdAndCheckInTimeBetween(agentId, start, end);

            int rowIdx = 1;
            for (Attendance att : attendances) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_FORMATTER) : "");
                row.createCell(1).setCellValue(att.getMart().getName());
                row.createCell(2).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_TIME_FORMATTER) : "");
                row.createCell(3).setCellValue(att.getCheckOutTime() != null ? att.getCheckOutTime().format(DATE_TIME_FORMATTER) : "");
                row.createCell(4).setCellValue(att.getStatus());
                row.createCell(5).setCellValue(att.getDistanceFromMart() != null ? att.getDistanceFromMart() : 0.0);
            }

            autoSizeColumns(sheet, columns.length);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createDailyAttendanceSheet(Workbook workbook, LocalDate date, Long agentId) {
        Sheet sheet = workbook.createSheet("Daily Attendance");
        
        Row headerRow = sheet.createRow(0);
        String[] columns = {"Agent ID", "Agent Name", "Mart Name", "Check-in Time", "Check-out Time", "Status", "Distance (m)"};
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(23, 59, 59);
        List<Attendance> attendances;
        if (agentId != null) {
            attendances = attendanceRepository.findByAgentIdAndCheckInTimeBetween(agentId, start, end);
        } else {
            attendances = attendanceRepository.findByCheckInTimeBetween(start, end);
        }

        int rowIdx = 1;
        for (Attendance att : attendances) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(att.getAgent().getAgentId());
            row.createCell(1).setCellValue(att.getAgent().getName());
            row.createCell(2).setCellValue(att.getMart().getName());
            row.createCell(3).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(4).setCellValue(att.getCheckOutTime() != null ? att.getCheckOutTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(5).setCellValue(att.getStatus());
            row.createCell(6).setCellValue(att.getDistanceFromMart() != null ? att.getDistanceFromMart() : 0.0);
        }

        autoSizeColumns(sheet, columns.length);
    }

    private void createMonthlyAttendanceSheet(Workbook workbook, Long agentId, Integer year, Integer month) {
        Sheet sheet = workbook.createSheet("Monthly Attendance");

        Row headerRow = sheet.createRow(0);
        String[] columns = {"Date", "Agent ID", "Agent Name", "Check-in Time", "Check-out Time", "Status", "Distance (m)"};

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int targetYear = year != null ? year : LocalDate.now().getYear();
        int targetMonth = month != null ? month : LocalDate.now().getMonthValue();
        
        List<Attendance> attendances;
        if (agentId != null) {
            attendances = attendanceRepository.getMonthlyAttendanceReportForAgent(agentId, targetYear, targetMonth);
        } else {
            LocalDateTime start = LocalDateTime.of(targetYear, targetMonth, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1).minusSeconds(1);
            attendances = attendanceRepository.findByCheckInTimeBetween(start, end);
        }

        int rowIdx = 1;
        for (Attendance att : attendances) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_FORMATTER) : "");
            row.createCell(1).setCellValue(att.getAgent().getAgentId());
            row.createCell(2).setCellValue(att.getAgent().getName());
            row.createCell(3).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(4).setCellValue(att.getCheckOutTime() != null ? att.getCheckOutTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(5).setCellValue(att.getStatus());
            row.createCell(6).setCellValue(att.getDistanceFromMart() != null ? att.getDistanceFromMart() : 0.0);
        }

        autoSizeColumns(sheet, columns.length);
    }

    private void createLateArrivalsSheet(Workbook workbook, LocalDate date, Long agentId) {
        Sheet sheet = workbook.createSheet("Late Arrivals");

        Row headerRow = sheet.createRow(0);
        String[] columns = {"Agent ID", "Agent Name", "Mart Name", "Check-in Time", "Distance (m)", "Status"};

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(23, 59, 59);
        List<Attendance> attendances = attendanceRepository.findByDateRangeAndStatus(start, end, "LATE");
        if (agentId != null) {
            attendances = attendances.stream()
                    .filter(att -> att.getAgent().getId().equals(agentId))
                    .toList();
        }

        int rowIdx = 1;
        for (Attendance att : attendances) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(att.getAgent().getAgentId());
            row.createCell(1).setCellValue(att.getAgent().getName());
            row.createCell(2).setCellValue(att.getMart().getName());
            row.createCell(3).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(4).setCellValue(att.getDistanceFromMart() != null ? att.getDistanceFromMart() : 0.0);
            row.createCell(5).setCellValue(att.getStatus());
        }

        autoSizeColumns(sheet, columns.length);
    }

    private void createSalesVisitsSheet(Workbook workbook, LocalDate date, Long agentId) {
        Sheet sheet = workbook.createSheet("Sales Visits");

        Row headerRow = sheet.createRow(0);
        String[] columns = {"Agent ID", "Agent Name", "Mart Name", "Check-in Time", "Check-out Time", "Status"};

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(23, 59, 59);
        List<Attendance> attendances = attendanceRepository.findByCheckInTimeBetween(start, end);
        if (agentId != null) {
            attendances = attendances.stream()
                    .filter(att -> att.getAgent().getId().equals(agentId))
                    .toList();
        }

        int rowIdx = 1;
        for (Attendance att : attendances) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(att.getAgent().getAgentId());
            row.createCell(1).setCellValue(att.getAgent().getName());
            row.createCell(2).setCellValue(att.getMart().getName());
            row.createCell(3).setCellValue(att.getCheckInTime() != null ? att.getCheckInTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(4).setCellValue(att.getCheckOutTime() != null ? att.getCheckOutTime().format(DATE_TIME_FORMATTER) : "");
            row.createCell(5).setCellValue(att.getStatus());
        }

        autoSizeColumns(sheet, columns.length);
    }

    private void createGeoFenceActionsSheet(Workbook workbook, Long agentId) {
        Sheet sheet = workbook.createSheet("Geo-fence Logs");

        Row headerRow = sheet.createRow(0);
        String[] columns = {"Timestamp", "Agent ID", "Agent Name", "Mart Name", "Action", "Latitude", "Longitude"};

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<GeoFenceLog> logs;
        if (agentId != null) {
            logs = geoFenceLogRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
        } else {
            logs = geoFenceLogRepository.findAll();
        }

        int rowIdx = 1;
        for (GeoFenceLog log : logs) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_TIME_FORMATTER) : "");
            row.createCell(1).setCellValue(log.getAgent().getAgentId());
            row.createCell(2).setCellValue(log.getAgent().getName());
            row.createCell(3).setCellValue(log.getMart().getName());
            row.createCell(4).setCellValue(log.getAction());
            row.createCell(5).setCellValue(log.getLatitude() != null ? log.getLatitude() : 0.0);
            row.createCell(6).setCellValue(log.getLongitude() != null ? log.getLongitude() : 0.0);
        }

        autoSizeColumns(sheet, columns.length);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
