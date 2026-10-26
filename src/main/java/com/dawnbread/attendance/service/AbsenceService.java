package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.AbsenceDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.AgentAbsence;
import com.dawnbread.attendance.repository.AgentAbsenceRepository;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CompanyHolidayRepository;
import com.dawnbread.attendance.util.KarachiTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saved ABSENT records (V34) for mart agents only (role AGENT). Local and
 * LMT salesmen no longer check in, so they are never marked absent — their
 * activity is tracked in the Sales cockpit instead.
 *
 * An absence is saved for a date only when ALL of these hold:
 * the agent is an active AGENT, the date is one of their working days, the
 * date is not a company holiday (V35), they have no check-in on that
 * Pakistan date, and no absence is already saved (the V34 unique key is the
 * final guard against duplicates, e.g. two instances running the job).
 */
@Service
public class AbsenceService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceService.class);
    public static final String MART_AGENT_ROLE = "AGENT";

    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private AgentAbsenceRepository absenceRepository;
    @Autowired
    private CompanyHolidayRepository holidayRepository;
    @Autowired
    private Clock clock;

    /** Self-reference so the after-commit removal goes through the transactional proxy. */
    @Autowired
    @Lazy
    private AbsenceService self;

    /**
     * Called by the 10 PM job for today's Pakistan date. Runs outside any
     * request (no tenant filter), so it covers every company and passes
     * tenantId explicitly. Each absence is saved on its own, so one
     * duplicate never stops the others. Returns how many were saved.
     */
    public int recordAbsences(LocalDate date) {
        String dayCode = date.getDayOfWeek().name().substring(0, 3);
        LocalDateTime start = KarachiTime.startOfDayUtc(date);
        LocalDateTime end = KarachiTime.endOfDayUtc(date);
        int saved = 0;
        for (Agent agent : agentRepository.findAll()) {
            if (!MART_AGENT_ROLE.equals(agent.getRole()) || !Boolean.TRUE.equals(agent.getIsActive())) continue;
            List<String> workingDays = agent.getWorkingDays();
            if (workingDays != null && !workingDays.contains(dayCode)) continue;
            if (agent.getCreatedAt() != null && agent.getCreatedAt().toLocalDate().isAfter(date)) continue;
            Long tenantId = agent.getTenantId();
            if (holidayRepository.existsByTenantIdAndHolidayDate(tenantId, date)) continue;
            if (!attendanceRepository.findByAgentIdAndCheckInTimeBetween(agent.getId(), start, end).isEmpty()) continue;
            if (absenceRepository.existsByTenantIdAndAgentIdAndAbsenceDate(tenantId, agent.getId(), date)) continue;

            AgentAbsence absence = new AgentAbsence();
            absence.setTenantId(tenantId);
            absence.setAgentId(agent.getId());
            absence.setAbsenceDate(date);
            absence.setCreatedAt(LocalDateTime.now(clock));
            try {
                absenceRepository.saveAndFlush(absence);
                saved++;
            } catch (DataIntegrityViolationException duplicate) {
                // Another run saved it first — the unique key did its job.
                log.info("Absence for agent {} on {} already saved", agent.getId(), date);
            }
        }
        log.info("Saved {} absence records for {}", saved, date);
        return saved;
    }

    /**
     * A check-in on a date removes that date's absence (e.g. an agent who
     * checks in after the 10 PM job ran). Runs only AFTER the check-in has
     * committed, in its own transaction, and never throws — it can never
     * block or roll back a check-in.
     */
    public void removeAfterCheckIn(Long tenantId, Long agentId, LocalDate date) {
        Runnable remove = () -> {
            try {
                self.deleteForAgentAndDate(tenantId, agentId, date);
            } catch (RuntimeException e) {
                log.error("Could not remove absence for agent {} on {} after check-in", agentId, date, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    remove.run();
                }
            });
        } else {
            remove.run();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteForAgentAndDate(Long tenantId, Long agentId, LocalDate date) {
        return absenceRepository.deleteForAgentAndDate(tenantId, agentId, date);
    }

    /** HR screens (request scope, tenant-filtered). */
    @Transactional(readOnly = true)
    public List<AbsenceDTO> getAbsences(LocalDate start, LocalDate end, Long agentId) {
        return agentId == null
                ? absenceRepository.findInRange(start, end)
                : absenceRepository.findInRangeForAgent(start, end, agentId);
    }
}
