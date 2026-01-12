#!/bin/bash
set -e

echo "Starting End-to-End Deployment and Test..."

# 1. Deploy Infrastructure (and update SG/Outputs if rerunning)
echo "----------------------------------------"
echo "Step 1: Deploying Infrastructure..."
./scripts/01-deploy.sh dev

# 2. Initialize Database
echo "----------------------------------------"
echo "Step 2: Initializing Database..."
./scripts/init-db.sh

# 3. Run Tests
echo "----------------------------------------"
echo "Step 3: Running Tests..."
./scripts/02-test-deployment.sh

echo "----------------------------------------"
echo "End-to-End Workflow Complete!"
