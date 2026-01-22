CREATE TABLE IF NOT EXISTS workflow_state (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(255) NOT NULL,
    loan_number VARCHAR(255) NOT NULL,

    review_type VARCHAR(255) NOT NULL,
    current_workflow_stage VARCHAR(255),
    execution_status VARCHAR(255),
    loan_decision VARCHAR(255),
    loan_status VARCHAR(255),
    current_assigned_username VARCHAR(255),
    task_token TEXT,
    retry_count INTEGER,
    is_reclass_confirmation BOOLEAN,
    attributes JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_request_loan ON workflow_state(request_number, loan_number);
CREATE INDEX IF NOT EXISTS idx_loan_number ON workflow_state(loan_number);
CREATE INDEX IF NOT EXISTS idx_created_at ON workflow_state(created_at);
