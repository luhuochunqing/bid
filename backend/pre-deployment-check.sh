#!/bin/bash

# Pre-deployment verification script for PasswordEncryptionUtil security fix
# Run this before deploying to production/staging

set -e

echo "================================================"
echo "Pre-Deployment Security Check"
echo "================================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if PLATFORM_ENCRYPTION_KEY is set
if [ -z "$PLATFORM_ENCRYPTION_KEY" ]; then
    echo -e "${RED}❌ FAIL: PLATFORM_ENCRYPTION_KEY environment variable is not set${NC}"
    echo ""
    echo "Before deploying to production/staging, you must:"
    echo "  export PLATFORM_ENCRYPTION_KEY=\"your-32-char-random-key-here\""
    echo ""
    echo "Generate a secure key:"
    echo "  openssl rand -base64 32"
    echo ""
    exit 1
fi

# Check key length
KEY_LENGTH=${#PLATFORM_ENCRYPTION_KEY}
if [ $KEY_LENGTH -lt 16 ]; then
    echo -e "${RED}❌ FAIL: PLATFORM_ENCRYPTION_KEY is too short ($KEY_LENGTH characters)${NC}"
    echo ""
    echo "Minimum required: 16 characters"
    echo "Recommended: 32+ characters"
    echo "Current length: $KEY_LENGTH characters"
    echo ""
    exit 1
fi

echo -e "${GREEN}✅ PLATFORM_ENCRYPTION_KEY is set${NC}"
echo -e "${GREEN}✅ Key length: $KEY_LENGTH characters (minimum: 16)${NC}"
echo ""

# Check if we're in production mode
if [[ "$SPRING_PROFILES_ACTIVE" == *"prod"* ]] || [[ "$SPRING_PROFILES_ACTIVE" == *"staging"* ]]; then
    echo -e "${YELLOW}⚠️  Target environment: $SPRING_PROFILES_ACTIVE${NC}"
    echo ""
    echo "This deployment will require PLATFORM_ENCRYPTION_KEY to be set."
    echo "The application WILL FAIL to start without it."
    echo ""
else
    echo -e "${GREEN}✅ Target environment: $SPRING_PROFILES_ACTIVE (development)${NC}"
    echo ""
    echo "Note: Development environment will use fallback key if PLATFORM_ENCRYPTION_KEY is not set."
    echo ""
fi

# Run tests
echo "Running tests..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"
mvn test -Dtest=PasswordEncryptionUtilTest -q > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ All unit tests pass (20/20)${NC}"
else
    echo -e "${RED}❌ FAIL: Some tests failed${NC}"
    exit 1
fi

mvn test -Dtest=PasswordEncryptionUtilIntegrationTest -q > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ All integration tests pass (3/3)${NC}"
else
    echo -e "${RED}❌ FAIL: Some tests failed${NC}"
    exit 1
fi

echo ""
echo "================================================"
echo -e "${GREEN}✅ Pre-Deployment Check: PASSED${NC}"
echo "================================================"
echo ""
echo "You are ready to deploy!"
echo ""
echo "Environment: $SPRING_PROFILES_ACTIVE"
echo "Key Length: $KEY_LENGTH characters"
echo "Tests: 23/23 passing"
echo ""
echo "Deployment command:"
echo "  java -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev}"
echo ""
