# MediaAIStudio AI Models — Sources, Licenses and Runtime Artifacts

Large model binaries are downloaded on first use into app-private storage. The repository must never contain placeholder model files.

## LaMa — Object Removal / Local Inpainting

- Model: `inpainting_lama_2025jan.onnx`
- Source: OpenCV Zoo / Hugging Face `opencv/opencv_zoo`
- URL: https://huggingface.co/opencv/opencv_zoo/resolve/main/models/inpainting_lama/inpainting_lama_2025jan.onnx
- Remote size: 92,591,623 bytes (about 92.6 MB)
- SHA-256: `7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2`
- License: Apache-2.0 for the OpenCV Zoo `inpainting_lama` model directory
- Runtime: ONNX Runtime Android
- Reference preprocessing: 512x512 image blob scaled by 1/255 and binary 512x512 mask. The reference implementation names the inputs `image` and `mask`.

## Real-ESRGAN x2plus — Upscale 2x

- Model: `RealESRGAN_x2plus.onnx`
- Source: Real-ESRGAN with the documented ONNX re-export hosted in the QtMeshEditor model mirror
- URL: https://huggingface.co/fernandotonon/QtMeshEditor-models/resolve/main/RealESRGAN_x2plus.onnx
- Remote size: about 67.2 MB
- License: BSD-3-Clause as documented by the dedicated ONNX model card; credit to xinntao/Real-ESRGAN
- Runtime: ONNX Runtime Android
- Input/output contract documented by the model card: `1x3xHxW` float NCHW in `[0,1]` to `1x3x(2H)x(2W)`.
- SHA-256: not published by the selected model mirror; the app therefore validates the minimum size and requires ONNX Runtime to successfully open the graph before activation.

## CPGA-Net — AI Low-Light Enhancement

- Model: `cpga_fp16.tflite`
- Source: LiteRT Community `CPGA-Net-LowLight-LiteRT`
- URL: https://huggingface.co/litert-community/CPGA-Net-LowLight-LiteRT/resolve/main/cpga_fp16.tflite
- Size: 93,820 bytes for the current model revision documented by the model card
- SHA-256: `8b125569618cbc342b3c0a095f712dfc899ac739e896208baf13cb1769c4c319`
- License: MIT
- Runtime: TFLite / LiteRT
- Contract: `1x3x256x256` float32 NCHW in `[0,1]` to `1x3x256x256` float32 in `[0,1]`.

## Runtime Download Security

Each download is written to a temporary `.part` file, then validated and activated only after successful verification. Published SHA-256 values are checked before activation. A model with an unknown published SHA-256 must still pass the runtime graph/open validation; it is never considered valid because of its filename alone.

The invalid placeholder ONNX assets previously present in the Android project have been removed and must not be reintroduced.
