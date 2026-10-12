CREATE TABLE catalog_product_price (
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    advertiser_id VARCHAR(64) NOT NULL,
    aweme_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    price_json LONGTEXT NOT NULL,
    PRIMARY KEY (tenant_id,account_id,advertiser_id,aweme_id,product_id,channel_id)
);
ALTER TABLE product ADD COLUMN user_price_manual BOOLEAN NOT NULL DEFAULT FALSE;
-- Historical non-null calculation prices may be manual, including values equal to the old pool price.
-- Preserve them conservatively; new selections no longer copy platform prices into user_price.
UPDATE product SET user_price_manual=TRUE WHERE user_price IS NOT NULL;
