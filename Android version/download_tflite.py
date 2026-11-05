import urllib.request
import os

url = "https://huggingface.co/mlboydaisuke/zero-dce-litert/resolve/main/zero_dce_litert_512x512_fp16.tflite"
path = "app/src/main/assets/models/cpga_fp16.tflite"

print(f"Downloading {url} to {path}...")
try:
    urllib.request.urlretrieve(url, path)
    print(f"Downloaded {path}, size: {os.path.getsize(path)}")
except Exception as e:
    print(f"Failed to download {url}: {e}")
    # Let's try another one from the same repo just in case
    url2 = "https://huggingface.co/mlboydaisuke/zero-dce-litert/resolve/main/zero-dce-litert-512x512-fp16.tflite"
    try:
        urllib.request.urlretrieve(url2, path)
        print(f"Downloaded {path}, size: {os.path.getsize(path)}")
    except Exception as e2:
        print(f"Failed to download {url2}: {e2}")

