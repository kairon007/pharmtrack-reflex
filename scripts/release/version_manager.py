#!/usr/bin/env python3
"""
Version management for KMP releases.
Handles fetching latest versions from stores and updating version files.
"""

import argparse
import re
import subprocess
import sys
from typing import Dict, Tuple

try:
    import jwt
    import requests
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
except ImportError:
    print("Error: Required Python packages not installed.", file=sys.stderr)
    print("Please run: just setup-python", file=sys.stderr)
    sys.exit(1)

from config import get_config


class VersionManager:
    """Manages version information for iOS and Android."""

    def __init__(self):
        """Initialize version manager."""
        self.config = get_config()

    def get_local_versions(self) -> Dict[str, str]:
        """Get current local version information."""
        return {
            "android_version_name": self._get_android_version_name(),
            "android_version_code": self._get_android_version_code(),
            "ios_version": self._get_ios_version_name(),
            "ios_build_number": self._get_ios_build_number(),
        }

    def _get_ios_version_name(self) -> str:
        """Get the marketing version from ios/project.yml."""
        path = self.config.ios_version_file
        if not path.exists():
            return "1.0.0"

        match = re.search(r'MARKETING_VERSION:\s*"?([^"\s]+)"?', path.read_text())
        if match:
            return match.group(1).strip()
        return "1.0.0"

    def _get_ios_build_number(self) -> str:
        """Get the iOS build number from ios/project.yml."""
        path = self.config.ios_version_file
        if not path.exists():
            return "1"

        match = re.search(r'CURRENT_PROJECT_VERSION:\s*"?([^"\s]+)"?', path.read_text())
        if match:
            return match.group(1).strip()
        return "1"

    def _get_android_version_name(self) -> str:
        """Get versionName from android/app/build.gradle.kts."""
        path = self.config.android_version_file
        if not path.exists():
            return "1.0.0"

        match = re.search(r'versionName\s*=\s*"([^"]+)"', path.read_text())
        if match:
            return match.group(1).strip()
        return "1.0.0"

    def _get_android_version_code(self) -> str:
        """Get versionCode from android/app/build.gradle.kts."""
        path = self.config.android_version_file
        if not path.exists():
            return "1"

        match = re.search(r'versionCode\s*=\s*(\d+)', path.read_text())
        if match:
            return match.group(1).strip()
        return "1"

    def update_versions(self, version_code: int = None, version_name: str = None):
        """Update the version in both platform build files."""
        ios_file = self.config.ios_version_file
        if ios_file.exists():
            content = ios_file.read_text()
            if version_code is not None:
                content = re.sub(
                    r'(CURRENT_PROJECT_VERSION:\s*"?)[^"\s]+',
                    lambda m: m.group(1) + str(version_code),
                    content,
                )
            if version_name is not None:
                content = re.sub(
                    r'(MARKETING_VERSION:\s*"?)[^"\s]+',
                    lambda m: m.group(1) + str(version_name),
                    content,
                )
            ios_file.write_text(content)

        android_file = self.config.android_version_file
        if android_file.exists():
            content = android_file.read_text()
            if version_code is not None:
                content = re.sub(
                    r'(versionCode\s*=\s*)\d+',
                    lambda m: m.group(1) + str(version_code),
                    content,
                )
            if version_name is not None:
                content = re.sub(
                    r'(versionName\s*=\s*")[^"]+',
                    lambda m: m.group(1) + str(version_name),
                    content,
                )
            android_file.write_text(content)

        print(f"✅ Updated versions: {version_name or 'unchanged'} ({version_code or 'unchanged'})")

    def fetch_latest_android_version(self) -> int:
        """Fetch latest Android version code from Google Play Store."""
        print("Fetching latest Android version from Google Play Store...")

        if not self.config.has_credentials("android"):
            print("⚠️  Google Play credentials not configured - skipping fetch")
            return 0

        self.config.validate_credentials("android")

        try:
            credentials = service_account.Credentials.from_service_account_file(
                self.config.google_play_service_account_key,
                scopes=["https://www.googleapis.com/auth/androidpublisher"],
            )
            service = build("androidpublisher", "v3", credentials=credentials)

            # Create edit
            edit_request = service.edits().insert(body={}, packageName=self.config.android_package_name)
            edit_response = edit_request.execute()
            edit_id = edit_response["id"]

            # Get tracks
            tracks_request = service.edits().tracks().get(
                packageName=self.config.android_package_name, editId=edit_id, track="internal"
            )
            tracks_response = tracks_request.execute()

            latest_version_code = 0
            if "releases" in tracks_response and tracks_response["releases"]:
                for release in tracks_response["releases"]:
                    if "versionCodes" in release:
                        for version in release["versionCodes"]:
                            if int(version) > latest_version_code:
                                latest_version_code = int(version)

            print(f"Latest Android Version Code: {latest_version_code}")
            return latest_version_code

        except Exception as e:
            print(f"Error fetching Android version: {e}", file=sys.stderr)
            print("Defaulting to 0", file=sys.stderr)
            return 0

    def fetch_latest_ios_build_number(self) -> Tuple[int, str]:
        """Fetch latest iOS build number and version string from TestFlight.

        Returns:
            Tuple of (build_number, version_string)
        """
        print("Fetching latest iOS build number from TestFlight...")

        if not self.config.has_credentials("ios"):
            print("⚠️  App Store Connect credentials not configured - skipping fetch")
            return 0, "1.0.0"

        self.config.validate_credentials("ios")

        try:
            # Create JWT token
            with open(self.config.app_store_connect_key_path, "r") as key_file:
                signing_key = key_file.read()

            import time

            header = {"alg": "ES256", "kid": self.config.app_store_connect_key_id, "typ": "JWT"}
            payload = {
                "iss": self.config.app_store_connect_issuer_id,
                "exp": int(time.time()) + 1200,
                "aud": "appstoreconnect-v1",
            }

            token = jwt.encode(payload, signing_key, algorithm="ES256", headers=header)
            headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

            # Get App ID and name
            url = f"https://api.appstoreconnect.apple.com/v1/apps?filter[bundleId]={self.config.apple_bundle_id}"
            response = requests.get(url, headers=headers)
            response.raise_for_status()
            app_data = response.json()["data"][0]
            app_id = app_data["id"]
            app_name = app_data["attributes"]["name"]
            bundle_id = app_data["attributes"]["bundleId"]

            print(f"App: {app_name}")
            print(f"Bundle ID: {bundle_id}")

            # Get all builds from TestFlight with pre-release version info
            # Sort by uploadedDate descending to get most recent builds
            url = f"https://api.appstoreconnect.apple.com/v1/builds?filter[app]={app_id}&include=preReleaseVersion&sort=-uploadedDate&limit=50"
            response = requests.get(url, headers=headers)
            response.raise_for_status()
            build_data = response.json()

            if build_data["data"]:
                # Create a map of preReleaseVersion IDs to version strings
                pre_release_versions = {}
                if "included" in build_data:
                    for item in build_data["included"]:
                        if item["type"] == "preReleaseVersions":
                            pre_release_versions[item["id"]] = item["attributes"]["version"]

                # Use the most recent build (first in list, sorted by uploadedDate descending)
                latest_build_number = int(build_data["data"][0]["attributes"]["version"])
                latest_version_str = "1.0.0"  # Default
                if "relationships" in build_data["data"][0] and "preReleaseVersion" in build_data["data"][0]["relationships"]:
                    pre_rel_id = build_data["data"][0]["relationships"]["preReleaseVersion"]["data"]["id"]
                    latest_version_str = pre_release_versions.get(pre_rel_id, "1.0.0")

                print(f"Latest Build: {latest_version_str} (Build #{latest_build_number})")
                return latest_build_number, latest_version_str
            else:
                print("No builds found in TestFlight")
                return 0, "1.0.0"

        except Exception as e:
            print(f"Error fetching iOS version: {e}", file=sys.stderr)
            print("Defaulting to 0", file=sys.stderr)
            return 0, "1.0.0"

    def prepare_release(self):
        """Prepare a new release by fetching latest versions and incrementing."""
        print("🚀 Preparing release...\n")

        # Get current local versions
        local = self.get_local_versions()
        local_android_code = int(local['android_version_code'])
        local_ios_build = int(local['ios_build_number'])

        # Fetch latest versions from stores
        fetched_android = self.fetch_latest_android_version()
        fetched_ios_build, fetched_ios_version = self.fetch_latest_ios_build_number()

        # Determine highest versions
        latest_android = max(fetched_android, local_android_code)
        latest_ios_build = max(fetched_ios_build, local_ios_build)

        # Use local version string if fetch failed or returned default
        if fetched_ios_build == 0:
            latest_ios_version = local["ios_version"]
        else:
            latest_ios_version = fetched_ios_version

        # Calculate unified version code
        highest_build_number = max(latest_android, latest_ios_build)
        new_build_number = highest_build_number + 1 + self.config.build_number_offset

        # Parse and increment unified version string
        match = re.match(r"(\d+)\.(\d+)\.(\d+)", latest_ios_version)
        if match:
            major, minor, patch = map(int, match.groups())
            patch += 1  # Increment patch version
            new_version_name = f"{major}.{minor}.{patch}"
        else:
            # Fallback if parsing fails
            new_version_name = f"1.0.{new_build_number}"

        print(f"\n📊 Version Summary:")
        print(f"Current: {local['ios_version']} ({local['ios_build_number']})")
        print(f"Fetched Highest Android: {latest_android}")
        print(f"Fetched Highest iOS: {latest_ios_version} ({latest_ios_build})")
        print(f"New Unified Release: {new_version_name} ({new_build_number})")

        # Update files
        self.update_versions(new_build_number, new_version_name)

        print("\n✅ Release prepared successfully!")

    def bump_version(self, bump_type: str = "patch"):
        """Bump semantic version (patch/minor/major)."""
        local = self.get_local_versions()

        # Parse current version
        current_version = local["ios_version"]
        match = re.match(r"(\d+)\.(\d+)\.(\d+)", current_version)
        if not match:
            print(f"Error: Invalid version format: {current_version}", file=sys.stderr)
            sys.exit(1)

        major, minor, patch = map(int, match.groups())

        # Bump version
        if bump_type == "major":
            major += 1
            minor = 0
            patch = 0
        elif bump_type == "minor":
            minor += 1
            patch = 0
        elif bump_type == "patch":
            patch += 1
        else:
            print(f"Error: Invalid bump type: {bump_type}", file=sys.stderr)
            sys.exit(1)

        new_version = f"{major}.{minor}.{patch}"

        # Get current build number and increment it too
        current_build = int(local["ios_build_number"])
        new_build = current_build + 1

        # Update both platforms
        self.update_versions(new_build, new_version)

        print(f"✅ Bumped {bump_type} version: {current_version} ({current_build}) → {new_version} ({new_build})")


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Manage KMP release versions")
    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # Info command
    subparsers.add_parser("info", help="Show current version information")

    # Fetch command
    subparsers.add_parser("fetch", help="Fetch latest versions from stores")

    # Prepare command
    subparsers.add_parser("prepare", help="Prepare release (fetch + increment)")

    # Bump command
    bump_parser = subparsers.add_parser("bump", help="Bump semantic version")
    bump_parser.add_argument(
        "type", choices=["patch", "minor", "major"], default="patch", nargs="?", help="Version bump type"
    )

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    manager = VersionManager()

    if args.command == "info":
        versions = manager.get_local_versions()
        print("📊 Current Versions:")
        print(f"Android: {versions['android_version_name']} (code: {versions['android_version_code']})")
        print(f"iOS: {versions['ios_version']} (build: {versions['ios_build_number']})")

    elif args.command == "fetch":
        android = manager.fetch_latest_android_version()
        ios_build, ios_version = manager.fetch_latest_ios_build_number()
        print(f"\n📊 Latest Store Versions:")
        print(f"Android: {android}")
        print(f"iOS: {ios_version} (Build {ios_build})")

    elif args.command == "prepare":
        manager.prepare_release()

    elif args.command == "bump":
        manager.bump_version(args.type)


if __name__ == "__main__":
    main()
