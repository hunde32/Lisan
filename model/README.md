# 🧠 Smart Glove Gesture Recognition Models

This directory contains the trained machine learning and deep learning models for gesture classification, trained on the 8-channel smart glove sensor dataset (`dataset.csv`).

## 📁 Available Model Formats

| File | Type / Framework | Size | Test Accuracy | Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **`glove_model.tflite`** | TensorFlow Lite (Optimized) | ~18 KB | **99.50%** | On-device mobile inference (Android/iOS/Embedded) |
| **`glove_model.keras`** | Keras Deep Neural Network | ~182 KB | **99.55%** | Native Keras 3 format for fine-tuning & evaluation |
| **`glove_model.h5`** | Legacy HDF5 Keras Model | ~184 KB | **99.55%** | Standard HDF5 model format |
| **`glove_rf_model.joblib`** | Scikit-Learn Random Forest | ~8.2 MB | **99.80%** | Python server/desktop inference |
| **`glove_svm_model.joblib`** | Scikit-Learn SVM (RBF) | ~4.8 MB | **25.86%** | Baseline comparison |
| **`labels.txt`** | Plain Text Labels | 224 B | - | Ordered line-by-line label mapping (0-25) |
| **`labels.json`** | JSON Label Mapping | 503 B | - | Key-value dictionary mapping (1-26) |
| **`dataset.csv`** | CSV Dataset | 525 KB | - | 13,296 samples with 8 features + labels |

---

## 📥 Model Input Features

The models accept an 8-dimensional float vector corresponding to:

1. `THUMB`: Flex sensor reading for thumb (ADC 0-1023)
2. `INDEX`: Flex sensor reading for index finger (ADC 0-1023)
3. `MIDDLE`: Flex sensor reading for middle finger (ADC 0-1023)
4. `RING`: Flex sensor reading for ring finger (ADC 0-1023)
5. `LITTLE`: Flex sensor reading for little finger (ADC 0-1023)
6. `G1`: MPU-6050 X-axis motion / gravity vector
7. `G2`: MPU-6050 Y-axis motion / gravity vector
8. `G3`: MPU-6050 Z-axis motion / gravity vector

> **Built-in Normalization**: The TensorFlow Lite model (`glove_model.tflite`) has an **integrated Keras Normalization layer** trained on the dataset statistics. You can pass raw unscaled sensor values directly to the model.

---

## 🏷️ Output Gesture Classes (26 Signs)

| Index (0-based) | Label ID (1-based) | Gesture Name | Amharic Meaning |
| :---: | :---: | :--- | :--- |
| 0 | 1 | Victory | ድል |
| 1 | 2 | Come here | ና ወደዚህ |
| 2 | 3 | Okay | እሺ |
| 3 | 4 | Stop | ቁም |
| 4 | 5 | You | አንተ / አንቺ |
| 5 | 6 | Hope | ተስፋ |
| 6 | 7 | Failure | ውድቀት |
| 7 | 8 | Really | በእውነት |
| 8 | 9 | Quote | ጥቅስ |
| 9 | 10 | No | አይ |
| 10 | 11 | I Love You | እወድሃለሁ |
| 11 | 12 | Live Long | ረጅም እድሜ |
| 12 | 13 | That's It | ያ ብቻ ነው |
| 13 | 14 | Solidarity | አንድነት |
| 14 | 15 | Rock On | በርታ |
| 15 | 16 | Good Bye | ደህና ሁን |
| 16 | 17 | What is wrong | ምን ተፈጠረ? |
| 17 | 18 | Why | ለምን? |
| 18 | 19 | Restroom | መጸዳጃ ቤት |
| 19 - 25 | 20 - 26 | Gesture 20 - 26 | ማሳያ ምልክት 20 - 26 |

---

## 🚀 Quick Usage in Python

```python
import numpy as np
import tensorflow as tf

# Load TFLite model
interpreter = tf.lite.Interpreter(model_path="glove_model.tflite")
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# Raw sensor input: [THUMB, INDEX, MIDDLE, RING, LITTLE, G1, G2, G3]
raw_sensors = np.array([[666, 732, 700, 965, 969, 15120, -736, 6704]], dtype=np.float32)

interpreter.set_tensor(input_details[0]['index'], raw_sensors)
interpreter.invoke()
output = interpreter.get_tensor(output_details[0]['index'])

predicted_class = np.argmax(output[0])
confidence = output[0][predicted_class]

with open("labels.txt") as f:
    labels = [line.strip() for line in f]

print(f"Predicted Gesture: {labels[predicted_class]} ({confidence*100:.1f}%)")
```
