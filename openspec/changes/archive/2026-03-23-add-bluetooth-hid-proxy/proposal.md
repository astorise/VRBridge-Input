# Proposal: Meta Quest 3 Bluetooth HID Proxy

## What Changes
An Android application that acts as a Bluetooth HID (Human Interface Device) proxy. It converts physical AZERTY keyboard inputs into QWERTY HID reports, and transforms the phone's touch screen (or a physical mouse connected via USB-C) into a HID mouse for the Meta Quest 3.

## Why
The Meta Quest 3 lacks native support for physical AZERTY keyboard layouts. By using an Android phone as a bridge, users can type naturally on their physical keyboard and use mouse/trackpad inputs in VR without compatibility issues.

## Capabilities

### New Capabilities
- bluetooth-hid-connection
- keyboard-hid-proxy
- mouse-hid-proxy
- foreground-service-persistence