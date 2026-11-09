#!/usr/bin/env python3
"""
Upload built KMP releases to Google Play Store and Apple TestFlight.
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional

try:
    import httplib2
    import google_auth_httplib2
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:
    print("Error: Required Python packages not installed.", file=sys.stderr)
    print("Please run: just setup-python", file=sys.stderr)
    sys.exit(1)

from config import get_config


def find_mapping_file() -> Optional[Path]:
    """Find the ProGuard/R8 mapping file from the latest build."""
    config = get_config()
    project_dir = config.project_dir

    # Check standard output location
    mapping_paths = [
        # Gradle build output
        project_dir / "android/app/build/outputs/mapping/release/mapping.txt",
    ]

    for mapping_path in mapping_paths:
        if mapping_path.exists():
            return mapping_path

    return None


class ReleaseUploader:
    """Handles uploading KMP releases to app stores."""

    def _upload_symbols_in_edit(self, service, edit_id: str, version_code: int):
        """Upload debug symbols within an existing edit."""
        mapping_path = find_mapping_file()

        # Upload ProGuard mapping (for Java/Kotlin code deobfuscation)
        if mapping_path is not None and mapping_path.exists():
            try:
                print(f"📤 Uploading debug symbols...")
                media = MediaFileUpload(str(mapping_path), mimetype="application/octet-stream")
                service.edits().deobfuscationfiles().upload(
                    editId=edit_id,
                    packageName=self.config.android_package_name,
                    apkVersionCode=version_code,
                    media_body=media,
                    deobfuscationFileType="proguard",
                ).execute()
                print(f"✅ Debug symbols uploaded")
            except Exception as e:
                print(f"⚠️  Symbol upload failed: {e}")

        # Upload native .so (non-stripped) for custom native code
        # Note: AndroidX and Play Services symbols are automatically fetched by Google Play
        # from their public symbol servers. This is only for YOUR custom .so files.

    def upload_android_symbols(self, mapping_path: Optional[Path] = None, edit_id: Optional[str] = None, version_code: Optional[int] = None):
        """Upload Android ProGuard/R8 mapping file for crash analysis.

        Args:
            mapping_path: Path to mapping.txt file. If None, will find latest.
            edit_id: The edit ID to use. If None, creates a new one.
            version_code: The version code from the uploaded bundle. If None, queries for it.
        """
        if mapping_path is None:
            mapping_path = find_mapping_file()

        if mapping_path is None or not mapping_path.exists():
            print(f"⚠️  No mapping file found, skipping symbol upload")
            return

        self.config.validate_credentials("android")

        print(f"📤 Uploading debug symbols: {mapping_path.name}")

        try:
            # Authenticate
            credentials = service_account.Credentials.from_service_account_file(
                self.config.google_play_service_account_key,
                scopes=["https://www.googleapis.com/auth/androidpublisher"],
            )

            http = httplib2.Http(timeout=600)
            authorized_http = google_auth_httplib2.AuthorizedHttp(credentials, http=http)
            service = build("androidpublisher", "v3", http=authorized_http)

            # Create new edit if not provided
            if edit_id is None:
                edit_request = service.edits().insert(body={}, packageName=self.config.android_package_name)
                edit_response = edit_request.execute()
                edit_id = edit_response["id"]

            # Get version code from edit if not provided
            if version_code is None:
                # Query the bundles in this edit to find the version code
                bundles = service.edits().bundles().list(
                    editId=edit_id, packageName=self.config.android_package_name
                ).execute()
                if bundles.get("bundles"):
                    version_code = int(bundles["bundles"][0]["versionCode"])

            if version_code is None:
                print(f"⚠️  Could not determine version code, skipping symbol upload")
                return

            # Upload deobfuscation file
            media = MediaFileUpload(str(mapping_path), mimetype="application/octet-stream")
            service.edits().deobfuscationfiles().upload(
                editId=edit_id,
                packageName=self.config.android_package_name,
                apkVersionCode=version_code,
                media_body=media,
                deobfuscationFileType="proguard",
            ).execute()

            # Commit edit
            service.edits().commit(editId=edit_id, packageName=self.config.android_package_name).execute()

            print(f"✅ Debug symbols uploaded successfully")

        except Exception as e:
            print(f"⚠️  Symbol upload failed: {e}", file=sys.stderr)
            # Continue anyway - this is a warning, not a failure

    def __init__(self):
        """Initialize uploader."""
        self.config = get_config()
        self.releases_dir = self.config.releases_dir

    def find_latest_android_build(self) -> Optional[Path]:
        """Find the latest Android AAB file."""
        # Check releases directory first
        android_dir = self.releases_dir / "android"
        aab_files = list(android_dir.glob("*.aab")) if android_dir.exists() else []

        if not aab_files:
            # Gradle build output: android/app/build/outputs/bundle/release/app-release.aab
            build_output = (
                self.config.project_dir
                / "android"
                / "app"
                / "build"
                / "outputs"
                / "bundle"
                / "release"
                / "app-release.aab"
            )
            if build_output.exists():
                return build_output

            # Alternative: check specifically for any .aab in that dir if naming differs
            output_dir = self.config.project_dir / "android" / "app" / "build" / "outputs" / "bundle" / "release"
            if output_dir.exists():
                found_aabs = list(output_dir.glob("*.aab"))
                if found_aabs:
                    return sorted(found_aabs, key=lambda p: p.stat().st_mtime, reverse=True)[0]
                    
            return None

        # Sort by modification time, newest first
        return sorted(aab_files, key=lambda p: p.stat().st_mtime, reverse=True)[0]

    def find_latest_ios_build(self) -> Optional[Path]:
        """Find the latest iOS IPA file."""
        # Check releases directory first
        ios_dir = self.releases_dir / "ios"
        ipa_files = list(ios_dir.glob("*.ipa")) if ios_dir.exists() else []

        if not ipa_files:
            # Try build output defined in justfile (build/ios/ipa)
            build_ipa_dir = self.config.project_dir / "build" / "ios" / "ipa"
            if build_ipa_dir.exists():
                ipa_files = list(build_ipa_dir.glob("*.ipa"))
                if ipa_files:
                    # Return the most recently modified IPA
                    return sorted(ipa_files, key=lambda p: p.stat().st_mtime, reverse=True)[0]
            return None

        # Sort by modification time, newest first
        return sorted(ipa_files, key=lambda p: p.stat().st_mtime, reverse=True)[0]

    def upload_android(self, aab_path: Optional[Path] = None, track: str = "internal", status: str = "draft"):
        """Upload Android AAB to Google Play Store.

        Args:
            aab_path: Path to AAB file. If None, will find latest.
            track: Release track (internal, alpha, beta, production)
            status: Release status (draft, completed, halted, inProgress)
        """
        self.config.validate_credentials("android")

        if aab_path is None:
            aab_path = self.find_latest_android_build()

        if aab_path is None or not aab_path.exists():
            print(f"Error: Android AAB not found", file=sys.stderr)
            sys.exit(1)

        print(f"📦 Uploading Android AAB: {aab_path.name}")
        print(f"📍 Track: {track}")
        print(f"📊 Status: {status}")

        try:
            # Authenticate
            print("🔐 Authenticating with Google Play...")
            credentials = service_account.Credentials.from_service_account_file(
                self.config.google_play_service_account_key,
                scopes=["https://www.googleapis.com/auth/androidpublisher"],
            )

            http = httplib2.Http(timeout=600)
            authorized_http = google_auth_httplib2.AuthorizedHttp(credentials, http=http)
            service = build("androidpublisher", "v3", http=authorized_http)

            # Create edit
            print("📝 Creating edit...")
            edit_request = service.edits().insert(body={}, packageName=self.config.android_package_name)
            edit_response = edit_request.execute()
            edit_id = edit_response["id"]
            print(f"✅ Edit created: {edit_id}")

            # Upload AAB
            print("⬆️  Uploading AAB...")
            media = MediaFileUpload(str(aab_path), mimetype="application/octet-stream")
            upload_response = service.edits().bundles().upload(
                editId=edit_id, packageName=self.config.android_package_name, media_body=media
            ).execute()

            version_code = upload_response["versionCode"]
            print(f"✅ AAB uploaded with version code: {version_code}")

            # Upload debug symbols if available
            self._upload_symbols_in_edit(service, edit_id, version_code)

            # Assign to track
            print(f"🎯 Assigning to {track} track...")
            track_body = {"releases": [{"versionCodes": [str(version_code)], "status": status}]}

            service.edits().tracks().update(
                editId=edit_id, track=track, packageName=self.config.android_package_name, body=track_body
            ).execute()

            # Commit edit
            print("💾 Committing changes...")
            service.edits().commit(editId=edit_id, packageName=self.config.android_package_name).execute()

            print(f"✅ Successfully uploaded to Google Play ({track} track)")

        except Exception as e:
            print(f"❌ Error uploading to Google Play: {e}", file=sys.stderr)
            import traceback

            traceback.print_exc()
            sys.exit(1)

    def upload_ios(self, ipa_path: Optional[Path] = None):
        """Upload iOS IPA to TestFlight.

        Args:
            ipa_path: Path to IPA file. If None, will find latest.
        """
        self.config.validate_credentials("ios")

        if ipa_path is None:
            ipa_path = self.find_latest_ios_build()

        if ipa_path is None or not ipa_path.exists():
            print(f"Error: iOS IPA not found", file=sys.stderr)
            sys.exit(1)

        # Get file size
        file_size_mb = ipa_path.stat().st_size / (1024 * 1024)
        print(f"📦 Uploading iOS IPA: {ipa_path.name}")
        print(f"📊 File size: {file_size_mb:.2f} MB")

        try:
            print("⬆️  Uploading to App Store Connect...")

            # altool looks for API keys in specific directories.
            # We need to ensure the key is in ~/.appstoreconnect/private_keys/
            key_source = Path(self.config.app_store_connect_key_path)
            key_dir = Path.home() / ".appstoreconnect" / "private_keys"
            key_dir.mkdir(parents=True, exist_ok=True)
            key_dest = key_dir / key_source.name

            # Only copy if source and dest are different, or dest doesn't exist
            if not key_dest.exists() or key_source.resolve() != key_dest.resolve():
                print(f"📋 Copying API key from {key_source} to {key_dir}...")
                if not key_source.exists():
                    print(f"❌ Error: API key not found at {key_source}", file=sys.stderr)
                    sys.exit(1)
                shutil.copy2(key_source, key_dest)
                print(f"✅ API key copied successfully")
            else:
                print(f"✅ API key already in place")

            print("📤 Starting upload... (this may take several minutes)")
            print("💡 Tip: Larger files take longer to upload")

            # Use altool to upload with real-time output streaming
            process = subprocess.Popen(
                [
                    "xcrun",
                    "altool",
                    "--upload-app",
                    "-f",
                    str(ipa_path),
                    "--type",
                    "ios",
                    "--apiKey",
                    self.config.app_store_connect_key_id,
                    "--apiIssuer",
                    self.config.app_store_connect_issuer_id,
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )

            # Stream output in real-time
            def stream_output(pipe, prefix=""):
                """Stream output from subprocess pipe."""
                for line in pipe:
                    line = line.strip()
                    if line:
                        print(f"{prefix}{line}")

            # Start threads to capture stdout and stderr
            import threading

            stdout_thread = threading.Thread(target=stream_output, args=(process.stdout, "  "))
            stderr_thread = threading.Thread(target=stream_output, args=(process.stderr, "  "))

            stdout_thread.daemon = True
            stderr_thread.daemon = True

            stdout_thread.start()
            stderr_thread.start()

            # Wait for process to complete
            return_code = process.wait()

            # Wait for threads to finish
            stdout_thread.join(timeout=1)
            stderr_thread.join(timeout=1)

            if return_code != 0:
                print(f"\n❌ Upload failed with exit code {return_code}", file=sys.stderr)
                sys.exit(1)

            print("\n✅ Successfully uploaded to TestFlight")
            print("⏳ Processing may take a few minutes before appearing in App Store Connect")

        except Exception as e:
            print(f"❌ Error uploading to TestFlight: {e}", file=sys.stderr)
            import traceback
            traceback.print_exc()
            sys.exit(1)


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Upload KMP releases to app stores")
    parser.add_argument(
        "platform",
        choices=["android", "ios", "all"],
        help="Platform to upload",
    )
    parser.add_argument(
        "--android-track",
        choices=["internal", "alpha", "beta", "production"],
        default="internal",
        help="Google Play track (default: internal)",
    )
    parser.add_argument(
        "--android-status",
        choices=["draft", "completed", "halted", "inProgress"],
        default="completed",
        help="Release status (default: completed)",
    )
    parser.add_argument("--android-aab", type=Path, help="Path to Android AAB file")
    parser.add_argument("--ios-ipa", type=Path, help="Path to iOS IPA file")

    args = parser.parse_args()

    uploader = ReleaseUploader()

    if args.platform in ["android", "all"]:
        uploader.upload_android(args.android_aab, args.android_track, args.android_status)

    if args.platform in ["ios", "all"]:
        uploader.upload_ios(args.ios_ipa)

    if args.platform == "all":
        print("\n🎉 All uploads complete!")


if __name__ == "__main__":
    main()
