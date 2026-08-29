window.addEventListener("DOMContentLoaded", async () => {
  await loadDepartmentOptions();
  document.getElementById("departmentFilter").addEventListener("change", load);
  load();
});

async function loadDepartmentOptions() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) return;
    const departments = await res.json();
    const select = document.getElementById("departmentFilter");
    select.innerHTML = '<option value="">全部署</option>' +
      departments.map((d) => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join("");
  } catch (e) {
    console.error(e);
  }
}

async function load() {
  const departmentId = document.getElementById("departmentFilter").value;
  const qs = departmentId ? `?departmentId=${departmentId}` : "";
  try {
    const res = await fetch(`/api/admin/attendance/dashboard${qs}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const d = await res.json();
    render(d);
  } catch (e) {
    console.error(e);
    document.getElementById("companyLabel").textContent = e.message || "読み込みエラー";
  }
}

function render(d) {
  const scope = d.departmentName ? `${d.companyName} / ${d.departmentName}` : `${d.companyName}(全部署)`;
  document.getElementById("companyLabel").textContent = `${scope} の勤怠状況(本日・今月)`;
  setVal("s_employeeCount", d.employeeCount);
  setVal("s_presentToday", d.presentTodayCount);
  setVal("s_absentToday", d.absentTodayCount);
  setVal("s_holidayWorkToday", d.holidayWorkTodayCount);
  setVal("s_unclocked", d.unclockedCount);
  setVal("s_monthWorking", d.monthTotalWorkingHours.toFixed(2));
  setVal("s_monthOvertime", d.monthTotalOvertimeHours.toFixed(2));
  setVal("s_regularOt", d.monthRegularOvertimeHours.toFixed(2));
  setVal("s_scheduledOt", d.monthScheduledHolidayOvertimeHours.toFixed(2));
  setVal("s_statutoryOt", d.monthStatutoryHolidayOvertimeHours.toFixed(2));

  renderNameList("missingClockOutList", d.missingClockOutNames, "未退勤の従業員はいません");
  renderNameList("missingClockInList", d.missingClockInNames, "未出勤の従業員はいません");
}

function renderNameList(elementId, names, emptyText) {
  const ul = document.getElementById(elementId);
  ul.innerHTML = "";
  if (!names || names.length === 0) {
    const li = document.createElement("li");
    li.className = "dashboard-alert-empty";
    li.textContent = emptyText;
    ul.appendChild(li);
    return;
  }
  names.forEach((name) => {
    const li = document.createElement("li");
    li.textContent = name;
    ul.appendChild(li);
  });
}

function setVal(id, value) {
  document.getElementById(id).textContent = value;
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
