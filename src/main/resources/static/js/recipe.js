// ------------------------------------------------------------------
// レシピ・調理方法・調理手順(Phase 4)
// ------------------------------------------------------------------

const COOKING_METHODS = [
  { value: "GRILL", label: "焼く" },
  { value: "STIR_FRY", label: "炒める" },
  { value: "SIMMER", label: "煮る" },
  { value: "STEAM", label: "蒸す" },
  { value: "BOIL", label: "茹でる" },
  { value: "DEEP_FRY", label: "揚げる" },
  { value: "MICROWAVE", label: "電子レンジ" },
  { value: "RICE_COOKER", label: "炊く" },
  { value: "TOSS", label: "和える" },
  { value: "RAW", label: "生食" },
  { value: "OTHER", label: "その他" },
];

let editingRecipeId = null;

window.addEventListener("DOMContentLoaded", () => {
  const select = document.getElementById("rCookingMethod");
  COOKING_METHODS.forEach(({ value, label }) => {
    const opt = document.createElement("option");
    opt.value = value;
    opt.textContent = label;
    select.appendChild(opt);
  });

  document.getElementById("recipeNewBtn").addEventListener("click", () => openForm(null));
  document.getElementById("recipeFormCloseBtn").addEventListener("click", closeForm);
  document.getElementById("recipeForm").addEventListener("submit", submitRecipeForm);
  document.getElementById("rIngredientAddBtn").addEventListener("click", () => addIngredientRow());
  document.getElementById("rStepAddBtn").addEventListener("click", () => addStepRow());

  loadRecipes();
});

async function readRecipeErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function loadRecipes() {
  const statusEl = document.getElementById("recipeListStatus");
  const listEl = document.getElementById("recipeList");
  try {
    const res = await fetch(`/api/recipes?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error(await readRecipeErrorMessage(res, "レシピの取得に失敗しました"));
    const recipes = await res.json();
    renderRecipeList(recipes);
    statusEl.textContent = "";
    statusEl.classList.remove("error");
  } catch (e) {
    console.error(e);
    listEl.innerHTML = "";
    statusEl.textContent = e.message || "レシピの取得に失敗しました";
    statusEl.classList.add("error");
  }
}

function renderRecipeList(recipes) {
  const listEl = document.getElementById("recipeList");
  listEl.innerHTML = "";
  if (!recipes || recipes.length === 0) {
    const p = document.createElement("p");
    p.className = "snack-empty";
    p.textContent = "まだレシピが登録されていません。「+ 新しいレシピを登録」から追加しましょう。";
    listEl.appendChild(p);
    return;
  }
  recipes.forEach((r) => listEl.appendChild(buildRecipeCard(r)));
}

// starLabel() / renderRecipeDetailHtml() / escapeAiHtml() は js/recipe-shared.js で定義(共通化)

function buildRecipeCard(r) {
  const card = document.createElement("section");
  card.className = "meal-card recipe-card";
  card.dataset.recipeId = r.id;

  const metaParts = [];
  if (r.cookingMethodLabel) metaParts.push(`調理方法: ${r.cookingMethodLabel}`);
  if (r.prepMinutes != null) metaParts.push(`準備 ${r.prepMinutes}分`);
  if (r.cookMinutes != null) metaParts.push(`調理 ${r.cookMinutes}分`);
  if (r.totalMinutes != null) metaParts.push(`合計 ${r.totalMinutes}分`);
  metaParts.push(`難易度 ${starLabel(r.difficulty)}`);
  if (r.calories != null) metaParts.push(`約${r.calories}kcal`);
  if (r.equipment) metaParts.push(`調理器具: ${r.equipment}`);

  card.innerHTML = `
    <div class="meal-card-head">
      <h2>🍳 ${escapeAiHtml(r.name)}</h2>
    </div>
    <div class="recipe-card-meta">${metaParts.map((m) => `<span>${escapeAiHtml(m)}</span>`).join("")}</div>
    <div class="recipe-card-detail" hidden></div>
    <div class="meal-view-actions">
      <button type="button" class="btn btn-secondary btn-sm recipe-detail-btn">詳細を見る</button>
      <button type="button" class="btn btn-secondary btn-sm recipe-edit-btn">編集</button>
      <button type="button" class="btn btn-danger btn-sm recipe-delete-btn">削除</button>
    </div>
  `;

  card.querySelector(".recipe-detail-btn").addEventListener("click", (e) => toggleDetail(r.id, card, e.currentTarget));
  card.querySelector(".recipe-edit-btn").addEventListener("click", () => openForm(r.id));
  card.querySelector(".recipe-delete-btn").addEventListener("click", () => deleteRecipe(r.id, r.name));
  return card;
}

async function toggleDetail(id, card, btn) {
  const detailEl = card.querySelector(".recipe-card-detail");
  if (!detailEl.hidden) {
    detailEl.hidden = true;
    btn.textContent = "詳細を見る";
    return;
  }
  btn.textContent = "読み込み中...";
  try {
    const res = await fetch(`/api/recipes/${id}?employeeId=${HEALTH_EMPLOYEE_ID}`);
    if (!res.ok) throw new Error(await readRecipeErrorMessage(res, "レシピ詳細の取得に失敗しました"));
    const detail = await res.json();
    detailEl.innerHTML = renderRecipeDetailHtml(detail);
    detailEl.hidden = false;
    btn.textContent = "詳細を閉じる";
  } catch (e) {
    console.error(e);
    btn.textContent = "詳細を見る";
    alert(e.message || "レシピ詳細の取得に失敗しました");
  }
}

async function deleteRecipe(id, name) {
  if (!window.confirm(`「${name}」を削除しますか?`)) return;
  try {
    const res = await fetch(`/api/recipes/${id}?employeeId=${HEALTH_EMPLOYEE_ID}`, { method: "DELETE" });
    if (!res.ok) throw new Error(await readRecipeErrorMessage(res, "レシピの削除に失敗しました"));
    loadRecipes();
  } catch (e) {
    console.error(e);
    alert(e.message || "レシピの削除に失敗しました");
  }
}

// ------------------------------------------------------------------
// 登録・編集フォーム
// ------------------------------------------------------------------

function addIngredientRow(ingredient) {
  const wrap = document.getElementById("rIngredientList");
  const row = document.createElement("div");
  row.className = "recipe-edit-row";
  row.innerHTML = `
    <input class="ri-name" type="text" placeholder="食材名(例: 鶏むね肉)" value="${escapeAttr(ingredient?.name)}" />
    <input class="ri-quantity" type="number" step="0.1" min="0" placeholder="数量" value="${escapeAttr(ingredient?.quantity)}" />
    <input class="ri-unit" type="text" placeholder="単位(g,個 等)" value="${escapeAttr(ingredient?.unit)}" />
    <button type="button" class="btn btn-danger btn-sm ri-remove">削除</button>
  `;
  row.querySelector(".ri-remove").addEventListener("click", () => row.remove());
  wrap.appendChild(row);
}

function addStepRow(text) {
  const wrap = document.getElementById("rStepList");
  const row = document.createElement("div");
  row.className = "recipe-edit-row";
  row.innerHTML = `
    <span class="recipe-step-no"></span>
    <input class="rs-text" type="text" placeholder="手順(例: フライパンを加熱する)" value="${escapeAttr(text)}" />
    <button type="button" class="btn btn-secondary btn-sm rs-up" title="上に移動">↑</button>
    <button type="button" class="btn btn-secondary btn-sm rs-down" title="下に移動">↓</button>
    <button type="button" class="btn btn-danger btn-sm rs-remove">削除</button>
  `;
  row.querySelector(".rs-remove").addEventListener("click", () => { row.remove(); renumberSteps(); });
  // ⑫調理手順の順番変更。前後の要素と入れ替えるだけのシンプルな実装
  row.querySelector(".rs-up").addEventListener("click", () => {
    const prev = row.previousElementSibling;
    if (prev) wrap.insertBefore(row, prev);
    renumberSteps();
  });
  row.querySelector(".rs-down").addEventListener("click", () => {
    const next = row.nextElementSibling;
    if (next) wrap.insertBefore(next, row);
    renumberSteps();
  });
  wrap.appendChild(row);
  renumberSteps();
}

function renumberSteps() {
  document.querySelectorAll("#rStepList .recipe-edit-row").forEach((row, i) => {
    row.querySelector(".recipe-step-no").textContent = `${i + 1}.`;
  });
}

async function openForm(id) {
  editingRecipeId = id;
  document.getElementById("recipeFormTitle").textContent = id ? "レシピを編集" : "レシピを登録";
  document.getElementById("recipeFormStatus").textContent = "";
  document.getElementById("recipeFormStatus").classList.remove("error");
  document.getElementById("recipeForm").reset();
  document.getElementById("rIngredientList").innerHTML = "";
  document.getElementById("rStepList").innerHTML = "";

  if (id) {
    try {
      const res = await fetch(`/api/recipes/${id}?employeeId=${HEALTH_EMPLOYEE_ID}`);
      if (!res.ok) throw new Error(await readRecipeErrorMessage(res, "レシピの取得に失敗しました"));
      const r = await res.json();
      document.getElementById("rName").value = r.name ?? "";
      document.getElementById("rCookingMethod").value = r.cookingMethod ?? "";
      document.getElementById("rPrep").value = r.prepMinutes ?? "";
      document.getElementById("rCook").value = r.cookMinutes ?? "";
      document.getElementById("rDifficulty").value = r.difficulty ?? "";
      document.getElementById("rEquipment").value = r.equipment ?? "";
      document.getElementById("rCalories").value = r.calories ?? "";
      document.getElementById("rProtein").value = r.proteinG ?? "";
      document.getElementById("rFat").value = r.fatG ?? "";
      document.getElementById("rCarbs").value = r.carbsG ?? "";
      document.getElementById("rSalt").value = r.saltG ?? "";
      document.getElementById("rMemo").value = r.memo ?? "";
      document.getElementById("rStorageFridge").checked = !!r.storageFridge;
      document.getElementById("rStorageFreezer").checked = !!r.storageFreezer;
      document.getElementById("rStorageDays").value = r.storageDays ?? "";
      document.getElementById("rReheatMethod").value = r.reheatMethod ?? "";
      (r.ingredients || []).forEach((i) => addIngredientRow(i));
      (r.steps || []).forEach((s) => addStepRow(s));
    } catch (e) {
      console.error(e);
      alert(e.message || "レシピの取得に失敗しました");
      return;
    }
  } else {
    addIngredientRow();
    addStepRow();
  }

  document.getElementById("recipeFormPanel").hidden = false;
  document.getElementById("recipeFormPanel").scrollIntoView({ behavior: "smooth" });
}

function closeForm() {
  document.getElementById("recipeFormPanel").hidden = true;
  editingRecipeId = null;
}

async function submitRecipeForm(e) {
  e.preventDefault();
  const statusEl = document.getElementById("recipeFormStatus");
  const name = document.getElementById("rName").value.trim();
  if (!name) {
    statusEl.textContent = "料理名は必須です";
    statusEl.classList.add("error");
    return;
  }

  const numOrNull = (el) => (el.value === "" ? null : Number(el.value));
  const strOrNull = (el) => (el.value.trim() === "" ? null : el.value.trim());

  const ingredients = Array.from(document.querySelectorAll("#rIngredientList .recipe-edit-row"))
    .map((row) => ({
      name: row.querySelector(".ri-name").value.trim(),
      quantity: row.querySelector(".ri-quantity").value === "" ? null : Number(row.querySelector(".ri-quantity").value),
      unit: row.querySelector(".ri-unit").value.trim() || null,
    }))
    .filter((i) => i.name);

  const steps = Array.from(document.querySelectorAll("#rStepList .rs-text"))
    .map((input) => input.value.trim())
    .filter((s) => s);

  const payload = {
    name,
    cookingMethod: strOrNull(document.getElementById("rCookingMethod")),
    prepMinutes: numOrNull(document.getElementById("rPrep")),
    cookMinutes: numOrNull(document.getElementById("rCook")),
    difficulty: numOrNull(document.getElementById("rDifficulty")),
    equipment: strOrNull(document.getElementById("rEquipment")),
    calories: numOrNull(document.getElementById("rCalories")),
    proteinG: numOrNull(document.getElementById("rProtein")),
    fatG: numOrNull(document.getElementById("rFat")),
    carbsG: numOrNull(document.getElementById("rCarbs")),
    saltG: numOrNull(document.getElementById("rSalt")),
    memo: strOrNull(document.getElementById("rMemo")),
    storageFridge: document.getElementById("rStorageFridge").checked,
    storageFreezer: document.getElementById("rStorageFreezer").checked,
    storageDays: numOrNull(document.getElementById("rStorageDays")),
    reheatMethod: strOrNull(document.getElementById("rReheatMethod")),
    ingredients,
    steps,
  };

  statusEl.textContent = "保存中...";
  statusEl.classList.remove("error");
  try {
    const url = editingRecipeId
      ? `/api/recipes/${editingRecipeId}?employeeId=${HEALTH_EMPLOYEE_ID}`
      : `/api/recipes?employeeId=${HEALTH_EMPLOYEE_ID}`;
    const res = await fetch(url, {
      method: editingRecipeId ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readRecipeErrorMessage(res, "レシピの保存に失敗しました"));
    statusEl.textContent = "保存しました";
    await loadRecipes();
    setTimeout(closeForm, 400);
  } catch (e) {
    console.error(e);
    statusEl.textContent = e.message || "レシピの保存に失敗しました";
    statusEl.classList.add("error");
  }
}

// escapeAiHtml() は js/recipe-shared.js で定義(共通化)

function escapeAttr(v) {
  if (v === null || v === undefined) return "";
  const div = document.createElement("div");
  div.textContent = String(v);
  return div.innerHTML;
}
