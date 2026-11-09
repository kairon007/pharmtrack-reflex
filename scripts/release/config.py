#!/usr/bin/env python3
"""
Configuration management for KMP release scripts.
Loads environment variables and provides configuration access.
"""

import os
import sys
from pathlib import Path
from typing import Optional


class ReleaseConfig:
    """Configuration for KMP release process."""

    def __init__(self):
        """Initialize configuration from environment variables."""
        # Determine root directory
        self.project_dir = Path(__file__).parent.parent.parent
        
        # Load .env file if it exists
        self._load_env_file()

        # App configuration
        self.app_name = self._get_env("APP_NAME", "KMPApp")

        # Package/bundle IDs
        self.android_package_name = self._get_env("ANDROID_PACKAGE_NAME", "com.example.kotlin_multiplatform")
        self.apple_bundle_id = self._get_env("APPLE_BUNDLE_ID", "com.example.kotlin_multiplatform")

        # Version configuration
        self.ios_app_id = self._get_env("APPLE_APP_ID", None)

        # Apple Team ID (machine-wide, shared by all apps)
        self.apple_team_id = self._get_env("APPLE_TEAM_ID", None)

        # Signing configuration
        self.user_signing_dir = self._get_env("USER_SIGNING_DIR", str(Path.home() / "signing"))

        # Android signing files
        self.android_keystore_path = self._get_env("ANDROID_KEYSTORE_PATH", None)
        self.android_keystore_password = self._get_env("ANDROID_KEYSTORE_PASSWORD", None)
        self.android_key_alias = self._get_env("ANDROID_KEY_ALIAS", "upload")
        self.android_key_password = self._get_env("ANDROID_KEY_PASSWORD", None)
        self.android_keystore_properties = self.project_dir / "android" / "app" / "keystore.properties"

        # iOS signing files (machine-wide)
        self.ios_certificate_path = self._get_env("IOS_CERTIFICATE_PATH", None)
        self.ios_certificate_password = self._get_env("IOS_CERTIFICATE_PASSWORD", None)
        # Note: Provisioning profiles are auto-managed by Xcode

        # Build configuration
        self.build_number_offset = int(self._get_env("BUILD_NUMBER_OFFSET", "0"))

        # File paths — the versions live in the two build files, one per platform.
        self.android_gradle_file = self.project_dir / "android" / "app" / "build.gradle.kts"
        self.android_gradle_fallback = self.project_dir / "android" / "app" / "build.gradle"
        self.ios_project_yml = self.project_dir / "ios" / "project.yml"
        self.ios_version_file = self.ios_project_yml
        self.android_version_file = self.android_gradle_file

        # Android signing properties file
        self.android_keystore_properties = self.project_dir / "android" / "app" / "keystore.properties"

        # Release output directory
        self.releases_dir = self.project_dir / "releases"

        # Google Play credentials
        self.google_play_service_account_key = self._get_env("GOOGLE_PLAY_SERVICE_ACCOUNT_KEY", None)

        # App Store Connect credentials
        self.app_store_connect_key_path = self._get_env("APPSTORE_CONNECT_KEY_PATH", None)
        self.app_store_connect_key_id = self._get_env("APPSTORE_CONNECT_KEY_ID", None)
        self.app_store_connect_issuer_id = self._get_env("APPSTORE_CONNECT_ISSUER_ID", None)

        # Validate configuration
        self._validate()

    def _load_env_file(self):
        """Load environment variables from .env file in project directory."""
        env_file = self.project_dir / ".env"
        if env_file.exists():
            print(f"📋 Loading environment from {env_file}")
            with open(env_file) as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#"):
                        if "=" in line:
                            key, value = line.split("=", 1)
                            key = key.strip()
                            value = value.strip().strip('"').strip("'")
                            if key and value:
                                # Expand variables like ${VAR} and ~ in values
                                value = os.path.expandvars(value)
                                os.environ[key] = value

    def _get_env(self, key: str, default: Optional[str] = None) -> str:
        """Get environment variable with optional default."""
        value = os.environ.get(key, default)
        if value is None:
            return default if default is not None else ""
        return value

    def _validate(self):
        """Validate that required configuration is present."""
        errors = []

        # Check Android files
        if not self.android_gradle_file.exists() and not self.android_gradle_fallback.exists():
            errors.append(f"Android build.gradle.kts not found at {self.android_gradle_file}")

        # Check iOS files
        if not self.ios_project_yml.exists():
            errors.append(f"iOS project.yml not found at {self.ios_project_yml}")

        if errors:
            print("⚠️  Configuration warnings:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            print()

    def validate_android_signing(self):
        """Validate Android signing configuration."""
        errors = []
        warnings = []

        # Check if keystore path is set
        if not self.android_keystore_path:
            # Try to find it in the signing directory
            default_keystore = Path(self.user_signing_dir) / "android" / "upload-keystore.jks"
            if default_keystore.exists():
                self.android_keystore_path = str(default_keystore)
                print(f"✅ Found Android keystore at: {self.android_keystore_path}")
            else:
                warnings.append("Android keystore path not set - release builds may fail")

        # Validate keystore exists
        if self.android_keystore_path:
            keystore_path = Path(self.android_keystore_path)
            if not keystore_path.exists():
                errors.append(f"Android keystore not found at: {self.android_keystore_path}")

        # Check passwords
        if not self.android_keystore_password:
            warnings.append("Android keystore password not set")

        if not self.android_key_password:
            warnings.append("Android key password not set")

        # Check keystore.properties
        if self.android_keystore_properties.exists():
            print(f"✅ Found keystore.properties at: {self.android_keystore_properties}")
        else:
            warnings.append(f"keystore.properties not found at {self.android_keystore_properties} - you may need to create it")

        if warnings:
            print("⚠️  Android signing warnings:", file=sys.stderr)
            for warning in warnings:
                print(f"  - {warning}", file=sys.stderr)
            print()

        if errors:
            print("❌ Android signing errors:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            sys.exit(1)

    def validate_ios_signing(self):
        """Validate iOS signing configuration."""
        errors = []
        warnings = []

        # Check certificate path
        if not self.ios_certificate_path:
            # Try to find it in the signing directory
            cert_path = Path(self.user_signing_dir) / "ios" / "Certificate.p12"
            if cert_path.exists():
                self.ios_certificate_path = str(cert_path)
                print(f"✅ Found iOS certificate at: {self.ios_certificate_path}")
            else:
                warnings.append("iOS certificate path not set")

        # Validate certificate exists
        if self.ios_certificate_path:
            cert_path = Path(self.ios_certificate_path)
            if not cert_path.exists():
                errors.append(f"iOS certificate not found at: {self.ios_certificate_path}")

        # Check certificate password
        if not self.ios_certificate_password:
            warnings.append("iOS certificate password not set")

        # Check Team ID
        if not self.apple_team_id:
            warnings.append("Apple Team ID not set (APPLE_TEAM_ID)")

        if warnings:
            print("⚠️  iOS signing warnings:", file=sys.stderr)
            for warning in warnings:
                print(f"  - {warning}", file=sys.stderr)
            print()

        if errors:
            print("❌ iOS signing errors:", file=sys.stderr)
            for error in errors:
                print(f"  - {error}", file=sys.stderr)
            sys.exit(1)

    def has_credentials(self, platform: str) -> bool:
        """Check if credentials exist for the specified platform."""
        if platform == "android":
            if not self.google_play_service_account_key:
                return False
            key_path = Path(self.google_play_service_account_key)
            if not key_path.exists():
                return False
            return True

        elif platform == "ios":
            if not self.app_store_connect_key_path or not Path(self.app_store_connect_key_path).exists():
                return False
            if not self.app_store_connect_key_id:
                return False
            if not self.app_store_connect_issuer_id:
                return False
            return True

        return False

    def validate_credentials(self, platform: str):
        """Validate credentials for the specified platform."""
        if platform == "android":
            self.validate_android_signing()
            
            if not self.google_play_service_account_key:
                print("❌ Error: Google Play Service Account Key not set (GOOGLE_PLAY_SERVICE_ACCOUNT_KEY)", file=sys.stderr)
                sys.exit(1)
                
            key_path = Path(self.google_play_service_account_key)
            if not key_path.exists():
                print(f"❌ Error: Google Play key file not found at {self.google_play_service_account_key}", file=sys.stderr)
                sys.exit(1)

        elif platform == "ios":
            self.validate_ios_signing()
            
            errors = []
            if not self.app_store_connect_key_path:
                errors.append("App Store Connect Key Path not set (APPSTORE_CONNECT_KEY_PATH)")
            elif not Path(self.app_store_connect_key_path).exists():
                errors.append(f"App Store Connect Key functionality not found at {self.app_store_connect_key_path}")
                
            if not self.app_store_connect_key_id:
                errors.append("App Store Connect Key ID not set (APPSTORE_CONNECT_KEY_ID)")
                
            if not self.app_store_connect_issuer_id:
                errors.append("App Store Connect Issuer ID not set (APPSTORE_CONNECT_ISSUER_ID)")
                
            if errors:
                print("❌ App Store Connect credential errors:", file=sys.stderr)
                for error in errors:
                    print(f"  - {error}", file=sys.stderr)
                sys.exit(1)


def get_config() -> ReleaseConfig:
    """Get the release configuration singleton."""
    return ReleaseConfig()
