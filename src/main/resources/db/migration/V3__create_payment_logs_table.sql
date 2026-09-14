CREATE TABLE IF NOT EXISTS payment_logs (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    gateway VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(255),
    error_message TEXT,
    charge_id VARCHAR(255),
    receipt_url VARCHAR(500),
    card_last4 VARCHAR(4),
    card_brand VARCHAR(50),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
