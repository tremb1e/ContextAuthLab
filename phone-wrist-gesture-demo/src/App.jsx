import { useEffect, useState } from "react";

const SCENES = [
  {
    id: "translate",
    index: 1,
    title: "左右平移",
    caption: "手腕左右移",
    note: "位置变 · 朝向不变",
    duration: 3,
    angle: 22,
    amplitude: 72,
    viewType: "front"
  },
  {
    id: "yaw",
    index: 2,
    title: "左右摆动",
    caption: "手腕左右转",
    note: "中心稳 · 朝向变",
    duration: 3,
    angle: 30,
    amplitude: 0,
    viewType: "front-yaw"
  },
  {
    id: "depth",
    index: 3,
    title: "前后摆动",
    caption: "手腕前后摆",
    note: "距离变 · 靠近远离",
    duration: 3,
    angle: 24,
    amplitude: 76,
    viewType: "side"
  }
];

function useReducedMotion() {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  return reduced;
}

export default function App() {
  const [playing, setPlaying] = useState(true);
  const reducedMotion = useReducedMotion();
  const rootClass = ["motion-page", playing ? "" : "is-paused", reducedMotion ? "reduced-active" : ""]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={rootClass}>
      <div className="app-shell">
        <header className="app-header">
          <h1>用手腕控制手机</h1>
          <p>固定前臂，只让右手手腕带动手机</p>
        </header>

        <main className="scene-grid" aria-label="右手手腕手机手势教学动画">
          {SCENES.map((scene) => (
            <GestureCard key={scene.id} scene={scene} />
          ))}
        </main>
      </div>

      <Controls
        playing={playing}
        reducedMotion={reducedMotion}
        onToggle={() => setPlaying((current) => !current)}
      />
    </div>
  );
}

function Controls({ playing, reducedMotion, onToggle }) {
  return (
    <div className="controls">
      <button
        className="control-button"
        type="button"
        aria-label={playing ? "暂停动画" : "播放动画"}
        aria-pressed={!playing}
        onClick={onToggle}
      >
        <IconPlaying playing={playing} />
        <span>{playing ? "暂停" : "播放"}</span>
      </button>
      {reducedMotion ? <span className="motion-note">减少动态：静态关键帧</span> : null}
    </div>
  );
}

function IconPlaying({ playing }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {playing ? (
        <>
          <rect x="7" y="5" width="3.8" height="14" rx="1.2" fill="currentColor" />
          <rect x="13.2" y="5" width="3.8" height="14" rx="1.2" fill="currentColor" />
        </>
      ) : (
        <path d="M8 5.5 18.5 12 8 18.5Z" fill="currentColor" />
      )}
    </svg>
  );
}

function GestureCard({ scene }) {
  const titleId = `${scene.id}-title`;
  const descId = `${scene.id}-desc`;

  return (
    <article className={`gesture-card scene-${scene.id}`} aria-labelledby={titleId}>
      <div className="card-title">
        <span aria-hidden="true">{scene.index}</span>
        <h2 id={titleId}>{scene.title}</h2>
      </div>
      <p className="scene-meta" id={descId}>
        {scene.note}
      </p>
      <MotionScene scene={scene} labelledBy={titleId} describedBy={descId} />
      <p className="caption">{scene.caption}</p>
    </article>
  );
}

function MotionScene({ scene, labelledBy, describedBy }) {
  const markerId = `arrow-${scene.id}`;
  const title = `${scene.title}：${scene.caption}`;

  return (
    <div className="motion-frame">
      <svg
        viewBox="0 0 360 430"
        role="img"
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
      >
        <title>{title}</title>
        <SvgDefs markerId={markerId} />
        {scene.id === "translate" ? <TranslateScene markerId={markerId} /> : null}
        {scene.id === "yaw" ? <YawScene markerId={markerId} /> : null}
        {scene.id === "depth" ? <DepthScene markerId={markerId} /> : null}
        <OrientationInset sceneId={scene.id} markerId={markerId} />
      </svg>
    </div>
  );
}

function SvgDefs({ markerId }) {
  return (
    <defs>
      <marker id={`${markerId}-accent`} viewBox="0 0 10 10" refX="8.4" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent)" />
      </marker>
      <marker id={`${markerId}-fixed`} viewBox="0 0 10 10" refX="8.4" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--fixed)" />
      </marker>
    </defs>
  );
}

function TranslateScene({ markerId }) {
  const phone = { cx: 180, cy: 198, width: 62, height: 116 };
  const wrist = { x: 180, y: 298 };

  return (
    <g>
      <BackgroundBand />

      {/* 固定层：人脸、人脸平面、大臂/前臂、支点、轨迹和残影都不接受动画 transform。 */}
      <g className="fixed-layer">
        <FaceFront cx={180} cy={68} r={30} />
        <PlaneIndicator.FaceFront x={124} y={110} width={112} label="脸平面" />
        <NormalArrow markerId={markerId} x1={180} y1={112} x2={180} y2={140} type="fixed" />
        <MotionPath d="M 143 205 Q 180 185 217 205" />
        <DirectionArc markerId={markerId} d="M 132 224 Q 180 207 228 224" doubleEnded />
        <PhoneFront cx={143} cy={205} width={phone.width} height={phone.height} ghost />
        <PhoneFront cx={180} cy={198} width={phone.width} height={phone.height} ghost />
        <PhoneFront cx={217} cy={205} width={phone.width} height={phone.height} ghost />
        <ForearmFront wristX={wrist.x} wristY={wrist.y} />
        <PivotMarker x={wrist.x} y={wrist.y} labelX={238} labelY={312} />
        <TextLabel x={180} y={146} text="屏幕始终正对脸" accent width={118} />
      </g>

      {/* 运动层：绕手腕做 ±22 度弧线，内部反向旋转，让手机屏幕平面始终与脸平面平行。 */}
      <g className="moving-layer s1-orbit">
        <g className="s1-level">
          <HandFront centerX={phone.cx} baseY={263} />
          <PhoneFront cx={phone.cx} cy={phone.cy} width={phone.width} height={phone.height} />
          <PlaneIndicator.ScreenFront x={phone.cx - phone.width / 2 + 7} y={phone.cy - phone.height / 2 + 13} width={phone.width - 14} height={phone.height - 26} />
          <NormalArrow markerId={markerId} x1={phone.cx} y1={phone.cy - 62} x2={phone.cx} y2={phone.cy - 90} type="accent" />
        </g>
      </g>
    </g>
  );
}

function YawScene({ markerId }) {
  const phone = { cx: 180, cy: 198, width: 62, height: 116 };
  const wrist = { x: 180, y: 298 };

  return (
    <g>
      <BackgroundBand />

      {/* 固定层：手机中心、手腕和前臂位置稳定；只有握手机的运动组 rotateY。 */}
      <g className="fixed-layer">
        <FaceFront cx={180} cy={68} r={30} />
        <PlaneIndicator.FaceFront x={124} y={110} width={112} label="脸平面" />
        <NormalArrow markerId={markerId} x1={180} y1={112} x2={180} y2={140} type="fixed" />
        <line className="phone-axis" x1={phone.cx} y1={phone.cy - 73} x2={phone.cx} y2={phone.cy + 73} />
        <DirectionArc markerId={markerId} d="M 136 126 Q 180 106 224 126" doubleEnded />
        <rect className="ghost-phone" x={148} y={140} width={34} height={116} rx={10} />
        <rect className="ghost-phone" x={178} y={140} width={34} height={116} rx={10} />
        <ForearmFront wristX={wrist.x} wristY={wrist.y} />
        <PivotMarker x={wrist.x} y={wrist.y} labelX={238} labelY={312} />
        <TextLabel x={116} y={184} text="手机中轴线" width={84} />
        <TextLabel x={180} y={146} text="绕中轴线转" accent width={96} />
      </g>

      {/* 运动层：手掌和手机中心固定，只绕手机自身竖直中轴线左右偏转。 */}
      <g className="moving-layer s2-phone-grip">
        <HandFront centerX={phone.cx} baseY={263} />
        <PhoneFront cx={phone.cx} cy={phone.cy} width={phone.width} height={phone.height} />
        <PlaneIndicator.ScreenFront x={phone.cx - phone.width / 2 + 7} y={phone.cy - phone.height / 2 + 13} width={phone.width - 14} height={phone.height - 26} />
      </g>
      <g className="moving-layer s2-normal">
        <NormalArrow markerId={markerId} x1={phone.cx} y1={phone.cy - 62} x2={phone.cx} y2={phone.cy - 90} type="accent" />
      </g>
    </g>
  );
}

function DepthScene({ markerId }) {
  const wrist = { x: 260, y: 322 };
  const phone = { cx: 222, cy: 218, width: 50, height: 104 };

  return (
    <g>
      <BackgroundBand />

      {/* 固定层：侧脸、脸平面、前臂、支点、距离标尺和两端残影不受动画影响。 */}
      <g className="fixed-layer">
        <FaceSide cx={78} cy={130} r={31} />
        <line className="face-plane" x1={114} y1={118} x2={114} y2={258} />
        <NormalArrow markerId={markerId} x1={114} y1={190} x2={146} y2={190} type="fixed" />
        <TextLabel x={114} y={276} text="脸平面" width={60} />
        <PhoneSide cx={194} cy={236} width={54} height={110} ghost angle={-18} />
        <PhoneSide cx={255} cy={214} width={44} height={92} ghost angle={16} />
        <MotionPath d="M 194 236 Q 224 218 255 214" />
        <DirectionArc markerId={markerId} d="M 158 152 L 262 152" doubleEnded />
        <line className="ruler-tick" x1={114} y1={144} x2={114} y2={160} />
        <line className="ruler-tick" x1={262} y1={144} x2={262} y2={160} />
        <ForearmSide wristX={wrist.x} wristY={wrist.y} />
        <PivotMarker x={wrist.x} y={wrist.y} labelX={206} labelY={342} />
        <TextLabel x={206} y={130} text="靠近 / 远离" accent width={92} />
        <g className="s3-ruler-current anim-target">
          <NormalArrow markerId={markerId} x1={114} y1={166} x2={200} y2={166} type="accent" doubleEnded />
        </g>
      </g>

      {/* 运动层：手掌和手机绕手腕支点前后摆动，侧视图中表现为靠近/远离人脸。 */}
      <g className="moving-layer s3-swing">
        <g className="s3-size">
          <HandSide phoneX={phone.cx} phoneY={phone.cy + 48} wristX={wrist.x} wristY={wrist.y} />
          <PhoneSide cx={phone.cx} cy={phone.cy} width={phone.width} height={phone.height} />
          <NormalArrow markerId={markerId} x1={phone.cx - 26} y1={phone.cy} x2={phone.cx - 58} y2={phone.cy} type="accent" />
        </g>
      </g>
    </g>
  );
}

function OrientationInset({ sceneId, markerId }) {
  const phoneClass = `inset-phone inset-${sceneId}`;
  const relationText = sceneId === "translate" ? "平行平移" : sceneId === "yaw" ? "朝向偏转" : "距离变化";

  return (
    <g className="orientation-inset" transform="translate(20 316)">
      <line className="inset-divider" x1={0} y1={0} x2={320} y2={0} />
      <text className="small-text" x={160} y={18} textAnchor="middle">
        俯视 · 屏幕与脸朝向
      </text>
      <g className="inset-face">
        <rect className="face-plane-fill" x={95} y={33} width={130} height={12} rx={6} />
        <NormalArrow markerId={markerId} x1={160} y1={47} x2={160} y2={72} type="fixed" />
        <text className="small-text" x={232} y={42}>
          人脸平面
        </text>
      </g>
      <g className={phoneClass}>
        <rect className="screen-plane-fill" x={117} y={96} width={86} height={12} rx={6} />
        <NormalArrow markerId={markerId} x1={160} y1={96} x2={160} y2={71} type="accent" />
        <text className="small-text" x={207} y={118}>
          屏幕平面
        </text>
      </g>
      <text className="small-text relation-text" x={48} y={72}>
        {relationText}
      </text>
    </g>
  );
}

function BackgroundBand() {
  return (
    <>
      <rect className="stage-bg" x={10} y={10} width={340} height={300} rx={8} />
      <path className="floor-line" d="M 64 290 H 304" />
    </>
  );
}

function FaceFront({ cx, cy, r }) {
  return (
    <g className="face">
      <circle className="face-fill" cx={cx} cy={cy} r={r} />
      <path className="ln-fixed-thin" d={`M ${cx - r} ${cy - 4} Q ${cx} ${cy - r * 1.42} ${cx + r} ${cy - 4}`} />
      <circle className="fixed-dot" cx={cx - 12} cy={cy - 2} r={2.4} />
      <circle className="fixed-dot" cx={cx + 12} cy={cy - 2} r={2.4} />
      <path className="ln-fixed-thin" d={`M ${cx} ${cy + 2} l -3 9 l 5 0`} />
      <path className="ln-fixed-thin" d={`M ${cx - 9} ${cy + 16} Q ${cx} ${cy + 21} ${cx + 9} ${cy + 16}`} />
      <path className="ln-fixed-thin" d={`M ${cx - 9} ${cy + r - 2} v 11 M ${cx + 9} ${cy + r - 2} v 11`} />
    </g>
  );
}

function FaceSide({ cx, cy, r }) {
  return (
    <g className="face">
      <circle className="face-fill" cx={cx} cy={cy} r={r} />
      <path
        className="ln-fixed"
        d={`M ${cx} ${cy - r}
          Q ${cx + r * 0.95} ${cy - r} ${cx + r * 0.98} ${cy - 8}
          L ${cx + r + 9} ${cy + 2}
          Q ${cx + r * 0.74} ${cy + 7} ${cx + r * 0.82} ${cy + 13}
          Q ${cx + r * 0.55} ${cy + r * 0.92} ${cx} ${cy + r}`}
      />
      <circle className="fixed-dot" cx={cx + r * 0.48} cy={cy - 5} r={2.4} />
      <path className="ln-fixed-thin" d={`M ${cx + r * 0.32} ${cy - 12} q 8 -3 14 0`} />
      <circle className="ln-fixed-thin fill-none" cx={cx - r * 0.34} cy={cy + 2} r={5} />
      <path className="ln-fixed-thin" d={`M ${cx - 2} ${cy + r - 2} l 1 12 M ${cx + r * 0.54} ${cy + r - 4} l 1 10`} />
    </g>
  );
}

function ForearmFront({ wristX, wristY }) {
  const elbowX = wristX - 30;
  const elbowY = wristY + 54;

  return (
    <g className="forearm fixed-arm">
      <path className="arm-fill thick-arm" d={`M ${elbowX} ${elbowY} L ${elbowX + 8} ${elbowY + 46}`} />
      <path className="arm-fill thick-arm" d={`M ${wristX} ${wristY} L ${elbowX} ${elbowY}`} />
      <circle className="face-fill" cx={elbowX} cy={elbowY} r={6} />
      <LockIcon x={wristX - 38} y={wristY + 18} />
      <TextLabel x={wristX - 42} y={wristY + 42} text="前臂不动" width={82} />
    </g>
  );
}

function ForearmSide({ wristX, wristY }) {
  const elbowX = wristX + 48;
  const elbowY = wristY + 36;

  return (
    <g className="forearm fixed-arm">
      <path className="arm-fill thick-arm" d={`M ${elbowX} ${elbowY} L ${elbowX + 18} ${elbowY + 42}`} />
      <path className="arm-fill thick-arm" d={`M ${wristX} ${wristY} L ${elbowX} ${elbowY}`} />
      <circle className="face-fill" cx={elbowX} cy={elbowY} r={6} />
      <LockIcon x={wristX + 36} y={wristY + 24} />
      <TextLabel x={wristX + 40} y={wristY + 50} text="前臂不动" width={82} />
    </g>
  );
}

function LockIcon({ x, y }) {
  return (
    <g aria-hidden="true">
      <path className="lock-shackle" d={`M ${x - 5} ${y - 1} v -4 a 5 5 0 0 1 10 0 v 4`} />
      <rect className="lock-body" x={x - 7} y={y - 1} width={14} height={11} rx={2.2} />
    </g>
  );
}

function PivotMarker({ x, y, labelX, labelY }) {
  return (
    <g className="pivot">
      <circle className="pivot-halo" cx={x} cy={y} r={10} />
      <circle className="pivot-ring" cx={x} cy={y} r={7.5} />
      <circle className="pivot-dot" cx={x} cy={y} r={3.2} />
      <TextLabel x={labelX} y={labelY} text="手腕发力" accent width={82} />
    </g>
  );
}

function TextLabel({ x, y, text, width = 74, accent = false }) {
  return (
    <g className="text-label" aria-hidden="true">
      <rect
        className={accent ? "label-box-accent" : "label-box"}
        x={x - width / 2}
        y={y - 13}
        width={width}
        height={24}
        rx={12}
      />
      <text className={accent ? "label-text-accent" : "label-text"} x={x} y={y + 4} textAnchor="middle">
        {text}
      </text>
    </g>
  );
}

function PhoneFront({ cx, cy, width, height, ghost = false }) {
  const x = cx - width / 2;
  const y = cy - height / 2;

  if (ghost) {
    return (
      <g className="phone-ghost">
        <rect className="ghost-phone" x={x} y={y} width={width} height={height} rx={11} />
        <rect className="ghost-screen" x={x + 7} y={y + 13} width={width - 14} height={height - 26} rx={6} />
      </g>
    );
  }

  return (
    <g className="phone">
      <rect className="phone-body" x={x} y={y} width={width} height={height} rx={11} />
      <rect className="phone-screen" x={x + 7} y={y + 13} width={width - 14} height={height - 26} rx={6} />
      <circle className="phone-camera" cx={cx} cy={y + 7} r={2} />
    </g>
  );
}

function PhoneSide({ cx, cy, width, height, ghost = false, angle = 0 }) {
  const x = cx - width / 2;
  const y = cy - height / 2;
  const body = ghost ? "ghost-phone" : "phone-body";
  const screen = ghost ? "ghost-screen" : "phone-screen";

  return (
    <g className={ghost ? "phone-ghost" : "phone"} transform={ghost ? `rotate(${angle} ${cx} ${cy})` : undefined}>
      <rect className={body} x={x} y={y} width={width} height={height} rx={9} />
      <rect className={screen} x={x + 4} y={y + 9} width={Math.max(15, width * 0.46)} height={height - 18} rx={5} />
      {!ghost ? <line className="screen-plane" x1={x + 3} y1={y + 5} x2={x + 3} y2={y + height - 5} /> : null}
      {!ghost ? <circle className="phone-camera" cx={x + 9} cy={y + 7} r={1.7} /> : null}
    </g>
  );
}

function HandFront({ centerX, baseY, fixed = false }) {
  const left = centerX - 35;
  const right = centerX + 35;
  const top = baseY - 30;
  const bottom = baseY + 23;

  return (
    <g className={fixed ? "hand fixed-hand" : "hand"}>
      <path
        className="hand-fill"
        d={`M ${left} ${top} L ${left} ${baseY - 4}
          Q ${left} ${bottom} ${centerX} ${bottom}
          Q ${right} ${bottom} ${right} ${baseY - 4}
          L ${right} ${top} Z`}
      />
      <path className="hand-fill" d={`M ${right - 2} ${baseY - 16} q 15 0 15 12 q 0 10 -15 9`} />
      <path className="hand-fill" d={`M ${left + 2} ${top + 7} q -11 0 -11 9 q 0 8 11 7`} />
      <path className="hand-fill" d={`M ${left + 2} ${top + 24} q -11 0 -11 9 q 0 8 11 7`} />
      <path className="finger-line" d={`M ${centerX - 18} ${baseY - 22} v 37 M ${centerX} ${baseY - 22} v 39 M ${centerX + 18} ${baseY - 22} v 37`} />
    </g>
  );
}

function HandSide({ phoneX, phoneY, wristX, wristY }) {
  return (
    <g className="hand">
      <path
        className="hand-fill"
        d={`M ${phoneX - 18} ${phoneY - 14}
          Q ${phoneX - 27} ${phoneY + 13} ${phoneX - 7} ${phoneY + 23}
          Q ${wristX - 20} ${phoneY + 35} ${wristX - 8} ${wristY}
          L ${wristX + 11} ${wristY - 3}
          Q ${wristX + 4} ${phoneY + 2} ${phoneX + 18} ${phoneY - 15}
          Q ${phoneX + 24} ${phoneY - 29} ${phoneX + 8} ${phoneY - 29} Z`}
      />
      <path className="finger-line" d={`M ${phoneX - 15} ${phoneY - 19} q -12 6 -8 19`} />
    </g>
  );
}

function MotionPath({ d }) {
  return <path className="motion-path" d={d} />;
}

function DirectionArc({ markerId, d, doubleEnded = false }) {
  return (
    <path
      className="direction-arrow"
      d={d}
      markerStart={doubleEnded ? `url(#${markerId}-accent)` : undefined}
      markerEnd={`url(#${markerId}-accent)`}
    />
  );
}

function NormalArrow({ markerId, x1, y1, x2, y2, type = "accent", doubleEnded = false }) {
  const suffix = type === "fixed" ? "fixed" : "accent";

  return (
    <line
      className={type === "fixed" ? "normal-arrow normal-fixed" : "normal-arrow normal-accent"}
      x1={x1}
      y1={y1}
      x2={x2}
      y2={y2}
      markerStart={doubleEnded ? `url(#${markerId}-${suffix})` : undefined}
      markerEnd={`url(#${markerId}-${suffix})`}
    />
  );
}

const PlaneIndicator = {
  FaceFront({ x, y, width, label }) {
    return (
      <g className="plane-indicator">
        <line className="face-plane" x1={x} y1={y} x2={x + width} y2={y} />
        <text className="small-text" x={x + width + 8} y={y + 4}>
          {label}
        </text>
      </g>
    );
  },
  ScreenFront({ x, y, width, height }) {
    return (
      <g className="plane-indicator">
        <rect className="screen-plane-rect" x={x} y={y} width={width} height={height} rx={6} />
      </g>
    );
  }
};
