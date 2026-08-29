// ------------------------------------------------------------------
// 健康管理トップ: 今日の食事ウィジェット
// (食事管理機能と健康ダッシュボードの統合ポイント。Phase 1では表示のみ)
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", loadTodayMealWidget);

async function loadTodayMealWidget() {
  const body = document.getElementById("tmwBody");
  try {
    const res = await fetch(`/api/meal/day?employeeId=${HEALTH_EMPLOYEE_ID}&date=${healthTodayStr()}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const day = await res.json();
    renderTodayMealWidget(day);
  } catch (e) {
    console.error(e);
    body.innerHTML = `<p class="alert-empty">食事記録の読み込みに失敗しました</p>`;
  }
}

function renderTodayMealWidget(day) {
  const body = document.getElementById("tmwBody");
  const rows = [
    { icon: "🌅", label: "朝", list: day.breakfast },
    { icon: "☀️", label: "昼", list: day.lunch },
    { icon: "🌙", label: "夜", list: day.dinner },
  ]
    .map((r) => {
      const text =
        r.list && r.list.length > 0
          ? r.list.map((m) => m.items).filter(Boolean).join("・") || "(内容未記入)"
          : "未入力";
      return `<div class="tmw-row"><span class="tmw-icon">${r.icon}${r.label}</span><span class="tmw-text">${escapeHtml(text)}</span></div>`;
    })
    .join("");

  const n = day.nutrition || {};
  body.innerHTML = `
    ${rows}
    <div class="tmw-nutrition">
      <div class="tmw-nutrition-item"><span class="tmw-n-label">カロリー</span><span class="tmw-n-value">${n.totalCalories ?? "-"}<small>kcal</small></span></div>
      <div class="tmw-nutrition-item"><span class="tmw-n-label">たんぱく質</span><span class="tmw-n-value">${n.totalProteinG ?? "-"}<small>g</small></span></div>
      <div class="tmw-nutrition-item"><span class="tmw-n-label">脂質</span><span class="tmw-n-value">${n.totalFatG ?? "-"}<small>g</small></span></div>
      <div class="tmw-nutrition-item"><span class="tmw-n-label">炭水化物</span><span class="tmw-n-value">${n.totalCarbsG ?? "-"}<small>g</small></span></div>
    </div>
  `;
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}
