-- OIDC subject identifier for SSO-linked accounts
ALTER TABLE users ADD COLUMN oidc_sub VARCHAR(255);
CREATE INDEX idx_users_oidc_sub ON users(oidc_sub);
