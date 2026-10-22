# PadHid

Trackpad + keyboard for a phone that is plugged into another phone/PC over USB,
using USB Arsenal's HID gadget (`/dev/hidg0` keyboard, `/dev/hidg1` mouse).
Needs root and the USB Arsenal binary on the phone.

- Start: runs `usb-arsenal hid start`, reads the device nodes, opens them.
- Trackpad: move = pointer, tap = left click, two-finger tap = right click,
  two-finger drag = scroll. Hold the Left button with another finger to drag.
- Keyboard button: opens your normal on-screen keyboard; what you type is sent as keys.
  Ctrl/Alt/Win/Shift are one-shot: tap, then press the next key.
- Layout assumed on the host: US.
- If USB Arsenal is not on PATH, type its full path in the text box.

Build: push to GitHub, run the "Build APK" workflow, download `PadHid-debug-apk`.
