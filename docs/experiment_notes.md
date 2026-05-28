# Experiment Notes

This stage only collects data. It does not implement identity authentication, routing, MoE, training, or inference.

| Category | Display | Accel Variance | Gyro Axis | Accel Band | Typical UI Signal | Intuition |
| --- | --- | --- | --- | --- | --- | --- |
| C0 | Quiet hold - Still timer | Very low | Very low | DC | no scroll/click, editable=0 | quiet holding |
| C1 | Static reading - Research protocol reading | Low | slow pitch drift | near DC | TextView-heavy, few events | static reading |
| C2 | Single-finger feed - Research information feed | Low-medium bursts | pitch pulses | 1-4Hz | scrolled, scrollable>=1 | feed scrolling |
| C3 | Text entry - Paragraph copy | Medium high-frequency | yaw micro-motion | 4-12Hz | input_method_visible, editable>=1; no per-character events | text input |
| C4 | Multi-control operation - Simulated phone settings | Typing plus pauses | mixed | intermittent | editable/clicked/slider/tab/switch/radio mixed | controls and form work |
| C5 | Landscape touch challenge - Blue ball tapping | Low-medium tap bursts | small grip transients | 1-8Hz | fullscreen game-like touch timing, repeated taps | target tapping in landscape |
| C6 | Video watching - Local video playback | Low posture drift | slow roll/pitch | near DC to 2Hz | fullscreen VideoView, overlaid play/pause, speed, seek, orientation | comfortable video viewing |
| C7 | Explicit wrist rotation - Wrist rotation | Medium-high periodic | roll/yaw/pitch | 0.5-3Hz | animation-guided left-right swing, lateral translation, and forward-back flexion | wrist and palm motion |

The table is for heuristic labeling and data quality analysis, not a trained authentication model. Uploaded task labels use English `task_name` and `task_intuitive_description`; participant UI remains bilingual.
