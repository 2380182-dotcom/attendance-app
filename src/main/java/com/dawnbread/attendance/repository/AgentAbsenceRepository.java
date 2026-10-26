package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.dto.AbsenceDTO;
import com.dawnbread.attendance.entity.AgentAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * The 10 PM job runs outside any request, so the Hibernate tenant filter is
 * not active there — every method it uses takes tenantId explicitly.
 */
@Repository
public interface AgentAbsenceRepository extends JpaRepository<AgentAbsence, Long> {

    boolean existsByTenantIdAndAgentIdAndAbsenceDate(Long tenantId, Long agentId, LocalDate absenceDate);

    /** A later check-in on the same Pakistan date removes that day's absence. */
    @Modifying
    @Query("DELETE FROM AgentAbsence ab WHERE ab.tenantId = :tenantId AND ab.agentId = :agentId AND ab.absenceDate = :date")
    int deleteForAgentAndDate(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId, @Param("date") LocalDate date);

    /** A holiday added for a date after absences were saved removes them. */
    @Modifying
    @Query("DELETE FROM AgentAbsence ab WHERE ab.tenantId = :tenantId AND ab.absenceDate = :date")
    int deleteForDate(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM AgentAbsence ab WHERE ab.agentId = :agentId")
    int deleteForAgent(@Param("agentId") Long agentId);

    @Query("SELECT new com.dawnbread.attendance.dto.AbsenceDTO(ab.id, ab.agentId, a.name, a.department, ab.absenceDate) "
            + "FROM AgentAbsence ab JOIN Agent a ON a.id = ab.agentId "
            + "WHERE ab.absenceDate BETWEEN :start AND :end ORDER BY ab.absenceDate DESC, a.name")
    List<AbsenceDTO> findInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT new com.dawnbread.attendance.dto.AbsenceDTO(ab.id, ab.agentId, a.name, a.department, ab.absenceDate) "
            + "FROM AgentAbsence ab JOIN Agent a ON a.id = ab.agentId "
            + "WHERE ab.absenceDate BETWEEN :start AND :end AND ab.agentId = :agentId ORDER BY ab.absenceDate DESC")
    List<AbsenceDTO> findInRangeForAgent(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                         @Param("agentId") Long agentId);
}
