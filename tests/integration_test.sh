#!/bin/bash
#
# DRS Agent Backend Integration Test Runner
#
# This script runs all integration tests for the DRS Intelligent Operations Agent.
# Integration tests cover:
# - Diagnosis flow (DiagnosisFlowIntegrationTest)
# - Experience management (ExperienceManagementIntegrationTest)
# - Feedback and learning (FeedbackLearningIntegrationTest)
# - MCP tool execution (MCPToolIntegrationTest)
#

echo "=============================================="
echo "DRS Agent Backend - Integration Test Runner"
echo "=============================================="
echo ""

# Set working directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."

cd "$PROJECT_DIR" || exit 1

echo "Working directory: $(pwd)"
echo ""

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven (mvn) is not installed or not in PATH"
    echo "Please install Maven before running tests"
    exit 1
fi

echo "Maven version:"
mvn --version
echo ""

# Clean and compile
echo "Step 1: Cleaning and compiling..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed"
    exit 1
fi

echo "Compilation successful!"
echo ""

# Run integration tests
echo "Step 2: Running integration tests..."
echo ""

# Run all integration tests
mvn test -Dtest="*IntegrationTest" -DfailIfNoTests=false

TEST_EXIT_CODE=$?

echo ""
echo "=============================================="
echo "Test Execution Summary"
echo "=============================================="

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "All integration tests PASSED!"
    echo ""
    echo "Tested modules:"
    echo "  - Diagnosis Flow Integration Tests"
    echo "  - Experience Management Integration Tests"
    echo "  - Feedback Learning Integration Tests"
    echo "  - MCP Tool Integration Tests"
else
    echo "Some integration tests FAILED!"
    echo "Exit code: $TEST_EXIT_CODE"
    echo ""
    echo "Please check the test output above for details."
fi

echo ""
echo "Test completed at: $(date)"
echo ""

# Generate test report summary
REPORT_DIR="target/surefire-reports"
if [ -d "$REPORT_DIR" ]; then
    echo "Test reports available at: $REPORT_DIR"
    echo ""
    echo "Test files:"
    ls -la "$REPORT_DIR"/*.txt 2>/dev/null || echo "No .txt report files found"
fi

exit $TEST_EXIT_CODE