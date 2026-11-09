#!/bin/bash
# Auto-generate androidApp/keystore.properties from .env file
# This script reads .env and creates keystore.properties with the correct paths and passwords

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Android Keystore Properties Generator ===${NC}"

# Check if .env exists
if [ ! -f ".env" ]; then
    echo -e "${RED}Error: .env file not found!${NC}"
    echo "Please create .env from .env.example first"
    exit 1
fi

# Source .env file
source .env

# Validate required variables
if [ -z "$USER_SIGNING_DIR" ]; then
    echo -e "${RED}Error: USER_SIGNING_DIR not set in .env${NC}"
    exit 1
fi

if [ -z "$ANDROID_KEYSTORE_PASSWORD" ] || [ "$ANDROID_KEYSTORE_PASSWORD" = "your-keystore-password" ]; then
    echo -e "${RED}Error: ANDROID_KEYSTORE_PASSWORD not set in .env${NC}"
    echo "Please update .env with your actual keystore password"
    exit 1
fi

if [ -z "$ANDROID_KEY_PASSWORD" ] || [ "$ANDROID_KEY_PASSWORD" = "your-key-password" ]; then
    echo -e "${RED}Error: ANDROID_KEY_PASSWORD not set in .env${NC}"
    echo "Please update .env with your actual key password"
    exit 1
fi

if [ -z "$ANDROID_KEY_ALIAS" ]; then
    echo -e "${RED}Error: ANDROID_KEY_ALIAS not set in .env${NC}"
    exit 1
fi

# Create android/app directory if it doesn't exist (it should)
mkdir -p android/app

# Generate keystore.properties
KEYSTORE_PATH="${USER_SIGNING_DIR}/android/upload-keystore.jks"

echo -e "${GREEN}Generating android/app/keystore.properties...${NC}"
echo "storeFile=${KEYSTORE_PATH}" > android/app/keystore.properties
echo "storePassword=${ANDROID_KEYSTORE_PASSWORD}" >> android/app/keystore.properties
echo "keyAlias=${ANDROID_KEY_ALIAS}" >> android/app/keystore.properties
echo "keyPassword=${ANDROID_KEY_PASSWORD}" >> android/app/keystore.properties

echo -e "${GREEN}✓ Generated android/app/keystore.properties${NC}"
echo ""
echo "Keystore location: ${KEYSTORE_PATH}"
echo "Key alias: ${ANDROID_KEY_ALIAS}"
echo ""
echo -e "${YELLOW}⚠️  IMPORTANT: androidApp/keystore.properties contains sensitive passwords!${NC}"
echo "Do NOT commit this file to version control."
echo "Make sure it's in your .gitignore file."
echo ""
echo -e "${GREEN}You can now build release Android apps!${NC}"
