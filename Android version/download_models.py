import urllib.request
import os

models = {
    "app/src/main/assets/models/inpainting_lama_2025jan.onnx": "https://huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx",
    "app/src/main/assets/models/RealESRGAN_x2plus.onnx": "https://huggingface.co/tidus2102/Real-ESRGAN/resolve/main/RealESRGAN_x2plus.onnx",
    "app/src/main/assets/models/cpga_fp16.tflite": "https://storage.googleapis.com/tfhub-lite-models/sayakpaul/lite-model/mirnet-fixed/dr/1.tflite"
}

for path, url in models.items():
    print(f"Downloading {url} to {path}...")
    try:
        urllib.request.urlretrieve(url, path)
        print(f"Downloaded {path}, size: {os.path.getsize(path)}")
    except Exception as e:
        print(f"Failed to download {url}: {e}")
