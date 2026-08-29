// ------------------------------------------------------------------
// 健康履歴(過去の体調チェックを一覧で振り返る)
// ------------------------------------------------------------------
let historyPeriod = "1m";

window.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      historyPeriod = btn.dataset.period;
      updateActiveTab();
      loadHistory();
    });
  });
  updateActiveTab();
  loadHistory();
});

function updateActiveTab() {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.period === historyPeriod);
  });
}

async function loadHistory() {
  const tbody = document.getElementById("historyTbody");
  tbody.innerHTML = `<tr class="empty-row"><td colspan="7">読み込み中...</td></tr>`;
  try {
    const res = await fetch(`/api/health/score/trend?employeeId=${HEALTH_EMPLOYEE_ID}&period=${historyPeriod}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const trend = await res.json();
    renderHistory(trend.points);
  } catch (e) {
    console.error(e);
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">読み込みに失敗しました</td></tr>`;
  }
}

function renderHistory(points) {
  const tbody = document.getElementById("historyTbody");
  const recorded = points.filter(
    (p) => p.healthScore !== null || p.weightKg !== null || p.sleepHours !== null ||
      p.fatigueLevel !== null || p.stressLevel !== null || p.exerciseMinutes !== null
  );

  if (recorded.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">この期間の記録はありません</td></tr>`;
    return;
  }

  tbody.innerHTML = "";
  recorded.slice().reverse().forEach((p) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${p.date}</td>
      <td>${p.healthScore ?? "-"}</td>
      <td>${p.weightKg ?? "-"}</td>
      <td>${p.sleepHours ?? "-"}</td>
      <td>${p.fatigueLevel ?? "-"}</td>
      <td>${p.stressLevel ?? "-"}</td>
      <td>${p.exerciseMinutes ?? "-"}</td>
    `;
    tbody.appendChild(tr);
  });
}
