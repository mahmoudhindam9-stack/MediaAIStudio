import urllib.request
import os

url = "https://raw.githubusercontent.com/sayakpaul/MIRNet-TFLite-TRT/main/mirnet_dr.tflite"
path = "app/src/main/assets/models/cpga_fp16.tflite"

print(f"Downloading {url} to {path}...")
try:
    urllib.request.urlretrieve(url, path)
    print(f"Downloaded {path}, size: {os.path.getsize(path)}")
except Exception as e:
    print(f"Failed to download {url}: {e}")
