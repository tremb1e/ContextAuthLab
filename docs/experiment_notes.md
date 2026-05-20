# Experiment Notes

This stage only collects data. It does not implement identity authentication, routing, MoE, training, or inference.

| Category | Display | Accel Variance | Gyro Axis | Accel Band | Typical UI Signal | Intuition |
| --- | --- | --- | --- | --- | --- | --- |
| C0 | 持机静止 - 静置计时 | Very low | Very low | DC | no scroll/click, editable=0 | quiet holding |
| C1 | 静态阅读 - 研究协议阅读 | Low | slow pitch drift | near DC | TextView-heavy, few events | static reading |
| C2 | 单指滑动信息流 - 研究咨询流 | Low-medium bursts | pitch pulses | 1-4Hz | scrolled, scrollable>=1 | feed scrolling |
| C3 | 文本输入 - 段落抄写 | Medium high-frequency | yaw micro-motion | 4-12Hz | text-changed, editable>=1 | text input |
| C4 | 多控件操作 - 模拟手机设置 | Typing plus pauses | mixed | intermittent | editable/clicked/slider/tab/switch/radio mixed | controls and form work |
| C5 | 主动倾斜操作 - 倾斜迷宫 | High | roll/pitch | 0.5-20Hz | tilt-maze interaction | deliberate tilt |
| C6 | 显式转腕挑战 - 手腕转动 | Medium-high periodic | roll/yaw/pitch | 0.5-3Hz | animation-guided wrist rotation | wrist rotation |

The table is for heuristic labeling and data quality analysis, not a trained authentication model.
