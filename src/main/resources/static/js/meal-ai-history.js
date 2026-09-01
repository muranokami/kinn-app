// ------------------------------------------------------------------
// AI献立履歴(過去にAIが提案した献立を日付ごとに振り返る)
// ------------------------------------------------------------------
let aiHistoryPeriod = "1w";

const AI_HISTORY_MEAL_ROWS = [
  { key: "breakfast", icon: "🌅", label: "朝食" },
  { key: "lunch", icon: "☀️", label: "昼食" },
  { key: "dinner", icon: "🌙", label: "夕食" },
];

window.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      aiHistoryPeriod = btn.dataset.period;
      updateAiHistoryActiveTab();
      loadAiHistory();
    });
  });
  updateAiHistoryActiveTab();
  loadAiHistory();
});

function updateAiHistoryActiveTab() {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.period === aiHistoryPeriod);
  });
}

async function loadAiHistory() {
  const listEl = document.getElementById("aiHistoryList");
  listEl.innerHTML = buildSkeletonBlock();
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - healthPeriodDays(aiHistoryPeriod) + 1);
  try {
    const res = await fetch(
      `/api/meal/ai-suggestion/history?employeeId=${HEALTH_EMPLOYEE_ID}&from=${healthFormatDate(from)}&to=${healthFormatDate(to)}`
    );
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const suggestions = await res.json();
    renderAiHistoryList(suggestions.slice().reverse());
  } catch (e) {
    console.error(e);
    listEl.innerHTML = `<p class="alert-empty">読み込みに失敗しました</p>`;
  }
}

function renderAiHistoryList(suggestions) {
  const listEl = document.getElementById("aiHistoryList");
  if (suggestions.length === 0) {
    listEl.innerHTML = `<p class="alert-empty">この期間のAI献立提案はありません</p>`;
    return;
  }

  listEl.innerHTML = "";
  suggestions.forEach((s) => {
    const card = document.createElement("div");
    card.className = "meal-history-day";

    const rows = AI_HISTORY_MEAL_ROWS.map((r) => {
      const meal = s[r.key];
      const text = meal && meal.dishName ? meal.dishName : "提案なし";
      return `<div class="mh-row"><span class="mh-icon">${r.icon}${r.label}</span><span class="mh-text">${escapeAiHistoryHtml(text)}</span></div>`;
    }).join("");

    const savedBadge = s.saved ? `<span class="mh-kcal">⭐ 保存済み</span>` : "";
    const sourceLabel = s.source === "AI" ? "AI" : "ルールベース";

    card.innerHTML = `
      <div class="mh-header">
        <span class="mh-date">${formatAiHistoryDate(s.suggestionDate)}</span>
        ${savedBadge}
      </div>
      ${rows}
      <div class="mh-row"><span class="mh-icon"></span><span class="mh-text" style="color:var(--ink-soft); font-size:12px;">生成方法: ${sourceLabel} / 試行 ${s.attemptNo}回目</span></div>
    `;
    listEl.appendChild(card);
  });
}

function formatAiHistoryDate(dateStr) {
  const d = new Date(dateStr + "T00:00:00");
  const weekday = ["日", "月", "火", "水", "木", "金", "土"][d.getDay()];
  return `${d.getMonth() + 1}/${d.getDate()} (${weekday})`;
}

function escapeAiHistoryHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}
