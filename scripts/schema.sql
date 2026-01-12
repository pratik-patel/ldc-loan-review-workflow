-- Schema for LDC Loan Review Workflow
-- Replaces DynamoDB tables

CREATE TABLE IF NOT EXISTS workflow_state (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(255) NOT NULL,
    loan_number VARCHAR(255) NOT NULL,
    task_number VARCHAR(255),
    review_type VARCHAR(255) NOT NULL,
    current_workflow_stage VARCHAR(255),
    execution_status VARCHAR(255),
    loan_decision VARCHAR(255),
    loan_status VARCHAR(255),
    current_assigned_username VARCHAR(255),
    task_token VARCHAR(255),
    retry_count INTEGER,
    is_reclass_confirmation BOOLEAN,
    attributes JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_request_loan ON workflow_state(request_number, loan_number);
CREATE INDEX IF NOT EXISTS idx_loan_number ON workflow_state(loan_number);
CREATE INDEX IF NOT EXISTS idx_created_at ON workflow_state(created_at);

CREATE TABLE IF NOT EXISTS audit_trail (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(255) NOT NULL,
    loan_number VARCHAR(255) NOT NULL,
    task_number VARCHAR(255),
    event_type VARCHAR(255) NOT NULL,
    workflow_stage VARCHAR(255),
    status VARCHAR(255),
    request_payload TEXT,
    response_payload TEXT,
    error_message TEXT,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_request_loan ON audit_trail(request_number, loan_number);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_trail(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_trail(event_type);
