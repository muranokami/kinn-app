// ------------------------------------------------------------------
// 今日の体調チェック
// ------------------------------------------------------------------
let selectedCondition = null;
let selectedFatigue = null;

window.addEventListener("DOMContentLoaded", () => {
  buildConditionPills();
  buildScale("fatigueScale", (v) => { selectedFatigue = v; });

  document.getElementById("checkDateLabel").textContent =
    `今日の記録(${healthTodayStr()})`;

  document.getElementById("checkForm").addEventListener("submit", (e) => {
    e.preventDefault();
    saveCheck();
  });

  loadToday();
  loadRecent();
});

function buildConditionPills() {
  const wrap = document.getElementById("conditionPills");
  HEALTH_CONDITION_OPTIONS.forEach((c) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "pill-btn";
    btn.textContent = `${c.icon} ${c.label}`;
    btn.dataset.value = c.value;
    btn.addEventListener("click", () => {
      selectedCondition = c.value;
      wrap.querySelectorAll(".pill-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
    });
    wrap.appendChild(btn);
  });
}

function buildScale(containerId, onSelect) {
  const wrap = document.getElementById(containerId);
  for (let i = 1; i <= 5; i++) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "scale-btn";
    btn.textContent = i;
    btn.dataset.value = i;
    btn.addEventListener("click", () => {
      onSelect(i);
      wrap.querySelectorAll(".scale-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
    });
    wrap.appendChild(btn);
  }
}

async function loadToday() {
  setStatus("読み込み中...", false, true);
  try {
    const res = await fetch(`/api/health/check?employeeId=${HEALTH_EMPLOYEE_ID}&date=${healthTodayStr()}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const c = await res.json();
    applyToForm(c);
    setStatus("", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "読み込みエラー", true);
  }
}

function applyToForm(c) {
  if (c.condition) selectPill(c.condition);
  if (c.fatigueLevel) selectScale("fatigueScale", c.fatigueLevel, (v) => { selectedFatigue = v; });
  document.getElementById("sleepHours").value = c.sleepHours ?? "";
  document.getElementById("exerciseMinutes").value = c.exerciseMinutes ?? "";
  document.getElementById("bodyTemperature").value = c.bodyTemperature ?? "";
  document.getElementById("memo").value = c.memo ?? "";
}

function selectPill(value) {
  selectedCondition = value;
  document.querySelectorAll("#conditionPills .pill-btn").forEach((b) => {
    b.classList.toggle("active", b.dataset.value === value);
  });
}

function selectScale(containerId, value, setter) {
  setter(value);
  document.querySelectorAll(`#${containerId} .scale-btn`).forEach((b) => {
    b.classList.toggle("active", Number(b.dataset.value) === value);
  });
}

async function saveCheck() {
  const num = (id) => {
    const v = document.getElementById(id).value;
    return v === "" ? null : Number(v);
  };
  const payload = {
    checkDate: healthTodayStr(),
    condition: selectedCondition,
    sleepHours: num("sleepHours"),
    fatigueLevel: selectedFatigue,
    exerciseMinutes: num("exerciseMinutes"),
    bodyTemperature: num("bodyTemperature"),
    memo: document.getElementById("memo").value,
  };

  setStatus("保存中...", false, true);
  try {
    const res = await fetch(`/api/health/check?employeeId=${HEALTH_EMPLOYEE_ID}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("保存に失敗しました");
    setStatus("保存しました。おつかれさまです 🙌", false);
    loadRecent();
  } catch (e) {
    console.error(e);
    setStatus(e.message || "保存エラー", true);
  }
}

async function loadRecent() {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 13);
  try {
    const res = await fetch(
      `/api/health/check/history?employeeId=${HEALTH_EMPLOYEE_ID}&from=${healthFormatDate(from)}&to=${healthFormatDate(to)}`
    );
    if (!res.ok) throw new Error("履歴の読み込みに失敗しました");
    const list = await res.json();
    renderRecent(list.slice().reverse());
  } catch (e) {
    console.error(e);
  }
}

function renderRecent(list) {
  const tbody = document.getElementById("recentTbody");
  tbody.innerHTML = "";
  const recorded = list.filter((c) => c.id);
  if (recorded.length === 0) {
    const tr = document.createElement("tr");
    tr.className = "empty-row";
    tr.innerHTML = `<td colspan="7">まだ記録がありません</td>`;
    tbody.appendChild(tr);
    return;
  }
  recorded.forEach((c) => {
    const conditionOpt = HEALTH_CONDITION_OPTIONS.find((o) => o.value === c.condition);
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${c.checkDate}</td>
      <td>${conditionOpt ? conditionOpt.icon + " " + conditionOpt.label : "-"}</td>
      <td>${c.sleepHours ?? "-"}</td>
      <td>${c.fatigueLevel ?? "-"}</td>
      <td>${c.exerciseMinutes ?? "-"}</td>
      <td>${c.bodyTemperature ?? "-"}</td>
      <td style="text-align:left;">${escapeHtml(c.memo ?? "")}</td>
    `;
    tbody.appendChild(tr);
  });
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}

function setStatus(msg, isError, isLoading) {
  const el = document.getElementById("statusMsg");
  el.textContent = msg;
  el.classList.toggle("error", !!isError);
  el.classList.toggle("is-loading", !!isLoading);
}
