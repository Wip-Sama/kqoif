#!/usr/bin/env bash
set -euo pipefail

echo "========================================"
echo " Running full kqoif test suite"
echo "========================================"

./gradlew check

echo "========================================"
echo " All tests passed successfully!"
echo "========================================"
