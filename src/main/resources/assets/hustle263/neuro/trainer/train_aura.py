"""Train a small recurrent rotation model from the client's recorded CSV files."""

import argparse
import csv
import json
import math
from pathlib import Path
from statistics import median

import torch
from torch import nn

from common import HEADER


def norm(value):
    return math.asinh(value) / 3.0


def motion_profile(path):
    if path is None or not path.is_file():
        return []
    profile = [[0, 0., 0.]]
    window = []

    def append_window():
        if len(window) < 4:
            return
        start, yaw, pitch = window[0]
        end, end_yaw, end_pitch = window[-1]
        offset = profile[-1][0]
        for time, x, y in window[1:-1]:
            fraction = (time - start) / (end - start)
            residual = [x - (yaw + (end_yaw - yaw) * fraction),
                        y - (pitch + (end_pitch - pitch) * fraction)]
            profile.append([offset + time - start, *(max(-0.35, min(0.35, value)) for value in residual)])
        profile.append([offset + end - start, 0., 0.])

    with path.open(encoding="utf-8", newline="") as source:
        reader = csv.reader(source)
        if next(reader, None) != ["time_ns", "yaw", "pitch"]:
            return []
        for row in reader:
            try:
                time, x, y = int(row[0]), float(row[1]), float(row[2])
            except (ValueError, IndexError):
                continue
            if time < 0 or not math.isfinite(x) or not math.isfinite(y):
                continue
            if window and (time <= window[-1][0] or time - window[0][0] > 80_000_000 or len(window) >= 64):
                append_window()
                window.clear()
                if len(profile) >= 512:
                    break
            window.append((time, x, y))
        append_window()
    return profile if any(x or y for _, x, y in profile) else []


def read_rows(dataset):
    with dataset.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != HEADER.split(","):
            raise SystemExit(f"Несовместимый CSV: {dataset.name}")
        yield from reader


def aim_point(dataset):
    sides, heights = [], []
    for row in read_rows(dataset):
        if any(row[key] != "1" for key in ("has", "clean", "on", "atk")):
            continue
        try:
            center = tuple(float(row[key]) for key in ("rx", "ry", "rz"))
            width, height = float(row["bw"]), float(row["bh"])
            yaw, pitch = (math.radians(float(row[key])) for key in ("yaw", "pitch"))
            if not all(map(math.isfinite, (*center, width, height, yaw, pitch))) or min(width, height) <= 0:
                continue
            direction = (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
            right_x, right_z, half_width = horizontal_axis(center[0], center[2], width)
            # Find the segment of the recorded view ray inside the hitbox.
            near, far = 0., 5.
            for component, midpoint, half in zip(direction, center, (width / 2, height / 2, width / 2)):
                if abs(component) < 1e-8:
                    if abs(midpoint) > half:
                        far = -1.
                    continue
                a, b = (midpoint - half) / component, (midpoint + half) / component
                near, far = max(near, min(a, b)), min(far, max(a, b))
            if near > far or far <= 0:
                continue
            distance = (near + far) / 2
            x, y, z = (component * distance - midpoint for component, midpoint in zip(direction, center))
            sides.append((x * right_x + z * right_z) / half_width)
            heights.append(y / (height / 2))
        except (ValueError, TypeError, OverflowError):
            continue
    if not sides:
        raise SystemExit(f"{dataset.name}: нет записанных ударов с прицелом внутри хитбокса")
    aim = [max(-0.9, min(0.9, median(values))) for values in (sides, heights)]
    return aim, len(sides)


def horizontal_axis(x, z, width):
    flat = math.hypot(x, z)
    right_x, right_z = (z / flat, -x / flat) if flat > 1e-8 else (1., 0.)
    return right_x, right_z, width / (2 * max(abs(right_x), abs(right_z)))


def samples(dataset, aim):
    previous = None
    older = None
    since_attack, frozen = 20, 0
    for row in read_rows(dataset):
        try:
            if any(row[key] != "1" for key in ("has", "clean", "vis")):
                previous = older = None
                continue
            yaw, pitch = float(row["yaw"]), float(row["pitch"])
            rx, ry, rz = (float(row[k]) for k in ("rx", "ry", "rz"))
            box_width, box_height = float(row["bw"]), float(row["bh"])
            right_x, right_z, half_width = horizontal_axis(rx, rz, box_width)
            rx += right_x * half_width * aim[0]
            ry += box_height * aim[1] / 2
            rz += right_z * half_width * aim[0]
            target_yaw = math.degrees(math.atan2(rz, rx)) - 90
            target_pitch = -math.degrees(math.atan2(ry, max(math.hypot(rx, rz), 0.05)))
            error_yaw = (target_yaw - yaw + 180) % 360 - 180
            error_pitch = target_pitch - pitch
            dyaw, dpitch = float(row["dyaw"]), float(row["dpitch"])
            dist = float(row["dist"])
            if not all(map(math.isfinite, (dist, yaw, pitch, rx, ry, rz, box_width, box_height, dyaw, dpitch))):
                previous = older = None
                continue
            current = (row["tid"], int(row["t"]), error_yaw, error_pitch, target_yaw, target_pitch, dyaw, dpitch)
            moving = abs(dyaw) + abs(dpitch) >= 0.0001
            if previous is None or previous[0] != current[0] or current[1] != previous[1] + 1:
                previous, older = current, None
                since_attack, frozen = (0 if row["atk"] == "1" else 20), 0
                continue
            _, _, last_ey, last_ep, last_ty, last_tp, last_dy, last_dp = previous
            _, _, older_ey, older_ep, _, _, older_dy, older_dp = older or (*previous[:6], 0., 0.)
            width = max(math.degrees(math.atan2(box_width / 2, max(math.hypot(rx, rz), 0.05))), 0.5)
            height = max(math.degrees(math.atan2(box_height / 2, max(math.hypot(rx, rz), 0.05))), 0.5)
            features = [norm(last_ey), norm(last_ep), norm((last_ey - older_ey + 180) % 360 - 180), norm(last_ep - older_ep),
                        norm((target_yaw - last_ty + 180) % 360 - 180), norm(target_pitch - last_tp), norm(last_dy), norm(last_dp),
                        norm(older_dy), norm(older_dp), norm(last_ey / width), norm(last_ep / height),
                        math.log(max(dist, 0.05) + 0.5) / 2,
                        math.log(width) / 3, float(abs(last_ey) <= width and abs(last_ep) <= height),
                        since_attack / 20, frozen / 12]
            yield features, [float(moving), math.asinh(dyaw), math.asinh(dpitch)]
            older, previous = previous, current
            since_attack = 0 if row["atk"] == "1" else min(20, since_attack + 1)
            frozen = 0 if moving else min(12, frozen + 1)
        except (ValueError, TypeError, KeyError, OverflowError):
            previous = older = None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--motion", type=Path)
    args = parser.parse_args()
    print(f"Обучение на {args.dataset.name}", flush=True)
    aim, hits = aim_point(args.dataset)
    print(f"Точка наведения: {hits} ударов, горизонталь={aim[0]:.2f}, высота={(aim[1] + 1) / 2:.0%}", flush=True)
    motion = motion_profile(args.motion)
    print(f"Точек микродвижений: {len(motion)}", flush=True)
    torch.set_num_threads(2)
    hidden = 32
    gru = nn.GRUCell(17, hidden)
    head = nn.Linear(hidden, 7)
    optimizer = torch.optim.Adam(list(gru.parameters()) + list(head.parameters()), lr=0.002)
    examples = list(samples(args.dataset, aim))
    count = len(examples)
    if count == 0 or not any(target[0] for _, target in examples):
        raise SystemExit("Нет последовательных тиков с движением прицела и видимой целью")
    features, targets = zip(*examples)
    features = torch.tensor(features, dtype=torch.float32)
    targets = torch.tensor(targets, dtype=torch.float32)
    # Balance smaller vertical adjustments against the typically larger yaw turns.
    axis_scale = targets[targets[:, 0] > .5, 1:3].square().mean(dim=0).sqrt().clamp_min(.25)
    for epoch in range(args.epochs):
        loss_sum = 0.
        for indices in torch.randperm(count).split(256):
            x, y = features[indices], targets[indices]
            output = head(gru(x, torch.zeros(len(x), hidden)))
            loss = nn.functional.binary_cross_entropy_with_logits(-output[:, 0], 1 - y[:, 0])
            moving = y[:, 0] > 0.5
            if moving.any():
                loss = loss + nn.functional.smooth_l1_loss(
                    output[moving, 2:4] / axis_scale, y[moving, 1:3] / axis_scale)
            optimizer.zero_grad()
            loss.backward()
            nn.utils.clip_grad_norm_(list(gru.parameters()) + list(head.parameters()), 5.)
            optimizer.step()
            loss_sum += float(loss.detach()) * len(indices)
        print(f"Эпоха {epoch + 1}/{args.epochs}, loss={loss_sum / count:.4f}, примеров={count}", flush=True)

    def array(tensor):
        return tensor.detach().tolist()

    # Java forward() uses the same r,z,n gate order as PyTorch's GRUCell.
    result = {"features": 17, "units": "deg", "hidden": hidden, "mix": 1,
              "freezeCut": 12, "limits": [180, 90], "error": [0.], "aim": aim, "motion": motion,
              "gru": {"wi": array(gru.weight_ih), "wh": array(gru.weight_hh),
                      "bi": array(gru.bias_ih), "bh": array(gru.bias_hh)},
              "out": {"w": array(head.weight), "b": array(head.bias)}}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as target:
        json.dump(result, target, allow_nan=False)
    print(f"Модель сохранена: {args.out}", flush=True)


if __name__ == "__main__":
    main()
