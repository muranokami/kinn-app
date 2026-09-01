// ------------------------------------------------------------------
// 定数
// ------------------------------------------------------------------
const CONDITIONS = [
  { value: "", label: "-" },
  { value: "GREAT", label: "絶好調" },
  { value: "GOOD", label: "良い" },
  { value: "NORMAL", label: "普通" },
  { value: "BAD", label: "不調" },
];

const WEEKDAY_LABELS = ["日", "月", "火", "水", "木", "金", "土"];
const EMPLOYEE_ID = "default";

let currentYear;
let currentMonth;

// ------------------------------------------------------------------
// 初期化
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", () => {
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

  document.getElementById("prevMonthBtn").addEventListener("click", () => shiftMonth(-1));
  document.getElementById("nextMonthBtn").addEventListener("click", () => shiftMonth(1));
  document.getElementById("loadBtn").addEventListener("click", () => loadMonth());
  document.getElementById("saveBtn").addEventListener("click", () => saveMonth());
  document.getElementById("yearInput").addEventListener("change", () => loadMonth());
  monthSelect.addEventListener("change", () => loadMonth());

  loadMonth();
});

function shiftMonth(delta) {
  let y = parseInt(document.getElementById("yearInput").value, 10);
  let m = parseInt(document.getElementById("monthSelect").value, 10) + delta;
  if (m < 1) { m = 12; y -= 1; }
  if (m > 12) { m = 1; y += 1; }
  document.getElementById("yearInput").value = y;
  document.getElementById("monthSelect").value = m;
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
    const res = await fetch(`/api/health/${currentYear}/${currentMonth}?employeeId=${EMPLOYEE_ID}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const data = await res.json();
    renderTable(data.days);
    renderSummary(data.summary);
    setStatus("読み込みました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "読み込みエラー", true);
  }
}

function renderTable(days) {
  const tbody = document.getElementById("healthTbody");
  tbody.innerHTML = "";

  days.forEach((day) => {
    const date = new Date(day.recordDate + "T00:00:00");
    const weekday = date.getDay();

    const tr = document.createElement("tr");
    tr.dataset.date = day.recordDate;
    if (weekday === 6) tr.classList.add("is-saturday");
    if (weekday === 0) tr.classList.add("is-sunday");

    tr.appendChild(cellText(`${date.getMonth() + 1}/${date.getDate()}`));
    tr.appendChild(cellText(WEEKDAY_LABELS[weekday]));
    tr.appendChild(cellNumberInput("weightKg", day.weightKg, 0.1));
    tr.appendChild(cellNumberInput("sleepHours", day.sleepHours, 0.5));
    tr.appendChild(cellNumberInput("steps", day.steps, 100));
    tr.appendChild(cellNumberInput("exerciseMinutes", day.exerciseMinutes, 5));
    tr.appendChild(cellNumberInput("systolicBp", day.systolicBp, 1));
    tr.appendChild(cellNumberInput("diastolicBp", day.diastolicBp, 1));
    tr.appendChild(cellConditionSelect(day.condition));
    tr.appendChild(cellTextInput("memo", day.memo ?? ""));

    tbody.appendChild(tr);
  });
}

function cellText(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

function cellNumberInput(field, value, step) {
  const td = document.createElement("td");
  const input = document.createElement("input");
  input.type = "number";
  input.min = "0";
  input.step = String(step ?? 1);
  input.dataset.field = field;
  if (value !== null && value !== undefined) input.value = value;
  td.appendChild(input);
  return td;
}

function cellConditionSelect(value) {
  const td = document.createElement("td");
  const select = document.createElement("select");
  select.className = "day-type";
  select.dataset.field = "condition";
  CONDITIONS.forEach((c) => {
    const opt = document.createElement("option");
    opt.value = c.value;
    opt.textContent = c.label;
    if (c.value === value || (!value && c.value === "")) opt.selected = true;
    select.appendChild(opt);
  });
  td.appendChild(select);
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
// 集計表示
// ------------------------------------------------------------------
function renderSummary(s) {
  setVal("s_recordedDays", s.recordedDays);
  setVal("s_avgWeightKg", s.avgWeightKg ?? "-");
  setVal("s_avgSleepHours", s.avgSleepHours ?? "-");
  setVal("s_totalSteps", s.totalSteps);
  setVal("s_avgSteps", s.avgSteps);
  setVal("s_exerciseDays", s.exerciseDays);
  setVal("s_totalExerciseMinutes", s.totalExerciseMinutes);
  const bp = (s.avgSystolicBp != null && s.avgDiastolicBp != null)
    ? `${s.avgSystolicBp} / ${s.avgDiastolicBp}`
    : "-";
  setVal("s_avgBp", bp);
  setVal("s_goodConditionDays", s.goodConditionDays);
  setVal("s_badConditionDays", s.badConditionDays);
}

function setVal(id, value) {
  document.getElementById(id).textContent = value;
}

// ------------------------------------------------------------------
// 保存
// ------------------------------------------------------------------
function readRow(tr) {
  const num = (field) => {
    const v = tr.querySelector(`[data-field="${field}"]`).value;
    return v === "" ? null : Number(v);
  };
  const condVal = tr.querySelector('[data-field="condition"]').value;
  return {
    recordDate: tr.dataset.date,
    weightKg: num("weightKg"),
    sleepHours: num("sleepHours"),
    steps: num("steps"),
    exerciseMinutes: num("exerciseMinutes"),
    systolicBp: num("systolicBp"),
    diastolicBp: num("diastolicBp"),
    condition: condVal === "" ? null : condVal,
    memo: tr.querySelector('[data-field="memo"]').value,
  };
}

async function saveMonth() {
  const rows = document.querySelectorAll("#healthTbody tr");
  const payload = [];
  rows.forEach((tr) => payload.push(readRow(tr)));

  setStatus("保存中...", false, true);
  try {
    const res = await fetch(`/api/health/${currentYear}/${currentMonth}?employeeId=${EMPLOYEE_ID}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("保存に失敗しました");
    const data = await res.json();
    renderSummary(data.summary);
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
