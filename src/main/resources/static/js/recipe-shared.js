// ------------------------------------------------------------------
// レシピ詳細の共通描画ロジック(recipe.js・meal.jsの両方から使う)。
// 「② レシピ詳細」の材料・調理方法・調理手順・調理時間・難易度・栄養情報の表示を
// 1箇所にまとめることで、レシピ帳画面(recipe.html)と食事記録画面(meal.html)の
// 「レシピを見る」でまったく同じ見た目・同じ内容を保証する(重複実装を避ける)。
// ------------------------------------------------------------------

function starLabel(difficulty) {
  if (!difficulty) return "未設定";
  const full = "★".repeat(difficulty);
  const empty = "☆".repeat(Math.max(0, 3 - difficulty));
  return full + empty;
}

function escapeAiHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

/**
 * レシピ詳細のHTML本文を生成する。
 * @param detail RecipeDto相当のオブジェクト(材料・手順・栄養情報を含む)
 * @param opts.includeHeader trueの場合、料理名や調理方法・調理時間・難易度・カロリーの
 *        見出しブロックも含める(meal.htmlのモーダルなど、単独で完結した表示が必要な場面用。
 *        recipe.htmlのカード内表示はカード自体に既にメタ情報があるためfalseのまま使う)。
 */
function renderRecipeDetailHtml(detail, opts) {
  opts = opts || {};

  const headerHtml = opts.includeHeader
    ? `<div class="recipe-detail-section recipe-detail-meta">
        ${detail.cookingMethodLabel ? `<span>調理方法: ${escapeAiHtml(detail.cookingMethodLabel)}</span>` : ""}
        ${detail.prepMinutes != null ? `<span>準備時間 ${detail.prepMinutes}分</span>` : ""}
        ${detail.cookMinutes != null ? `<span>調理時間 ${detail.cookMinutes}分</span>` : ""}
        ${detail.totalMinutes != null ? `<span>合計 ${detail.totalMinutes}分</span>` : ""}
        <span>難易度 ${starLabel(detail.difficulty)}</span>
        ${detail.calories != null ? `<span>約${detail.calories}kcal</span>` : ""}
      </div>`
    : "";

  const ingredientsHtml =
    detail.ingredients && detail.ingredients.length > 0
      ? detail.ingredients
          .map((i) => {
            const qty = [i.quantity, i.unit].filter((v) => v !== null && v !== undefined && v !== "").join("");
            return `<li>${escapeAiHtml(i.name)}${qty ? ` <span class="recipe-qty">${escapeAiHtml(qty)}</span>` : ""}</li>`;
          })
          .join("")
      : "<li>(材料は未登録です)</li>";

  const stepsHtml =
    detail.steps && detail.steps.length > 0
      ? detail.steps.map((s) => `<li>${escapeAiHtml(s)}</li>`).join("")
      : "<li>(手順は未登録です)</li>";

  const nutritionParts = [];
  if (detail.proteinG != null) nutritionParts.push(`たんぱく質 ${detail.proteinG}g`);
  if (detail.fatG != null) nutritionParts.push(`脂質 ${detail.fatG}g`);
  if (detail.carbsG != null) nutritionParts.push(`炭水化物 ${detail.carbsG}g`);
  if (detail.saltG != null) nutritionParts.push(`食塩相当量 ${detail.saltG}g`);
  const nutritionHtml =
    nutritionParts.length > 0
      ? `<div class="recipe-detail-section"><h3>栄養情報(1人前目安)</h3><p>${escapeAiHtml(nutritionParts.join(" / "))}</p></div>`
      : "";

  const storageParts = [];
  if (detail.storageFridge) storageParts.push("冷蔵保存OK");
  if (detail.storageFreezer) storageParts.push("冷凍保存OK");
  if (detail.storageDays != null) storageParts.push(`保存目安 ${detail.storageDays}日`);
  if (detail.reheatMethod) storageParts.push(`再加熱: ${detail.reheatMethod}`);

  return `
    ${headerHtml}
    <div class="recipe-detail-section">
      <h3>材料</h3>
      <ul class="recipe-ingredient-list">${ingredientsHtml}</ul>
    </div>
    <div class="recipe-detail-section">
      <h3>調理方法</h3>
      <p>${escapeAiHtml(detail.cookingMethodLabel || "未設定")}</p>
    </div>
    ${detail.equipment ? `<div class="recipe-detail-section"><h3>調理器具</h3><p>${escapeAiHtml(detail.equipment)}</p></div>` : ""}
    <div class="recipe-detail-section">
      <h3>調理手順</h3>
      <ol class="recipe-step-list">${stepsHtml}</ol>
    </div>
    ${nutritionHtml}
    ${storageParts.length > 0 ? `<div class="recipe-detail-section"><h3>作り置き</h3><p>${escapeAiHtml(storageParts.join(" / "))}</p></div>` : ""}
    ${detail.memo ? `<div class="recipe-detail-section"><h3>メモ</h3><p>${escapeAiHtml(detail.memo)}</p></div>` : ""}
  `;
}
