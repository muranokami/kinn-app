window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("createDeptForm").addEventListener("submit", handleCreate);
  load();
});

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function load() {
  try {
    const res = await fetch("/api/admin/departments");
    if (!res.ok) throw new Error(await readErrorMessage(res, "読み込みに失敗しました"));
    const departments = await res.json();
    renderTable(departments);
  } catch (e) {
    console.error(e);
    document.getElementById("deptStatus").textContent = e.message || "読み込みエラー";
    document.getElementById("deptStatus").classList.add("error");
  }
}

function renderTable(departments) {
  const tbody = document.getElementById("deptTbody");
  tbody.innerHTML = "";
  if (!departments || departments.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="2">部署が登録されていません</td></tr>`;
    return;
  }
  departments.forEach((d) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${escapeHtml(d.name)}</td><td>${d.employeeCount}人</td>`;
    tbody.appendChild(tr);
  });
}

async function handleCreate(e) {
  e.preventDefault();
  const statusEl = document.getElementById("deptStatus");
  const name = document.getElementById("deptNameInput").value.trim();
  if (!name) return;

  statusEl.textContent = "登録中...";
  statusEl.classList.remove("error");
  try {
    const res = await fetch("/api/admin/departments", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "登録に失敗しました"));
    document.getElementById("deptNameInput").value = "";
    statusEl.textContent = "登録しました";
    await load();
  } catch (e2) {
    console.error(e2);
    statusEl.textContent = e2.message || "登録エラー";
    statusEl.classList.add("error");
  }
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
