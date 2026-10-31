# Drishti Perception ML Pipeline & Model Training Guide

This directory contains the complete offline training, conversion, and evaluation pipeline for Drishti's rebuilt **Geometry-First + 12-Class Whitelist Perception Architecture**.

---

## 1. Core Architecture Overview

Blind users require trustworthy, actionable physical hazard alerts without annoying distractions or hallucinated object names:
1. **Layer 1 — 3D Geometry**: Fits a ground plane using device IMU pitch + camera elevation (1.35m) + monocular depth. Protrusions inside the corridor ($\pm 15^\circ$, $0.5 - 4.0\text{m}$) are immediately treated as hazards regardless of object class. Drop-offs (stairs down, open manholes) and head-height obstacles are detected by spatial depth discontinuities.
2. **Layer 2 — Strict 12-Class Whitelist**: Only 12 real-world outdoor obstacle categories are whitelisted (`stairs_down`, `stairs_up`, `pothole_open_trench`, `curb_step`, `overhanging_branch`, `protruding_signboard`, `pedestrian_moving`, `bicycle_escooter`, `motorcycle_rickshaw`, `car_van_truck`, `animal_dog_cattle`, `pole_bollard`).
   * **Hard Blacklist**: All indoor furniture and household objects (laptop, desk, table, chair, TV, cup, bottle, etc.) are strictly blacklisted.
   * **Generic Fallback**: If an obstacle is detected geometrically but classifier confidence is below threshold, Drishti announces `"Obstacle ahead, 12 o'clock, 1.5 meters"`. It **never** guesses a name.
3. **Layer 3 — Multi-Frame Temporal Confirmation & Voting**:
   * Critical hazards (drop-offs, fast approaching vehicles) require 2 out of 3 consecutive frames.
   * Static obstacles require 3 out of 5 frames with tracker ID persistence.
   * Tracklet labels use 10-frame majority voting.
   * Cooldown: 4 seconds per track ID unless danger escalates.
   * "Path clear" is never spoken.

---

## 2. Directory Structure

```text
ml_pipeline/
├── prepare_dataset.py     # Parses IDD, Mapillary, and custom datasets into 12 classes + hard-negative background
├── train_detector.py      # PyTorch QAT training with Indian street augmentations (pitch tilt, sway, motion blur)
├── train_segmentation.py  # Lightweight walkable ground surface segmentation training (PIDNet-S / DeepLabV3)
├── export_litert.py       # Converts models to LiteRT INT8 (.tflite) with <2% mAP drop verification
├── evaluate.py            # Benchmarking script checking critical recall (>=95%) and false alarms (<0.15/min)
└── README.md              # Pipeline documentation
```

---

## 3. Step-by-Step Training Workflow

### Step 1: Dataset Preparation & Location-Based Splitting
```bash
python prepare_dataset.py --output-dir ./dataset_processed
```
- Maps categories from public datasets:
  * **India Driving Dataset (IDD)**: Vehicles, auto-rickshaws, pedestrians, cattle, two-wheelers.
  * **Mapillary Vistas / Cityscapes**: Curbs, poles, traffic signs, ground surfaces.
  * **Potholes / Stairs Open Datasets**: Ground drop-offs and open trenches.
  * **Hard Negatives Set**: Indoor furniture, speed bumps, shadows, wet reflections labeled with empty annotation files.
- Splits into `train`, `val`, and `test` **strictly by location** (hashes street IDs so no identical street appears in both train and test).

### Step 2: Training the Whitelist Detector with Street Augmentations
```bash
python train_detector.py --data ./dataset_processed/drishti_data.yaml --model yolo11n.pt --epochs 100 --batch 32
```
Augmentations explicitly simulate real-world mobile camera motion:
- Camera pitch tilt ($\pm 30^\circ$)
- Handheld walking sway and motion blur
- Glare, direct sunlight, and evening street light variations

### Step 3: Walkable Ground Semantic Segmentation
```bash
python train_segmentation.py --data ./segmentation_data --epochs 40 --batch 16 --imgsz 256
```
Trains a 5-class segmenter (`walkable_ground`, `non_walkable`, `vehicle`, `pedestrian`, `sky`) to segment safe walking corridors.

### Step 4: LiteRT INT8 Quantization & Export
```bash
python export_litert.py --weights ./runs/train_assistive/drishti_12class_qat/weights/best.pt --imgsz 416
```
Produces an INT8-quantized `.tflite` model optimized for mobile NPU/GPU/CPU delegates. Copy the resulting file to:
`app/src/main/assets/models/yolov8n_float16.tflite`

### Step 5: Benchmark Evaluation
```bash
python evaluate.py --predictions ./test_predictions.json --ground-truth ./test_ground_truth.json
```
Enforces benchmark criteria:
- **Recall $\ge 95\%$** on critical hazards (`stairs_down`, `pothole`, `overhanging_branch`, `approaching vehicle`) at $1.5 - 3.5\text{ m}$.
- **False Alarm Rate $< 0.15$** per walking minute on clear paths.
- **100% rejection** of indoor furniture terms.

