// ------------------------------------------------------------------
// 管理者向け勤怠管理。
// ?userId=指定なし → 全社員フラット一覧(既存)
// ?userId=指定あり → その従業員の月間カレンダー・勤怠修正(新規)
// ------------------------------------------------------------------

const WEEKDAY_LABELS_MON_FIRST = ["月", "火", "水", "木", "金", "土", "日"];

function toHours(minutes) {
  if (!minutes) return "0.00";
  return (minutes / 60).toFixed(2);
}

function readErrorMessage(res, fallback) {
  return res
    .json()
    .then((body) => body?.message || fallback)
    .catch(() => fallback);
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

window.addEventListener("DOMContentLoaded", () => {
  const userId = new URLSearchParams(window.location.search).get("userId");
  if (userId) {
    initEmployeeCalendarMode(userId);
  } else {
    initFlatListMode();
  }
});

// ====================================================================
// モード1: 全社員フラット一覧(既存機能。⑧⑨ 締め日を指定した期間での部署別集計)
// ====================================================================
let flatClosingDay;
let flatStartDate, flatEndDate;
let flatAllRows = []; // 直近に読み込んだ全行(部署フィルタ適用済み)。従業員名検索はこれをその場で絞り込む
let employeeSearchTerm = "";

function initFlatListMode() {
  document.getElementById("flatListSection").hidden = false;

  const closingDaySelect = document.getElementById("closingDaySelect");
  populateClosingDaySelect(closingDaySelect);
  flatClosingDay = getStoredClosingDay();
  closingDaySelect.value = flatClosingDay;

  closingDaySelect.addEventListener("change", () => {
    flatClosingDay = parseInt(closingDaySelect.value, 10);
    setStoredClosingDay(flatClosingDay);
    loadFlatList(todayIsoDate());
  });
  document.getElementById("prevPeriodBtn").addEventListener("click", () => {
    loadFlatList(addDaysToIsoDate(flatStartDate, -1));
  });
  document.getElementById("nextPeriodBtn").addEventListener("click", () => {
    loadFlatList(addDaysToIsoDate(flatEndDate, 1));
  });
  document.getElementById("todayPeriodBtn").addEventListener("click", () => loadFlatList(todayIsoDate()));
  document.getElementById("loadBtn").addEventListener("click", () => loadFlatList(flatStartDate || todayIsoDate()));
  document.getElementById("departmentFilter").addEventListener("change", () => loadFlatList(flatStartDate || todayIsoDate()));

  // 従業員名検索(③見づらさ改善)。サーバーへ再取得しに行かず、読み込み済みの一覧をその場で絞り込む
  document.getElementById("employeeSearchInput").addEventListener("input", (e) => {
    employeeSearchTerm = e.target.value.trim();
    applyEmployeeSearchAndRender();
  });

  loadDepartmentFilterOptions();
  loadFlatList(todayIsoDate());
}

async function loadDepartmentFilterOptions() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    const departments = await res.json();
    const select = document.getElementById("departmentFilter");
    select.innerHTML = '<option value="">全部署</option>' +
      departments.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}(${d.employeeCount}人)</option>`).join("");
  } catch (e) {
    console.error(e);
  }
}

async function loadFlatList(baseDate) {
  const departmentId = document.getElementById("departmentFilter").value;
  const statusEl = document.getElementById("statusMsg");
  statusEl.textContent = "読み込み中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");

  try {
    const qs = new URLSearchParams({ closingDay: flatClosingDay, baseDate });
    if (departmentId) qs.set("departmentId", departmentId);
    const res = await fetch(`/api/admin/attendance/period?${qs}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    const data = await res.json();
    flatStartDate = data.startDate;
    flatEndDate = data.endDate;
    document.getElementById("periodLabel").textContent = formatPeriodLabel(data.startDate, data.endDate);
    flatAllRows = data.rows || [];
    applyEmployeeSearchAndRender();
    statusEl.textContent = "";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "読み込みエラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

/** 従業員名検索(部署フィルタとは独立に、読み込み済みの一覧をその場で絞り込む) */
function applyEmployeeSearchAndRender() {
  const hintEl = document.getElementById("searchHint");
  let rows = flatAllRows;
  if (employeeSearchTerm) {
    const term = employeeSearchTerm.toLowerCase();
    rows = flatAllRows.filter((r) => (r.fullName || "").toLowerCase().includes(term));
    const matchedCount = new Set(rows.map((r) => r.userId)).size;
    hintEl.textContent = `「${employeeSearchTerm}」に一致する従業員: ${matchedCount}人`;
    hintEl.hidden = false;
  } else {
    hintEl.hidden = true;
  }
  renderFlatRows(rows);
}

/**
 * 従業員ごとにグループ化して表示する(見やすさ改善)。
 * 以前は全社員・全日付が1つの巨大な表にフラットに並び、氏名列にも生のemployeeId
 * ("会社ID|ログインID")がそのまま表示されていて非常に見づらかったため、
 * 従業員名の見出し行(部署名・カレンダーで見るリンク付き)で区切る形にした。
 */
function renderFlatRows(rows) {
  const tbody = document.getElementById("rowsTbody");
  tbody.innerHTML = "";
  if (!rows || rows.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="11">データがありません</td></tr>`;
    return;
  }

  let lastUserId = null;
  rows.forEach((r) => {
    if (r.userId !== lastUserId) {
      lastUserId = r.userId;
      const headerTr = document.createElement("tr");
      headerTr.className = "attendance-employee-header";
      const td = document.createElement("td");
      td.colSpan = 11;
      const deptText = escapeHtml(r.departmentName ?? "未設定");
      const nameText = escapeHtml(r.fullName || r.employeeId);
      td.innerHTML = r.userId
        ? `<span class="aeh-name">${nameText}</span><span class="aeh-dept">${deptText}</span>
           <a class="aeh-link" href="admin-attendance.html?userId=${r.userId}">カレンダーで見る &raquo;</a>`
        : `<span class="aeh-name">${nameText}</span><span class="aeh-dept">${deptText}</span>`;
      headerTr.appendChild(td);
      tbody.appendChild(headerTr);
    }

    const date = new Date(r.workDate + "T00:00:00");
    const tr = document.createElement("tr");
    if (r.isPublicHoliday) tr.classList.add("is-holiday");

    tr.innerHTML = `
      <td>${date.getMonth() + 1}/${date.getDate()}</td>
      <td>${escapeHtml(r.dayOfWeekLabel)}</td>
      <td>${r.holidayName ? `<span class="holiday-badge">${escapeHtml(r.holidayName)}</span>` : ""}</td>
      <td>${escapeHtml(r.dayTypeLabel)}</td>
      <td>${r.startTime ? r.startTime.substring(0, 5) : ""}</td>
      <td>${r.endTime ? r.endTime.substring(0, 5) : ""}</td>
      <td>${toHours(r.workMinutes)}</td>
      <td>${toHours(r.overtimeMinutes)}</td>
      <td>${toHours(r.scheduledHolidayOvertimeMinutes)}</td>
      <td>${toHours(r.statutoryHolidayOvertimeMinutes)}</td>
      <td>${toHours(r.totalOvertimeMinutes)}</td>
    `;
    tbody.appendChild(tr);
  });
}

// ====================================================================
// モード2: 従業員別カレンダー・修正(⑧⑩ 締め日を指定した個人の期間表示)
// ====================================================================
let empUserId = null;
let empClosingDay;
let empStartDate, empEndDate;
let empDaysByDate = {};

async function initEmployeeCalendarMode(userId) {
  empUserId = userId;
  document.getElementById("employeeCalendarSection").hidden = false;

  const closingDaySelect = document.getElementById("calClosingDaySelect");
  populateClosingDaySelect(closingDaySelect);
  empClosingDay = getStoredClosingDay();
  closingDaySelect.value = empClosingDay;

  closingDaySelect.addEventListener("change", () => {
    empClosingDay = parseInt(closingDaySelect.value, 10);
    setStoredClosingDay(empClosingDay);
    loadEmployeePeriod(todayIsoDate());
  });
  document.getElementById("calPrevBtn").addEventListener("click", () => {
    loadEmployeePeriod(addDaysToIsoDate(empStartDate, -1));
  });
  document.getElementById("calNextBtn").addEventListener("click", () => {
    loadEmployeePeriod(addDaysToIsoDate(empEndDate, 1));
  });
  document.getElementById("calTodayBtn").addEventListener("click", () => loadEmployeePeriod(todayIsoDate()));

  document.getElementById("editCloseBtn").addEventListener("click", closeEditModal);
  document.getElementById("editForm").addEventListener("submit", handleEditSubmit);

  try {
    const res = await fetch(`/api/admin/employees/${userId}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "従業員情報の取得に失敗しました"));
    const emp = await res.json();
    document.getElementById("pageSubtitle").textContent = `勤怠管理(管理者) - ${emp.fullName}`;
    document.getElementById("breadcrumb").innerHTML = `
      <a href="admin-top.html">管理者トップ</a><span class="crumb-sep">&rang;</span>
      <a href="admin-employees.html">従業員管理</a><span class="crumb-sep">&rang;</span>
      <span class="crumb-current">${escapeHtml(emp.fullName)}</span>
    `;
  } catch (e) {
    console.error(e);
    document.getElementById("employeeCalStatus").textContent = e.message || "取得エラー";
    document.getElementById("employeeCalStatus").classList.add("error");
    return;
  }

  loadEmployeePeriod(todayIsoDate());
  loadAuditHistory();
}

async function loadEmployeePeriod(baseDate) {
  const statusEl = document.getElementById("employeeCalStatus");
  statusEl.textContent = "読み込み中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");

  try {
    const qs = new URLSearchParams({ closingDay: empClosingDay, baseDate });
    const res = await fetch(`/api/admin/attendance/employees/${empUserId}/period?${qs}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    const data = await res.json();
    empStartDate = data.startDate;
    empEndDate = data.endDate;
    document.getElementById("calPeriodLabel").textContent = formatPeriodLabel(data.startDate, data.endDate);
    empDaysByDate = {};
    data.days.forEach((d) => { empDaysByDate[d.workDate] = d; });
    renderCalendar(data.days);
    renderCalSummary(data.summary);
    statusEl.textContent = "";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "読み込みエラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

function renderCalSummary(s) {
  document.getElementById("cs_regularHours").textContent = s.regularHours.toFixed(2);
  document.getElementById("cs_overtimeHours").textContent = s.overtimeHours.toFixed(2);
  document.getElementById("cs_scheduledOt").textContent = s.scheduledHolidayOvertimeHours.toFixed(2);
  document.getElementById("cs_statutoryOt").textContent = s.statutoryHolidayOvertimeHours.toFixed(2);
  document.getElementById("cs_totalOt").textContent = s.totalOvertimeHours.toFixed(2);
}

/** 月曜始まり7列のカレンダーグリッドを描画する */
function renderCalendar(days) {
  const grid = document.getElementById("calendarGrid");
  grid.innerHTML = "";

  WEEKDAY_LABELS_MON_FIRST.forEach((label) => {
    const head = document.createElement("div");
    head.className = "calendar-head";
    head.textContent = label;
    grid.appendChild(head);
  });

  if (days.length === 0) return;
  const firstDate = new Date(days[0].workDate + "T00:00:00");
  // 月曜=0 になるようずらす(JSのgetDay()は日曜=0)
  const leadingBlanks = (firstDate.getDay() + 6) % 7;
  for (let i = 0; i < leadingBlanks; i++) {
    const blank = document.createElement("div");
    blank.className = "calendar-cell calendar-cell-blank";
    grid.appendChild(blank);
  }

  days.forEach((d) => {
    const date = new Date(d.workDate + "T00:00:00");
    const cell = document.createElement("div");
    cell.className = "calendar-cell";
    if (d.isPublicHoliday) cell.classList.add("is-holiday");
    if (date.getDay() === 0) cell.classList.add("is-sunday");
    if (date.getDay() === 6) cell.classList.add("is-saturday");

    const timeText = d.startTime && d.endTime
      ? `${d.startTime.substring(0, 5)}-${d.endTime.substring(0, 5)}`
      : (d.startTime ? `${d.startTime.substring(0, 5)}-` : "");

    cell.innerHTML = `
      <div class="calendar-cell-date">${date.getDate()}</div>
      ${d.holidayName ? `<div class="holiday-badge">${escapeHtml(d.holidayName)}</div>` : ""}
      <div class="calendar-cell-type">${escapeHtml(d.dayType ? dayTypeLabel(d.dayType) : "")}</div>
      <div class="calendar-cell-time">${escapeHtml(timeText)}</div>
      <div class="calendar-cell-hours">実働 ${toHours(d.workMinutes)}h</div>
      ${d.totalOvertimeMinutes ? `<div class="calendar-cell-ot">残業 ${toHours(d.totalOvertimeMinutes)}h</div>` : ""}
    `;
    cell.addEventListener("click", () => openEditModal(d.workDate));
    grid.appendChild(cell);
  });
}

function dayTypeLabel(dayType) {
  const map = {
    NORMAL: "通常勤務", SCHEDULED_HOLIDAY: "所定休日", STATUTORY_HOLIDAY: "法定休日",
    PAID_LEAVE: "有給休暇", SUBSTITUTE_HOLIDAY: "振替休日", COMPENSATORY_HOLIDAY: "代休", ABSENCE: "欠勤",
  };
  return map[dayType] || dayType;
}

// ------------------------------------------------------------------
// 勤怠修正モーダル
// ------------------------------------------------------------------
let editingDate = null;

function openEditModal(workDate) {
  editingDate = workDate;
  const d = empDaysByDate[workDate] || {};
  document.getElementById("editModalTitle").textContent = `勤怠を修正(${workDate})`;
  document.getElementById("eDayType").value = d.dayType || "NORMAL";
  document.getElementById("eStartTime").value = d.startTime ? d.startTime.substring(0, 5) : "";
  document.getElementById("eEndTime").value = d.endTime ? d.endTime.substring(0, 5) : "";
  document.getElementById("eBreakMinutes").value = d.breakMinutes ?? 0;
  document.getElementById("eRemarks").value = d.remarks ?? "";
  document.getElementById("editStatus").textContent = "";
  document.getElementById("editStatus").classList.remove("error");
  document.getElementById("editModal").hidden = false;
}

function closeEditModal() {
  document.getElementById("editModal").hidden = true;
  editingDate = null;
}

async function handleEditSubmit(e) {
  e.preventDefault();
  if (!editingDate) return;
  const statusEl = document.getElementById("editStatus");
  const startTime = document.getElementById("eStartTime").value;
  const endTime = document.getElementById("eEndTime").value;

  const payload = {
    dayType: document.getElementById("eDayType").value,
    startTime: startTime ? startTime + ":00" : null,
    endTime: endTime ? endTime + ":00" : null,
    breakMinutes: parseInt(document.getElementById("eBreakMinutes").value || "0", 10),
    remarks: document.getElementById("eRemarks").value || null,
  };

  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/admin/attendance/employees/${empUserId}/${editingDate}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "保存に失敗しました"));
    statusEl.textContent = "保存しました";
    await loadEmployeePeriod(empStartDate);
    await loadAuditHistory();
    setTimeout(closeEditModal, 400);
  } catch (e2) {
    console.error(e2);
    statusEl.textContent = e2.message || "保存エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

// ------------------------------------------------------------------
// 修正履歴(監査ログ)
// ------------------------------------------------------------------
async function loadAuditHistory() {
  const listEl = document.getElementById("auditList");
  try {
    const res = await fetch(`/api/admin/attendance/employees/${empUserId}/audit`);
    if (!res.ok) throw new Error("取得に失敗しました");
    const audits = await res.json();
    if (!audits || audits.length === 0) {
      listEl.innerHTML = `<p class="snack-empty">修正履歴はありません</p>`;
      return;
    }
    listEl.innerHTML = audits.map((a) => {
      const editedAt = a.editedAt ? a.editedAt.replace("T", " ").substring(0, 16) : "";
      const prevTime = fmtTimeRange(a.previousStartTime, a.previousEndTime);
      const newTime = fmtTimeRange(a.newStartTime, a.newEndTime);
      return `
        <div style="padding:8px 0; border-bottom:1px solid var(--paper-line);">
          <div>${escapeHtml(editedAt)} ／ ${escapeHtml(a.workDate)} ／ 修正者: ${escapeHtml(a.editedByName ?? "")}</div>
          <div style="color:var(--ink-soft);">
            ${escapeHtml(dayTypeLabel(a.previousDayType) || "-")} ${escapeHtml(prevTime)}
            &rarr;
            ${escapeHtml(dayTypeLabel(a.newDayType) || "-")} ${escapeHtml(newTime)}
          </div>
        </div>
      `;
    }).join("");
  } catch (e) {
    console.error(e);
    listEl.innerHTML = `<p class="status-msg error">修正履歴の取得に失敗しました</p>`;
  }
}

function fmtTimeRange(start, end) {
  const s = start ? start.substring(0, 5) : "";
  const e = end ? end.substring(0, 5) : "";
  if (!s && !e) return "(未打刻)";
  return `${s}-${e}`;
}
