"""
Download pre-trained chess piece detection TFLite model from GitHub.
This model is ready to use - no conversion needed!
"""

import urllib.request
import os

def download_tflite_model():
    """Download EfficientDet-Lite4 chess piece detection model."""
    print("="*60)
    print("Downloading Pre-Trained Chess Piece Detection Model")
    print("="*60)
    print("\nModel: EfficientDet-Lite4")
    print("Source: GitHub - Chess Piece Object Detection")
    print()
    
    # Direct link to the TFLite model
    url = "https://github.com/Hann-THL/chess_piece_object_detection/raw/main/model.tflite"
    output_file = "chess_yolo.tflite"
    
    try:
        print(f"Downloading from: {url}")
        print("Please wait...")
        
        urllib.request.urlretrieve(url, output_file)
        
        size_mb = os.path.getsize(output_file) / (1024 * 1024)
        print(f"\n✓ Downloaded successfully!")
        print(f"✓ File: {output_file}")
        print(f"✓ Size: {size_mb:.2f} MB")
        
        return output_file
        
    except Exception as e:
        print(f"\n✗ Download failed: {e}")
        print("\nManual download:")
        print("1. Visit: https://github.com/Hann-THL/chess_piece_object_detection")
        print("2. Download 'model.tflite'")
        print("3. Rename to 'chess_yolo.tflite'")
        return None

def copy_to_assets(model_file):
    """Copy model to Android assets directory."""
    print("\n" + "="*60)
    print("Copying to Android Assets")
    print("="*60)
    
    assets_dir = "app/src/main/assets"
    os.makedirs(assets_dir, exist_ok=True)
    
    dest_file = os.path.join(assets_dir, "chess_yolo.tflite")
    
    try:
        import shutil
        shutil.copy(model_file, dest_file)
        
        size_mb = os.path.getsize(dest_file) / (1024 * 1024)
        print(f"\n✓ Copied to: {dest_file}")
        print(f"✓ Size: {size_mb:.2f} MB")
        return True
        
    except Exception as e:
        print(f"\n✗ Copy failed: {e}")
        return False

def main():
    print("\nChess Piece Detection - Model Downloader")
    print("="*60)
    
    # Download model
    model_file = download_tflite_model()
    if not model_file:
        return False
    
    # Copy to assets
    if not copy_to_assets(model_file):
        return False
    
    print("\n" + "="*60)
    print("SUCCESS! Model is ready to use!")
    print("="*60)
    print("\nNext steps:")
    print("1. Build the APK:")
    print("   .\\gradlew assembleDebug")
    print("\n2. Install on your phone:")
    print("   adb install app\\build\\outputs\\apk\\debug\\app-debug.apk")
    print("\n3. Test the app!")
    
    return True

if __name__ == "__main__":
    import sys
    success = main()
    sys.exit(0 if success else 1)
