#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# DroidLink — Touch & Stylus Input Protocol Automated Unit Test Suite

import unittest
import struct
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "bin")))
from importlib.machinery import SourceFileLoader

# Load droidlink-input module
droidlink_input = SourceFileLoader("droidlink_input", os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "bin", "droidlink-input"))).load_module()


class TestInputProtocol(unittest.TestCase):

    def test_parse_valid_touch_packet(self):
        # Pack a valid 24-byte ACTION_DOWN finger event
        # magic=0xD1, action=1, tool=0, pointer_id=0, x=32768, y=16384, pressure=65535, tilt_x=0, tilt_y=0, buttons=1
        pkt = struct.pack(
            "<BBBBIIIhhBBBB",
            0xD1, 1, 0, 0,
            32768, 16384, 65535,
            0, 0,
            1, 0, 0, 0
        )
        self.assertEqual(len(pkt), 24)
        parsed = droidlink_input.parse_packet(pkt)
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["action"], droidlink_input.ACTION_DOWN)
        self.assertEqual(parsed["tool"], droidlink_input.TOOL_FINGER)
        self.assertEqual(parsed["pointer_id"], 0)
        self.assertEqual(parsed["x"], 32768)
        self.assertEqual(parsed["y"], 16384)
        self.assertEqual(parsed["pressure"], 65535)

    def test_parse_valid_stylus_packet(self):
        # Pack a valid 24-byte ACTION_MOVE stylus event with tilt and barrel button
        pkt = struct.pack(
            "<BBBBIIIhhBBBB",
            0xD1, 2, 1, 0,
            10000, 20000, 45000,
            -30, 0,
            2, 0, 0, 0
        )
        parsed = droidlink_input.parse_packet(pkt)
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["action"], droidlink_input.ACTION_MOVE)
        self.assertEqual(parsed["tool"], droidlink_input.TOOL_STYLUS)
        self.assertEqual(parsed["tilt_x"], -30)
        self.assertEqual(parsed["buttons"], 2)

    def test_invalid_magic_rejected(self):
        # Wrong magic byte
        pkt = struct.pack(
            "<BBBBIIIhhBBBB",
            0xFF, 1, 0, 0,
            0, 0, 0,
            0, 0,
            0, 0, 0, 0
        )
        self.assertIsNone(droidlink_input.parse_packet(pkt))

    def test_invalid_action_or_tool_rejected(self):
        # Invalid action 99
        pkt1 = struct.pack("<BBBBIIIhhBBBB", 0xD1, 99, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        self.assertIsNone(droidlink_input.parse_packet(pkt1))

        # Invalid tool 55
        pkt2 = struct.pack("<BBBBIIIhhBBBB", 0xD1, 1, 55, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        self.assertIsNone(droidlink_input.parse_packet(pkt2))

    def test_boundary_clamping(self):
        # Coordinates and pressure beyond bounds
        pkt = struct.pack(
            "<BBBBIIIhhBBBB",
            0xD1, 1, 0, 25,
            70000, 80000, 99999,
            120, -150,
            1, 0, 0, 0
        )
        parsed = droidlink_input.parse_packet(pkt)
        self.assertIsNotNone(parsed)
        self.assertEqual(parsed["pointer_id"], 9)  # Clamped to max 9
        self.assertEqual(parsed["x"], 65535)       # Clamped to 65535
        self.assertEqual(parsed["y"], 65535)       # Clamped to 65535
        self.assertEqual(parsed["pressure"], 65535)# Clamped to 65535
        self.assertEqual(parsed["tilt_x"], 90)     # Clamped to 90
        self.assertEqual(parsed["tilt_y"], -90)    # Clamped to -90

    def test_short_packet_rejected(self):
        self.assertIsNone(droidlink_input.parse_packet(b"\xD1\x01\x00"))

    def test_mock_uinput_touch_lifecycle(self):
        dev = droidlink_input.UinputDevice("Test Touch", is_touch=True, mock=True)
        # Touch down
        dev.handle_touch(droidlink_input.ACTION_DOWN, pointer_id=0, x=1000, y=2000, pressure=50000)
        self.assertIn(0, dev.active_slots)

        # Multi-touch second finger down
        dev.handle_touch(droidlink_input.ACTION_POINTER_DOWN, pointer_id=1, x=5000, y=6000, pressure=40000)
        self.assertIn(1, dev.active_slots)

        # Move
        dev.handle_touch(droidlink_input.ACTION_MOVE, pointer_id=0, x=1200, y=2200, pressure=50000)
        self.assertIn(0, dev.active_slots)

        # Release first finger
        dev.handle_touch(droidlink_input.ACTION_POINTER_UP, pointer_id=0, x=1200, y=2200, pressure=0)
        self.assertNotIn(0, dev.active_slots)
        self.assertIn(1, dev.active_slots)

        # Release second finger
        dev.handle_touch(droidlink_input.ACTION_UP, pointer_id=1, x=5000, y=6000, pressure=0)
        self.assertNotIn(1, dev.active_slots)
        self.assertEqual(len(dev.active_slots), 0)

        # Clean teardown
        dev.release_all()
        dev.close()

    def test_mock_uinput_stylus_lifecycle(self):
        dev = droidlink_input.UinputDevice("Test Stylus", is_touch=False, mock=True)
        dev.handle_stylus(droidlink_input.ACTION_DOWN, x=1000, y=2000, pressure=30000, tilt_x=15, buttons=0, is_eraser=False)
        dev.handle_stylus(droidlink_input.ACTION_MOVE, x=1500, y=2500, pressure=40000, tilt_x=20, buttons=2, is_eraser=False)
        dev.handle_stylus(droidlink_input.ACTION_UP, x=1500, y=2500, pressure=0, tilt_x=0, buttons=0, is_eraser=False)
        # Eraser test
        dev.handle_stylus(droidlink_input.ACTION_DOWN, x=2000, y=3000, pressure=20000, tilt_x=0, buttons=0, is_eraser=True)
        dev.handle_stylus(droidlink_input.ACTION_UP, x=2000, y=3000, pressure=0, tilt_x=0, buttons=0, is_eraser=True)
        dev.release_all()
        dev.close()

    def test_uinput_permission_error_raised_when_unwritable(self):
        # When /dev/uinput is restricted, initializing without mock must raise PermissionError
        if not os.access("/dev/uinput", os.W_OK) and not os.access("/dev/input/uinput", os.W_OK):
            with self.assertRaises(PermissionError):
                droidlink_input.UinputDevice("Test Perm", is_touch=True, mock=False)

    def test_uinput_ioctl_constants(self):
        # Ensure ioctl values match Linux kernel headers
        self.assertEqual(droidlink_input.UI_SET_EVBIT, 0x40045564)
        self.assertEqual(droidlink_input.UI_SET_KEYBIT, 0x40045565)
        self.assertEqual(droidlink_input.UI_SET_ABSBIT, 0x40045567)  # Note: 0x40045566 is UI_SET_RELBIT
        self.assertEqual(droidlink_input.UI_SET_PROPBIT, 0x4004556e)
        self.assertEqual(droidlink_input.INPUT_PROP_DIRECT, 0x01)

    def test_real_uinput_creation_if_permitted(self):
        if os.access("/dev/uinput", os.W_OK) or os.access("/dev/input/uinput", os.W_OK):
            touch = droidlink_input.UinputDevice("DroidLink Test Touch", is_touch=True, mock=False)
            self.assertIsNotNone(touch.fd)
            touch.close()
            self.assertIsNone(touch.fd)

            stylus = droidlink_input.UinputDevice("DroidLink Test Stylus", is_touch=False, mock=False)
            self.assertIsNotNone(stylus.fd)
            stylus.close()
            self.assertIsNone(stylus.fd)

    def test_touch_coordinate_scaling_invariance(self):
        # Coordinates normalized to [0, 65535] regardless of whether logical scale is 1.0, 1.5, or 1.6
        def normalize(raw, total):
            return int((max(0.0, min(float(raw), float(total))) / total) * 65535.0)

        # Scale 1.0 (2560x1600) vs Scale 1.6 (1600x1000)
        # Top-left corner
        self.assertEqual(normalize(0, 2560), 0)
        self.assertEqual(normalize(0, 1600), 0)

        # Center point
        self.assertEqual(normalize(1280, 2560), 32767)
        self.assertEqual(normalize(800, 1600), 32767)

        # Bottom-right corner
        self.assertEqual(normalize(2560, 2560), 65535)
        self.assertEqual(normalize(1600, 1600), 65535)


if __name__ == "__main__":
    unittest.main()
