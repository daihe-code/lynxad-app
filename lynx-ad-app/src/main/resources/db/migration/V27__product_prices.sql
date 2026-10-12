CREATE TABLE catalog_product_price (
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    advertiser_id VARCHAR(64) NOT NULL,
    aweme_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    price_json TEXT NOT NULL,
    PRIMARY KEY (tenant_id,account_id,advertiser_id,aweme_id,product_id,channel_id)
);
ALTER TABLE product ADD COLUMN user_price_manual BOOLEAN NOT NULL DEFAULT FALSE;
-- Preserve every historical calculation value, even when it equals the old platform price.
UPDATE product SET user_price_manual=TRUE WHERE user_price IS NOT NULL;
