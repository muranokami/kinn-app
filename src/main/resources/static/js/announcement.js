// ------------------------------------------------------------------
// お知らせ一覧(一般ユーザー向け)。
// 全社向け、または自分の部署宛のお知らせのうち、公開済み・表示終了していないものだけを
// サーバー側(AnnouncementService#getForUser)で絞り込んで返す。このJSはその結果を
// 重要度→投稿日時の順のまま描画するだけで、追加のフィルタ・並び替えは行わない。
// ------------------------------------------------------------------

const IMPORTANCE_LABEL = { NORMAL: "通常", IMPORTANT: "重要" };

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

function setStatus(text, isError, isLoading) {
  const el = document.getElementById("statusMsg");
  el.textContent = text || "";
  el.classList.toggle("error", !!isError);
  el.classList.toggle("is-loading", !!isLoading);
}

/** LocalDateTimeのJSON表現("2026-08-30T10:15:30"等)を "MM/DD HH:mm" に整形する */
function formatDateTime(iso) {
  if (!iso) return "-";
  const [datePart, timePart] = iso.split("T");
  if (!datePart || !timePart) return iso;
  const [, m, d] = datePart.split("-");
  const hm = timePart.substring(0, 5);
  return `${m}/${d} ${hm}`;
}

window.addEventListener("DOMContentLoaded", () => {
  loadAnnouncements();
});

async function loadAnnouncements() {
  try {
    const res = await fetch("/api/announcements");
    if (!res.ok) {
      setStatus("お知らせの取得に失敗しました", true);
      return;
    }
    const list = await res.json();
    renderList(list);
    setStatus("");
  } catch (e) {
    console.error(e);
    setStatus("お知らせの取得に失敗しました", true);
  }
}

function renderList(list) {
  const container = document.getElementById("announcementList");
  const emptyEl = document.getElementById("announcementEmpty");
  container.innerHTML = "";

  if (!list || list.length === 0) {
    emptyEl.hidden = false;
    return;
  }
  emptyEl.hidden = true;

  list.forEach((a) => {
    const card = document.createElement("div");
    card.className = "announcement-card";
    card.classList.toggle("is-important", a.importance === "IMPORTANT");
    card.classList.toggle("is-unread", !a.read);
    card.dataset.id = a.id;

    card.innerHTML = `
      <div class="announcement-card-head">
        ${a.read ? "" : '<span class="announcement-unread-dot" aria-hidden="true"></span>'}
        <span class="importance-badge importance-${a.importance}">${escapeHtml(IMPORTANCE_LABEL[a.importance] || a.importance)}</span>
        <span class="scope-badge">${escapeHtml(a.departmentName || "全社")}</span>
        <span class="announcement-title">${escapeHtml(a.title)}</span>
        <span class="announcement-date">${formatDateTime(a.publishedAt)}</span>
      </div>
      <div class="announcement-body" hidden>${escapeHtml(a.body)}</div>
      <div class="announcement-meta" hidden>
        <span>投稿者: ${escapeHtml(a.createdByName || "-")}</span>
      </div>
    `;

    card.addEventListener("click", () => onCardClick(card, a));
    container.appendChild(card);
  });
}

/** クリックで本文を展開/折りたたみし、開いたタイミングで既読にする(未読の場合のみAPIを呼ぶ) */
async function onCardClick(card, announcement) {
  const bodyEl = card.querySelector(".announcement-body");
  const metaEl = card.querySelector(".announcement-meta");
  const opening = bodyEl.hidden;
  bodyEl.hidden = !opening;
  metaEl.hidden = !opening;

  if (opening && !announcement.read) {
    try {
      const res = await fetch(`/api/announcements/${announcement.id}/read`, { method: "POST" });
      if (res.ok) {
        announcement.read = true;
        card.classList.remove("is-unread");
        const dot = card.querySelector(".announcement-unread-dot");
        if (dot) dot.remove();
      }
    } catch (e) {
      console.error(e);
      // 既読登録に失敗しても本文の表示自体は継続する(閲覧を妨げない)
    }
  }
}
