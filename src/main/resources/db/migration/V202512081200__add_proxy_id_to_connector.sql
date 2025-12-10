-- Add proxy_id column to connector table for message queue routing
-- When proxy_id is set, the connector communicates via message queue proxy
-- When proxy_id is NULL, the connector uses direct REST communication

ALTER TABLE connector ADD COLUMN proxy_id VARCHAR(255);

COMMENT ON COLUMN connector.proxy_id IS 'Proxy instance ID for message queue routing (NULL = use REST)';
