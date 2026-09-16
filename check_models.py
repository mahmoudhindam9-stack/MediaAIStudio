import onnx
try:
    m = onnx.load("app/src/main/assets/models/inpainting_lama_2025jan.onnx")
    print("LaMa Inputs:")
    for i in m.graph.input:
        print(" ", i.name, [d.dim_value for d in i.type.tensor_type.shape.dim], "type:", i.type.tensor_type.elem_type)
    print("LaMa Outputs:")
    for o in m.graph.output:
        print(" ", o.name, [d.dim_value for d in o.type.tensor_type.shape.dim], "type:", o.type.tensor_type.elem_type)
except Exception as e:
    print("LaMa err:", e)

import tensorflow as tf
try:
    interp = tf.lite.Interpreter(model_path="app/src/main/assets/models/cpga_fp16.tflite")
    print("\nCPGA Inputs:")
    for det in interp.get_input_details():
        print(" ", det['name'], det['shape'], det['dtype'])
    print("CPGA Outputs:")
    for det in interp.get_output_details():
        print(" ", det['name'], det['shape'], det['dtype'])
except Exception as e:
    print("CPGA err:", e)
