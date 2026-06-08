#!/usr/bin/env bash
# Wrapper: sources session-gate.sh in current shell to get trap cleanup
# Usage: source scripts/session-gate-wrapper.sh
# Should only be called from dev-env.sh

source "$(dirname "${BASH_SOURCE[0]}")/session-gate.sh"
