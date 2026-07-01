#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# One-command build + run for beginners (Linux / macOS / Git Bash).
# Run from the repo root:  data/  must be writable here (auto-created on first run).
# Requires JDK 8+ and Maven 3.6.3+ on PATH.
# ---------------------------------------------------------------------------
set -euo pipefail
mvn -q clean package
java -jar "target/wechat-ilink-bot-1.0.0-SNAPSHOT.jar"
