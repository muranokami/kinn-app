// ------------------------------------------------------------------
// 管理者向け部署別スケジュール管理(①②③)。
// カレンダー描画はschedule-calendar.js(個人ページと共通)を使う。
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
let currentDepartmentId = ""; // "" = 全部署
let departmentsCache = [];

function categoryLabel(value) {
  return (CATEGORIES.find((c) => c.value === value) || {}).label || value;
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

function readErrorMessage(res, fallback) {
  return res.json().then((body) => body?.message || fallback).catch(() => fallback);
}

// ------------------------------------------------------------------
// 初期化
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", async () => {
  const now = new Date();
  currentYear = now.getFullYear();
  currentMonth = now.getMonth() + 1;

  document.getElementById("prevMonthBtn").addEventListener("click", () => shiftMonth(-1));
  document.getElementById("nextMonthBtn").addEventListener("click", () => shiftMonth(1));
  document.getElementById("todayBtn").addEventListener("click", () => goToToday());
  document.getElementById("departmentFilter").addEventListener("change", onDepartmentFilterChange);
  document.getElementById("c_department").addEventListener("change", onCreateDepartmentChange);
  document.getElementById("createForm").addEventListener("submit", onCreateSubmit);

  document.getElementById("editCloseBtn").addEventListener("click", closeEditModal);
  document.getElementById("editForm").addEventListener("submit", onEditSubmit);
  document.getElementById("editDeleteBtn").addEventListener("click", onEditDelete);

  document.getElementById("sharedCreateForm").addEventListener("submit", onSharedCreateSubmit);
  document.getElementById("sharedEditCloseBtn").addEventListener("click", closeSharedEditModal);
  document.getElementById("sharedEditForm").addEventListener("submit", onSharedEditSubmit);
  document.getElementById("sharedEditDeleteBtn").addEventListener("click", onSharedEditDelete);

  await loadDepartments();
  await onCreateDepartmentChange();
  loadMonth();
  loadSharedSchedule();
});

function shiftMonth(delta) {
  currentMonth += delta;
  if (currentMonth < 1) { currentMonth = 12; currentYear -= 1; }
  if (currentMonth > 12) { currentMonth = 1; currentYear += 1; }
  loadMonth();
  loadSharedSchedule();
}

function goToToday() {
  const now = new Date();
  currentYear = now.getFullYear();
  currentMonth = now.getMonth() + 1;
  loadMonth();
  loadSharedSchedule();
}

function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

// ------------------------------------------------------------------
// 部署選択(①)
// ------------------------------------------------------------------
async function loadDepartments() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    departmentsCache = await res.json();

    const filterSelect = document.getElementById("departmentFilter");
    filterSelect.innerHTML = '<option value="">全部署</option>' +
      departmentsCache.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}(${d.employeeCount}人)</option>`).join("");

    const createSelect = document.getElementById("c_department");
    createSelect.innerHTML = departmentsCache.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join("");

    // 部署共有スケジュール登録フォーム自身の部署選択(上の一覧フィルタとは独立)
    const sharedDeptSelect = document.getElementById("s_department");
    sharedDeptSelect.innerHTML = departmentsCache.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join("");
  } catch (e) {
    console.error(e);
  }
}

function onDepartmentFilterChange() {
  currentDepartmentId = document.getElementById("departmentFilter").value;
  // 一覧フィルタで具体的な部署を選んだときは、登録フォームの初期選択も合わせておく
  // (全部署に戻したときは、登録フォームの選択はそのまま維持する)
  if (currentDepartmentId) {
    document.getElementById("s_department").value = currentDepartmentId;
  }
  loadMonth();
  loadSharedSchedule();
}

/** 登録フォームの「部署」が変わったら、その部署の従業員一覧(+部署全員)を担当者に詰め直す(⑫⑬) */
async function onCreateDepartmentChange() {
  const departmentId = document.getElementById("c_department").value;
  const employeeSelect = document.getElementById("c_employee");
  employeeSelect.innerHTML = `<option value="ALL">(部署全員)</option>`;
  if (!departmentId) return;

  try {
    const res = await fetch(`/api/admin/employees?departmentId=${departmentId}`);
    if (!res.ok) return;
    const employees = await res.json();
    employeeSelect.innerHTML += employees
      .map((e) => `<option value="${e.userId}">${escapeHtml(e.fullName)}</option>`)
      .join("");
  } catch (e) {
    console.error(e);
  }
}

// ------------------------------------------------------------------
// カレンダー・今日の予定の読み込み(②③⑨⑩⑰㉒)
// ------------------------------------------------------------------
let currentEventsRaw = [];

async function loadMonth() {
  const statusEl = document.getElementById("statusMsg");
  statusEl.textContent = "読み込み中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");

  try {
    const qs = currentDepartmentId ? `?departmentId=${currentDepartmentId}` : "";
    const res = await fetch(`/api/admin/schedule/${currentYear}/${currentMonth}${qs}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    const data = await res.json();
    currentEventsRaw = data.events || [];

    const scopeLabel = data.departmentName || "全部署";
    document.getElementById("calendarTitle").textContent = `${scopeLabel} ${currentYear}年${currentMonth}月`;
    document.getElementById("todayPanelTitle").textContent = `今日の予定(${scopeLabel})`;

    renderCombinedCalendar();
    statusEl.textContent = "";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "読み込みエラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

/**
 * 管理者の月間カレンダー・今日の予定は、個人予定(AdminScheduleRowDto)と
 * 部署共有スケジュール(DepartmentScheduleEventDto)を1つに合成して表示する。
 * 以前は個人予定しかカレンダーに表示されず、部署共有スケジュールは下の一覧でしか
 * 確認できなかったため、「誰が登録したか」も含めてカレンダー上で見えるようにする。
 */
function renderCombinedCalendar() {
  const merged = [
    ...currentEventsRaw.map((e) => ({ ...e, scheduleType: "PERSONAL" })),
    ...currentSharedEvents.map((e) => ({ ...e, scheduleType: "DEPARTMENT" })),
  ];
  renderCalendarView(merged);
  renderTodayPanel(merged);
}

function renderCalendarView(events) {
  const eventsByDate = scheduleGroupByDate(events, "eventDate");
  const grid = document.getElementById("calendarGrid");
  renderScheduleCalendar(grid, currentYear, currentMonth, eventsByDate, scheduleEventLine, onCalendarEventClick);
}

/** カレンダーセル内の1行 = 「誰が・いつ・何をする」がひと目で分かる表示(⑤⑱)。部署共有スケジュールは登録者名も添える */
function scheduleEventLine(ev) {
  const time = ev.startTime ? ev.startTime.substring(0, 5) : "終日";
  if (ev.scheduleType === "DEPARTMENT") {
    const who = ev.createdByName ? `(${ev.createdByName})` : "";
    return `[${ev.departmentName || "部署共有"}] ${time} ${ev.title} ${who}`.trim();
  }
  return `${ev.fullName} ${time} ${ev.title}`;
}

function onCalendarEventClick(ev) {
  if (ev.scheduleType === "DEPARTMENT") {
    openSharedEditModal(ev);
  } else {
    openEditModal(ev);
  }
}

function renderTodayPanel(events) {
  const todayStr = formatDate(new Date());
  const todays = events
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
    const isDept = ev.scheduleType === "DEPARTMENT";
    const badge = isDept
      ? `<span class="type-badge type-badge-department">${escapeHtml(ev.departmentName || "部署共有")}</span>`
      : "";
    const who = isDept
      ? (ev.createdByName ? `登録者: ${escapeHtml(ev.createdByName)}` : "")
      : `${escapeHtml(ev.fullName)}（${escapeHtml(ev.departmentName || "未設定")}）`;
    item.innerHTML = `
      <span class="today-event-time">${time}</span>
      ${badge}
      <span class="today-event-title">${escapeHtml(ev.title)}</span>
      <span class="today-event-who">${who}</span>
    `;
    item.addEventListener("click", () => (isDept ? openSharedEditModal(ev) : openEditModal(ev)));
    container.appendChild(item);
  });
}

// ------------------------------------------------------------------
// スケジュール登録(⑫⑬)
// ------------------------------------------------------------------
async function onCreateSubmit(e) {
  e.preventDefault();
  const statusEl = document.getElementById("statusMsg");

  const departmentId = document.getElementById("c_department").value;
  const employeeValue = document.getElementById("c_employee").value;
  const payload = {
    eventDate: document.getElementById("c_eventDate").value,
    startTime: toTimeOrNull(document.getElementById("c_startTime").value),
    endTime: toTimeOrNull(document.getElementById("c_endTime").value),
    title: document.getElementById("c_title").value.trim(),
    category: document.getElementById("c_category").value,
    location: document.getElementById("c_location").value.trim() || null,
    memo: document.getElementById("c_memo").value.trim(),
  };

  if (!departmentId) {
    statusEl.textContent = "部署を選択してください";
    statusEl.classList.add("error");
    return;
  }
  if (!payload.eventDate || !payload.title) {
    statusEl.textContent = "日付とタイトルは必須です";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "登録中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const url = employeeValue === "ALL"
      ? `/api/admin/schedule/departments/${departmentId}`
      : `/api/admin/schedule/employees/${employeeValue}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "登録に失敗しました"));

    statusEl.textContent = "登録しました";
    document.getElementById("createForm").reset();
    document.getElementById("c_category").value = "OTHER";

    // 登録した日付の月に合わせて表示を切り替える
    const [y, m] = payload.eventDate.split("-").map(Number);
    currentYear = y;
    currentMonth = m;
    loadMonth();
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "登録エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

function toTimeOrNull(hhmm) {
  return hhmm ? hhmm + ":00" : null;
}

// ------------------------------------------------------------------
// 予定編集・削除モーダル(⑥⑭)
// ------------------------------------------------------------------
let editingEvent = null;

function openEditModal(ev) {
  editingEvent = ev;
  document.getElementById("editModalTitle").textContent = `予定を編集(${ev.title}）`;
  document.getElementById("eOwnerLabel").textContent = ev.fullName;
  document.getElementById("eDeptLabel").textContent = ev.departmentName || "未設定";

  document.getElementById("e_eventDate").value = ev.eventDate;
  document.getElementById("e_startTime").value = ev.startTime ? ev.startTime.substring(0, 5) : "";
  document.getElementById("e_endTime").value = ev.endTime ? ev.endTime.substring(0, 5) : "";
  document.getElementById("e_title").value = ev.title;
  document.getElementById("e_category").value = ev.category;
  document.getElementById("e_location").value = ev.location || "";
  document.getElementById("e_memo").value = ev.memo || "";
  document.getElementById("editStatus").textContent = "";
  document.getElementById("editStatus").classList.remove("error");

  document.getElementById("editModal").hidden = false;
}

/** キャンセル: 保存処理を一切実行せず、モーダルを閉じてカレンダーへ戻るだけ(⑭の不具合確認事項) */
function closeEditModal() {
  document.getElementById("editModal").hidden = true;
  editingEvent = null;
}

async function onEditSubmit(e) {
  e.preventDefault();
  if (!editingEvent) return;
  const statusEl = document.getElementById("editStatus");

  const payload = {
    eventDate: document.getElementById("e_eventDate").value,
    startTime: toTimeOrNull(document.getElementById("e_startTime").value),
    endTime: toTimeOrNull(document.getElementById("e_endTime").value),
    title: document.getElementById("e_title").value.trim(),
    category: document.getElementById("e_category").value,
    memo: document.getElementById("e_memo").value.trim(),
  };
  if (!payload.eventDate || !payload.title) {
    statusEl.textContent = "日付とタイトルは必須です";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/admin/schedule/employees/${editingEvent.userId}/${editingEvent.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "保存に失敗しました"));
    statusEl.textContent = "保存しました";
    await loadMonth();
    setTimeout(closeEditModal, 400);
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "保存エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

async function onEditDelete() {
  if (!editingEvent) return;
  if (!confirm("この予定を削除しますか？")) return;
  const statusEl = document.getElementById("editStatus");

  statusEl.textContent = "削除中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/admin/schedule/employees/${editingEvent.userId}/${editingEvent.id}`, {
      method: "DELETE",
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "削除に失敗しました"));
    await loadMonth();
    closeEditModal();
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "削除エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

// ------------------------------------------------------------------
// 部署共有スケジュール(⑥⑦⑧⑯⑰⑳㉑)。
// 1件のレコードを部署全体で共有する「本当の部署共有スケジュール」を管理する
// (上の「スケジュールを登録」= 従業員個人の予定を複製登録する機能とは別物)。
// 登録先の部署はフォーム自身の#s_departmentで選ぶ(画面上部の一覧フィルタ#departmentFilterとは
// 独立)。一覧フィルタは「どの部署の予定を表示するか」だけを制御する。
// ------------------------------------------------------------------
let currentSharedEvents = [];

function toContentOrNull(v) {
  const t = v.trim();
  return t === "" ? null : t;
}

/**
 * 登録フォーム(sharedCreateForm)は常に表示する(自分自身の「部署」選択で登録先を決めるため、
 * 上の一覧フィルタの状態に左右されない)。以前はここで一覧フィルタが「全部署」のときに
 * フォーム自体を隠していたため、「部署共有スケジュールを登録できない」不具合の原因になっていた。
 * sharedNoteは「全部署」表示中であることを伝える補足情報としてのみ使う。
 */
function updateSharedSectionState() {
  document.getElementById("sharedNote").hidden = !!currentDepartmentId;
  const dept = departmentsCache.find((d) => String(d.id) === String(currentDepartmentId));
  document.getElementById("sharedDeptLabel").textContent = dept ? `（${dept.name}）` : "（全部署）";
}

/**
 * 部署共有スケジュールの読み込み。部署が選択されていればその部署のみ、
 * 「全部署」選択時は自社の全部署を横断して取得する(一般ユーザーが登録した内容を
 * 管理者が部署ごとに見て回らなくても一度に確認できるようにするため)。
 */
async function loadSharedSchedule() {
  updateSharedSectionState();
  const listEl = document.getElementById("sharedScheduleList");

  try {
    const url = currentDepartmentId
      ? `/api/admin/schedule/departments/${currentDepartmentId}/shared/${currentYear}/${currentMonth}`
      : `/api/admin/schedule/shared/${currentYear}/${currentMonth}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(await readErrorMessage(res, "部署共有スケジュールの読み込みに失敗しました"));
    const data = await res.json();
    currentSharedEvents = data.events || [];
    renderSharedList(currentSharedEvents);
    renderCombinedCalendar(); // カレンダー・今日の予定にも部署共有スケジュールを反映する
  } catch (e) {
    console.error(e);
    listEl.innerHTML = `<p class="snack-empty">${escapeHtml(e.message || "読み込みエラー")}</p>`;
  }
}

/**
 * 部署共有スケジュール一覧(⑱⑳)。誰が登録したかより「何が予定されているか」を優先する表示だが、
 * 「全部署」表示時は部署名も併せて出す(どの部署の予定か一目で分かるようにするため)。
 * 登録者名は常に表示する(一般ユーザーが登録した内容を管理者が確認しやすくするため)。
 */
function renderSharedList(events) {
  const container = document.getElementById("sharedScheduleList");
  container.innerHTML = "";

  if (!events || events.length === 0) {
    container.innerHTML = `<p class="snack-empty">この月の部署共有スケジュールはまだ登録されていません。</p>`;
    return;
  }

  const showDepartment = !currentDepartmentId;

  events.forEach((ev) => {
    const timeText = ev.startTime
      ? `${ev.startTime.substring(0, 5)}${ev.endTime ? " - " + ev.endTime.substring(0, 5) : ""}`
      : "終日";
    const sub = [ev.location, ev.content].filter(Boolean).join(" ／ ");
    // 登録者名(・全部署表示時は部署名)は、他の補足情報に埋もれないよう独立したバッジで表示する
    const deptBadge = showDepartment && ev.departmentName
      ? `<span class="type-badge type-badge-department">${escapeHtml(ev.departmentName)}</span>` : "";
    const ownerBadge = ev.createdByName
      ? `<span class="dsr-owner">登録者: ${escapeHtml(ev.createdByName)}</span>` : "";

    const row = document.createElement("div");
    row.className = "dept-schedule-row";
    row.innerHTML = `
      <span class="dsr-date">${ev.eventDate.slice(5).replace("-", "/")}(${ev.dayOfWeekLabel})</span>
      <span class="dsr-time">${timeText}</span>
      <span class="dsr-main">
        <span class="dsr-title">${deptBadge}${escapeHtml(ev.title)}</span>
        ${sub ? `<span class="dsr-sub">${escapeHtml(sub)}</span>` : ""}
        ${ownerBadge}
      </span>
      <span class="dsr-actions"></span>
    `;
    row.addEventListener("click", () => openSharedEditModal(ev));
    container.appendChild(row);
  });
}

async function onSharedCreateSubmit(e) {
  e.preventDefault();
  const statusEl = document.getElementById("statusMsg");

  // 登録先の部署は、このフォーム自身の「部署」選択を必ず使う(一覧フィルタの状態には依存しない)
  const departmentId = document.getElementById("s_department").value;
  const payload = {
    eventDate: document.getElementById("s_eventDate").value,
    startTime: toTimeOrNull(document.getElementById("s_startTime").value),
    endTime: toTimeOrNull(document.getElementById("s_endTime").value),
    title: document.getElementById("s_title").value.trim(),
    category: document.getElementById("s_category").value,
    location: toContentOrNull(document.getElementById("s_location").value),
    content: toContentOrNull(document.getElementById("s_content").value),
  };
  if (!departmentId) {
    statusEl.textContent = "部署を選択してください";
    statusEl.classList.add("error");
    return;
  }
  if (!payload.eventDate || !payload.title) {
    statusEl.textContent = "日付とタイトルは必須です";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "部署共有スケジュールを登録中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/admin/schedule/departments/${departmentId}/shared`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "登録に失敗しました"));

    statusEl.textContent = "部署共有スケジュールを登録しました";
    const keepDepartment = document.getElementById("s_department").value;
    document.getElementById("sharedCreateForm").reset();
    document.getElementById("s_department").value = keepDepartment;
    document.getElementById("s_category").value = "MEETING";

    const [y, m] = payload.eventDate.split("-").map(Number);
    currentYear = y;
    currentMonth = m;
    loadMonth();
    loadSharedSchedule();
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "登録エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

let editingSharedEvent = null;

function openSharedEditModal(ev) {
  editingSharedEvent = ev;
  document.getElementById("sharedEditModalTitle").textContent = `部署共有スケジュールを編集(${ev.title}）`;
  document.getElementById("se_eventDate").value = ev.eventDate;
  document.getElementById("se_startTime").value = ev.startTime ? ev.startTime.substring(0, 5) : "";
  document.getElementById("se_endTime").value = ev.endTime ? ev.endTime.substring(0, 5) : "";
  document.getElementById("se_title").value = ev.title;
  document.getElementById("se_category").value = ev.category;
  document.getElementById("se_location").value = ev.location || "";
  document.getElementById("se_content").value = ev.content || "";
  document.getElementById("sharedEditStatus").textContent = "";
  document.getElementById("sharedEditStatus").classList.remove("error");

  document.getElementById("sharedEditModal").hidden = false;
}

/** キャンセル: 保存処理を一切実行せず、元の一覧画面へ正しく戻る(⑯の不具合確認事項) */
function closeSharedEditModal() {
  document.getElementById("sharedEditModal").hidden = true;
  editingSharedEvent = null;
}

async function onSharedEditSubmit(e) {
  e.preventDefault();
  if (!editingSharedEvent) return;
  const statusEl = document.getElementById("sharedEditStatus");

  const payload = {
    eventDate: document.getElementById("se_eventDate").value,
    startTime: toTimeOrNull(document.getElementById("se_startTime").value),
    endTime: toTimeOrNull(document.getElementById("se_endTime").value),
    title: document.getElementById("se_title").value.trim(),
    category: document.getElementById("se_category").value,
    location: toContentOrNull(document.getElementById("se_location").value),
    content: toContentOrNull(document.getElementById("se_content").value),
  };
  if (!payload.eventDate || !payload.title) {
    statusEl.textContent = "日付とタイトルは必須です";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    // 「全部署」表示中はcurrentDepartmentIdが空なので、必ず編集対象の行自身が持つ
    // departmentId を使う(どちらの表示モードでも常に正しい部署を指すため)
    const res = await fetch(`/api/admin/schedule/departments/${editingSharedEvent.departmentId}/shared/${editingSharedEvent.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "保存に失敗しました"));
    statusEl.textContent = "保存しました";
    await loadSharedSchedule();
    loadMonth();
    setTimeout(closeSharedEditModal, 400);
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "保存エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

/** 削除: 誤操作防止のため確認ダイアログを表示する(⑰) */
async function onSharedEditDelete() {
  if (!editingSharedEvent) return;
  if (!confirm("この部署共有スケジュールを削除しますか？")) return;
  const statusEl = document.getElementById("sharedEditStatus");

  statusEl.textContent = "削除中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/admin/schedule/departments/${editingSharedEvent.departmentId}/shared/${editingSharedEvent.id}`, {
      method: "DELETE",
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "削除に失敗しました"));
    await loadSharedSchedule();
    loadMonth();
    closeSharedEditModal();
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "削除エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}
