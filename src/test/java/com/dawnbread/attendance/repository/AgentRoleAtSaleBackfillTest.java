package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V30's backfill UPDATE runs once, against whatever existed at migration
 * time — a fresh test schema has no pre-existing rows when Flyway applies
 * it, so this test proves the SQL itself is correct by running the exact
 * same statement directly against a row built to look like "old data"
 * (agent_role_at_sale left NULL, exactly as every row looked before V30
 * added the column and ran its backfill).
 */
@SpringBootTest
class AgentRoleAtSaleBackfillTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("BACKFILLTEST");
                    t.setName("Backfill Test Co");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    @Test
    void backfillSqlSetsAgentRoleAtSaleFromTheAgentsCurrentRoleForPreExistingRows() {
        Long tid = tenantId();

        Agent agent = new Agent();
        agent.setTenantId(tid);
        agent.setAgentId("BACKFILL_AGENT_" + System.nanoTime());
        agent.setName("Backfill Test Agent");
        agent.setEmail("backfill-" + System.nanoTime() + "@example.com");
        agent.setRole("SALESMAN_LMT");
        agent.setCreatedAt(LocalDateTime.now());
        agent = agentRepository.save(agent);

        // Simulates a row that existed BEFORE V30 — agentRoleAtSale never
        // set, exactly like every real pre-existing sales_records row.
        SalesRecord record = new SalesRecord();
        record.setTenantId(tid);
        record.setAgent(agent);
        record.setTotalAmount(100.0);
        record.setSaleDate(LocalDate.now());
        record.setSaleTime(LocalTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setStatus("PENDING");
        record = salesRecordRepository.save(record);

        assertNull(record.getAgentRoleAtSale(), "Precondition: a record saved without this field set must start NULL, matching real pre-V30 data");

        // The exact statement V30 runs (see the migration file) — proves
        // the backfill logic itself is correct, independent of Flyway's
        // own execution timing in a fresh test schema.
        jdbcTemplate.update(
                "UPDATE sales_records SET agent_role_at_sale = " +
                        "(SELECT a.role FROM agent a WHERE a.id = sales_records.agent_id) " +
                        "WHERE agent_role_at_sale IS NULL AND id = ?",
                record.getId());

        String backfilledRole = jdbcTemplate.queryForObject(
                "SELECT agent_role_at_sale FROM sales_records WHERE id = ?", String.class, record.getId());
        assertEquals("SALESMAN_LMT", backfilledRole,
                "The backfill must set agent_role_at_sale to the agent's CURRENT role for a pre-existing row");
    }
}
