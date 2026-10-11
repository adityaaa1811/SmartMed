ALTER TABLE medications ADD COLUMN low_stock_threshold BIGINT NULL;
ALTER TABLE medications ADD COLUMN low_stock_alert_active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE medications ADD COLUMN low_stock_episode BIGINT NOT NULL DEFAULT 0;
