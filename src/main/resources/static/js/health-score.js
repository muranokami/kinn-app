// ------------------------------------------------------------------
// 健康スコア
// ------------------------------------------------------------------
const SCORE_BREAKDOWN_ITEMS = [
  { key: "sleepScore", label: "睡眠スコア" },
  { key: "fatigueScore", label: "疲労スコア" },
  { key: "stressScore", label: "ストレススコア" },
  { key: "exerciseScore", label: "運動スコア" },
  { key: "conditionScore", label: "体調スコア" },
];

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("scoreDateLabel").textContent = healthTodayStr();
  loadScore();
  loadAlerts();
});

async function loadScore() {
  try {
    const res = await fetch(`/api/health/score?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const s = await res.json();
    renderScore(s);
  } catch (e) {
    console.error(e);
  }
}

function renderScore(s) {
  document.getElementById("scoreNum").textContent = s.totalScore;
  document.getElementById("scoreLevel").textContent = s.level;
  document.getElementById("scoreRing").style.setProperty("--pct", s.totalScore);
  document.getElementById("scoreNote").textContent = s.hasData
    ? "今日の記録をもとに算出したスコアです。"
    : "まだ今日の記録がないため、目安のスコアを表示しています。";

  const grid = document.getElementById("breakdownGrid");
  grid.innerHTML = "";
  SCORE_BREAKDOWN_ITEMS.forEach((item) => {
    const value = s[item.key];
    const div = document.createElement("div");
    div.className = "score-bar-item";
    div.innerHTML = `
      <div class="sb-label"><span>${item.label}</span><span class="sb-value">${value}</span></div>
      <div class="score-bar-track"><div class="score-bar-fill" style="width:${value}%;"></div></div>
    `;
    grid.appendChild(div);
  });
}

async function loadAlerts() {
  const listEl = document.getElementById("alertList");
  try {
    const res = await fetch(`/api/health/alerts?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const alerts = await res.json();
    if (alerts.length === 0) {
      listEl.innerHTML = `<p class="alert-empty">現在、特に気になる傾向はありません。この調子でいきましょう ✨</p>`;
      return;
    }
    listEl.innerHTML = "";
    alerts.forEach((a) => {
      const div = document.createElement("div");
      div.className = `alert-item severity-${a.severity}`;
      div.innerHTML = `
        <span class="alert-icon">${a.severity === "WARNING" ? "⚠️" : "💡"}</span>
        <div class="alert-body">
          <div class="alert-type">${a.alertTypeLabel}</div>
          <p class="alert-message">${a.message}</p>
          <div class="alert-date">${a.triggeredDate}</div>
        </div>
      `;
      listEl.appendChild(div);
    });
  } catch (e) {
    console.error(e);
    listEl.innerHTML = `<p class="alert-empty">アラートの読み込みに失敗しました</p>`;
  }
}
