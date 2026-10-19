CREATE TABLE IF NOT EXISTS catalog_product_price (
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    advertiser_id VARCHAR(64) NOT NULL,
    aweme_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    price_json LONGTEXT NOT NULL,
    PRIMARY KEY (tenant_id,account_id,advertiser_id,aweme_id,product_id,channel_id)
);
SET @lynx_new_manual = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'user_price_manual');
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE product ADD COLUMN user_price_manual BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'user_price_manual');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
-- Historical non-null calculation prices may be manual, including values equal to the old pool price.
-- Preserve them conservatively; new selections no longer copy platform prices into user_price.
UPDATE product SET user_price_manual=TRUE WHERE @lynx_new_manual AND user_price IS NOT NULL;
