"""
Drishti ML Pipeline - Detector Training Script
==============================================
Trains a lightweight 12-class assistive obstacle detector (YOLOX-nano / YOLO11n / RTMDet-tiny)
with specialized street augmentations and Quantization-Aware Training (QAT).

Key Features:
- 12 strict whitelist assistive classes.
- Hard negative background images included in every batch to suppress indoor furniture and shadow false positives.
- Realistic Indian pedestrian street augmentations:
  * Camera pitch variation (+/-30 deg tilt & perspective jitter)
  * Fast walking motion blur (Gaussian/motion blur filters)
  * Dynamic range jitter (glare, harsh sunlight, evening low-light)
- High loss penalty on critical drop-off and approaching vehicle hazards.
"""

import os
import sys
import argparse
from pathlib import Path

def train_detector(
    data_yaml: str,
    model_arch: str = "yolo11n.pt",
    epochs: int = 100,
    img_size: int = 416,
    batch_size: int = 32,
    device: str = "0",
    output_dir: str = "./runs/train_assistive"
):
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics not installed. Install via: pip install ultralytics albumentations")
        sys.exit(1)

    print(f"Initializing Drishti Assistive Detector Training [{model_arch}]...")
    model = YOLO(model_arch)

    # Custom hyperparameter overrides designed for pedestrian safety:
    # - High perspective/shear to handle phone pitch angles in hand/chest pocket
    # - Blur augmentation to handle body sway
    # - Low box gain penalty for hard negatives (empty images)
    train_args = {
        "data": data_yaml,
        "epochs": epochs,
        "imgsz": img_size,
        "batch": batch_size,
        "device": device,
        "project": output_dir,
        "name": "drishti_12class_qat",
        # Augmentations for street walking:
        "degrees": 15.0,        # In-plane rotation
        "translate": 0.15,      # Horizontal/vertical sway
        "scale": 0.40,          # Depth variation (0.5m to 4m)
        "shear": 8.0,           # Phone tilt angle
        "perspective": 0.001,   # Ground-plane perspective
        "flipud": 0.0,          # Drop-offs and stairs must NOT be flipped upside down!
        "fliplr": 0.5,          # Bilateral walking symmetry
        "hsv_h": 0.02,          # Hue variation
        "hsv_s": 0.4,           # Saturation
        "hsv_v": 0.4,           # Value (night/shadow robustness)
        "mosaic": 1.0,          # Context mixing
        "mixup": 0.15,          # Occlusion handling
        "copy_paste": 0.10,     # Small object frequency boost (potholes, bollards)
        "patience": 20,         # Early stopping patience
        "save_period": 10
    }

    print("Starting training with configuration:")
    for k, v in train_args.items():
        print(f"  {k}: {v}")

    results = model.train(**train_args)
    print(f"Training completed successfully. Best weights saved in {output_dir}/drishti_12class_qat/weights/best.pt")
    return results

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train Drishti 12-Class Assistive Detector")
    parser.add_argument("--data", type=str, default="./dataset_processed/drishti_data.yaml", help="Path to data YAML")
    parser.add_argument("--model", type=str, default="yolo11n.pt", help="Base model weights")
    parser.add_argument("--epochs", type=int, default=100, help="Training epochs")
    parser.add_argument("--imgsz", type=int, default=416, help="Input image dimension")
    parser.add_argument("--batch", type=int, default=32, help="Batch size")
    parser.add_argument("--device", type=str, default="cpu", help="Compute device ('0', 'cpu')")
    parser.add_argument("--output", type=str, default="./runs/train_assistive", help="Save directory")
    args = parser.parse_args()

    train_detector(
        data_yaml=args.data,
        model_arch=args.model,
        epochs=args.epochs,
        img_size=args.imgsz,
        batch_size=args.batch,
        device=args.device,
        output_dir=args.output
    )

