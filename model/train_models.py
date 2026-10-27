"""
Smart Glove Sign Language Gesture Classification Training & Export Pipeline

Trains multiple ML models on the 8-channel glove dataset:
1. Deep Neural Network (Keras / TensorFlow) with embedded Normalization layer
2. TensorFlow Lite (.tflite) model optimized for on-device mobile inference (Android)
3. Traditional Random Forest (.joblib)
4. Support Vector Machine (.joblib)
"""

import os
import json
import shutil
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.svm import SVC
from sklearn.metrics import accuracy_score
import joblib

LABEL_MAP = {
    1: "Victory",
    2: "Come here",
    3: "Okay",
    4: "Stop",
    5: "You",
    6: "Hope",
    7: "Failure",
    8: "Really",
    9: "Quote",
    10: "No",
    11: "I Love You",
    12: "Live Long",
    13: "That's It",
    14: "Solidarity",
    15: "Rock On",
    16: "Good Bye",
    17: "What is wrong",
    18: "Why",
    19: "Restroom",
    20: "Gesture 20",
    21: "Gesture 21",
    22: "Gesture 22",
    23: "Gesture 23",
    24: "Gesture 24",
    25: "Gesture 25",
    26: "Gesture 26"
}

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    data_path = os.path.join(script_dir, "dataset.csv")

    df = pd.read_csv(data_path)
    features = ['THUMB', 'INDEX', 'MIDDLE', 'RING', 'LITTLE', 'G1', 'G2', 'G3']
    X = df[features].values.astype(np.float32)
    y_raw = df['LABEL'].values.astype(np.int32)
    y = y_raw - 1  # 0-indexed for 26 classes

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.15, random_state=42, stratify=y
    )
    print(f"Dataset split: {X_train.shape[0]} train, {X_test.shape[0]} test samples.")

    # 1. Keras Neural Network with embedded Normalization
    print("\n--- Training Keras Neural Network ---")
    inputs = tf.keras.Input(shape=(8,), name="sensor_inputs")
    norm_layer = tf.keras.layers.Normalization(name="normalization")
    norm_layer.adapt(X_train)

    x = norm_layer(inputs)
    x = tf.keras.layers.Dense(128, activation="relu", name="dense_1")(x)
    x = tf.keras.layers.BatchNormalization(name="bn_1")(x)
    x = tf.keras.layers.Dropout(0.2, name="dropout_1")(x)
    x = tf.keras.layers.Dense(64, activation="relu", name="dense_2")(x)
    x = tf.keras.layers.BatchNormalization(name="bn_2")(x)
    outputs = tf.keras.layers.Dense(26, activation="softmax", name="probabilities")(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="glove_gesture_model")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    model.fit(
        X_train, y_train,
        epochs=25,
        batch_size=32,
        validation_split=0.1,
        verbose=1
    )

    loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
    print(f"Keras Model Test Accuracy: {test_acc*100:.2f}%")

    keras_path = os.path.join(script_dir, "glove_model.keras")
    h5_path = os.path.join(script_dir, "glove_model.h5")
    model.save(keras_path)
    model.save(h5_path)
    print(f"Saved: {keras_path} and {h5_path}")

    # 2. Convert to TensorFlow Lite
    print("\n--- Converting to TensorFlow Lite ---")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    tflite_path = os.path.join(script_dir, "glove_model.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    print(f"Saved TFLite Model: {tflite_path} ({len(tflite_model)} bytes)")

    # 3. Traditional ML models
    print("\n--- Training Random Forest & SVM ---")
    rf = RandomForestClassifier(n_estimators=100, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)
    rf_acc = accuracy_score(y_test, rf.predict(X_test))
    print(f"Random Forest Test Accuracy: {rf_acc*100:.2f}%")
    joblib.dump(rf, os.path.join(script_dir, "glove_rf_model.joblib"))

    svm = SVC(kernel="rbf", random_state=42)
    svm.fit(X_train, y_train)
    svm_acc = accuracy_score(y_test, svm.predict(X_test))
    print(f"SVM Test Accuracy: {svm_acc*100:.2f}%")
    joblib.dump(svm, os.path.join(script_dir, "glove_svm_model.joblib"))

    # 4. Copy to mobile-app assets
    assets_dir = os.path.join(script_dir, "..", "mobile-app", "app", "src", "main", "assets")
    if os.path.exists(assets_dir):
        shutil.copyfile(tflite_path, os.path.join(assets_dir, "glove_model.tflite"))
        shutil.copyfile(os.path.join(script_dir, "labels.txt"), os.path.join(assets_dir, "labels.txt"))
        print(f"Synchronized model to mobile app assets: {assets_dir}")

    print("\nAll models trained and exported successfully!")

if __name__ == "__main__":
    main()
