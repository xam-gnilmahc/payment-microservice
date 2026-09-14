CREATE TABLE IF NOT EXISTS user_payment_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    gateway INT NOT NULL,
    gateway_name VARCHAR(255) NOT NULL,
    public_key VARCHAR(500) NOT NULL,
    secret_key VARCHAR(500) NOT NULL,
    webhook_secret VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_merchant_gateway UNIQUE (user_id, gateway)
);
