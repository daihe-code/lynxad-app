-- Keep files and plan bindings intact; only remove deleted entries from library queries.
CREATE TABLE creative_library_deletion (
    tenant_id VARCHAR(64) NOT NULL,
    library_id VARCHAR(320) NOT NULL,
    deleted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (tenant_id, library_id)
);
