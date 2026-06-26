package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Mart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MartRepository extends JpaRepository<Mart, Long> {
    
    // ===== CUSTOM FINDER METHODS =====
    
    Optional<Mart> findByName(String name);
    List<Mart> findByNameContainingIgnoreCase(String name);
    List<Mart> findByAddressContainingIgnoreCase(String address);
    
    @Query("SELECT m FROM Mart m WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(m.latitude)) * " +
           "cos(radians(m.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(m.latitude)))) < :radius")
    List<Mart> findMartsWithinRadius(@Param("latitude") double latitude,
                                      @Param("longitude") double longitude,
                                      @Param("radius") double radius);
    
    List<Mart> findByCreatedAtAfter(LocalDateTime date);
    List<Mart> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    boolean existsByName(String name);
    
    // ===== JPQL QUERIES =====
    
    @Query("SELECT m.id, m.name, COUNT(at) as attendanceCount FROM Mart m LEFT JOIN m.attendances at GROUP BY m.id ORDER BY attendanceCount DESC")
    List<Object[]> findMartsWithAttendanceCount();
    
    @Query("SELECT DISTINCT m FROM Mart m JOIN m.attendances at WHERE at.checkInTime BETWEEN :start AND :end")
    List<Mart> findMartsWithActiveAgents(@Param("start") LocalDateTime todayStart,
                                          @Param("end") LocalDateTime todayEnd);
    
    @Query("SELECT m, COUNT(at) as count FROM Mart m LEFT JOIN m.attendances at GROUP BY m ORDER BY count DESC")
    List<Object[]> findTopMartsByAttendance(@Param("limit") int limit);
}
