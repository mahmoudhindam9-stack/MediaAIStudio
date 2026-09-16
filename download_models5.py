import urllib.request
import os

url = "https://raw.githubusercontent.com/tulasiram58827/Zero-DCE/master/Zero-DCE_extension/Zero-DCE_TFLite/zero_dce_lite_192x192_fp16.tflite"
path = "app/src/main/assets/models/cpga_fp16.tflite"

print(f"Downloading {url} to {path}...")
try:
    urllib.request.urlretrieve(url, path)
    print(f"Downloaded {path}, size: {os.path.getsize(path)}")
except Exception as e:
    print(f"Failed to download {url}: {e}")
