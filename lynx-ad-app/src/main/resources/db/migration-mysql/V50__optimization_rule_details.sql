CREATE TABLE optimization_rule_day (
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    day_key VARCHAR(10) NOT NULL,
    effective_seconds BIGINT NOT NULL DEFAULT 0,
    last_observed_at TIMESTAMP NULL,
    last_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, plan_id, day_key)
);
