#!/bin/bash
set -e

# LDC Loan Review Workflow Orchestrator
# This script is the single entry point for running the end-to-end simulation.

echo "================================================================"
echo "   LDC Loan Review Workflow - Orchestrator"
echo "================================================================"
echo "1. Deploying Infrastructure..."
cd scripts
./01-deploy.sh dev

echo ""
echo "2. Running Simulation Test..."
./06-simulation-test.sh

echo ""
echo "================================================================"
echo "   Orchestration Complete"
echo "================================================================"
