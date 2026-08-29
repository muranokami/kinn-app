// ------------------------------------------------------------------
// 管理者ダッシュボード(会社・部署単位の集計のみ)
// ------------------------------------------------------------------
let adminDays = 30;
let currentDepartments = [];

window.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      adminDays = Number(btn.dataset.days);
      updateActiveTab();
      loadDashboard();
    });
  });
  document.getElementById("departmentSelect").addEventListener("change", renderDepartmentDetail);
  updateActiveTab();
  loadDashboard();
});

function updateActiveTab() {
  document.querySelectorAll("#periodTabs .period-tab").forEach((btn) => {
    btn.classList.toggle("active", Number(btn.dataset.days) === adminDays);
  });
}

async function loadDashboard() {
  try {
    const res = await fetch(`/api/admin/health/dashboard?days=${adminDays}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const data = await res.json();
    renderSummary(data);
    currentDepartments = data.departments ?? [];
    renderDepartmentOptions();
    renderDepartments(currentDepartments);
    renderDepartmentDetail();
  } catch (e) {
    console.error(e);
  }
}

function renderSummary(d) {
  setVal("s_employeeCount", d.employeeCount);
  setVal("s_avgHealthScore", d.avgHealthScore ?? "-");
  setVal("s_avgSleepHours", d.avgSleepHours ?? "-");
  setVal("s_avgFatigueLevel", d.avgFatigueLevel ?? "-");
  setVal("s_avgOvertimeHours", d.avgOvertimeHours ?? "-");
  setVal("s_alertCount", d.alertCount);
}

function setVal(id, value) {
  document.getElementById(id).textContent = value;
}

/** 部署選択ドロップダウンの選択肢を、直近の集計取得結果から組み立てる(選択状態は維持する) */
function renderDepartmentOptions() {
  const select = document.getElementById("departmentSelect");
  const previous = select.value;
  select.innerHTML = '<option value="">選択してください</option>' +
    currentDepartments.map((d) => `<option value="${escapeHtml(d.department)}">${escapeHtml(d.department)}(${d.employeeCount}人)</option>`).join("");
  if (currentDepartments.some((d) => d.department === previous)) {
    select.value = previous;
  }

  if (currentDepartments.length > 0) {
    document.getElementById("thresholdHint").textContent = currentDepartments[0].minEmployeeCountThreshold;
  }
}

/** 選択した1部署の集計をカード表示する。人数がしきい値未満の場合は数値の代わりに案内文を出す */
function renderDepartmentDetail() {
  const select = document.getElementById("departmentSelect");
  const detail = document.getElementById("departmentDetail");
  const note = document.getElementById("departmentInsufficientNote");
  const grid = document.getElementById("departmentDetailGrid");

  const dept = currentDepartments.find((d) => d.department === select.value);
  if (!dept) {
    detail.hidden = true;
    return;
  }
  detail.hidden = false;

  if (dept.insufficientData) {
    grid.hidden = true;
    note.hidden = false;
    note.textContent = `「${dept.department}」は対象人数が${dept.minEmployeeCountThreshold}名未満(現在${dept.employeeCount}名)のため、個人が特定されるリスクを考慮して集計を表示できません。`;
    return;
  }

  grid.hidden = false;
  note.hidden = true;
  setVal("d_employeeCount", dept.employeeCount);
  setVal("d_avgHealthScore", dept.avgHealthScore ?? "-");
  setVal("d_avgSleepHours", dept.avgSleepHours ?? "-");
  setVal("d_avgFatigueLevel", dept.avgFatigueLevel ?? "-");
  setVal("d_avgOvertimeHours", dept.avgOvertimeHours ?? "-");
  setVal("d_alertCount", dept.alertCount);
}

function renderDepartments(departments) {
  const tbody = document.getElementById("deptTbody");
  if (!departments || departments.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">データがありません</td></tr>`;
    return;
  }
  tbody.innerHTML = "";
  departments.forEach((d) => {
    const tr = document.createElement("tr");
    if (d.insufficientData) {
      tr.innerHTML = `
        <td>${escapeHtml(d.department)}</td>
        <td>${d.employeeCount}</td>
        <td colspan="5" style="text-align:center; color:var(--ink-soft);">対象人数が少ないため集計を表示できません</td>
      `;
    } else {
      tr.innerHTML = `
        <td>${escapeHtml(d.department)}</td>
        <td>${d.employeeCount}</td>
        <td>${d.avgHealthScore ?? "-"}</td>
        <td>${d.avgSleepHours ?? "-"}</td>
        <td>${d.avgFatigueLevel ?? "-"}</td>
        <td>${d.avgOvertimeHours ?? "-"}</td>
        <td>${d.alertCount}</td>
      `;
    }
    tbody.appendChild(tr);
  });
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
