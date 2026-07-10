package com.dawnbread.attendance.security;

import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Tenant;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the actual claim this whole feature exists for: the raw column
 * value sitting in the database is not the plaintext embedding — not "the
 * converter's unit logic works," which FaceDataEncryptionConverterTest
 * already covers, but that a real Agent saved through the real JPA/Hibernate
 * path via the real test key really does end up encrypted in the real
 * (H2, for this suite) database.
 *
 * Also proves the dual-read migration path: a row written as legacy
 * plaintext (bypassing the converter entirely, via a native SQL UPDATE — the
 * same shape production rows are in today) still reads back correctly
 * through the entity.
 */
@SpringBootTest
class FaceDataEncryptionEntityIntegrationTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long tenantId() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Tenant t = new Tenant();
                    t.setCompanyCode("ENCTEST");
                    t.setName("Encryption Test Co");
                    t.setIsActive(true);
                    t.setCreatedAt(LocalDateTime.now());
                    t.setCreatedBy("TEST");
                    return tenantRepository.save(t);
                })
                .getId();
    }

    private Agent seedAgent(String agentId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId());
        agent.setAgentId(agentId);
        agent.setName("Seed " + agentId);
        agent.setEmail(agentId.toLowerCase() + "@example.com");
        agent.setRole("AGENT");
        agent.setCreatedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    @Test
    void savedFaceEmbeddingIsNotPlaintextInTheRawDatabaseColumn() {
        Agent agent = seedAgent("ENC_RAW_CHECK");
        String realEmbedding = "AAECAwQFBgcICQoLDA0ODw==-fake-but-realistic-shaped-base64-embedding";
        agent.setFaceEmbedding(realEmbedding);
        agentRepository.saveAndFlush(agent);
        entityManager.clear(); // force the next read to hit the DB, not the persistence-context cache

        // Bypasses JPA/the converter entirely — reads exactly what's on disk.
        String rawColumnValue = jdbcTemplate.queryForObject(
                "SELECT face_embedding FROM agent WHERE id = ?", String.class, agent.getId());

        assertFalse(rawColumnValue.equals(realEmbedding),
                "The raw database column must not equal the plaintext embedding");
        assertFalse(rawColumnValue.contains(realEmbedding),
                "The raw database column must not contain the plaintext embedding as a substring: " + rawColumnValue);
        assertTrue(rawColumnValue.startsWith("ENCv1:"), "The raw column must carry the encryption marker: " + rawColumnValue);

        // But reading it back through the entity is fully transparent.
        Agent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        assertEquals(realEmbedding, reloaded.getFaceEmbedding());
    }

    @Test
    @Transactional
    void aLegacyPlaintextRowWrittenOutsideTheConverterStillReadsCorrectly() {
        Agent agent = seedAgent("ENC_LEGACY_CHECK");
        String legacyPlaintextEmbedding = "legacy-base64-embedding-written-before-this-feature-existed";

        // Simulates the real production shape today: a value written by the
        // OLD code path, with no "ENCv" prefix — direct SQL, bypassing the
        // converter, exactly as if this row had never gone through it.
        jdbcTemplate.update("UPDATE agent SET face_embedding = ? WHERE id = ?", legacyPlaintextEmbedding, agent.getId());
        entityManager.clear();

        Agent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        assertEquals(legacyPlaintextEmbedding, reloaded.getFaceEmbedding(),
                "A legacy plaintext row (no ENCv prefix) must still be readable through the entity — this is what makes the migration non-breaking");
    }
}
