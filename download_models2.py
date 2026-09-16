import urllib.request
import os

models = {
    "app/src/main/assets/models/RealESRGAN_x2plus.onnx": "https://huggingface.co/tsing-zhao/Real-ESRGAN/resolve/main/RealESRGAN_x2plus.onnx"
}

for path, url in models.items():
    print(f"Downloading {url} to {path}...")
    try:
        urllib.request.urlretrieve(url, path)
        print(f"Downloaded {path}, size: {os.path.getsize(path)}")
    except Exception as e:
        print(f"Failed to download {url}: {e}")
