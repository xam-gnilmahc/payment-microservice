CREATE TABLE IF NOT EXISTS user_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    gateway INT NOT NULL COMMENT '0=STRIPE, 1=AUTHORIZE_NET',
    gateway_name VARCHAR(255) NOT NULL,
    public_key VARCHAR(500) NOT NULL,
    secret_key VARCHAR(500) NOT NULL,
    webhook_secret VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_gateway (user_id, gateway)
);
