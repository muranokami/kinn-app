// ------------------------------------------------------------------
// 勤怠×健康分析
// ------------------------------------------------------------------
let analysisPeriod = "1m";

window.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      analysisPeriod = btn.dataset.period;
      updateActiveTab();
      loadAnalysis();
    });
  });
  updateActiveTab();
  loadAnalysis();
});

function updateActiveTab() {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.period === analysisPeriod);
  });
}

async function loadAnalysis() {
  const tbody = document.getElementById("analysisTbody");
  tbody.innerHTML = `<tr class="empty-row"><td colspan="6">読み込み中...</td></tr>`;
  try {
    const res = await fetch(`/api/health/analysis?employeeId=${HEALTH_EMPLOYEE_ID}&period=${analysisPeriod}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const data = await res.json();
    renderSummary(data.summary);
    renderTable(data.points);
  } catch (e) {
    console.error(e);
    tbody.innerHTML = `<tr class="empty-row"><td colspan="6">読み込みに失敗しました</td></tr>`;
  }
}

function renderSummary(s) {
  setVal("scoreLowOt", s.avgHealthScoreLowOvertime);
  setVal("scoreHighOt", s.avgHealthScoreHighOvertime);
  setVal("fatigueShortSleep", s.avgFatigueShortSleep);
  setVal("fatigueEnoughSleep", s.avgFatigueEnoughSleep);
}

function setVal(id, value) {
  document.getElementById(id).textContent = value ?? "-";
}

function renderTable(points) {
  const tbody = document.getElementById("analysisTbody");
  if (!points || points.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="6">この期間のデータはありません</td></tr>`;
    return;
  }
  tbody.innerHTML = "";
  points.slice().reverse().forEach((p) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${p.date}</td>
      <td>${p.workHours ?? "-"}</td>
      <td>${p.overtimeHours ?? "-"}</td>
      <td>${p.sleepHours ?? "-"}</td>
      <td>${p.fatigueLevel ?? "-"}</td>
      <td>${p.healthScore ?? "-"}</td>
    `;
    tbody.appendChild(tr);
  });
}
