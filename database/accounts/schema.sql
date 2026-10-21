-- Jamii Savings – accounts schema (PostgreSQL 16)
-- Loaded automatically by the Postgres container on first start
-- (mounted into /docker-entrypoint-initdb.d as 01-schema.sql).

CREATE TABLE IF NOT EXISTS customers (
                                         customer_id     VARCHAR(20)  NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    national_id     VARCHAR(20)  NOT NULL,
    phone_number    VARCHAR(20)  NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id),
    CONSTRAINT uq_customers_national_id UNIQUE (national_id)
    );

CREATE TABLE IF NOT EXISTS accounts (
                                        account_number  CHAR(10)      NOT NULL,
    customer_id     VARCHAR(20)   NOT NULL,
    account_type    VARCHAR(20)   NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    currency        CHAR(3)       NOT NULL DEFAULT 'KES',
    opened_on       DATE          NOT NULL,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_accounts PRIMARY KEY (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'DORMANT', 'CLOSED')),
    -- PostgreSQL uses ~ for POSIX regular expressions instead of REGEXP
    CONSTRAINT ck_accounts_number CHECK (account_number ~ '^[0-9]{10}$')
    );

CREATE INDEX ix_accounts_customer ON accounts (customer_id);

-- =====================================================================
-- PostgreSQL equivalent for MySQL's "ON UPDATE CURRENT_TIMESTAMP"
-- =====================================================================
CREATE OR REPLACE FUNCTION update_accounts_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION update_accounts_timestamp();