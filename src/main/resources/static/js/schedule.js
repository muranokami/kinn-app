// ------------------------------------------------------------------
// スケジュール画面(一般ユーザー)。
//
// [個人予定][部署共有予定][すべて] の3タブ(④⑬)を切り替えられるようにする。
// ・個人予定タブ: 従来どおりの挙動(追加・編集・削除フォーム+一覧+月次集計)を一切変更しない。
// ・部署共有予定タブ: GET /api/schedule/department/{year}/{month} を使い、ログイン中ユーザーが
//   所属する部署の共有スケジュールを閲覧のみで表示する(⑥⑨⑩⑪⑳。登録・編集・削除は管理者のみ⑧)。
// ・すべてタブ: GET /api/schedule/all/{year}/{month} で個人予定+部署共有予定を1つのカレンダーに
//   合成表示する(⑬)。カレンダー上は scheduleType に応じたバッジ・枠色で区別する(⑫)。
//
// どのタブでも、カレンダー・今日の予定パネル・詳細モーダルは同じ描画関数を共用する。
// 3種類のAPIレスポンス形式(ScheduleEventDto / DepartmentScheduleEventDto / ScheduleFeedEventDto)を
// 都度同じ「フィード形式」に正規化してから渡すことで、描画ロジックを重複させない。
// ------------------------------------------------------------------

const CATEGORIES = [
  { value: "WORK", label: "仕事" },
  { value: "MEETING", label: "会議" },
  { value: "PRIVATE", label: "プライベート" },
  { value: "HEALTH", label: "通院・健康" },
  { value: "OTHER", label: "その他" },
];

const WEEKDAY_LABELS = ["日", "月", "火", "水", "木", "金", "土"];

let currentYear;
let currentMonth;
let editingId = null;
let editingType = null; // "PERSONAL" | "DEPARTMENT" | null(新規追加時)。編集対象がどちらのAPIか
let currentMode = "personal"; // "personal" | "department" | "all"
let currentUser = null; // /api/auth/me の結果(部署名・権限・userIdの表示/判定に使う)
let lastEvents = []; // 直近に描画したカレンダーの正規化済みイベント(⑫日別モーダル用)
let pendingDeleteEvent = null; // 削除確認モーダルで確認待ちの予定(⑥⑭)

/** サーバーからのエラーメッセージ(ScheduleExceptionHandlerが返すJSONの message)を画面に出す(㉔) */
function readErrorMessage(res, fallback) {
  return res.json().then((body) => body?.message || fallback).catch(() => fallback);
}

/**
 * 部署共有スケジュールを編集・削除できるか(⑭⑮⑯)。登録者本人、または管理者のみ true。
 * 画面側の表示制御用(バックエンド側でも必ず同じ条件を確認する。DepartmentScheduleService参照)。
 * 個人予定はAPIが常に自分の予定しか返さないため、この判定は不要(呼び出し不要)。
 */
function canManageDepartmentEvent(ev) {
  if (!currentUser) return false;
  if (currentUser.role === "ADMIN") return true;
  return currentUser.userId != null && ev.createdByUserId != null && ev.createdByUserId === currentUser.userId;
}

// ------------------------------------------------------------------
// 初期化
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", async () => {
  const now = new Date();
  currentYear = now.getFullYear();
  currentMonth = now.getMonth() + 1;

  const monthSelect = document.getElementById("monthSelect");
  for (let m = 1; m <= 12; m++) {
    const opt = document.createElement("option");
    opt.value = m;
    opt.textContent = m;
    monthSelect.appendChild(opt);
  }

  document.getElementById("yearInput").value = currentYear;
  monthSelect.value = currentMonth;
  document.getElementById("f_eventDate").value = formatDate(now);

  document.getElementById("prevMonthBtn").addEventListener("click", () => shiftMonth(-1));
  document.getElementById("nextMonthBtn").addEventListener("click", () => shiftMonth(1));
  document.getElementById("todayBtn").addEventListener("click", () => goToToday());
  document.getElementById("loadBtn").addEventListener("click", () => loadMonth());
  document.getElementById("yearInput").addEventListener("change", () => loadMonth());
  monthSelect.addEventListener("change", () => loadMonth());

  document.querySelectorAll("#scopeTabs [data-mode]").forEach((btn) => {
    btn.addEventListener("click", () => setMode(btn.dataset.mode));
  });

  document.getElementById("eventForm").addEventListener("submit", onSubmit);
  document.getElementById("cancelFormBtn").addEventListener("click", closeForm);
  document.getElementById("addScheduleBtn").addEventListener("click", () => {
    // ②部署共有予定タブから追加した場合は「部署共有予定」を初期選択にする
    openAddForm(null, currentMode === "department" ? "DEPARTMENT" : "PERSONAL");
  });

  document.getElementById("detailCloseBtn").addEventListener("click", closeDetailModal);
  document.getElementById("detailEditBtn").addEventListener("click", () => {
    if (!detailEvent) return;
    const ev = detailEvent;
    closeDetailModal();
    openEditForm(ev);
  });
  document.getElementById("detailDeleteBtn").addEventListener("click", () => {
    if (!detailEvent) return;
    const ev = detailEvent;
    closeDetailModal();
    requestDelete(ev);
  });

  document.getElementById("deleteConfirmCancelBtn").addEventListener("click", closeDeleteConfirm);
  document.getElementById("deleteConfirmOkBtn").addEventListener("click", async () => {
    const ev = pendingDeleteEvent;
    closeDeleteConfirm();
    if (ev) await deleteEvent(ev);
  });

  document.getElementById("dayModalCloseBtn").addEventListener("click", closeDayModal);
  document.getElementById("dayAddBtn").addEventListener("click", () => {
    const date = currentDayDate;
    closeDayModal();
    openAddForm(date, currentMode === "department" ? "DEPARTMENT" : "PERSONAL");
  });

  await loadCurrentUser();
  loadMonth();
});

/** ログイン中ユーザーの会社・部署・権限を取得する(⑨㉒自動的に自分の部署を解決するため) */
async function loadCurrentUser() {
  try {
    const res = await fetch("/api/auth/me");
    if (!res.ok) return;
    currentUser = await res.json();
    if (currentUser.role === "ADMIN") {
      document.getElementById("adminScheduleLink").hidden = false;
    }
  } catch (e) {
    console.error(e);
  }
}

function goToToday() {
  const now = new Date();
  document.getElementById("yearInput").value = now.getFullYear();
  document.getElementById("monthSelect").value = now.getMonth() + 1;
  loadMonth();
}

function shiftMonth(delta) {
  let y = parseInt(document.getElementById("yearInput").value, 10);
  let m = parseInt(document.getElementById("monthSelect").value, 10) + delta;
  if (m < 1) { m = 12; y -= 1; }
  if (m > 12) { m = 1; y += 1; }
  document.getElementById("yearInput").value = y;
  document.getElementById("monthSelect").value = m;
  loadMonth();
}

function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

// ------------------------------------------------------------------
// タブ切り替え(④⑬)
// ------------------------------------------------------------------
function setMode(mode) {
  if (mode === currentMode) return;
  currentMode = mode;

  document.querySelectorAll("#scopeTabs [data-mode]").forEach((btn) => {
    btn.classList.toggle("is-active", btn.dataset.mode === mode);
  });

  // 個人予定タブでのみ、一覧/月次集計を表示する(部署共有予定は
  // 一般ユーザーは登録できないため⑨⑳。管理者による登録・編集・削除は管理者ページで行う⑧)
  const isPersonal = mode === "personal";
  document.getElementById("personalSummarySection").hidden = !isPersonal;
  document.getElementById("personalListSection").hidden = !isPersonal;
  document.getElementById("departmentListSection").hidden = mode === "personal";
  // [スケジュールを追加](①)はどのタブでも出す。一般ユーザーも部署共有予定を登録できるように
  // なったため(⑥)、部署共有予定タブでも隠さない(フォーム内の「種類」で個人/部署を選べる②)

  closeForm(); // タブを切り替えたら、開いていた追加・編集フォームは必ず閉じる(⑤⑳)

  loadMonth();
}

// ------------------------------------------------------------------
// データ取得・描画
// ------------------------------------------------------------------
async function loadMonth() {
  currentYear = parseInt(document.getElementById("yearInput").value, 10);
  currentMonth = parseInt(document.getElementById("monthSelect").value, 10);
  setStatus("読み込み中...", false, true);

  try {
    if (currentMode === "personal") {
      await loadPersonalMonth();
    } else if (currentMode === "department") {
      await loadDepartmentMonth();
    } else {
      await loadAllMonth();
    }
    setStatus("読み込みました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "読み込みエラー", true);
  }
}

async function loadPersonalMonth() {
  document.getElementById("scopeNote").textContent = "";
  document.getElementById("todayPanelTitle").textContent = "今日の予定";
  document.getElementById("calendarTitle").textContent = `${currentYear}年${currentMonth}月`;

  const res = await fetch(`/api/schedule/${currentYear}/${currentMonth}`);
  if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
  const data = await res.json();

  renderList(data.events);
  renderSummary(data.summary);
  const normalized = data.events.map(normalizePersonalEvent);
  renderCalendarView(normalized);
  renderTodayPanel(normalized);
}

async function loadDepartmentMonth() {
  const res = await fetch(`/api/schedule/department/${currentYear}/${currentMonth}`);
  if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
  const data = await res.json();

  const deptLabel = data.departmentName || "未所属";
  document.getElementById("scopeNote").textContent = data.departmentName
    ? `${deptLabel}の共有スケジュール(部署メンバー全員が同じ内容を閲覧できます)`
    : "部署が設定されていないため、部署共有スケジュールはありません。管理者に部署の設定を依頼してください。";
  document.getElementById("todayPanelTitle").textContent = `今日の部署予定${data.departmentName ? "（" + deptLabel + "）" : ""}`;
  document.getElementById("calendarTitle").textContent = `${deptLabel} ${currentYear}年${currentMonth}月`;
  document.getElementById("departmentListTitle").textContent = `${deptLabel}共有スケジュール一覧`;

  const normalized = data.events.map(normalizeDepartmentEvent);
  renderCalendarView(normalized);
  renderTodayPanel(normalized);
  renderDepartmentList(normalized);
}

async function loadAllMonth() {
  const deptLabel = currentUser && currentUser.departmentName ? currentUser.departmentName : "所属部署";
  document.getElementById("scopeNote").textContent =
    `自分の個人予定と、${deptLabel}の共有予定をまとめて表示しています。`;
  document.getElementById("todayPanelTitle").textContent = "今日の予定(個人＋部署)";
  document.getElementById("calendarTitle").textContent = `${currentYear}年${currentMonth}月`;

  const res = await fetch(`/api/schedule/all/${currentYear}/${currentMonth}`);
  if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
  const feed = await res.json();

  const normalized = feed.map(normalizeFeedEvent);
  renderCalendarView(normalized);
  renderTodayPanel(normalized);
}

// ------------------------------------------------------------------
// 正規化: 個人(ScheduleEventDto) / 部署共有(DepartmentScheduleEventDto) / 統合フィード
// (ScheduleFeedEventDto) の3種類のAPIレスポンスを、カレンダー・今日の予定・詳細モーダルが
// 共通で扱える1つの形へそろえる(⑫個人/部署の区別に必要な scheduleType もここで付与する)。
// ------------------------------------------------------------------
function normalizePersonalEvent(e) {
  return {
    id: e.id,
    scheduleType: "PERSONAL",
    eventDate: e.eventDate,
    startTime: e.startTime,
    endTime: e.endTime,
    title: e.title,
    category: e.category,
    categoryLabel: categoryLabel(e.category),
    content: e.memo,
    memo: e.memo,
    location: e.location,
    departmentName: null,
    createdByUserId: null,
    createdByName: null,
  };
}

function normalizeDepartmentEvent(e) {
  return {
    id: e.id,
    scheduleType: "DEPARTMENT",
    eventDate: e.eventDate,
    startTime: e.startTime,
    endTime: e.endTime,
    title: e.title,
    category: e.category,
    categoryLabel: e.categoryLabel || categoryLabel(e.category),
    content: e.content,
    memo: e.content,
    location: e.location,
    departmentName: e.departmentName,
    createdByUserId: e.createdByUserId,
    createdByName: e.createdByName,
  };
}

function normalizeFeedEvent(e) {
  return {
    id: e.id,
    scheduleType: e.scheduleType,
    eventDate: e.eventDate,
    startTime: e.startTime,
    endTime: e.endTime,
    title: e.title,
    category: e.category,
    categoryLabel: e.categoryLabel || categoryLabel(e.category),
    content: e.content,
    memo: e.content,
    location: e.location,
    departmentName: e.departmentName,
    createdByUserId: e.createdByUserId,
    createdByName: e.createdByName,
  };
}

function categoryLabel(value) {
  return (CATEGORIES.find((c) => c.value === value) || {}).label || value;
}

// ------------------------------------------------------------------
// カレンダー表示(③⑤) + 今日の予定(⑲) + 詳細モーダル(⑥⑫⑯)
// ------------------------------------------------------------------
let detailEvent = null;
let currentDayDate = null;

function renderCalendarView(events) {
  lastEvents = events;
  const eventsByDate = scheduleGroupByDate(events, "eventDate");
  const grid = document.getElementById("calendarGrid");
  // どのタブでも、日付クリックでその日の予定を確認・追加できる(⑫)
  renderScheduleCalendar(grid, currentYear, currentMonth, eventsByDate, scheduleEventLine, openDetailModal, openDayModal);
}

/** カレンダーの日付をクリックしたときの「その日の予定」モーダル(⑫⑬⑭) */
function openDayModal(dateStr) {
  currentDayDate = dateStr;
  const date = new Date(dateStr + "T00:00:00");
  document.getElementById("dayModalTitle").textContent =
    `${date.getMonth() + 1}月${date.getDate()}日(${WEEKDAY_LABELS[date.getDay()]})の予定`;

  const events = lastEvents
    .filter((ev) => ev.eventDate === dateStr)
    .sort((a, b) => (a.startTime || "").localeCompare(b.startTime || ""));

  const listEl = document.getElementById("dayModalList");
  listEl.innerHTML = "";
  if (events.length === 0) {
    listEl.innerHTML = `<p class="snack-empty">この日の予定はまだ登録されていません。</p>`;
  } else {
    events.forEach((ev) => {
      const item = document.createElement("div");
      item.className = "today-event-item";
      const time = ev.startTime
        ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " - " + ev.endTime.substring(0, 5) : ""}`
        : "終日";
      const badge = `<span class="type-badge ${ev.scheduleType === "DEPARTMENT" ? "type-badge-department" : "type-badge-personal"}">${escapeHtml(scheduleTypeLabel(ev))}</span>`;
      item.innerHTML = `
        <span class="today-event-time">${time}</span>
        ${badge}
        <span class="today-event-title">${escapeHtml(ev.title)}</span>
      `;
      item.addEventListener("click", () => {
        closeDayModal();
        openDetailModal(ev);
      });
      listEl.appendChild(item);
    });
  }

  document.getElementById("dayModal").hidden = false;
}

function closeDayModal() {
  document.getElementById("dayModal").hidden = true;
}

/**
 * カレンダー・今日の予定・日別モーダルで、予定の種類を表すバッジ/接頭辞に使うラベル。
 * 部署共有予定は一律「部署」ではなく、実際の部署名(例:「営業部」)を表示する
 * (複数部署の予定が混在する「すべて」表示などで、どの部署の予定か一目で分かるようにするため)。
 */
function scheduleTypeLabel(ev) {
  if (ev.scheduleType === "DEPARTMENT") return ev.departmentName || "部署共有";
  return "個人";
}

function scheduleEventLine(ev) {
  const time = ev.startTime ? ev.startTime.substring(0, 5) : "終日";
  const prefix = ev.scheduleType === "DEPARTMENT" ? `[${scheduleTypeLabel(ev)}] ` : currentMode === "all" ? "[個人] " : "";
  return `${prefix}${time} ${ev.title}`;
}

function renderTodayPanel(events) {
  const todayStr = formatDate(new Date());
  const todays = (events || [])
    .filter((ev) => ev.eventDate === todayStr)
    .sort((a, b) => (a.startTime || "").localeCompare(b.startTime || ""));

  const container = document.getElementById("todayEvents");
  container.innerHTML = "";
  if (todays.length === 0) {
    container.innerHTML = `<p class="snack-empty">今日の予定はありません。</p>`;
    return;
  }
  todays.forEach((ev) => {
    const item = document.createElement("div");
    item.className = "today-event-item";
    const time = ev.startTime
      ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " - " + ev.endTime.substring(0, 5) : ""}`
      : "終日";
    const badge = currentMode === "all"
      ? `<span class="type-badge ${ev.scheduleType === "DEPARTMENT" ? "type-badge-department" : "type-badge-personal"}">${escapeHtml(scheduleTypeLabel(ev))}</span>`
      : "";
    item.innerHTML = `
      <span class="today-event-time">${time}</span>
      ${badge}
      <span class="today-event-title">${escapeHtml(ev.title)}</span>
    `;
    item.addEventListener("click", () => openDetailModal(ev));
    container.appendChild(item);
  });
}

/** 部署共有スケジュール一覧(⑥⑱⑳)。誰が登録したかより「何が予定されているか」を優先する */
function renderDepartmentList(events) {
  const container = document.getElementById("departmentScheduleList");
  container.innerHTML = "";
  const sorted = [...events].sort((a, b) =>
    a.eventDate === b.eventDate
      ? (a.startTime || "").localeCompare(b.startTime || "")
      : a.eventDate.localeCompare(b.eventDate)
  );

  if (sorted.length === 0) {
    container.innerHTML = `<p class="snack-empty">この月の部署共有スケジュールはまだ登録されていません。</p>`;
    return;
  }

  sorted.forEach((ev) => {
    const date = new Date(ev.eventDate + "T00:00:00");
    const row = document.createElement("div");
    row.className = "dept-schedule-row";
    const timeText = ev.startTime
      ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " - " + ev.endTime.substring(0, 5) : ""}`
      : "終日";
    const sub = [ev.location, ev.content].filter(Boolean).join(" ／ ");
    row.innerHTML = `
      <span class="dsr-date">${date.getMonth() + 1}/${date.getDate()}(${WEEKDAY_LABELS[date.getDay()]})</span>
      <span class="dsr-time">${timeText}</span>
      <span class="dsr-main">
        <span class="dsr-title">${escapeHtml(ev.title)}</span>
        ${sub ? `<span class="dsr-sub">${escapeHtml(sub)}</span>` : ""}
      </span>
      <span class="dsr-actions"></span>
    `;
    row.addEventListener("click", () => openDetailModal(ev));

    // 登録者本人・管理者だけ、一覧からも直接編集・削除できるようにする(⑭⑮⑯)
    if (canManageDepartmentEvent(ev)) {
      const actionsEl = row.querySelector(".dsr-actions");
      const editBtn = document.createElement("button");
      editBtn.type = "button";
      editBtn.className = "btn btn-secondary btn-sm";
      editBtn.textContent = "編集";
      editBtn.addEventListener("click", (e) => { e.stopPropagation(); openEditForm(ev); });
      actionsEl.appendChild(editBtn);

      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "btn btn-danger btn-sm";
      delBtn.textContent = "削除";
      delBtn.addEventListener("click", (e) => { e.stopPropagation(); requestDelete(ev); });
      actionsEl.appendChild(delBtn);
    }

    container.appendChild(row);
  });
}

function openDetailModal(ev) {
  detailEvent = ev;
  const date = new Date(ev.eventDate + "T00:00:00");
  const weekday = WEEKDAY_LABELS[date.getDay()];
  const timeText = ev.startTime
    ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " ～ " + ev.endTime.substring(0, 5) : ""}`
    : "終日";
  const isDepartment = ev.scheduleType === "DEPARTMENT";

  let rows = `
    <dt>種別</dt><dd><span class="type-badge ${isDepartment ? "type-badge-department" : "type-badge-personal"}">${isDepartment ? "部署共有予定" : "個人予定"}</span></dd>
    <dt>日付</dt><dd>${date.getMonth() + 1}/${date.getDate()}(${weekday})</dd>
    <dt>時間</dt><dd>${timeText}</dd>
    <dt>タイトル</dt><dd>${escapeHtml(ev.title)}</dd>
    <dt>分類</dt><dd><span class="category-badge category-${ev.category}">${ev.categoryLabel}</span></dd>
  `;
  if (isDepartment) {
    rows += `<dt>部署</dt><dd>${escapeHtml(ev.departmentName || "-")}</dd>`;
    if (ev.createdByName) {
      rows += `<dt>登録者</dt><dd>${escapeHtml(ev.createdByName)}</dd>`;
    }
  }
  rows += `
    <dt>場所</dt><dd>${escapeHtml(ev.location || "(なし)")}</dd>
    <dt>内容・備考</dt><dd>${escapeHtml(ev.content || "(なし)")}</dd>
  `;
  document.getElementById("detailGrid").innerHTML = rows;

  // 個人予定は常に自分の予定(APIが他人の予定を返さない)。部署共有予定は登録者本人・
  // 管理者のみ編集・削除できる(⑭⑮⑯。他ユーザーが登録した分は閲覧のみ)
  const canManage = isDepartment ? canManageDepartmentEvent(ev) : true;
  document.getElementById("detailEditBtn").hidden = !canManage;
  document.getElementById("detailDeleteBtn").hidden = !canManage;

  document.getElementById("detailModal").hidden = false;
}

function closeDetailModal() {
  document.getElementById("detailModal").hidden = true;
  detailEvent = null;
}

/**
 * 削除確認モーダルを開く(⑥⑭「このスケジュールを削除しますか？」[キャンセル][削除する])。
 * ブラウザ標準のconfirm()は使わない(ボタン文言を指定どおりにできないため)。
 */
function requestDelete(ev) {
  pendingDeleteEvent = ev;
  const date = new Date(ev.eventDate + "T00:00:00");
  document.getElementById("deleteConfirmSummary").textContent =
    `${date.getMonth() + 1}/${date.getDate()} ${ev.title}`;
  document.getElementById("deleteConfirmModal").hidden = false;
}

function closeDeleteConfirm() {
  document.getElementById("deleteConfirmModal").hidden = true;
  pendingDeleteEvent = null;
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

function renderList(events) {
  const tbody = document.getElementById("eventTbody");
  tbody.innerHTML = "";

  if (!events || events.length === 0) {
    const tr = document.createElement("tr");
    tr.className = "empty-row";
    const td = document.createElement("td");
    td.colSpan = 8;
    td.textContent = "この月の予定はまだ登録されていません。";
    tr.appendChild(td);
    tbody.appendChild(tr);
    return;
  }

  events.forEach((ev) => {
    const date = new Date(ev.eventDate + "T00:00:00");
    const weekday = date.getDay();

    const tr = document.createElement("tr");
    if (weekday === 6) tr.classList.add("is-saturday");
    if (weekday === 0) tr.classList.add("is-sunday");

    tr.appendChild(cellText(`${date.getMonth() + 1}/${date.getDate()}`));
    tr.appendChild(cellText(WEEKDAY_LABELS[weekday]));

    const timeText = ev.startTime
      ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " - " + ev.endTime.substring(0, 5) : ""}`
      : "終日";
    tr.appendChild(cellText(timeText));

    tr.appendChild(cellText(ev.title));

    const catTd = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = `category-badge category-${ev.category}`;
    badge.textContent = categoryLabel(ev.category);
    catTd.appendChild(badge);
    tr.appendChild(catTd);

    tr.appendChild(cellText(ev.location || ""));
    tr.appendChild(cellText(ev.memo || ""));

    const actionTd = document.createElement("td");
    const actions = document.createElement("div");
    actions.className = "row-actions";

    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "btn btn-secondary btn-sm";
    editBtn.textContent = "編集";
    editBtn.addEventListener("click", () => openEditForm({ ...ev, scheduleType: "PERSONAL" }));
    actions.appendChild(editBtn);

    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "削除";
    delBtn.addEventListener("click", () => requestDelete({ ...ev, scheduleType: "PERSONAL" }));
    actions.appendChild(delBtn);

    actionTd.appendChild(actions);
    tr.appendChild(actionTd);

    tbody.appendChild(tr);
  });
}

function cellText(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

function renderSummary(s) {
  setVal("s_totalEvents", s.totalEvents);
  setVal("s_workCount", s.workCount);
  setVal("s_meetingCount", s.meetingCount);
  setVal("s_privateCount", s.privateCount);
  setVal("s_healthCount", s.healthCount);
  setVal("s_otherCount", s.otherCount);
}

function setVal(id, value) {
  document.getElementById(id).textContent = value;
}

// ------------------------------------------------------------------
// フォーム(追加・編集)。個人予定・部署共有予定の両方をこの1つのフォームで扱う(②)。
// どちらの種類で保存するかはフォーム内の「種類」ラジオ(f_scheduleType)で選ぶ。
// ①[スケジュールを追加]ボタン・⑫カレンダーの日付クリック・⑬詳細の[編集]の
// いずれからも、この同じフォームを開く。
// ------------------------------------------------------------------

/** フォームの「種類」ラジオを設定する。編集時(locked=true)は種類の変更を許可しない(⑪) */
function setFormType(type, locked) {
  document.querySelectorAll('input[name="f_scheduleType"]').forEach((r) => {
    r.checked = r.value === type;
    r.disabled = !!locked;
  });
  document.getElementById("typeField").classList.toggle("is-locked", !!locked);
}

function selectedFormType() {
  const checked = document.querySelector('input[name="f_scheduleType"]:checked');
  return checked ? checked.value : "PERSONAL";
}

/**
 * [スケジュールを追加](①⑫)。dateStrを渡すと、その日付をあらかじめ入力しておく。
 * defaultTypeで初期選択する種類を指定できる(②部署共有予定タブから追加した場合は
 * "DEPARTMENT" を渡す)。種類はここでは変更可能(⑪どの画面から追加したかではなく、
 * 登録時に選択した種類を基準にするため)。
 */
function openAddForm(dateStr, defaultType) {
  editingId = null;
  editingType = null;
  document.getElementById("formTitle").textContent = "予定を追加";
  document.getElementById("submitBtn").textContent = "追加";
  document.getElementById("eventForm").reset();
  document.getElementById("f_eventDate").value = dateStr || formatDate(new Date());
  document.getElementById("f_category").value = "OTHER";
  setFormType(defaultType || "PERSONAL", false);
  document.getElementById("scheduleFormSection").hidden = false;
  document.getElementById("formTitle").scrollIntoView({ behavior: "smooth", block: "center" });
}

/**
 * 予定を編集(⑤⑭)。個人予定は常に自分の予定(⑦⑰APIが他人の予定を返さない)。
 * 部署共有予定は登録者本人・管理者のみ(⑭⑯。念のためここでも二重チェックする)。
 * 編集中は種類を変更できないようにする(⑪。登録後の種類変更は対象外の設計判断)。
 */
function openEditForm(ev) {
  if (ev.scheduleType === "DEPARTMENT" && !canManageDepartmentEvent(ev)) return;
  editingId = ev.id;
  editingType = ev.scheduleType || "PERSONAL";
  document.getElementById("formTitle").textContent = "予定を編集";
  document.getElementById("submitBtn").textContent = "更新";

  document.getElementById("f_eventDate").value = ev.eventDate;
  document.getElementById("f_startTime").value = ev.startTime ? ev.startTime.substring(0, 5) : "";
  document.getElementById("f_endTime").value = ev.endTime ? ev.endTime.substring(0, 5) : "";
  document.getElementById("f_title").value = ev.title;
  document.getElementById("f_category").value = ev.category;
  document.getElementById("f_location").value = ev.location || "";
  document.getElementById("f_memo").value = ev.memo || "";
  setFormType(editingType, true);
  document.getElementById("scheduleFormSection").hidden = false;

  document.getElementById("formTitle").scrollIntoView({ behavior: "smooth", block: "center" });
}

/**
 * [キャンセル](㉕)。保存処理は一切実行せず、フォームを閉じて一覧・カレンダーへ戻る。
 * 「キャンセルを押しても遷移しない」不具合の修正: 以前はフォーム自体を閉じておらず、
 * 常に画面に表示されたままだった。ここで確実に非表示に戻す。
 */
function closeForm() {
  editingId = null;
  editingType = null;
  document.getElementById("formTitle").textContent = "予定を追加";
  document.getElementById("submitBtn").textContent = "追加";
  document.getElementById("eventForm").reset();
  document.getElementById("f_eventDate").value = formatDate(new Date());
  document.getElementById("f_category").value = "OTHER";
  setFormType("PERSONAL", false);
  document.getElementById("scheduleFormSection").hidden = true;
}

async function onSubmit(e) {
  e.preventDefault();
  const type = selectedFormType();

  const commonFields = {
    eventDate: document.getElementById("f_eventDate").value,
    startTime: toTimeOrNull(document.getElementById("f_startTime").value),
    endTime: toTimeOrNull(document.getElementById("f_endTime").value),
    title: document.getElementById("f_title").value.trim(),
    category: document.getElementById("f_category").value,
    location: document.getElementById("f_location").value.trim() || null,
  };
  const text = document.getElementById("f_memo").value.trim();
  // 個人予定APIは"memo"、部署共有予定APIは"content"というキー名の違いだけを吸収する
  const payload = type === "DEPARTMENT" ? { ...commonFields, content: text } : { ...commonFields, memo: text };

  // ㉓ 入力チェック(サーバー側でも必ず同じ内容を検証する。ここはユーザーへの即時フィードバック用)
  if (!payload.eventDate) {
    setStatus("日付は必須です", true);
    return;
  }
  if (!payload.title) {
    setStatus("タイトルは必須です", true);
    return;
  }
  if (payload.startTime && payload.endTime && payload.endTime < payload.startTime) {
    setStatus("終了時間は開始時間より前にできません", true);
    return;
  }

  setStatus(editingId ? "更新中..." : "追加中...", false, true);
  try {
    const apiBase = type === "DEPARTMENT" ? "/api/schedule/department" : "/api/schedule";
    const url = editingId ? `${apiBase}/${editingId}` : apiBase;
    const method = editingId ? "PUT" : "POST";

    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      throw new Error(await readErrorMessage(
          res, editingId ? "スケジュールの更新に失敗しました。" : "スケジュールの登録に失敗しました。"));
    }

    setStatus(editingId ? "更新しました" : "追加しました", false);
    closeForm();

    // 追加・更新した予定の月に合わせて表示を切り替える
    const [y, m] = payload.eventDate.split("-").map(Number);
    document.getElementById("yearInput").value = y;
    document.getElementById("monthSelect").value = m;
    loadMonth();
  } catch (err) {
    console.error(err);
    setStatus(err.message || "保存エラー", true);
  }
}

function toTimeOrNull(hhmm) {
  return hhmm ? hhmm + ":00" : null;
}

/** 削除確認モーダルで[削除する]が押された後に呼ばれる(⑥⑭⑮。確認自体はrequestDeleteが担当) */
async function deleteEvent(ev) {
  const id = ev.id;
  const type = ev.scheduleType || "PERSONAL";
  setStatus("削除中...", false, true);
  try {
    const url = type === "DEPARTMENT" ? `/api/schedule/department/${id}` : `/api/schedule/${id}`;
    const res = await fetch(url, { method: "DELETE" });
    if (!res.ok) throw new Error(await readErrorMessage(res, "スケジュールの削除に失敗しました。"));
    setStatus("削除しました", false);
    if (editingId === id) closeForm();
    loadMonth();
  } catch (e) {
    console.error(e);
    setStatus(e.message || "削除エラー", true);
  }
}

function setStatus(msg, isError, isLoading) {
  const el = document.getElementById("statusMsg");
  el.textContent = msg;
  el.classList.toggle("error", !!isError);
  el.classList.toggle("is-loading", !!isLoading);
}
