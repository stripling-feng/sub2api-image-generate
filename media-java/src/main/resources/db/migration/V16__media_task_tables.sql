CREATE TABLE media_tasks (
    id text PRIMARY KEY,
    api_key text NOT NULL,
    task_type varchar(10) NOT NULL CHECK (task_type IN ('IMAGE', 'VIDEO')),
    request_id varchar(64) NOT NULL,
    model_config_id bigint NOT NULL REFERENCES media_ai_models(id),
    user_request jsonb NOT NULL DEFAULT '{}'::jsonb,
    task_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    system_response jsonb NOT NULL DEFAULT '{}'::jsonb,
    upstream_task_id text,
    upstream_operation text,
    upstream_request jsonb NOT NULL DEFAULT '{}'::jsonb,
    upstream_response jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    error_message text,
    completed_at timestamp,
    billing_status varchar(30) CHECK (billing_status IS NULL OR billing_status IN (
        'PENDING', 'NOT_REQUIRED', 'RESERVED', 'CHARGED', 'FAILED',
        'RELEASED', 'RELEASE_FAILED', 'CHARGE_FAILED'
    )),
    billing_amount numeric(20,10),
    progress integer NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    duration_ms integer,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX media_tasks_owner_history_idx
    ON media_tasks(api_key, task_type, created_at DESC, id DESC);
CREATE INDEX media_tasks_request_idx
    ON media_tasks(api_key, task_type, request_id, created_at);
CREATE INDEX media_tasks_upstream_idx ON media_tasks(upstream_task_id);
CREATE INDEX media_tasks_model_config_idx ON media_tasks(model_config_id);
CREATE INDEX media_tasks_poll_idx
    ON media_tasks(task_type, status, created_at)
    WHERE status = 'PENDING' AND upstream_task_id IS NOT NULL;
CREATE INDEX media_tasks_billing_idx ON media_tasks(billing_status, created_at);

CREATE TABLE media_task_results (
    id text PRIMARY KEY,
    task_id text NOT NULL REFERENCES media_tasks(id) ON DELETE CASCADE,
    address text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT media_task_results_task_order_unique UNIQUE (task_id, sort_order)
);
CREATE INDEX media_task_results_task_idx ON media_task_results(task_id, sort_order);

CREATE TABLE media_billing_records (
    id text PRIMARY KEY,
    task_id text NOT NULL UNIQUE REFERENCES media_tasks(id) ON DELETE CASCADE,
    api_key text NOT NULL,
    task_fee numeric(20,10) NOT NULL DEFAULT 0 CHECK (task_fee >= 0),
    deduction_status varchar(30) NOT NULL DEFAULT 'PENDING' CHECK (deduction_status IN (
        'PENDING', 'NOT_REQUIRED', 'RESERVED', 'CHARGED', 'FAILED',
        'RELEASED', 'RELEASE_FAILED', 'CHARGE_FAILED'
    )),
    api_key_id text,
    user_id text,
    account_id text,
    usage_log_id text,
    reserved_at timestamp,
    settled_at timestamp,
    error_message text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX media_billing_records_owner_idx ON media_billing_records(api_key, created_at DESC);
CREATE INDEX media_billing_records_status_idx ON media_billing_records(deduction_status, created_at);

COMMENT ON TABLE media_tasks IS 'Unified image and video generation tasks';
COMMENT ON TABLE media_task_results IS 'Unified generated media results';
COMMENT ON TABLE media_billing_records IS 'One billing record per media task';
COMMENT ON COLUMN media_tasks.id IS 'Media task ID';
COMMENT ON COLUMN media_tasks.api_key IS 'User supplied API key';
COMMENT ON COLUMN media_tasks.task_type IS 'IMAGE or VIDEO';
COMMENT ON COLUMN media_tasks.request_id IS 'Public request ID shared by fanout tasks';
COMMENT ON COLUMN media_tasks.model_config_id IS 'Media model configuration ID';
COMMENT ON COLUMN media_tasks.user_request IS 'Original user request JSON';
COMMENT ON COLUMN media_tasks.task_data IS 'Normalized task data JSON';
COMMENT ON COLUMN media_tasks.system_response IS 'Original system response JSON';
COMMENT ON COLUMN media_tasks.upstream_task_id IS 'Upstream task ID';
COMMENT ON COLUMN media_tasks.upstream_operation IS 'Upstream image operation';
COMMENT ON COLUMN media_tasks.upstream_request IS 'Original upstream request JSON';
COMMENT ON COLUMN media_tasks.upstream_response IS 'First upstream response JSON';
COMMENT ON COLUMN media_tasks.status IS 'Generation task status';
COMMENT ON COLUMN media_tasks.error_message IS 'Generation error message';
COMMENT ON COLUMN media_tasks.completed_at IS 'Task completion time';
COMMENT ON COLUMN media_tasks.billing_status IS 'Billing summary status';
COMMENT ON COLUMN media_tasks.billing_amount IS 'Billing summary amount';
COMMENT ON COLUMN media_tasks.progress IS 'Generation progress from 0 to 100';
COMMENT ON COLUMN media_tasks.duration_ms IS 'Total task duration in milliseconds';
COMMENT ON COLUMN media_tasks.created_at IS 'Creation time';
COMMENT ON COLUMN media_tasks.updated_at IS 'Last update time';
COMMENT ON COLUMN media_task_results.id IS 'Media result ID';
COMMENT ON COLUMN media_task_results.task_id IS 'Media task ID';
COMMENT ON COLUMN media_task_results.address IS 'Public result address';
COMMENT ON COLUMN media_task_results.metadata IS 'Result MIME, dimensions, size, and storage metadata';
COMMENT ON COLUMN media_task_results.sort_order IS 'Result order within a task';
COMMENT ON COLUMN media_task_results.created_at IS 'Creation time';
COMMENT ON COLUMN media_billing_records.id IS 'Billing record ID';
COMMENT ON COLUMN media_billing_records.task_id IS 'Media task ID';
COMMENT ON COLUMN media_billing_records.api_key IS 'User supplied API key';
COMMENT ON COLUMN media_billing_records.task_fee IS 'Task fee';
COMMENT ON COLUMN media_billing_records.deduction_status IS 'Deduction status';
COMMENT ON COLUMN media_billing_records.api_key_id IS 'External sub2api API key ID';
COMMENT ON COLUMN media_billing_records.user_id IS 'External sub2api user ID';
COMMENT ON COLUMN media_billing_records.account_id IS 'External sub2api account ID';
COMMENT ON COLUMN media_billing_records.usage_log_id IS 'External sub2api usage log ID';
COMMENT ON COLUMN media_billing_records.reserved_at IS 'Balance reservation time';
COMMENT ON COLUMN media_billing_records.settled_at IS 'Charge or release completion time';
COMMENT ON COLUMN media_billing_records.error_message IS 'Billing error message';
COMMENT ON COLUMN media_billing_records.created_at IS 'Creation time';
COMMENT ON COLUMN media_billing_records.updated_at IS 'Last update time';
