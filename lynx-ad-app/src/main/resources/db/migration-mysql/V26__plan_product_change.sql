CREATE TABLE IF NOT EXISTS delivery_product_change (
    tenant_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    root_plan_id VARCHAR(64) NOT NULL,
    source_plan_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    members_json LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(2000),
    lease_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id, request_key),
    INDEX idx_product_change_root (tenant_id, root_plan_id, status)
);
