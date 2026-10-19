#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# DroidLink — Dynamic Bitrate Controller Automated Unit Test Suite

import unittest
import time

class BitrateController:
    def __init__(self, initial_kbps=20000, min_kbps=5000, max_kbps=30000, cooldown_sec=5.0):
        self.current_kbps = initial_kbps
        self.min_kbps = min_kbps
        self.max_kbps = max_kbps
        self.last_adjustment = 0.0
        self.cooldown_sec = cooldown_sec

    def evaluate(self, median_rtt_ms: float, now: float = None) -> int:
        if now is None:
            now = time.time()
        if (now - self.last_adjustment) < self.cooldown_sec:
            return None

        if median_rtt_ms > 45.0 and self.current_kbps > self.min_kbps:
            new_kbps = max(int(self.current_kbps * 0.8), self.min_kbps)
            if new_kbps != self.current_kbps:
                self.current_kbps = new_kbps
                self.last_adjustment = now
                return new_kbps
        elif median_rtt_ms < 25.0 and self.current_kbps < self.max_kbps:
            new_kbps = min(int(self.current_kbps * 1.1), self.max_kbps)
            if new_kbps != self.current_kbps:
                self.current_kbps = new_kbps
                self.last_adjustment = now
                return new_kbps
        return None


class TestBitrateController(unittest.TestCase):

    def test_step_down_on_high_latency(self):
        ctrl = BitrateController(initial_kbps=20000)
        t0 = 1000.0
        # High latency (> 45ms) should reduce bitrate by 20%
        new_rate = ctrl.evaluate(median_rtt_ms=55.0, now=t0)
        self.assertEqual(new_rate, 16000)
        self.assertEqual(ctrl.current_kbps, 16000)

    def test_cooldown_prevents_rapid_oscillation(self):
        ctrl = BitrateController(initial_kbps=20000)
        t0 = 1000.0
        ctrl.evaluate(median_rtt_ms=60.0, now=t0)
        # Attempt immediate second adjustment during cooldown (< 5s)
        blocked = ctrl.evaluate(median_rtt_ms=60.0, now=t0 + 2.0)
        self.assertIsNone(blocked)
        self.assertEqual(ctrl.current_kbps, 16000)

        # After cooldown passes (> 5s), second step-down permitted
        new_rate = ctrl.evaluate(median_rtt_ms=60.0, now=t0 + 6.0)
        self.assertEqual(new_rate, 12800)

    def test_step_up_on_low_latency(self):
        ctrl = BitrateController(initial_kbps=10000)
        t0 = 1000.0
        # Low latency (< 25ms) should gradually increase bitrate by 10%
        new_rate = ctrl.evaluate(median_rtt_ms=18.0, now=t0)
        self.assertEqual(new_rate, 11000)

    def test_clamped_to_min_and_max_bounds(self):
        # Minimum bound test
        ctrl_min = BitrateController(initial_kbps=5500, min_kbps=5000)
        t0 = 1000.0
        rate = ctrl_min.evaluate(median_rtt_ms=70.0, now=t0)
        self.assertEqual(rate, 5000)
        rate_clamped = ctrl_min.evaluate(median_rtt_ms=70.0, now=t0 + 6.0)
        self.assertIsNone(rate_clamped) # Already at min, no further reduction

        # Maximum bound test
        ctrl_max = BitrateController(initial_kbps=29000, max_kbps=30000)
        rate = ctrl_max.evaluate(median_rtt_ms=15.0, now=t0)
        self.assertEqual(rate, 30000)
        rate_clamped = ctrl_max.evaluate(median_rtt_ms=15.0, now=t0 + 6.0)
        self.assertIsNone(rate_clamped) # Already at max, no further increase


if __name__ == "__main__":
    unittest.main()
