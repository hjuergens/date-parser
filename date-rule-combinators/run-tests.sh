#!/bin/bash
# Shell script to run tests for date-rule-combinators module
# This is a convenience wrapper for the project-level run-tests.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Execute the project-level script with this module
"$PROJECT_DIR/run-tests.sh" -m date-rule-combinators "$@"
