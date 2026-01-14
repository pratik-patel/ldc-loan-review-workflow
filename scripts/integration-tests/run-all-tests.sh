#!/bin/bash

# Master Integration Test Runner
# Executes all integration test scenarios and generates report

set -e

REPORT_DIR="./test-results"
REPORT_FILE="$REPORT_DIR/integration-test-report-$(date +%Y%m%d-%H%M%S).md"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Create report directory
mkdir -p "$REPORT_DIR"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Integration Test Suite Execution${NC}"
echo -e "${BLUE}=========================================${NC}"
echo "Start Time: $(date)"
echo "Report: $REPORT_FILE"
echo ""

# Initialize report
cat > "$REPORT_FILE" <<EOF
# Integration Test Report

**Execution Date**: $(date)  
**Test Suite**: LDC Loan Review Workflow End-to-End Integration Tests

## Executive Summary

EOF

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# Function to run a test and capture results
run_test() {
  local test_script=$1
  local test_name=$2
  
  echo -e "\n${YELLOW}Running: $test_name${NC}"
  echo "Script: $test_script"
  
  ((TOTAL_TESTS++))
  
  if [ ! -f "$test_script" ]; then
    echo -e "${RED}✗ SKIPPED: Script not found${NC}"
    ((SKIPPED_TESTS++))
    echo "- **$test_name**: ❌ SKIPPED (script not found)" >> "$REPORT_FILE"
    return
  fi
  
  # Run test and capture output
  if bash "$test_script" > "$REPORT_DIR/$(basename $test_script .sh).log" 2>&1; then
    echo -e "${GREEN}✓ PASSED${NC}"
    ((PASSED_TESTS++))
    echo "- **$test_name**: ✅ PASSED" >> "$REPORT_FILE"
  else
    echo -e "${RED}✗ FAILED${NC}"
    ((FAILED_TESTS++))
    echo "- **$test_name**: ❌ FAILED" >> "$REPORT_FILE"
  fi
}

# Run all tests
echo -e "\n${BLUE}=== Core Happy Path Tests ===${NC}"
run_test "test-T01-happy-path-all-approved.sh" "T01: Happy Path - All Approved"
run_test "test-T02-all-rejected.sh" "T02: All Rejected"
run_test "test-T03-partially-approved.sh" "T03: Partially Approved"
run_test "test-T04-repurchase-decision.sh" "T04: Repurchase Decision"

echo -e "\n${BLUE}=== Complex Workflow Tests ===${NC}"
run_test "test-T05-reclass-with-confirmation.sh" "T05: Reclass with Confirmation"
run_test "test-T06-pending-attributes-loop.sh" "T06: Pending Attributes Loop"

echo -e "\n${BLUE}=== Business Logic Tests ===${NC}"
run_test "test-T07-decision-priority.sh" "T07: Loan Decision Priority"

echo -e "\n${BLUE}=== Validation Tests ===${NC}"
run_test "test-T08-duplicate-prevention.sh" "T08: Duplicate Execution Prevention"
run_test "test-T09-invalid-input-validation.sh" "T09: Invalid Input Validation"
run_test "test-T10-invalid-attribute.sh" "T10: Invalid Attribute Decision"

echo -e "\n${BLUE}=== Schema & Edge Case Tests ===${NC}"
run_test "test-T11-schema-compliance.sh" "T11: Schema Compliance"
run_test "test-T12-empty-attributes.sh" "T12: Empty Attributes"
run_test "test-T13-single-attribute.sh" "T13: Single Attribute Scenarios"

echo -e "\n${BLUE}=== Database & Audit Tests ===${NC}"
run_test "test-T14-database-persistence.sh" "T14: Database State Persistence"
run_test "test-T15-audit-trail.sh" "T15: Audit Trail Completeness"

# Generate report summary
cat >> "$REPORT_FILE" <<EOF

## Test Results Summary

- **Total Tests**: $TOTAL_TESTS
- **Passed**: $PASSED_TESTS ($(( TOTAL_TESTS > 0 ? PASSED_TESTS * 100 / TOTAL_TESTS : 0 ))%)
- **Failed**: $FAILED_TESTS
- **Skipped**: $SKIPPED_TESTS

## Pass Rate

\`\`\`
$(( TOTAL_TESTS > 0 ? PASSED_TESTS * 100 / TOTAL_TESTS : 0 ))% ($PASSED_TESTS/$TOTAL_TESTS tests passed)
\`\`\`

## Detailed Logs

Individual test logs available in:
\`\`\`
$REPORT_DIR/*.log
\`\`\`

EOF

# Print summary
echo -e "\n${BLUE}=========================================${NC}"
echo -e "${BLUE}Test Execution Complete${NC}"
echo -e "${BLUE}=========================================${NC}"
echo "End Time: $(date)"
echo ""
echo "Summary:"
echo "  Total:   $TOTAL_TESTS"
echo -e "  ${GREEN}Passed:  $PASSED_TESTS${NC}"
echo -e "  ${RED}Failed:  $FAILED_TESTS${NC}"
echo -e "  ${YELLOW}Skipped: $SKIPPED_TESTS${NC}"
echo ""
echo "Pass Rate: $(( TOTAL_TESTS > 0 ? PASSED_TESTS * 100 / TOTAL_TESTS : 0 ))%"
echo ""
echo "Report saved to: $REPORT_FILE"
echo -e "${BLUE}=========================================${NC}"

# Exit with failure if any tests failed
if [ $FAILED_TESTS -gt 0 ]; then
  exit 1
fi
