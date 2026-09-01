// ------------------------------------------------------------------
// 食事記録(朝食・昼食・夕食・間食の入力と、今日の食事の可視化)
//
// 朝食・昼食・夕食は「表示モード」と「編集モード」を切り替える方式:
//   ・記録があれば内容 + [編集][削除] を表示
//   ・記録がなければ「まだ記録されていません」+ [食事を記録] を表示
//   ・編集/記録ボタンを押すとフォームに切り替わり、保存 or キャンセルで表示モードへ戻る
// ------------------------------------------------------------------

const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER"];
const MEAL_LABELS = { BREAKFAST: "朝食", LUNCH: "昼食", DINNER: "夕食" };

// 直近に取得した各区分の記録(編集フォームを開くときに再取得せず使う)
const mealRecordCache = {};

window.addEventListener("DOMContentLoaded", () => {
  MEAL_TYPES.forEach((type) => {
    const card = mealCardEl(type);
    const form = card.querySelector(".meal-form");
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      saveMealForm(type);
    });
    form.querySelector(".m-cancel-btn").addEventListener("click", () => {
      showView(type);
    });
  });
  document.getElementById("snackForm").addEventListener("submit", addSnack);
  document.getElementById("recipeViewCloseBtn").addEventListener("click", closeRecipeModal);
  loadDay();
});

function mealCardEl(type) {
  return document.querySelector(`.meal-card[data-meal-type="${type}"]`);
}

async function loadDay() {
  try {
    const res = await fetch(`/api/meal/day?employeeId=${HEALTH_EMPLOYEE_ID}&date=${healthTodayStr()}`);
    if (!res.ok) throw new Error("読み込みに失敗しました");
    const day = await res.json();
    mealRecordCache.BREAKFAST = day.breakfast?.[0] ?? null;
    mealRecordCache.LUNCH = day.lunch?.[0] ?? null;
    mealRecordCache.DINNER = day.dinner?.[0] ?? null;
    MEAL_TYPES.forEach((type) => renderView(type));
    renderSnacks(day.snacks);
    renderSummary(day);
  } catch (e) {
    console.error(e);
    MEAL_TYPES.forEach((type) => {
      const body = mealCardEl(type).querySelector(".m-view-body");
      body.textContent = "読み込みに失敗しました";
      body.classList.add("is-empty");
    });
  }
}

/** 表示モード(記録内容 or 「まだ記録されていません」)を描画する */
function renderView(type) {
  const card = mealCardEl(type);
  const body = card.querySelector(".m-view-body");
  const actions = card.querySelector(".m-view-actions");
  const record = mealRecordCache[type];

  body.innerHTML = "";
  actions.innerHTML = "";

  if (record) {
    body.classList.remove("is-empty");
    if (record.dishName) {
      const dish = document.createElement("div");
      dish.className = "meal-view-dish";
      dish.textContent = record.dishName;
      body.appendChild(dish);
    }
    const items = document.createElement("div");
    items.textContent = record.items && record.items.trim() ? record.items : "(内容未記入)";
    body.appendChild(items);

    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "btn btn-secondary btn-sm";
    editBtn.textContent = "編集";
    editBtn.addEventListener("click", () => showForm(type, record));

    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "削除";
    delBtn.addEventListener("click", () => deleteMeal(type, record.id));

    actions.appendChild(editBtn);
    actions.appendChild(delBtn);

    // ①料理名が分かっていて、かつ既にレシピが紐づいている記録にだけ「レシピを見る」を表示する(⑧)。
    // 「レシピを作成」ボタンは廃止したため、レシピ未登録の記録にはボタンを出さない。
    if (record.dishName && record.dishName.trim() && record.recipeId) {
      const recipeBtn = document.createElement("button");
      recipeBtn.type = "button";
      recipeBtn.className = "btn btn-secondary btn-sm";
      recipeBtn.textContent = "🍳 レシピを見る";
      recipeBtn.addEventListener("click", () => openRecipeModal(record, type));
      actions.appendChild(recipeBtn);
    }
  } else {
    body.classList.add("is-empty");
    body.textContent = "まだ記録されていません";

    const addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.className = "btn btn-success btn-sm";
    addBtn.textContent = "食事を記録";
    addBtn.addEventListener("click", () => showForm(type, null));
    actions.appendChild(addBtn);
  }

  showView(type);
}

function showView(type) {
  const card = mealCardEl(type);
  card.querySelector(".meal-view").hidden = false;
  card.querySelector(".meal-form").hidden = true;
}

function showForm(type, record) {
  const card = mealCardEl(type);
  applyMealToForm(type, record);
  card.querySelector(".meal-view").hidden = true;
  card.querySelector(".meal-form").hidden = false;
  setStatus(type, "");
}

function applyMealToForm(type, record) {
  const card = mealCardEl(type);
  const form = card.querySelector(".meal-form");
  form.querySelector(".m-items").value = record?.items ?? "";
  form.querySelector(".m-dish").value = record?.dishName ?? "";
  form.querySelector(".m-amount").value = record?.amount ?? "";
  form.querySelector(".m-time").value = record?.mealTime ?? "";
  form.querySelector(".m-calories").value = record?.calories ?? "";
  form.querySelector(".m-protein").value = record?.proteinG ?? "";
  form.querySelector(".m-fat").value = record?.fatG ?? "";
  form.querySelector(".m-carbs").value = record?.carbsG ?? "";
  form.querySelector(".m-fiber").value = record?.fiberG ?? "";
  form.querySelector(".m-salt").value = record?.saltG ?? "";
  form.querySelector(".m-photo").value = record?.photoUrl ?? "";
  form.querySelector(".m-memo").value = record?.memo ?? "";
  form.dataset.recordId = record?.id ?? "";
}

function setStatus(type, text, isError, isLoading) {
  const statusEl = mealCardEl(type).querySelector(".m-status");
  statusEl.textContent = text;
  statusEl.classList.toggle("error", !!isError);
  statusEl.classList.toggle("is-loading", !!isLoading);
}

/** サーバーからの応答を読み、エラー時は message フィールド(あれば)をそのまま使う。
 *  技術的な詳細(スタックトレース等)はサーバー側が返さない設計なので、そのまま出してよい。 */
async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function saveMealForm(type) {
  const card = mealCardEl(type);
  const form = card.querySelector(".meal-form");
  const num = (sel) => {
    const v = form.querySelector(sel).value;
    return v === "" ? null : Number(v);
  };
  const str = (sel) => {
    const v = form.querySelector(sel).value;
    return v === "" ? null : v;
  };
  const payload = {
    id: form.dataset.recordId ? Number(form.dataset.recordId) : null,
    mealDate: healthTodayStr(),
    mealType: type,
    mealTime: str(".m-time"),
    dishName: str(".m-dish"),
    items: str(".m-items"),
    amount: str(".m-amount"),
    calories: num(".m-calories"),
    proteinG: num(".m-protein"),
    fatG: num(".m-fat"),
    carbsG: num(".m-carbs"),
    fiberG: num(".m-fiber"),
    saltG: num(".m-salt"),
    photoUrl: str(".m-photo"),
    memo: str(".m-memo"),
  };

  setStatus(type, "保存中...", false, true);
  try {
    const res = await fetch(`/api/meal?employeeId=${HEALTH_EMPLOYEE_ID}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "食事の保存に失敗しました"));
    const saved = await res.json();
    mealRecordCache[type] = saved;
    renderView(type);
    setStatus(type, `${MEAL_LABELS[type]}を保存しました`);
  } catch (e) {
    console.error(e);
    setStatus(type, e.message || "食事の保存に失敗しました", true);
  }
}

async function deleteMeal(type, id) {
  if (!id) return;
  if (!window.confirm("この食事記録を削除しますか?")) return;

  try {
    const res = await fetch(`/api/meal/${id}?employeeId=${HEALTH_EMPLOYEE_ID}`, { method: "DELETE" });
    if (!res.ok) throw new Error(await readErrorMessage(res, "食事の削除に失敗しました"));
    mealRecordCache[type] = null;
    renderView(type);
    setStatus(type, `${MEAL_LABELS[type]}を削除しました`);
    loadDay(); // 栄養サマリー欄も最新の状態に合わせて再取得する
  } catch (e) {
    console.error(e);
    setStatus(type, e.message || "食事の削除に失敗しました", true);
  }
}

function renderSnacks(snacks) {
  const wrap = document.getElementById("snackList");
  wrap.innerHTML = "";
  if (!snacks || snacks.length === 0) {
    const p = document.createElement("p");
    p.className = "snack-empty";
    p.textContent = "間食の記録はありません";
    wrap.appendChild(p);
    return;
  }
  snacks.forEach((s) => {
    const row = document.createElement("div");
    row.className = "snack-row";
    row.innerHTML = `
      <span class="snack-items">${escapeHtml(s.items ?? "")}</span>
      ${s.calories ? `<span class="snack-kcal">${s.calories}kcal</span>` : ""}
      <button type="button" class="btn btn-sm btn-danger snack-del" data-id="${s.id}">削除</button>
    `;
    wrap.appendChild(row);
  });
  wrap.querySelectorAll(".snack-del").forEach((btn) => {
    btn.addEventListener("click", () => deleteSnack(Number(btn.dataset.id)));
  });
}

async function deleteSnack(id) {
  if (!window.confirm("この間食の記録を削除しますか?")) return;
  try {
    const res = await fetch(`/api/meal/${id}?employeeId=${HEALTH_EMPLOYEE_ID}`, { method: "DELETE" });
    if (!res.ok) throw new Error(await readErrorMessage(res, "削除に失敗しました"));
    loadDay();
  } catch (e) {
    console.error(e);
  }
}

async function addSnack(e) {
  e.preventDefault();
  const input = document.getElementById("snackItems");
  const statusEl = document.getElementById("snackStatus");
  const items = input.value.trim();
  if (!items) {
    statusEl.textContent = "食べたものを入力してください";
    statusEl.classList.add("error");
    return;
  }
  statusEl.textContent = "追加中...";
  statusEl.classList.remove("error");
  statusEl.classList.add("is-loading");
  try {
    const res = await fetch(`/api/meal?employeeId=${HEALTH_EMPLOYEE_ID}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mealDate: healthTodayStr(), mealType: "SNACK", items }),
    });
    if (!res.ok) throw new Error(await readErrorMessage(res, "追加に失敗しました"));
    input.value = "";
    statusEl.textContent = "";
    statusEl.classList.remove("error");
    loadDay();
  } catch (e2) {
    console.error(e2);
    statusEl.textContent = e2.message || "追加エラー";
    statusEl.classList.add("error");
  } finally {
    statusEl.classList.remove("is-loading");
  }
}

function renderSummary(day) {
  const wrap = document.getElementById("summaryMeals");
  wrap.innerHTML = "";
  const groups = [
    { label: "朝食", icon: "🌅", list: day.breakfast },
    { label: "昼食", icon: "☀️", list: day.lunch },
    { label: "夕食", icon: "🌙", list: day.dinner },
  ];
  groups.forEach((g) => {
    const box = document.createElement("div");
    box.className = "meal-summary-item";
    const itemsText =
      g.list && g.list.length > 0
        ? g.list.map((r) => r.items).filter(Boolean).join(" / ") || "(内容未記入)"
        : "未入力";
    box.innerHTML = `
      <div class="ms-title">${g.icon} ${g.label}</div>
      <div class="ms-body">${escapeHtml(itemsText)}</div>
    `;
    wrap.appendChild(box);
  });
  if (day.snacks && day.snacks.length > 0) {
    const box = document.createElement("div");
    box.className = "meal-summary-item";
    box.innerHTML = `
      <div class="ms-title">🍪 間食</div>
      <div class="ms-body">${escapeHtml(day.snacks.map((s) => s.items).filter(Boolean).join(" / "))}</div>
    `;
    wrap.appendChild(box);
  }

  const n = day.nutrition || {};
  document.getElementById("sumCalories").textContent = n.totalCalories ?? "-";
  document.getElementById("sumProtein").textContent = n.totalProteinG ?? "-";
  document.getElementById("sumFat").textContent = n.totalFatG ?? "-";
  document.getElementById("sumCarbs").textContent = n.totalCarbsG ?? "-";
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}

// ------------------------------------------------------------------
// レシピ詳細モーダル(①②③④⑤⑥⑦⑧⑨⑫)
// ------------------------------------------------------------------

/** 「レシピを見る」ボタンから呼ばれる。既にレシピが紐づいている記録専用(⑧。
 *  レシピ未登録の記録にはそもそもボタンを表示しないため、通常はここに来ない)。 */
async function openRecipeModal(record, type) {
  document.getElementById("recipeViewModal").hidden = false;
  document.getElementById("recipeViewTitle").textContent = record.dishName || "レシピ";
  document.getElementById("recipeViewStatus").textContent = "";
  document.getElementById("recipeViewStatus").classList.remove("error");

  if (record.recipeId) {
    const bodyEl = document.getElementById("recipeViewBody");
    bodyEl.innerHTML = '<p class="alert-empty is-loading">読み込み中...</p>';
    try {
      const res = await fetch(`/api/recipes/${record.recipeId}`);
      if (!res.ok) throw new Error(await readErrorMessage(res, "レシピの取得に失敗しました"));
      const detail = await res.json();
      bodyEl.innerHTML = renderRecipeDetailHtml(detail, { includeHeader: true });
    } catch (e) {
      console.error(e);
      // 紐づいていたレシピが(削除等で)見つからない場合も、エラー画面にせず作成し直せるようにする(⑧)
      renderRecipeMissing(record, type, e.message);
    }
  } else {
    renderRecipeMissing(record, type, null);
  }
}

/** レシピ未登録(または紐づいていたレシピが削除済み等で見つからない)時の表示。
 *  「レシピを作成」ボタンは廃止したため、案内メッセージのみを表示する(⑧)。 */
function renderRecipeMissing(record, type, errorMessage) {
  const bodyEl = document.getElementById("recipeViewBody");
  bodyEl.innerHTML = `
    <p>${errorMessage ? escapeHtml(errorMessage) : "「" + escapeHtml(record.dishName) + "」のレシピ情報はまだありません。"}</p>
  `;
}

function closeRecipeModal() {
  document.getElementById("recipeViewModal").hidden = true;
}
