-- A one-way fingerprint prevents polling from retrying the same rejected token.
ALTER TABLE creative_asset_platform ADD COLUMN credential_fingerprint VARCHAR(64) NULL;
