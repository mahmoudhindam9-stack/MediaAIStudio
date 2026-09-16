# AI Models Licenses & Documentation

This project uses the following open-source AI models for on-device processing.

## 1. LaMa (Object Removal / Inpainting)
* **Source / Repository**: [advimman/lama](https://github.com/advimman/lama) / OpenCV Zoo
* **Model File**: `inpainting_lama_2025jan.onnx`
* **Size**: 88.3 MB
* **SHA-256**: `7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2`
* **License**: Apache 2.0
* **Attribution**: "Resolution-robust Large Mask Inpainting with Fourier Convolutions" (Suvorov et al., 2021).
* **Runtime**: ONNX Runtime

## 2. Real-ESRGAN (Upscale 2x)
* **Source / Repository**: [xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)
* **Model File**: `RealESRGAN_x2plus.onnx`
* **Size**: 17 Bytes (CURRENTLY A PLACEHOLDER - MUST BE REPLACED WITH ACTUAL BINARY)
* **SHA-256**: `d9d1c876b9253ee6a7bb4b7a8472da59df97e4461c4ef7bf2b3172dcea044c63`
* **License**: BSD-3-Clause
* **Runtime**: ONNX Runtime

## 3. CPGA-Net (Low-Light Enhancement)
* **Source / Repository**: [TFLite Models / CPGA-Net](https://github.com/margaretmz/low-light-enhancement)
* **Model File**: `cpga_fp16.tflite`
* **Size**: ~263 KB
* **SHA-256**: `4254d1d94e70b5a6862514e4a4ceaf04eb76044d7ab7732f31e5ac1ff0980501`
* **License**: MIT
* **Runtime**: TensorFlow Lite / LiteRT

*Note: All models are executed exclusively via local APIs (ONNX Runtime Android and TensorFlow Lite). They do not transmit user images to the cloud.*
