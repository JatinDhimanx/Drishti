"""
Drishti ML Pipeline - Dataset Preparation Script
=================================================
Prepares training, validation, and test datasets for the 12-class assistive obstacle detector
and walkable surface segmentation.

Strict 12 Assistive Classes:
0: stairs_down
1: stairs_up
2: pothole_open_trench
3: curb_step
4: overhanging_branch
5: protruding_signboard
6: pedestrian_moving
7: bicycle_escooter
8: motorcycle_rickshaw
9: car_van_truck
10: animal_dog_cattle
11: pole_bollard

Hard Negative Classes (Explicitly mapped to Background / empty annotations):
- Indoor furniture: laptop, desk, table, chair, couch, tv, bed, book, keyboard, mouse
- Street artifacts: shadows, wet patches, painted zebra crossings, manhole covers, speed bumps
"""

import os
import sys
import json
import shutil
import hashlib
import argparse
from pathlib import Path
from typing import Dict, List, Tuple

ASSISTIVE_CLASSES = [
    "stairs_down",
    "stairs_up",
    "pothole_open_trench",
    "curb_step",
    "overhanging_branch",
    "protruding_signboard",
    "pedestrian_moving",
    "bicycle_escooter",
    "motorcycle_rickshaw",
    "car_van_truck",
    "animal_dog_cattle",
    "pole_bollard"
]

CLASS_TO_ID = {name: idx for idx, name in enumerate(ASSISTIVE_CLASSES)}

HARD_NEGATIVE_INDOOR_LABELS = {
    "chair", "dining table", "table", "desk", "sofa", "bed", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
    "toothbrush", "bottle", "cup", "fork", "knife", "spoon", "bowl"
}

def split_by_location(image_id: str, location_tag: str) -> str:
    """
    Splits data strictly by geographic location / street identifier to prevent
    data leakage between train and test distributions.
    """
    h = int(hashlib.md5(location_tag.encode("utf-8")).hexdigest(), 16)
    bucket = h % 100
    if bucket < 70:
        return "train"
    elif bucket < 85:
        return "val"
    else:
        return "test"

def process_coco_annotations(
    coco_json_path: Path,
    images_dir: Path,
    output_dir: Path,
    source_name: str,
    class_mapping: Dict[str, str]
):
    """
    Parses a COCO-format dataset, maps whitelisted classes to Drishti's 12 classes,
    and discards/marks indoor objects as negative background images.
    """
    print(f"Processing COCO annotations from {coco_json_path} [{source_name}]...")
    with open(coco_json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    cat_id_to_name = {c["id"]: c["name"] for c in data.get("categories", [])}
    images_info = {img["id"]: img for img in data.get("images", [])}

    # Group annotations by image_id
    img_to_anns: Dict[int, List] = {}
    for ann in data.get("annotations", []):
        img_id = ann["image_id"]
        img_to_anns.setdefault(img_id, []).append(ann)

    os.makedirs(output_dir / "images" / "train", exist_ok=True)
    os.makedirs(output_dir / "images" / "val", exist_ok=True)
    os.makedirs(output_dir / "images" / "test", exist_ok=True)
    os.makedirs(output_dir / "labels" / "train", exist_ok=True)
    os.makedirs(output_dir / "labels" / "val", exist_ok=True)
    os.makedirs(output_dir / "labels" / "test", exist_ok=True)

    counts = {"train": 0, "val": 0, "test": 0, "negatives": 0}

    for img_id, img_info in images_info.items():
        file_name = img_info["file_name"]
        w = float(img_info["width"])
        h = float(img_info["height"])
        if w <= 0 or h <= 0:
            continue

        src_img = images_dir / file_name
        if not src_img.exists():
            continue

        # Location tag is derived from folder or prefix or source
        location_tag = f"{source_name}_{file_name.split('_')[0]}"
        split = split_by_location(file_name, location_tag)

        yolo_lines = []
        has_indoor_negative = False

        for ann in img_to_anns.get(img_id, []):
            cat_name = cat_id_to_name.get(ann["category_id"], "")
            if cat_name.lower() in HARD_NEGATIVE_INDOOR_LABELS:
                has_indoor_negative = True
                continue

            mapped_class = class_mapping.get(cat_name)
            if mapped_class and mapped_class in CLASS_TO_ID:
                cid = CLASS_TO_ID[mapped_class]
                bbox = ann["bbox"] # [x, y, width, height]
                xc = (bbox[0] + bbox[2] / 2.0) / w
                yc = (bbox[1] + bbox[3] / 2.0) / h
                bw = bbox[2] / w
                bh = bbox[3] / h
                xc = min(max(xc, 0.0), 1.0)
                yc = min(max(yc, 0.0), 1.0)
                bw = min(max(bw, 0.0), 1.0)
                bh = min(max(bh, 0.0), 1.0)
                yolo_lines.append(f"{cid} {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}")

        # If it contains hard negative indoor furniture and NO whitelist objects,
        # it is saved with an EMPTY label file (pure negative image for background suppression).
        target_img_name = f"{source_name}_{file_name}"
        dst_img = output_dir / "images" / split / target_img_name
        dst_lbl = output_dir / "labels" / split / (Path(target_img_name).stem + ".txt")

        shutil.copy2(src_img, dst_img)
        with open(dst_lbl, "w", encoding="utf-8") as lf:
            if yolo_lines:
                lf.write("\n".join(yolo_lines) + "\n")
            else:
                counts["negatives"] += 1

        counts[split] += 1

    print(f"Dataset preparation complete for {source_name}: {counts}")

def generate_data_yaml(output_dir: Path):
    yaml_content = f"""# Drishti Assistive Perception Whitelist Dataset
path: {output_dir.resolve().as_posix()}
train: images/train
val: images/val
test: images/test

nc: {len(ASSISTIVE_CLASSES)}
names: {ASSISTIVE_CLASSES}
"""
    with open(output_dir / "drishti_data.yaml", "w", encoding="utf-8") as f:
        f.write(yaml_content)
    print(f"Generated YAML configuration at {output_dir / 'drishti_data.yaml'}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Drishti Dataset Preparation")
    parser.add_argument("--output-dir", type=str, default="./dataset_processed", help="Output directory")
    args = parser.parse_args()
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    generate_data_yaml(out_dir)
    print("Run with source datasets (IDD, Mapillary, Potholes, Stairs, HardNegatives).")

