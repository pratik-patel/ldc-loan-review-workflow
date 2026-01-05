-- LDC Loan Review Workflow - PostgreSQL Schema
-- This script creates the necessary tables for the workflow state and audit trail

-- Create workflow_state table
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
    task_token TEXT,
    retry_count INTEGER,
    is_reclass_confirmation BOOLEAN,
    attributes JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(request_number, loan_number)
);

-- Create indexes for workflow_state table
CREATE INDEX IF NOT EXISTS idx_request_loan ON workflow_state(request_number, loan_number);
CREATE INDEX IF NOT EXISTS idx_loan_number ON workflow_state(loan_number);
CREATE INDEX IF NOT EXISTS idx_created_at ON workflow_state(created_at);
CREATE INDEX IF NOT EXISTS idx_execution_status ON workflow_state(execution_status);

-- Create audit_trail table
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
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for audit_trail table
CREATE INDEX IF NOT EXISTS idx_audit_request_loan ON audit_trail(request_number, loan_number);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_trail(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_trail(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_loan_number ON audit_trail(loan_number);

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update updated_at on workflow_state
DROP TRIGGER IF EXISTS update_workflow_state_updated_at ON workflow_state;
CREATE TRIGGER update_workflow_state_updated_at
    BEFORE UPDATE ON workflow_state
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust user as needed)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON workflow_state TO ldc_app_user;
-- GRANT SELECT, INSERT ON audit_trail TO ldc_app_user;
-- GRANT USAGE, SELECT ON SEQUENCE workflow_state_id_seq TO ldc_app_user;
-- GRANT USAGE, SELECT ON SEQUENCE audit_trail_id_seq TO ldc_app_user;

-- Verify tables were created
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('workflow_state', 'audit_trail');
