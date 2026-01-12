#!/bin/bash
set -e

# Go to project root
cd "$(dirname "$0")/.."

echo "Fetching database endpoint..."
if ! command -v terraform &> /dev/null; then
    echo "Error: terraform not found"
    exit 1
fi

ENDPOINT=$(terraform -chdir=terraform output -raw database_endpoint)
if [ -z "$ENDPOINT" ]; then
    echo "Error: Could not get database_endpoint from terraform output."
    echo "Make sure deployment is complete and outputs.tf is updated."
    exit 1
fi

HOST=$(echo $ENDPOINT | cut -d: -f1)
PORT=$(echo $ENDPOINT | cut -d: -f2)

echo "Initializing database at $HOST:$PORT..."
# Using postgres:15-alpine image to run psql
docker run --rm -v $(pwd)/scripts:/scripts postgres:15-alpine \
  psql "postgresql://postgres:postgres_password_123@$HOST:$PORT/ldc_loan_review" -f /scripts/schema.sql

echo "Database initialized successfully."
