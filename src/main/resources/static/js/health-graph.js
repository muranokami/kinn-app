// ------------------------------------------------------------------
// 健康グラフ(健康スコア・体重・睡眠時間などの推移)
// ------------------------------------------------------------------
const METRIC_CONFIG = {
  healthScore: { label: "健康スコア", color: "#1F3A5F", unit: "", minFixed: 0, maxFixed: 100 },
  weightKg: { label: "体重", color: "#3E6B52", unit: "kg" },
  sleepHours: { label: "睡眠時間", color: "#C33B2E", unit: "h" },
  fatigueLevel: { label: "疲労度", color: "#D9A441", unit: "", minFixed: 1, maxFixed: 5 },
  exerciseMinutes: { label: "運動時間", color: "#5B3E9C", unit: "分" },
};

let graphPeriod = "1m";
let currentTrend = null;

window.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      graphPeriod = btn.dataset.period;
      updateActiveTab();
      loadTrend();
    });
  });
  document.getElementById("metricSelect").addEventListener("change", renderChart);
  window.addEventListener("resize", renderChart);

  updateActiveTab();
  loadTrend();
});

function updateActiveTab() {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.period === graphPeriod);
  });
}

async function loadTrend() {
  try {
    const res = await fetch(`/api/health/score/trend?employeeId=${HEALTH_EMPLOYEE_ID}&period=${graphPeriod}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    currentTrend = await res.json();
    renderChart();
  } catch (e) {
    console.error(e);
  }
}

function renderChart() {
  if (!currentTrend) return;
  const metricKey = document.getElementById("metricSelect").value;
  const config = METRIC_CONFIG[metricKey];

  const points = currentTrend.points.map((p) => ({
    label: shortLabel(p.date),
    value: p[metricKey],
  }));

  const canvas = document.getElementById("trendCanvas");
  drawHealthLineChart(canvas, points, {
    color: config.color,
    unit: config.unit,
    minFixed: config.minFixed,
    maxFixed: config.maxFixed,
  });
}

function shortLabel(dateStr) {
  const [, m, d] = dateStr.split("-");
  return `${Number(m)}/${Number(d)}`;
}
