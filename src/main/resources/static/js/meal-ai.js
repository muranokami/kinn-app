// ------------------------------------------------------------------
// AI献立提案(前日の食事記録・健康情報・食の好みから今日の朝食/昼食/夕食を提案)
// ------------------------------------------------------------------

const AI_MEAL_TYPES = [
  { type: "BREAKFAST", label: "朝食", icon: "🌅" },
  { type: "LUNCH", label: "昼食", icon: "☀️" },
  { type: "DINNER", label: "夕食", icon: "🌙" },
];

let currentAiSuggestion = null;

window.addEventListener("DOMContentLoaded", () => {
  loadAiSuggestion();
  loadFoodPreference();

  document.getElementById("aiMealRegenerateBtn").addEventListener("click", regenerateAiSuggestion);
  document.getElementById("aiMealSaveBtn").addEventListener("click", saveAiSuggestion);
  document.getElementById("prefSaveBtn").addEventListener("click", saveFoodPreference);
});

async function loadAiSuggestion() {
  const summaryEl = document.getElementById("aiMealSummary");
  summaryEl.textContent = "前日の食事記録を分析しています...";
  try {
    const res = await fetch(`/api/meal/ai-suggestion?employeeId=${HEALTH_EMPLOYEE_ID}&date=${healthTodayStr()}`);
    if (!res.ok) throw new Error(await readAiErrorMessage(res, "AI献立提案の取得に失敗しました"));
    const suggestion = await res.json();
    renderAiSuggestion(suggestion);
  } catch (e) {
    console.error(e);
    renderAiUnavailable();
  }
}

async function regenerateAiSuggestion() {
  const btn = document.getElementById("aiMealRegenerateBtn");
  const statusEl = document.getElementById("aiMealStatus");
  setButtonLoading(btn, true);
  statusEl.textContent = "別の献立を考えています...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(
      `/api/meal/ai-suggestion/regenerate?employeeId=${HEALTH_EMPLOYEE_ID}&date=${healthTodayStr()}`,
      { method: "POST" }
    );
    if (!res.ok) throw new Error(await readAiErrorMessage(res, "献立の再提案に失敗しました。もう一度お試しください。"));
    const suggestion = await res.json();
    renderAiSuggestion(suggestion);
    statusEl.textContent = "別の献立を提案しました";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "献立の再提案に失敗しました。もう一度お試しください。";
    statusEl.classList.add("error");
  } finally {
    setButtonLoading(btn, false);
    statusEl.classList.remove("is-loading");
  }
}

/** サーバーからのエラーJSON({"message": "..."})があればその文言を、無ければfallbackを使う。
 *  スタックトレース等の技術情報はサーバー側が返さない設計なので、そのまま画面に出してよい。 */
async function readAiErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function saveAiSuggestion() {
  if (!currentAiSuggestion || !currentAiSuggestion.id) return;
  const btn = document.getElementById("aiMealSaveBtn");
  const statusEl = document.getElementById("aiMealStatus");
  setButtonLoading(btn, true);
  try {
    const res = await fetch(
      `/api/meal/ai-suggestion/${currentAiSuggestion.id}/save?employeeId=${HEALTH_EMPLOYEE_ID}`,
      { method: "POST" }
    );
    if (!res.ok) throw new Error(await readAiErrorMessage(res, "献立の保存に失敗しました"));
    const suggestion = await res.json();
    renderAiSuggestion(suggestion);
    statusEl.textContent = "この献立を保存しました";
    statusEl.classList.remove("error");
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  } finally {
    setButtonLoading(btn, false);
  }
}

async function registerAiMeal(mealType, btn) {
  if (!currentAiSuggestion || !currentAiSuggestion.id) return;
  const statusEl = btn.parentElement.querySelector(".ai-meal-register-status");
  setButtonLoading(btn, true);
  statusEl.textContent = "登録中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(
      `/api/meal/ai-suggestion/${currentAiSuggestion.id}/register?employeeId=${HEALTH_EMPLOYEE_ID}&mealType=${mealType}`,
      { method: "POST" }
    );
    if (!res.ok) throw new Error(await readAiErrorMessage(res, "食事記録への登録に失敗しました"));
    statusEl.textContent = "食事記録に登録しました";
    statusEl.classList.remove("is-loading");
    // 登録済みのため再登録させない(is-loadingは解除しつつ、disabledのまま維持する)
    btn.classList.remove("is-loading");
    btn.closest(".ai-meal-card").classList.add("is-registered");
    // 上部の「食べたもの」入力フォームと今日のサマリーにも反映する(meal.js)
    if (typeof loadDay === "function") loadDay();
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "登録エラー";
    statusEl.classList.remove("is-loading");
    statusEl.classList.add("error");
    setButtonLoading(btn, false);
  }
}

function renderAiSuggestion(suggestion) {
  currentAiSuggestion = suggestion;

  document.getElementById("aiMealSummary").textContent =
    suggestion.summaryNote || "今日のおすすめメニューを提案します。";

  const noteEl = document.getElementById("aiMealSourceNote");
  if (suggestion.aiAvailable && suggestion.source === "AI") {
    noteEl.textContent = "";
    noteEl.classList.add("is-empty");
  } else {
    noteEl.textContent = "現在AI献立機能(外部AI)は利用できないため、栄養バランスを考慮したルールベースの提案を表示しています。";
    noteEl.classList.remove("is-empty");
    noteEl.classList.add("is-fallback");
  }

  const saveBtn = document.getElementById("aiMealSaveBtn");
  saveBtn.textContent = suggestion.saved ? "⭐ 保存済み" : "⭐ この献立を保存";
  saveBtn.classList.toggle("is-saved", !!suggestion.saved);

  const grid = document.getElementById("aiMealGrid");
  grid.innerHTML = "";
  AI_MEAL_TYPES.forEach(({ type, label, icon }) => {
    const key = type.toLowerCase();
    const meal = suggestion[key];
    grid.appendChild(buildAiMealCard(type, label, icon, meal));
  });
}

function buildAiMealCard(type, label, icon, meal) {
  const card = document.createElement("div");
  card.className = "ai-meal-card";
  card.dataset.mealType = type;

  if (!meal) {
    card.innerHTML = `<div class="ai-meal-card-title">${icon} ${label}</div><div class="ai-meal-dish">提案を準備できませんでした</div>`;
    return card;
  }

  const nutrition = [];
  if (meal.calories != null) nutrition.push(`約${meal.calories}kcal`);
  if (meal.proteinG != null) nutrition.push(`たんぱく質 約${meal.proteinG}g`);
  if (meal.fatG != null) nutrition.push(`脂質 約${meal.fatG}g`);
  if (meal.carbsG != null) nutrition.push(`炭水化物 約${meal.carbsG}g`);
  if (meal.cookingMinutes != null) nutrition.push(`調理時間 約${meal.cookingMinutes}分`);

  card.innerHTML = `
    <div class="ai-meal-card-title">${icon} ${label}</div>
    <div class="ai-meal-dish">${escapeAiHtml(meal.dishName || "")}</div>
    ${meal.ingredients ? `<div class="ai-meal-ingredients">使用食材: ${escapeAiHtml(meal.ingredients)}</div>` : ""}
    <div class="ai-meal-nutrition">${nutrition.map((n) => `<span>${escapeAiHtml(n)}</span>`).join("")}</div>
    ${meal.reason ? `<div class="ai-meal-reason"><strong>おすすめ理由:</strong> ${escapeAiHtml(meal.reason)}</div>` : ""}
    <div class="ai-meal-card-actions">
      <button type="button" class="btn btn-sm btn-secondary ai-meal-register-btn">この献立を${label}に登録</button>
      <span class="status-msg ai-meal-register-status"></span>
    </div>
  `;
  card.querySelector(".ai-meal-register-btn").addEventListener("click", (e) => registerAiMeal(type, e.currentTarget));
  return card;
}

function renderAiUnavailable() {
  document.getElementById("aiMealSummary").textContent = "";
  document.getElementById("aiMealSourceNote").textContent = "";
  const grid = document.getElementById("aiMealGrid");
  grid.innerHTML = `<p class="ai-meal-unavailable">AI献立機能は現在利用できません。しばらくしてから再度お試しください。</p>`;
  document.getElementById("aiMealRegenerateBtn").disabled = true;
  document.getElementById("aiMealSaveBtn").disabled = true;
}

// ------------------------------------------------------------------
// 食の好み設定
// ------------------------------------------------------------------

async function loadFoodPreference() {
  try {
    const res = await fetch(`/api/meal/preference?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const pref = await res.json();
    document.getElementById("prefFavorite").value = pref.favoriteFoods ?? "";
    document.getElementById("prefDisliked").value = pref.dislikedFoods ?? "";
    document.getElementById("prefAllergies").value = pref.allergies ?? "";
    document.getElementById("prefRestrictions").value = pref.dietaryRestrictions ?? "";
    document.getElementById("prefBudget").value = pref.budgetYen ?? "";
    document.getElementById("prefCookingMinutes").value = pref.cookingMinutes ?? "";
    document.getElementById("prefSelfCooked").value = pref.selfCooked ?? "";
    document.getElementById("prefCookingLevel").value = pref.cookingLevel ?? "";
  } catch (e) {
    console.error(e);
  }
}

async function saveFoodPreference() {
  const btn = document.getElementById("prefSaveBtn");
  const statusEl = document.getElementById("prefStatus");
  const str = (id) => {
    const v = document.getElementById(id).value.trim();
    return v === "" ? null : v;
  };
  const num = (id) => {
    const v = document.getElementById(id).value;
    return v === "" ? null : Number(v);
  };
  const payload = {
    favoriteFoods: str("prefFavorite"),
    dislikedFoods: str("prefDisliked"),
    allergies: str("prefAllergies"),
    dietaryRestrictions: str("prefRestrictions"),
    budgetYen: num("prefBudget"),
    cookingMinutes: num("prefCookingMinutes"),
    selfCooked: str("prefSelfCooked"),
    cookingLevel: str("prefCookingLevel"),
  };

  setButtonLoading(btn, true);
  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/meal/preference?employeeId=${HEALTH_EMPLOYEE_ID}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("保存に失敗しました");
    statusEl.textContent = "保存しました(次回の献立提案から反映されます)";
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "保存エラー";
    statusEl.classList.add("error");
  } finally {
    setButtonLoading(btn, false);
    statusEl.classList.remove("is-loading");
  }
}

function escapeAiHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}
