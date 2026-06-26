package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    // ===== CUSTOM FINDER METHODS =====
    
    List<Attendance> findByAgentId(Long agentId);
    List<Attendance> findByMartId(Long martId);
    List<Attendance> findByStatus(String status);
    List<Attendance> findByCheckInTimeBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);
    List<Attendance> findByCheckOutTimeBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);
    List<Attendance> findByAgentIdAndCheckInTimeBetween(Long agentId, LocalDateTime startDateTime, LocalDateTime endDateTime);
    List<Attendance> findByMartIdAndCheckInTimeBetween(Long martId, LocalDateTime startDateTime, LocalDateTime endDateTime);
    List<Attendance> findByAgentIdAndStatus(Long agentId, String status);
    
    @Query("SELECT a FROM Attendance a WHERE a.checkOutTime IS NULL")
    List<Attendance> findOpenAttendance();
    
    @Query("SELECT a FROM Attendance a WHERE a.agent.id = :agentId AND a.checkOutTime IS NULL")
    Optional<Attendance> findOpenAttendanceByAgentId(@Param("agentId") Long agentId);
    
    // ===== JPQL QUERIES =====
    
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.agent.id = :agentId")
    long countByAgentId(@Param("agentId") Long agentId);
    
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.mart.id = :martId")
    long countByMartId(@Param("martId") Long martId);
    
    @Query("SELECT " +
           "COUNT(DISTINCT DATE(a.checkInTime)) as totalDays, " +
           "SUM(CASE WHEN a.status = 'IN' THEN 1 ELSE 0 END) as presentDays, " +
           "SUM(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) as lateDays " +
           "FROM Attendance a WHERE a.agent.id = :agentId AND a.checkInTime BETWEEN :startDate AND :endDate")
    Object[] getAttendanceSummaryForAgent(@Param("agentId") Long agentId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT a FROM Attendance a WHERE DATE(a.checkInTime) = DATE(:date)")
    List<Attendance> getDailyAttendanceReport(@Param("date") LocalDateTime date);
    
    @Query("SELECT a FROM Attendance a WHERE a.agent.id = :agentId " +
           "AND YEAR(a.checkInTime) = :year AND MONTH(a.checkInTime) = :month")
    List<Attendance> getMonthlyAttendanceReportForAgent(@Param("agentId") Long agentId,
                                                         @Param("year") int year,
                                                         @Param("month") int month);
    
    @Query("SELECT a FROM Attendance a WHERE a.checkInTime BETWEEN :startDate AND :endDate AND a.status = :status")
    List<Attendance> findByDateRangeAndStatus(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate,
                                               @Param("status") String status);
    
    @Query("SELECT a.agent.id, a.agent.name, a.agent.email, a.checkInTime, a.checkOutTime, a.status, a.distanceFromMart " +
           "FROM Attendance a WHERE a.checkInTime BETWEEN :startOfDay AND :endOfDay " +
           "ORDER BY a.checkInTime DESC")
    List<Object[]> getTodayAttendanceReport(@Param("startOfDay") LocalDateTime startOfDay,
                                            @Param("endOfDay") LocalDateTime endOfDay);
    
    @Query("SELECT " +
           "COUNT(a) as total, " +
           "SUM(CASE WHEN a.checkInTime IS NOT NULL THEN 1 ELSE 0 END) as checkedIn, " +
           "SUM(CASE WHEN a.checkOutTime IS NOT NULL THEN 1 ELSE 0 END) as checkedOut, " +
           "SUM(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) as late " +
           "FROM Attendance a WHERE a.checkInTime BETWEEN :startDate AND :endDate")
    Object[] getAttendanceStatistics(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
