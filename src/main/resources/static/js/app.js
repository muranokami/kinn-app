// ------------------------------------------------------------------
// 定数
// ------------------------------------------------------------------
const DAY_TYPES = [
  { value: "NORMAL", label: "通常勤務" },
  { value: "SCHEDULED_HOLIDAY", label: "所定休日" },
  { value: "STATUTORY_HOLIDAY", label: "法定休日" },
  { value: "PAID_LEAVE", label: "有給休暇" },
  { value: "SUBSTITUTE_HOLIDAY", label: "振替休日" },
  { value: "COMPENSATORY_HOLIDAY", label: "代休" },
  { value: "ABSENCE", label: "欠勤" },
];

const WEEKDAY_LABELS = ["日", "月", "火", "水", "木", "金", "土"];
const STANDARD_WORK_MINUTES = 8 * 60;
const NIGHT_START_MIN = 22 * 60;
const NIGHT_END_MIN = 29 * 60;

// 現在表示中の期間(締め日を起点とした開始日・終了日)。サーバーが計算した値をそのまま保持し、
// 前後移動時はこの値を基準にbaseDateをずらして再取得する(締め日ロジックはサーバー側だけに置く)。
let currentClosingDay;
let currentStartDate;
let currentEndDate;

// ------------------------------------------------------------------
// 初期化
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", () => {
  const closingDaySelect = document.getElementById("closingDaySelect");
  populateClosingDaySelect(closingDaySelect);
  currentClosingDay = getStoredClosingDay();
  closingDaySelect.value = currentClosingDay;

  closingDaySelect.addEventListener("change", () => {
    currentClosingDay = parseInt(closingDaySelect.value, 10);
    setStoredClosingDay(currentClosingDay);
    loadPeriod(todayIsoDate());
  });
  document.getElementById("prevPeriodBtn").addEventListener("click", () => {
    loadPeriod(addDaysToIsoDate(currentStartDate, -1));
  });
  document.getElementById("nextPeriodBtn").addEventListener("click", () => {
    loadPeriod(addDaysToIsoDate(currentEndDate, 1));
  });
  document.getElementById("todayPeriodBtn").addEventListener("click", () => loadPeriod(todayIsoDate()));
  document.getElementById("loadBtn").addEventListener("click", () => loadPeriod(currentStartDate || todayIsoDate()));
  document.getElementById("saveBtn").addEventListener("click", () => savePeriod());

  loadPeriod(todayIsoDate());
});

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

// ------------------------------------------------------------------
// データ取得・描画(①②③⑥ 締め日・期間表示・前後移動)
// ------------------------------------------------------------------

/** baseDateを含む、現在の締め日の期間を読み込んで表示する */
async function loadPeriod(baseDate) {
  setStatus("読み込み中...", false, true);

  try {
    const qs = `closingDay=${currentClosingDay}&baseDate=${baseDate}`;
    const res = await fetch(`/api/attendance/period?${qs}`);
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    const data = await res.json();
    applyPeriod(data);
    setStatus("読み込みました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "読み込みエラー", true);
  }
}

function applyPeriod(data) {
  currentStartDate = data.startDate;
  currentEndDate = data.endDate;
  document.getElementById("periodLabel").textContent = formatPeriodLabel(data.startDate, data.endDate);
  document.getElementById("csvLink").href =
    `/api/attendance/period/csv?startDate=${data.startDate}&endDate=${data.endDate}`;
  renderTable(data.days);
  renderSummary(data.summary);
}

function renderTable(days) {
  const tbody = document.getElementById("attendanceTbody");
  tbody.innerHTML = "";

  days.forEach((day) => {
    const date = new Date(day.workDate + "T00:00:00");
    const weekday = date.getDay(); // 0=日,6=土

    const tr = document.createElement("tr");
    tr.dataset.date = day.workDate;
    if (weekday === 6) tr.classList.add("is-saturday");
    if (weekday === 0) tr.classList.add("is-sunday");
    if (day.isPublicHoliday) tr.classList.add("is-holiday");

    tr.appendChild(cellText(`${date.getMonth() + 1}/${date.getDate()}`));
    tr.appendChild(cellText(WEEKDAY_LABELS[weekday]));
    tr.appendChild(cellHolidayBadge(day.holidayName));
    tr.appendChild(cellDayTypeSelect(day.dayType));
    tr.appendChild(cellTimeInput("startTime", day.startTime));
    tr.appendChild(cellTimeInput("endTime", day.endTime));
    tr.appendChild(cellBreakInput(day));
    tr.appendChild(cellCheckbox("lateOrEarly", day.lateOrEarly));
    tr.appendChild(cellReadonly("workHours", toHours(day.workMinutes)));
    tr.appendChild(cellReadonly("overtimeHours", toHours(day.overtimeMinutes)));
    tr.appendChild(cellReadonly("companyHolidayHours", toHours(day.companyHolidayWorkMinutes)));
    tr.appendChild(cellReadonly("scheduledHolidayOvertimeHours", toHours(day.scheduledHolidayOvertimeMinutes)));
    tr.appendChild(cellReadonly("statutoryHolidayHours", toHours(day.statutoryHolidayWorkMinutes)));
    tr.appendChild(cellReadonly("statutoryHolidayOvertimeHours", toHours(day.statutoryHolidayOvertimeMinutes)));
    tr.appendChild(cellReadonly("totalOvertimeHours", toHours(day.totalOvertimeMinutes)));
    tr.appendChild(cellReadonly("nightHours", toHours(day.nightMinutes)));
    tr.appendChild(cellTextInput("remarks", day.remarks ?? ""));

    tbody.appendChild(tr);
  });

  // 入力変更のたびに、その行と月次集計をリアルタイム再計算
  tbody.querySelectorAll("input, select").forEach((el) => {
    el.addEventListener("input", () => {
      recalcRow(el.closest("tr"));
      recalcSummaryFromTable();
    });
  });
}

function cellText(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

function cellHolidayBadge(holidayName) {
  const td = document.createElement("td");
  if (holidayName) {
    const span = document.createElement("span");
    span.className = "holiday-badge";
    span.textContent = holidayName;
    td.appendChild(span);
  }
  return td;
}

function cellReadonly(field, text) {
  const td = document.createElement("td");
  td.className = "readonly-val";
  td.dataset.field = field;
  td.textContent = text;
  return td;
}

function cellDayTypeSelect(value) {
  const td = document.createElement("td");
  const select = document.createElement("select");
  select.className = "day-type";
  select.dataset.field = "dayType";
  DAY_TYPES.forEach((dt) => {
    const opt = document.createElement("option");
    opt.value = dt.value;
    opt.textContent = dt.label;
    if (dt.value === value) opt.selected = true;
    select.appendChild(opt);
  });
  td.appendChild(select);
  return td;
}

function cellTimeInput(field, value) {
  const td = document.createElement("td");
  const input = document.createElement("input");
  input.type = "time";
  input.dataset.field = field;
  if (value) input.value = value.substring(0, 5);
  td.appendChild(input);
  return td;
}

function cellNumberInput(field, value) {
  const td = document.createElement("td");
  const input = document.createElement("input");
  input.type = "number";
  input.min = "0";
  input.step = "5";
  input.dataset.field = field;
  input.value = value;
  td.appendChild(input);
  return td;
}

/**
 * 休憩(分)の入力欄(⑩既存機能)に加えて、トップページの自動休憩機能で記録された
 * 休憩開始〜終了時刻があれば、その下に小さく表示する(例: 12:00〜13:00)。
 * 手動入力のみの日はbreakStartTimeが無いため、この行は表示されない(⑯手動休憩との共存)。
 */
function cellBreakInput(day) {
  const td = cellNumberInput("breakMinutes", day.breakMinutes ?? 0);
  if (day.breakStartTime) {
    const hint = document.createElement("div");
    hint.className = "break-time-hint";
    const start = day.breakStartTime.substring(0, 5);
    const end = day.breakEndTime ? day.breakEndTime.substring(0, 5) : "休憩中";
    hint.textContent = `${start}〜${end}`;
    td.appendChild(hint);
  }
  return td;
}

function cellCheckbox(field, checked) {
  const td = document.createElement("td");
  const input = document.createElement("input");
  input.type = "checkbox";
  input.dataset.field = field;
  input.checked = !!checked;
  td.appendChild(input);
  return td;
}

function cellTextInput(field, value) {
  const td = document.createElement("td");
  const input = document.createElement("input");
  input.type = "text";
  input.className = "remarks";
  input.dataset.field = field;
  input.value = value;
  td.appendChild(input);
  return td;
}

// ------------------------------------------------------------------
// クライアント側リアルタイム再計算(保存前のプレビュー用)
// サーバー側 AttendanceService の計算ロジックと同じ考え方で計算する。
// 実働のうち所定労働時間(8時間)を超えた部分を残業とし、その日の勤務区分によって
// 「通常残業」「所定休日残業」「法定休日残業」のどれに計上するかを振り分ける
// (休日区分ごとに矛盾する計算ルールを新設せず、同じ考え方を使い回している)。
// ------------------------------------------------------------------
function timeToMinutes(hhmm) {
  if (!hhmm) return null;
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

function computeDay(rowData) {
  const s = timeToMinutes(rowData.startTime);
  const e = timeToMinutes(rowData.endTime);
  const worked = s !== null && e !== null;
  let workMinutes = 0;
  let nightMinutes = 0;
  let overtimeMinutes = 0;
  let regularMinutes = 0;
  let companyHolidayMinutes = 0;
  let statutoryHolidayMinutes = 0;
  let scheduledHolidayOvertimeMinutes = 0;
  let statutoryHolidayOvertimeMinutes = 0;

  if (worked) {
    let span = e <= s ? (e + 24 * 60) - s : e - s;
    workMinutes = Math.max(0, span - (rowData.breakMinutes || 0));

    let sAbs = s, eAbs = e <= s ? e + 24 * 60 : e;
    const overlap = Math.min(eAbs, NIGHT_END_MIN) - Math.max(sAbs, NIGHT_START_MIN);
    nightMinutes = Math.max(0, overlap);

    const overStandard = Math.max(0, workMinutes - STANDARD_WORK_MINUTES);

    if (rowData.dayType === "NORMAL") {
      overtimeMinutes = overStandard;
      regularMinutes = workMinutes - overtimeMinutes;
    } else if (rowData.dayType === "SCHEDULED_HOLIDAY") {
      companyHolidayMinutes = workMinutes;
      scheduledHolidayOvertimeMinutes = overStandard;
    } else if (rowData.dayType === "STATUTORY_HOLIDAY") {
      statutoryHolidayMinutes = workMinutes;
      statutoryHolidayOvertimeMinutes = overStandard;
    }
  }
  const totalOvertimeMinutes = overtimeMinutes + scheduledHolidayOvertimeMinutes + statutoryHolidayOvertimeMinutes;
  return {
    workMinutes, overtimeMinutes, nightMinutes, worked,
    regularMinutes, companyHolidayMinutes, statutoryHolidayMinutes,
    scheduledHolidayOvertimeMinutes, statutoryHolidayOvertimeMinutes, totalOvertimeMinutes,
  };
}

function readRow(tr) {
  return {
    date: tr.dataset.date,
    dayType: tr.querySelector('[data-field="dayType"]').value,
    startTime: tr.querySelector('[data-field="startTime"]').value,
    endTime: tr.querySelector('[data-field="endTime"]').value,
    breakMinutes: parseInt(tr.querySelector('[data-field="breakMinutes"]').value || "0", 10),
    lateOrEarly: tr.querySelector('[data-field="lateOrEarly"]').checked,
    remarks: tr.querySelector('[data-field="remarks"]').value,
  };
}

function recalcRow(tr) {
  const rowData = readRow(tr);
  const result = computeDay(rowData);
  tr.querySelector('[data-field="workHours"]').textContent = toHours(result.workMinutes);
  tr.querySelector('[data-field="overtimeHours"]').textContent = toHours(result.overtimeMinutes);
  tr.querySelector('[data-field="companyHolidayHours"]').textContent = toHours(result.companyHolidayMinutes);
  tr.querySelector('[data-field="scheduledHolidayOvertimeHours"]').textContent = toHours(result.scheduledHolidayOvertimeMinutes);
  tr.querySelector('[data-field="statutoryHolidayHours"]').textContent = toHours(result.statutoryHolidayMinutes);
  tr.querySelector('[data-field="statutoryHolidayOvertimeHours"]').textContent = toHours(result.statutoryHolidayOvertimeMinutes);
  tr.querySelector('[data-field="totalOvertimeHours"]').textContent = toHours(result.totalOvertimeMinutes);
  tr.querySelector('[data-field="nightHours"]').textContent = toHours(result.nightMinutes);
}

function recalcSummaryFromTable() {
  const rows = document.querySelectorAll("#attendanceTbody tr");
  let workingDays = 0, workingMinutes = 0, overtimeMinutes = 0, nightOvertimeMinutes = 0, regularMinutes = 0;
  let lateOrEarlyCount = 0, absenceDays = 0;
  let scheduledHolidayWorkDays = 0, scheduledHolidayNightMinutes = 0, scheduledHolidayWorkMinutes = 0, scheduledHolidayOvertimeMinutes = 0;
  let paidLeaveDays = 0, substituteOrCompensatoryDays = 0;
  let statutoryHolidayWorkDays = 0, statutoryHolidayNightMinutes = 0, statutoryHolidayWorkMinutes = 0, statutoryHolidayOvertimeMinutes = 0;
  let scheduledWorkingDays = 0;

  rows.forEach((tr) => {
    const rowData = readRow(tr);
    const { workMinutes, overtimeMinutes: ot, nightMinutes, worked, regularMinutes: reg,
            companyHolidayMinutes, statutoryHolidayMinutes,
            scheduledHolidayOvertimeMinutes: schOt, statutoryHolidayOvertimeMinutes: statOt } = computeDay(rowData);

    if (rowData.lateOrEarly) lateOrEarlyCount++;

    switch (rowData.dayType) {
      case "NORMAL":
        scheduledWorkingDays += 1;
        if (worked) {
          workingDays += 1;
          workingMinutes += workMinutes;
          overtimeMinutes += ot;
          regularMinutes += reg;
          nightOvertimeMinutes += nightMinutes;
        }
        break;
      case "SCHEDULED_HOLIDAY":
        if (worked) {
          scheduledHolidayWorkDays += 1;
          scheduledHolidayWorkMinutes += companyHolidayMinutes;
          scheduledHolidayOvertimeMinutes += schOt;
          scheduledHolidayNightMinutes += nightMinutes;
        }
        break;
      case "STATUTORY_HOLIDAY":
        if (worked) {
          statutoryHolidayWorkDays += 1;
          statutoryHolidayWorkMinutes += statutoryHolidayMinutes;
          statutoryHolidayOvertimeMinutes += statOt;
          statutoryHolidayNightMinutes += nightMinutes;
        }
        break;
      case "PAID_LEAVE":
        paidLeaveDays += 1;
        break;
      case "SUBSTITUTE_HOLIDAY":
      case "COMPENSATORY_HOLIDAY":
        substituteOrCompensatoryDays += 1;
        break;
      case "ABSENCE":
        absenceDays += 1;
        break;
    }
  });

  const totalNightMinutes = nightOvertimeMinutes + scheduledHolidayNightMinutes + statutoryHolidayNightMinutes;
  const totalActualMinutes = regularMinutes + overtimeMinutes + scheduledHolidayWorkMinutes + statutoryHolidayWorkMinutes;
  const totalOvertimeMinutes = overtimeMinutes + scheduledHolidayOvertimeMinutes + statutoryHolidayOvertimeMinutes;

  renderSummary({
    workingDays,
    workingHours: workingMinutes / 60,
    overtimeHours: overtimeMinutes / 60,
    nightOvertimeHours: nightOvertimeMinutes / 60,
    lateOrEarlyCount,
    absenceDays,
    scheduledHolidayWorkDays,
    scheduledHolidayNightHours: scheduledHolidayNightMinutes / 60,
    paidLeaveDays,
    substituteOrCompensatoryDays,
    statutoryHolidayWorkDays,
    statutoryHolidayNightHours: statutoryHolidayNightMinutes / 60,
    scheduledWorkingHours: (scheduledWorkingDays * STANDARD_WORK_MINUTES) / 60,
    regularHours: regularMinutes / 60,
    scheduledHolidayWorkHours: scheduledHolidayWorkMinutes / 60,
    statutoryHolidayWorkHours: statutoryHolidayWorkMinutes / 60,
    totalNightHours: totalNightMinutes / 60,
    totalActualWorkingHours: totalActualMinutes / 60,
    scheduledHolidayOvertimeHours: scheduledHolidayOvertimeMinutes / 60,
    statutoryHolidayOvertimeHours: statutoryHolidayOvertimeMinutes / 60,
    totalOvertimeHours: totalOvertimeMinutes / 60,
  });
}

function toHours(minutes) {
  if (!minutes) return "0.00";
  return (minutes / 60).toFixed(2);
}

function renderSummary(s) {
  setVal("s_workingDays", s.workingDays);
  setVal("s_overtimeHours", fmt(s.overtimeHours));
  setVal("s_lateOrEarlyCount", s.lateOrEarlyCount);
  setVal("s_absenceDays", s.absenceDays);
  setVal("s_scheduledHolidayWorkDays", s.scheduledHolidayWorkDays);
  setVal("s_paidLeaveDays", s.paidLeaveDays);
  setVal("s_substituteOrCompensatoryDays", s.substituteOrCompensatoryDays);
  setVal("s_statutoryHolidayWorkDays", s.statutoryHolidayWorkDays);
  setVal("s_scheduledWorkingHours", fmt(s.scheduledWorkingHours));
  setVal("s_regularHours", fmt(s.regularHours));
  setVal("s_scheduledHolidayWorkHours", fmt(s.scheduledHolidayWorkHours));
  setVal("s_statutoryHolidayWorkHours", fmt(s.statutoryHolidayWorkHours));
  setVal("s_totalNightHours", fmt(s.totalNightHours));
  setVal("s_totalActualWorkingHours", fmt(s.totalActualWorkingHours));
  setVal("s_scheduledHolidayOvertimeHours", fmt(s.scheduledHolidayOvertimeHours));
  setVal("s_statutoryHolidayOvertimeHours", fmt(s.statutoryHolidayOvertimeHours));
  setVal("s_totalOvertimeHours", fmt(s.totalOvertimeHours));
}

function fmt(value) {
  if (value === undefined || value === null) return "-";
  return value.toFixed ? value.toFixed(2) : value;
}

function setVal(id, value) {
  document.getElementById(id).textContent = value;
}

// ------------------------------------------------------------------
// 保存
// ------------------------------------------------------------------
async function savePeriod() {
  const rows = document.querySelectorAll("#attendanceTbody tr");
  const payload = [];
  rows.forEach((tr) => {
    const rowData = readRow(tr);
    payload.push({
      workDate: rowData.date,
      dayType: rowData.dayType,
      startTime: rowData.startTime ? rowData.startTime + ":00" : null,
      endTime: rowData.endTime ? rowData.endTime + ":00" : null,
      breakMinutes: rowData.breakMinutes,
      lateOrEarly: rowData.lateOrEarly,
      paidLeaveUnit: 1.0,
      remarks: rowData.remarks,
    });
  });

  setStatus("保存中...", false, true);
  try {
    const res = await fetch(
      `/api/attendance/period?startDate=${currentStartDate}&endDate=${currentEndDate}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      }
    );
    if (!res.ok) throw new Error(await readErrorMessage(res, "保存に失敗しました"));
    const data = await res.json();
    applyPeriod(data);
    setStatus("保存しました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "保存エラー", true);
  }
}

function setStatus(msg, isError, isLoading) {
  const el = document.getElementById("statusMsg");
  el.textContent = msg;
  el.classList.toggle("error", !!isError);
  el.classList.toggle("is-loading", !!isLoading);
}
