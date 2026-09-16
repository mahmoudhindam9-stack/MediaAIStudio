# AI Validation Notes

The AI model hardening pass tightened LaMa artifact input/output validation and changed CPGA-Net preprocessing so the full source image is preserved through aspect-ratio fitting instead of center-cropping the original image.

LaMa validation now requires all configured expected input/output names to be present in the ONNX graph.

CPGA-Net now fits the full image into the required 256x256 model canvas with letterboxing, processes the real model output, and restores the enhanced image to the original dimensions without discarding side content.
