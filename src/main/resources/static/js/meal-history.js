// ------------------------------------------------------------------
// 食事履歴(今日/昨日/1週間/1か月の食事内容をまとめて振り返る)
// ------------------------------------------------------------------
let historyPeriod = "1w";

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

function periodRange(period) {
  const from = new Date();
  const to = new Date();
  switch (period) {
    case "yesterday":
      from.setDate(from.getDate() - 1);
      to.setDate(to.getDate() - 1);
      break;
    case "1w":
      from.setDate(from.getDate() - 6);
      break;
    case "1m":
      from.setDate(from.getDate() - 29);
      break;
    default:
      // today: fromもtoも今日のまま
      break;
  }
  return { from, to };
}

async function loadHistory() {
  const listEl = document.getElementById("dayList");
  listEl.innerHTML = buildSkeletonBlock();
  const { from, to } = periodRange(historyPeriod);
  try {
    const res = await fetch(
      `/api/meal/history?employeeId=${HEALTH_EMPLOYEE_ID}&from=${healthFormatDate(from)}&to=${healthFormatDate(to)}`
    );
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const days = await res.json();
    renderCountSummary(days);
    renderDayList(days.slice().reverse());
  } catch (e) {
    console.error(e);
    listEl.innerHTML = `<p class="alert-empty">読み込みに失敗しました</p>`;
  }
}

/** 期間内の朝・昼・夕それぞれの記録日数(食事回数や傾向をひと目で確認できるようにする) */
function renderCountSummary(days) {
  const wrap = document.getElementById("countSummary");
  const breakfastDays = days.filter((d) => d.breakfastRecorded).length;
  const lunchDays = days.filter((d) => d.lunchRecorded).length;
  const dinnerDays = days.filter((d) => d.dinnerRecorded).length;
  const vegDays = days.filter((d) => dayContainsVegetable(d)).length;

  wrap.innerHTML = `
    <div class="compare-card">
      <h3>この期間(${days.length}日)の記録状況</h3>
      <div class="compare-row"><span class="cr-label">🌅 朝食を記録した日</span><span class="cr-value">${breakfastDays}日</span></div>
      <div class="compare-row"><span class="cr-label">☀️ 昼食を記録した日</span><span class="cr-value">${lunchDays}日</span></div>
      <div class="compare-row"><span class="cr-label">🌙 夕食を記録した日</span><span class="cr-value">${dinnerDays}日</span></div>
      <div class="compare-row"><span class="cr-label">🥦 野菜を含む食事があった日</span><span class="cr-value">${vegDays}日</span></div>
    </div>
  `;
}

/** ごく簡易な判定(一般的な野菜の単語が含まれるかどうか)。医療的な栄養評価は行わない */
function dayContainsVegetable(day) {
  const vegWords = ["野菜", "サラダ", "ほうれん草", "キャベツ", "にんじん", "トマト", "ブロッコリー", "きのこ", "もやし", "レタス"];
  const allItems = [...(day.breakfast || []), ...(day.lunch || []), ...(day.dinner || []), ...(day.snacks || [])]
    .map((r) => r.items || "")
    .join(" ");
  return vegWords.some((w) => allItems.includes(w));
}

function renderDayList(days) {
  const listEl = document.getElementById("dayList");
  const recorded = days.filter(
    (d) => d.breakfastRecorded || d.lunchRecorded || d.dinnerRecorded || (d.snacks && d.snacks.length > 0)
  );

  if (recorded.length === 0) {
    listEl.innerHTML = `<p class="alert-empty">この期間の食事記録はありません</p>`;
    return;
  }

  listEl.innerHTML = "";
  recorded.forEach((day) => {
    const card = document.createElement("div");
    card.className = "meal-history-day";
    const dateLabel = formatDateLabel(day.date);

    const rows = [
      { icon: "🌅", label: "朝", list: day.breakfast },
      { icon: "☀️", label: "昼", list: day.lunch },
      { icon: "🌙", label: "夜", list: day.dinner },
    ]
      .map((r) => {
        const text =
          r.list && r.list.length > 0
            ? r.list.map((m) => m.items).filter(Boolean).join(" / ") || "(内容未記入)"
            : "未入力";
        return `<div class="mh-row"><span class="mh-icon">${r.icon}${r.label}</span><span class="mh-text">${escapeHtml(text)}</span></div>`;
      })
      .join("");

    const snackRow =
      day.snacks && day.snacks.length > 0
        ? `<div class="mh-row"><span class="mh-icon">🍪間食</span><span class="mh-text">${escapeHtml(
            day.snacks.map((s) => s.items).filter(Boolean).join(" / ")
          )}</span></div>`
        : "";

    const n = day.nutrition || {};
    const kcalText = n.totalCalories != null ? `${n.totalCalories} kcal` : "";

    card.innerHTML = `
      <div class="mh-header">
        <span class="mh-date">${dateLabel}</span>
        ${kcalText ? `<span class="mh-kcal">${kcalText}</span>` : ""}
      </div>
      ${rows}
      ${snackRow}
    `;
    listEl.appendChild(card);
  });
}

function formatDateLabel(dateStr) {
  const d = new Date(dateStr + "T00:00:00");
  const weekday = ["日", "月", "火", "水", "木", "金", "土"][d.getDay()];
  return `${d.getMonth() + 1}/${d.getDate()} (${weekday})`;
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}
