import urllib.request
import os

models = {
    "app/src/main/assets/models/RealESRGAN_x2plus.onnx": "https://huggingface.co/fernandotonon/QtMeshEditor-realesrgan-onnx/resolve/main/RealESRGAN_x2plus.onnx",
    "app/src/main/assets/models/cpga_fp16.tflite": "https://huggingface.co/sayakpaul/mirnet/resolve/main/dr_1.tflite"
}

for path, url in models.items():
    print(f"Downloading {url} to {path}...")
    try:
        urllib.request.urlretrieve(url, path)
        print(f"Downloaded {path}, size: {os.path.getsize(path)}")
    except Exception as e:
        print(f"Failed to download {url}: {e}")
