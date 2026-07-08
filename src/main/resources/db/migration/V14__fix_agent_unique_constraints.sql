-- agent_id and email were globally unique; multi-tenancy requires them
-- unique only within a tenant (two different companies can each have their
-- own AGENT001, and per decision, can share the same email address).
-- Exact constraint names below were confirmed by querying
-- information_schema.table_constraints against production directly —
-- these are Hibernate's auto-generated hash names, not guessed.
ALTER TABLE agent DROP CONSTRAINT uk96097vsrm0u2kxtxbjcbhtlkq; -- old global UNIQUE(agent_id)
ALTER TABLE agent DROP CONSTRAINT ukpxogqxl64ae07j2lox1tgvrlx; -- old global UNIQUE(email)

CREATE UNIQUE INDEX ux_agent_tenant_agent_id ON agent (tenant_id, agent_id);
CREATE UNIQUE INDEX ux_agent_tenant_email ON agent (tenant_id, email);
