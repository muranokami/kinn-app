// ------------------------------------------------------------------
// 健康管理(拡張機能)共通ユーティリティ
// ------------------------------------------------------------------
const HEALTH_EMPLOYEE_ID = "default";

const HEALTH_CONDITION_OPTIONS = [
  { value: "GREAT", label: "絶好調", icon: "😄" },
  { value: "GOOD", label: "好調", icon: "🙂" },
  { value: "NORMAL", label: "普通", icon: "😐" },
  { value: "SLIGHTLY_BAD", label: "少し不調", icon: "😕" },
  { value: "BAD", label: "不調", icon: "😣" },
];

function healthFormatDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function healthTodayStr() {
  return healthFormatDate(new Date());
}

/** period("1w"/"1m"/"3m"/"6m")から開始日文字列を計算する(グラフの横軸ラベル間引き判定などに使用) */
function healthPeriodDays(period) {
  switch (period) {
    case "1w": return 7;
    case "3m": return 92;
    case "6m": return 183;
    default: return 31;
  }
}

/** シンプルな折れ線グラフを<canvas>に描画する(外部ライブラリ不使用) */
function drawHealthLineChart(canvas, points, options) {
  const dpr = window.devicePixelRatio || 1;
  const cssWidth = canvas.clientWidth || 600;
  const cssHeight = canvas.clientHeight || 320;
  canvas.width = cssWidth * dpr;
  canvas.height = cssHeight * dpr;
  const ctx = canvas.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssWidth, cssHeight);

  const padding = { top: 18, right: 18, bottom: 34, left: 44 };
  const w = cssWidth - padding.left - padding.right;
  const h = cssHeight - padding.top - padding.bottom;

  const color = options.color || "#1F3A5F";
  const unit = options.unit || "";
  const values = points.map((p) => p.value).filter((v) => v !== null && v !== undefined);

  ctx.font = "11px 'Hiragino Sans', sans-serif";
  ctx.fillStyle = "#5B5B52";

  if (values.length === 0) {
    ctx.textAlign = "center";
    ctx.fillText("データがありません", cssWidth / 2, cssHeight / 2);
    return;
  }

  let min = Math.min(...values);
  let max = Math.max(...values);
  if (options.minFixed !== undefined) min = Math.min(min, options.minFixed);
  if (options.maxFixed !== undefined) max = Math.max(max, options.maxFixed);
  if (min === max) { min -= 1; max += 1; }
  const rangePad = (max - min) * 0.12;
  min -= rangePad;
  max += rangePad;

  const xAt = (i) => padding.left + (points.length <= 1 ? w / 2 : (w * i) / (points.length - 1));
  const yAt = (v) => padding.top + h - ((v - min) / (max - min)) * h;

  // グリッド + Y軸ラベル
  ctx.strokeStyle = "#EFE9DB";
  ctx.lineWidth = 1;
  const gridLines = 4;
  ctx.textAlign = "right";
  ctx.textBaseline = "middle";
  for (let i = 0; i <= gridLines; i++) {
    const v = min + ((max - min) * i) / gridLines;
    const y = yAt(v);
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(padding.left + w, y);
    ctx.stroke();
    ctx.fillText(Math.round(v * 10) / 10 + unit, padding.left - 6, y);
  }

  // X軸ラベル(間引き表示)
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  const labelStep = Math.max(1, Math.ceil(points.length / 6));
  points.forEach((p, i) => {
    if (i % labelStep !== 0 && i !== points.length - 1) return;
    ctx.fillText(p.label, xAt(i), padding.top + h + 8);
  });

  // 折れ線(値が無い箇所は線をつながない)
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  ctx.beginPath();
  let started = false;
  points.forEach((p, i) => {
    if (p.value === null || p.value === undefined) { started = false; return; }
    const x = xAt(i);
    const y = yAt(p.value);
    if (!started) { ctx.moveTo(x, y); started = true; } else { ctx.lineTo(x, y); }
  });
  ctx.stroke();

  // 点
  ctx.fillStyle = color;
  points.forEach((p, i) => {
    if (p.value === null || p.value === undefined) return;
    ctx.beginPath();
    ctx.arc(xAt(i), yAt(p.value), 2.6, 0, Math.PI * 2);
    ctx.fill();
  });
}
