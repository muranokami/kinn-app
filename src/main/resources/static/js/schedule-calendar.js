// ------------------------------------------------------------------
// スケジュールの月間カレンダー描画の共通処理。
// schedule.js(個人)・admin-schedule.js(部署全体)の両方から使う
// (カレンダーの日付生成・セル描画ロジックを2箇所に重複実装しないため)。
//
// 既存のadmin-attendance.js「従業員別カレンダー(月〜日の7列)」と同じ
// .calendar-grid / .calendar-cell クラス(attendance-extra.css)をそのまま再利用する。
// ------------------------------------------------------------------

const SCHEDULE_WEEKDAY_LABELS_MON_FIRST = ["月", "火", "水", "木", "金", "土", "日"];
/** 1日のマスに表示する予定の最大件数(⑤ 予定が多い日でも見づらくならないように) */
const SCHEDULE_MAX_EVENTS_PER_CELL = 4;

function scheduleFormatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** year年month月(1〜12)の全日付をDateオブジェクトの配列で返す */
function scheduleDaysOfMonth(year, month) {
  const days = [];
  const date = new Date(year, month - 1, 1);
  while (date.getMonth() === month - 1) {
    days.push(new Date(date));
    date.setDate(date.getDate() + 1);
  }
  return days;
}

/** 予定配列を日付(YYYY-MM-DD)ごとにグルーピングする */
function scheduleGroupByDate(events, dateField) {
  const map = {};
  (events || []).forEach((ev) => {
    const key = ev[dateField];
    if (!map[key]) map[key] = [];
    map[key].push(ev);
  });
  return map;
}

/**
 * 月間カレンダーを描画する。
 * @param gridEl 描画先の要素(.calendar-gridクラスを持つ)
 * @param year 表示年
 * @param month 表示月(1〜12)
 * @param eventsByDate scheduleGroupByDateで作った日付ごとの予定
 * @param renderLine 1件の予定を1行のテキスト(またはノード)に変換する関数(ev) => string
 * @param onEventClick 予定クリック時のコールバック(ev, dateStr)
 * @param onDayClick 省略可。日付セル自体(予定が無い部分)クリック時のコールバック(dateStr) => void。
 *   個人スケジュール画面(schedule.js)の「日付をクリックしてその日の予定を追加」(⑫)用。
 *   既存呼び出し元(admin-schedule.js)は渡さないため、その画面の挙動は変わらない。
 */
function renderScheduleCalendar(gridEl, year, month, eventsByDate, renderLine, onEventClick, onDayClick) {
  gridEl.innerHTML = "";

  SCHEDULE_WEEKDAY_LABELS_MON_FIRST.forEach((label) => {
    const head = document.createElement("div");
    head.className = "calendar-head";
    head.textContent = label;
    gridEl.appendChild(head);
  });

  const days = scheduleDaysOfMonth(year, month);
  if (days.length === 0) return;

  // 月曜=0 になるようずらす(JSのgetDay()は日曜=0)
  const leadingBlanks = (days[0].getDay() + 6) % 7;
  for (let i = 0; i < leadingBlanks; i++) {
    const blank = document.createElement("div");
    blank.className = "calendar-cell calendar-cell-blank";
    gridEl.appendChild(blank);
  }

  const todayStr = scheduleFormatDate(new Date());

  days.forEach((date) => {
    const dateStr = scheduleFormatDate(date);
    const weekday = date.getDay();
    const cell = document.createElement("div");
    cell.className = "calendar-cell schedule-cell";
    if (weekday === 0) cell.classList.add("is-sunday");
    if (weekday === 6) cell.classList.add("is-saturday");
    if (dateStr === todayStr) cell.classList.add("is-today");
    if (onDayClick) {
      cell.classList.add("is-day-clickable");
      // 予定の行(.schedule-cell-event)はクリック時にstopPropagationしているため、
      // ここには「予定が無い部分」をクリックしたときだけ届く
      cell.addEventListener("click", () => onDayClick(dateStr));
    }

    const dateLabel = document.createElement("div");
    dateLabel.className = "calendar-cell-date";
    dateLabel.textContent = date.getDate();
    cell.appendChild(dateLabel);

    const events = eventsByDate[dateStr] || [];
    const eventList = document.createElement("div");
    eventList.className = "schedule-cell-events";

    events.slice(0, SCHEDULE_MAX_EVENTS_PER_CELL).forEach((ev) => {
      const line = document.createElement("div");
      // scheduleType(PERSONAL/DEPARTMENT)を持つイベントのみ区別用クラスを追加する(⑫)。
      // 既存呼び出し元(admin-schedule.js)の行はscheduleTypeを持たないため影響しない。
      const typeClass = ev.scheduleType ? ` schedule-type-${ev.scheduleType}` : "";
      line.className = `schedule-cell-event category-${ev.category}${typeClass}`;
      line.textContent = renderLine(ev);
      if (onEventClick) {
        line.addEventListener("click", (e) => {
          e.stopPropagation();
          onEventClick(ev, dateStr);
        });
      }
      eventList.appendChild(line);
    });

    if (events.length > SCHEDULE_MAX_EVENTS_PER_CELL) {
      const more = document.createElement("div");
      more.className = "schedule-cell-more";
      more.textContent = `+${events.length - SCHEDULE_MAX_EVENTS_PER_CELL}件`;
      eventList.appendChild(more);
    }

    cell.appendChild(eventList);
    gridEl.appendChild(cell);
  });
}
