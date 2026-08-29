// ------------------------------------------------------------------
// 健康プロフィール
// ------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", () => {
  loadProfile();

  document.getElementById("heightCm").addEventListener("input", updateBmiPreview);
  document.getElementById("weightKg").addEventListener("input", updateBmiPreview);
  document.getElementById("profileForm").addEventListener("submit", (e) => {
    e.preventDefault();
    saveProfile();
  });
});

async function loadProfile() {
  setStatus("読み込み中...", false);
  try {
    const res = await fetch(`/api/health/profile?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const p = await res.json();
    setVal("heightCm", p.heightCm);
    setVal("weightKg", p.weightKg);
    setVal("systolicBp", p.systolicBp);
    setVal("diastolicBp", p.diastolicBp);
    setVal("bodyTemperature", p.bodyTemperature);
    setVal("exerciseMinutes", p.exerciseMinutes);
    setVal("avgSleepHours", p.avgSleepHours);
    setVal("stressLevel", p.stressLevel);
    document.getElementById("smokingStatus").value = p.smokingStatus ?? "";
    document.getElementById("drinkingStatus").value = p.drinkingStatus ?? "";
    document.getElementById("department").value = p.department ?? "";
    document.getElementById("healthMemo").value = p.healthMemo ?? "";
    updateBmiPreview(p.bmi);
    setStatus("読み込みました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "読み込みエラー", true);
  }
}

function setVal(id, value) {
  const el = document.getElementById(id);
  el.value = value === null || value === undefined ? "" : value;
}

function updateBmiPreview(serverBmi) {
  const height = parseFloat(document.getElementById("heightCm").value);
  const weight = parseFloat(document.getElementById("weightKg").value);
  let bmi = typeof serverBmi === "number" ? serverBmi : null;
  if (!isNaN(height) && !isNaN(weight) && height > 0) {
    const h = height / 100;
    bmi = Math.round((weight / (h * h)) * 10) / 10;
  }
  document.getElementById("bmiView").value = bmi === null ? "" : bmi;
}

async function saveProfile() {
  const numOrNull = (id) => {
    const v = document.getElementById(id).value;
    return v === "" ? null : Number(v);
  };
  const strOrNull = (id) => {
    const v = document.getElementById(id).value;
    return v === "" ? null : v;
  };

  const payload = {
    heightCm: numOrNull("heightCm"),
    weightKg: numOrNull("weightKg"),
    systolicBp: numOrNull("systolicBp"),
    diastolicBp: numOrNull("diastolicBp"),
    bodyTemperature: numOrNull("bodyTemperature"),
    exerciseMinutes: numOrNull("exerciseMinutes"),
    avgSleepHours: numOrNull("avgSleepHours"),
    stressLevel: numOrNull("stressLevel"),
    smokingStatus: strOrNull("smokingStatus"),
    drinkingStatus: strOrNull("drinkingStatus"),
    department: strOrNull("department"),
    healthMemo: strOrNull("healthMemo"),
  };

  setStatus("保存中...", false);
  try {
    const res = await fetch(`/api/health/profile?employeeId=${HEALTH_EMPLOYEE_ID}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("保存に失敗しました");
    const p = await res.json();
    updateBmiPreview(p.bmi);
    setStatus("保存しました", false);
  } catch (e) {
    console.error(e);
    setStatus(e.message || "保存エラー", true);
  }
}

function setStatus(msg, isError) {
  const el = document.getElementById("statusMsg");
  el.textContent = msg;
  el.classList.toggle("error", !!isError);
}
