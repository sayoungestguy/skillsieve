-- Keycloak sign-in (R2, FR-13, ADR-009). A user may have a password, a Keycloak identity, or
-- both. The pair (issuer, subject) is the stable Keycloak id; email can change.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_user ADD COLUMN oidc_issuer TEXT;
ALTER TABLE app_user ADD COLUMN oidc_subject TEXT;
CREATE UNIQUE INDEX ux_app_user_oidc ON app_user(oidc_issuer, oidc_subject)
  WHERE oidc_subject IS NOT NULL;
