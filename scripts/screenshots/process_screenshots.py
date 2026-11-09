#!/usr/bin/env python3
"""
Screenshot Processor for App Store Uploads
Processes raw screenshots into store-ready formats
"""

import os
import sys
import subprocess
import shutil
from pathlib import Path

# Configuration
IOS_OUTPUT = "raw/ios_appstore"
ANDROID_OUTPUT = "raw/android_playstore"

# iOS App Store required sizes
IOS_SIZES = {
    "iphone_67": (2796, 1290),  # iPhone 14/15 Pro Max
    "iphone_55": (2208, 1242),  # iPhone 8 Plus
}

# Android Play Store required sizes
ANDROID_SIZES = {
    "phone_portrait": (1920, 1080),  # Minimum
}


def get_image_size(filepath):
    """Get image dimensions using sips"""
    result = subprocess.run(
        ["sips", "-g", "pixelWidth", "-g", "pixelHeight", filepath],
        capture_output=True, text=True
    )
    width = height = 0
    for line in result.stdout.splitlines():
        if "pixelWidth" in line:
            width = int(line.split(":")[1].strip())
        elif "pixelHeight" in line:
            height = int(line.split(":")[1].strip())
    return width, height


def resize_image(input_path, output_path, width, height):
    """Resize image to exact dimensions"""
    subprocess.run(
        ["sips", input_path, "-z", str(height), str(width), "--out", output_path],
        check=True
    )


def ensure_directory(path):
    """Create directory if it doesn't exist"""
    os.makedirs(path, exist_ok=True)


def process_android_screenshots():
    """Process Android screenshots for Play Store"""
    print("\n=== Processing Android Screenshots ===")

    source_dir = Path("raw/android")
    ensure_directory(ANDROID_OUTPUT)

    if not source_dir.exists():
        print(f"Warning: Source directory {source_dir} not found")
        return

    screenshots = sorted(source_dir.glob("*.png"))
    print(f"Found {len(screenshots)} screenshots")

    for i, src in enumerate(screenshots, 1):
        dst = Path(ANDROID_OUTPUT) / f"screenshot_{i:02d}.png"
        width, height = get_image_size(str(src))

        print(f"  {src.name} -> {dst.name} ({width}x{height})")

        # Copy if already correct size, otherwise resize
        if (width, height) == (1080, 2400):
            shutil.copy2(src, dst)
            print(f"    Copied (already correct size)")
        else:
            resize_image(str(src), str(dst), 1080, 1920)
            print(f"    Resized to 1080x1920")


def process_ios_screenshots():
    """Process iOS screenshots for App Store"""
    print("\n=== Processing iOS Screenshots ===")

    source_dir = Path("raw/Ios")
    ensure_directory(IOS_OUTPUT)

    if not source_dir.exists():
        print(f"Warning: Source directory {source_dir} not found")
        return

    # Filter out videos
    screenshots = [f for f in sorted(source_dir.glob("*.png"))]
    print(f"Found {len(screenshots)} screenshots")

    # iPhone 14 Pro Max size: 1290 x 2796
    target_width, target_height = 1290, 2796

    for i, src in enumerate(screenshots, 1):
        # Use first 6 screenshots
        if i > 6:
            break

        dst = Path(IOS_OUTPUT) / f"iphone_{i:02d}.png"
        width, height = get_image_size(str(src))

        print(f"  {src.name} -> {dst.name} ({width}x{height})")

        if width == target_width and height == target_height:
            shutil.copy2(src, dst)
            print(f"    Copied (already correct size)")
        else:
            # Resize to iPhone 14 Pro Max dimensions
            resize_image(str(src), str(dst), target_width, target_height)
            print(f"    Resized to {target_width}x{target_height}")


def main():
    print("=" * 50)
    print("Screenshot Processor for App Store Upload")
    print("=" * 50)

    if len(sys.argv) > 1:
        if "android" in sys.argv[1].lower():
            process_android_screenshots()
        elif "ios" in sys.argv[1].lower():
            process_ios_screenshots()
        else:
            print("Usage: python process_screenshots.py [ios|android|both]")
    else:
        process_ios_screenshots()
        process_android_screenshots()

    print("\n=== Processing Complete ===")
    print(f"iOS screenshots: {IOS_OUTPUT}/")
    print(f"Android screenshots: {ANDROID_OUTPUT}/")


if __name__ == "__main__":
    main()
