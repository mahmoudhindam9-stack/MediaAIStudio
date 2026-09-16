import tensorflow as tf
import os

# Create a small valid model that "enhances" an image (multiplies by 1.5, clips)
inputs = tf.keras.Input(shape=(256, 256, 3), name='input')
x = tf.keras.layers.Conv2D(16, (3,3), padding='same', activation='relu')(inputs)
x = tf.keras.layers.Conv2D(16, (3,3), padding='same', activation='relu')(x)
x = tf.keras.layers.Conv2D(3, (3,3), padding='same')(x)
# Add residual connection and scale
outputs = tf.clip_by_value(inputs + x * 0.5, 0.0, 255.0)

model = tf.keras.Model(inputs=inputs, outputs=outputs)

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

with open("app/src/main/assets/models/cpga_fp16.tflite", "wb") as f:
    f.write(tflite_model)

print(f"Generated TFLite model, size: {os.path.getsize('app/src/main/assets/models/cpga_fp16.tflite')}")
