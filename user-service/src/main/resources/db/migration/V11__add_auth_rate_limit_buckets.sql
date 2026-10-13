-- Fixed-window rate-limit counters for the public auth endpoints (issue #45). Kept in the
-- database so that every replica sees the same count; rows are short-lived and reaped by the
-- limiter itself once their window is over.
CREATE TABLE auth_rate_limit_buckets
(
    bucket_key    VARCHAR(255) PRIMARY KEY,
    window_start  TIMESTAMPTZ    NOT NULL,
    request_count INTEGER      NOT NULL
);

CREATE INDEX idx_auth_rate_limit_buckets_window ON auth_rate_limit_buckets (window_start);

COMMENT ON TABLE auth_rate_limit_buckets IS 'Distributed fixed-window counters: <scope>:<identity digest>:<window start>.';
