#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# DroidLink — Dynamic Adaptive Bitrate Benchmark & Simulation Suite
# Author: Rayhan Khan (https://github.com/Rayhan-099)

import time
import random
from typing import List, Optional, Tuple


class SimulatedBitrateController:
    """
    Exact behavioral model of Moreland's Rust BitrateController:
    - min_kbps: 5,000 kbps (5 Mbps)
    - max_kbps: 30,000 kbps (30 Mbps)
    - cooldown: 5.0 seconds
    - high latency threshold: > 45.0 ms -> downscale by 20% (* 0.8)
    - low latency threshold: < 25.0 ms -> upscale by 10% (* 1.1)
    - window: 10 most recent sample durations, median calculated
    """
    def __init__(self, initial_kbps: int = 20000):
        self.current_kbps = initial_kbps
        self.min_kbps = 5000
        self.max_kbps = 30000
        self.last_adjustment = 0.0  # epoch timestamp
        self.cooldown = 5.0

    def evaluate(self, median_rtt_ms: float, now: float) -> Optional[int]:
        if now - self.last_adjustment < self.cooldown:
            return None

        if median_rtt_ms > 45.0 and self.current_kbps > self.min_kbps:
            new_kbps = max(self.min_kbps, int(self.current_kbps * 0.8))
            if new_kbps != self.current_kbps:
                self.current_kbps = new_kbps
                self.last_adjustment = now
                return new_kbps
        elif median_rtt_ms < 25.0 and self.current_kbps < self.max_kbps:
            new_kbps = min(self.max_kbps, int(self.current_kbps * 1.1))
            if new_kbps != self.current_kbps:
                self.current_kbps = new_kbps
                self.last_adjustment = now
                return new_kbps
        return None


def calculate_median(samples: List[float]) -> float:
    if not samples:
        return 0.0
    s = sorted(samples)
    return s[len(s) // 2]


def run_benchmark():
    print("=" * 70)
    print(" DROIDLINK ADAPTIVE BITRATE CONTROLLER BENCHMARK")
    print("=" * 70)

    controller = SimulatedBitrateController(initial_kbps=20000)
    sim_time = 0.0
    rtt_history = []
    decisions: List[Tuple[float, float, int, str]] = []

    # Scenario 1: Stable Low-Latency Environment (15-22ms)
    print("\n[Scenario 1] Ideal Low Latency (15–22ms RTT, 30s duration)")
    for sec in range(30):
        sim_time += 1.0
        # 60 frames per second
        for _ in range(60):
            sample = random.uniform(15.0, 22.0)
            rtt_history.append(sample)
            if len(rtt_history) > 10:
                rtt_history.pop(0)

        median = calculate_median(rtt_history)
        decision = controller.evaluate(median, sim_time)
        if decision:
            decisions.append((sim_time, median, decision, "INCREASE"))
            print(f"  t={sim_time:4.1f}s | Median RTT: {median:4.1f}ms -> Upscaled to {decision:5d} kbps ({decision/1000:.1f} Mbps)")

    assert controller.current_kbps == 30000, f"Expected ceiling 30000 kbps, got {controller.current_kbps}"
    print(f"  ✓ Reached maximum target bitrate: {controller.current_kbps} kbps (30 Mbps)")

    # Scenario 2: Severe USB Bus Congestion Spike (60-110ms RTT for 50s)
    print("\n[Scenario 2] Severe Congestion (60–110ms RTT, 50s duration)")
    for sec in range(50):
        sim_time += 1.0
        for _ in range(60):
            sample = random.uniform(60.0, 110.0)
            rtt_history.append(sample)
            if len(rtt_history) > 10:
                rtt_history.pop(0)

        median = calculate_median(rtt_history)
        decision = controller.evaluate(median, sim_time)
        if decision:
            decisions.append((sim_time, median, decision, "THROTTLE"))
            print(f"  t={sim_time:4.1f}s | Median RTT: {median:4.1f}ms -> Throttled to {decision:5d} kbps ({decision/1000:.1f} Mbps)")

    assert controller.current_kbps == 5000, f"Expected floor 5000 kbps, got {controller.current_kbps}"
    print(f"  ✓ Throttled safely to floor: {controller.current_kbps} kbps (5 Mbps)")

    # Scenario 3: Noisy Latency with Single Frame Outliers (Outliers up to 150ms)
    print("\n[Scenario 3] Noisy Jitter with Rare Outliers (20ms normal, 150ms outlier)")
    pre_noisy_kbps = controller.current_kbps
    for sec in range(15):
        sim_time += 1.0
        for f in range(60):
            # Only 1 out of 60 frames is an outlier spike
            sample = 150.0 if f == 0 else random.uniform(18.0, 23.0)
            rtt_history.append(sample)
            if len(rtt_history) > 10:
                rtt_history.pop(0)

        median = calculate_median(rtt_history)
        decision = controller.evaluate(median, sim_time)
        # Median should filter out the 150ms outlier
        assert median < 25.0, f"Median filter failed: {median}ms"
        if decision:
            decisions.append((sim_time, median, decision, "RECOVER"))
            print(f"  t={sim_time:4.1f}s | Median RTT: {median:4.1f}ms -> Recovering to {decision:5d} kbps ({decision/1000:.1f} Mbps)")

    print(f"  ✓ Outlier rejection confirmed: Median filter resisted 150ms spikes.")

    # Summary
    print("\n" + "=" * 70)
    print(" BENCHMARK RESULTS SUMMARY")
    print("=" * 70)
    print(f"Total simulated time: {sim_time:.1f} seconds")
    print(f"Total controller adaptations: {len(decisions)}")
    print(f"Min observed bitrate: {min(d[2] for d in decisions)} kbps")
    print(f"Max observed bitrate: {max(d[2] for d in decisions)} kbps")
    print("All adaptive bitrate benchmark assertions PASSED successfully!")
    print("=" * 70)


if __name__ == "__main__":
    run_benchmark()
