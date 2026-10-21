CREATE TABLE tenant_legal_profile (
 tenant_id VARCHAR(36) PRIMARY KEY REFERENCES sys_tenant(id),
 company_name VARCHAR(200) NOT NULL,
 credit_code VARCHAR(18) NOT NULL,
 verification_status VARCHAR(24) NOT NULL,
 review_reason VARCHAR(512),
 revision INTEGER NOT NULL,
 submitted_at TIMESTAMPTZ NOT NULL,
 reviewed_at TIMESTAMPTZ,
 reviewed_by VARCHAR(36)
);
CREATE INDEX idx_legal_review_queue ON tenant_legal_profile(verification_status, submitted_at);
CREATE TABLE tenant_invoice_profile (
 tenant_id VARCHAR(36) PRIMARY KEY REFERENCES sys_tenant(id),
 title VARCHAR(200) NOT NULL,
 invoice_type VARCHAR(32) NOT NULL,
 tax_no VARCHAR(18) NOT NULL,
 bank VARCHAR(200) NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
UPDATE billing_plan SET ad_account_limit=3, benefits_json='["3 个广告账户","全部智能投放","自动优化策略","投放效果深度分析","素材 A/B 测试","专属客服支持"]' WHERE code='PROFESSIONAL';
UPDATE billing_plan SET ad_account_limit=10, benefits_json='["10 个广告账户","全部专业权益","多账户统一管理","优先排期定制","专属资深经理","API 高级接口"]' WHERE code='FLAGSHIP';
