CREATE TABLE auth_transactions (
  transaction_hash VARCHAR(255) NOT NULL,
  org_id           INT          NOT NULL,
  api_key          VARCHAR(255) NOT NULL,
  created_at       TIMESTAMP    NOT NULL,
  is_checked       BOOLEAN      NOT NULL,
  deleted_at       TIMESTAMP,
  PRIMARY KEY (transaction_hash)
);

CREATE TABLE auth_phone_transactions (
  phone_number     BIGINT       NOT NULL,
  PRIMARY KEY (transaction_hash)
) inherits(auth_transactions);

CREATE TABLE gate_auth_codes (
  transaction_hash VARCHAR (255) NOT NULL ,
  code_hash        VARCHAR (255) NOT NULL ,
  attempts         INT NOT NULL,
  is_deleted       BOOLEAN NOT NULL ,
  primary key(transaction_hash)
);

CREATE TABLE auth_tokens (
  token_id   VARCHAR(255) NOT NULL,
  token_key  VARCHAR(255) NOT NULL,
  deleted_at TIMESTAMP,
  PRIMARY KEY (token_id)
);

CREATE TABLE auth_sessions (
  org_id       INT          NOT NULL,
  api_key      VARCHAR(255) NOT NULL,
  user_id      INT          NOT NULL,
  token_id     VARCHAR(255) NOT NULL,
  created_at   TIMESTAMP    NOT NULL,
  deleted_at   TIMESTAMP,
  PRIMARY KEY (org_id, user_id, token_id)
);

CREATE INDEX idx_auth_sessions_token_id
  ON auth_sessions (token_id);