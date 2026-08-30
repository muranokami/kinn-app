// ------------------------------------------------------------------
// 健康スコア
// ------------------------------------------------------------------
const SCORE_BREAKDOWN_ITEMS = [
  { key: "sleepScore", label: "睡眠スコア" },
  { key: "fatigueScore", label: "疲労スコア" },
  { key: "exerciseScore", label: "運動スコア" },
  { key: "conditionScore", label: "体調スコア" },
];

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("scoreDateLabel").textContent = healthTodayStr();
  loadScore();
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
