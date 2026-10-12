CREATE TABLE delivery_product_change (
    tenant_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    root_plan_id VARCHAR(64) NOT NULL,
    source_plan_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    members_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(2000),
    lease_until TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (tenant_id, request_key)
);
CREATE INDEX idx_product_change_root ON delivery_product_change (tenant_id, root_plan_id, status);
