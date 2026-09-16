import onnx
import sys

def check_model(path):
    try:
        model = onnx.load(path)
        onnx.checker.check_model(model)
        print(f"{path} is a valid ONNX model.")
        print(f"IR Version: {model.ir_version}")
        for i, input in enumerate(model.graph.input):
            print(f"Input {i}: {input.name}")
            shape = []
            for dim in input.type.tensor_type.shape.dim:
                shape.append(dim.dim_value if dim.dim_value > 0 else 'dynamic')
            print(f"  Shape: {shape}")
            
        for i, output in enumerate(model.graph.output):
            print(f"Output {i}: {output.name}")
            shape = []
            for dim in output.type.tensor_type.shape.dim:
                shape.append(dim.dim_value if dim.dim_value > 0 else 'dynamic')
            print(f"  Shape: {shape}")
    except Exception as e:
        print(f"{path} is INVALID: {e}")

check_model("app/src/main/assets/models/inpainting_lama_2025jan.onnx")
print("---")
check_model("app/src/main/assets/models/RealESRGAN_x2plus.onnx")
