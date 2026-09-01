// ------------------------------------------------------------------
// 認証共通処理。ほぼ全ページで読み込む(login.html/register.html含む)。
//
// 1. Spring SecurityのCSRF保護に対応するため、fetch()をラップして
//    Cookie(XSRF-TOKEN)から読んだ値を X-XSRF-TOKEN ヘッダへ自動で付与する。
// 2. ログイン中ユーザー情報(氏名・会社名)+ログアウトボタンを .app-header に描画する。
// 3. セッション切れ等で401が返ってきたら、ログイン画面へ自動的に戻す。
// ------------------------------------------------------------------

/**
 * ボタンの「送信中」表示を統一するヘルパー(項目2)。
 * 見た目(文字を隠してスピナーを表示・not-allowedカーソル)はstyle.css/break.cssの
 * .is-loadingクラス側で一元管理しているため、呼び出し側はこの関数でクラスの
 * 付け外しとdisabled属性の切り替えだけを行う(is-loadingクラスだけではCSSの
 * pointer-events:noneが多くのブラウザ操作を止めるが、フォーム送信やEnterキー等
 * まで確実に防ぐにはdisabled属性自体も必要なため、必ずセットで切り替える)。
 * ほぼ全ページで読み込まれるこのファイルに置くことで、どの画面からも呼べるようにする。
 * @param {HTMLButtonElement|null} btn 対象のボタン要素(nullなら何もしない)
 * @param {boolean} isLoading trueで読み込み中表示、falseで解除
 */
function setButtonLoading(btn, isLoading) {
  if (!btn) return;
  btn.classList.toggle("is-loading", !!isLoading);
  btn.disabled = !!isLoading;
}

/**
 * 一覧データの初回読み込み中(まだ何も表示されていない状態)に表示する、
 * 薄いグレーのプレースホルダー矩形(style.cssの.skeleton-line)を並べた
 * <tr>のHTMLを組み立てる。「読み込み中...」の文字だけより状態が伝わりやすいための
 * 簡易スケルトン表示(項目2の余力対応分)。
 * @param {number} colCount テーブルの列数(そのままcolspanとしても使う)
 * @param {number} [rowCount=3] 表示する行数
 */
function buildSkeletonRows(colCount, rowCount = 3) {
  const cell = '<td><div class="skeleton-line"></div></td>';
  const row = `<tr class="empty-row skeleton-row">${cell.repeat(colCount)}</tr>`;
  return row.repeat(rowCount);
}

/**
 * buildSkeletonRows()のカード/リスト版。テーブルではなく<p>や<div>を並べる一覧
 * (meal-ai-history.js、meal-history.js等)で使う。
 * @param {number} [lineCount=3] 表示する行数
 */
function buildSkeletonBlock(lineCount = 3) {
  const line = '<div class="skeleton-line"></div>';
  return `<div class="alert-empty skeleton-row">${line.repeat(lineCount)}</div>`;
}

(function () {
  const LOGIN_PAGE = "/login.html";
  const AUTH_PAGES = ["/login.html", "/register.html", "/forgot-password.html", "/reset-password.html"];
  const isAuthPage = () => {
    const p = window.location.pathname;
    return AUTH_PAGES.some((page) => p === page || p.endsWith(page));
  };

  function getCookie(name) {
    const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
    return match ? decodeURIComponent(match[1]) : null;
  }

  const originalFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    init = init || {};
    const method = (init.method || "GET").toUpperCase();
    const mutating = ["POST", "PUT", "PATCH", "DELETE"].includes(method);

    if (mutating) {
      const token = getCookie("XSRF-TOKEN");
      if (token) {
        init.headers = new Headers(init.headers || {});
        init.headers.set("X-XSRF-TOKEN", token);
      }
    }
    // 同一オリジンのCookie(セッション/CSRFトークン)を常に送る
    if (init.credentials === undefined) {
      init.credentials = "same-origin";
    }

    return originalFetch(input, init).then((res) => {
      const url = typeof input === "string" ? input : input.url;
      const isLoginCall = url.includes("/api/auth/login");
      if (res.status === 401 && !isAuthPage() && !isLoginCall) {
        window.location.href = LOGIN_PAGE;
      }
      return res;
    });
  };

  if (isAuthPage()) return;

  // ログイン中ユーザー情報バーを .app-header に描画する
  window.addEventListener("DOMContentLoaded", async () => {
    const header = document.querySelector(".app-header") || document.querySelector(".hero");
    if (!header) return;

    try {
      const res = await originalFetchWithCsrf("/api/auth/me");
      if (!res.ok) return;
      const user = await res.json();

      const bar = document.createElement("div");
      bar.className = "auth-bar";
      // 一般ユーザーは自分でパスワードを変更できない仕様のため、この導線は管理者にのみ表示する
      // (管理者による強制リセット直後は、リンクを辿らずともMustChangePasswordFilterが
      // change-password.htmlへ自動的に誘導する)。
      bar.innerHTML = `
        <span class="auth-bar-user">${escapeHtml(user.fullName)}</span>
        <span class="auth-bar-company">${escapeHtml(user.companyName)}${user.departmentName ? " / " + escapeHtml(user.departmentName) : ""}</span>
        ${user.role === "ADMIN" ? '<a class="btn btn-secondary btn-sm" href="/admin-top.html">🏢 管理者メニュー</a><a class="btn btn-secondary btn-sm" href="/change-password.html">🔑 パスワード変更</a>' : ""}
        <button type="button" class="btn btn-secondary btn-sm" id="authLogoutBtn">ログアウト</button>
      `;
      header.appendChild(bar);
      document.getElementById("authLogoutBtn").addEventListener("click", logout);

      // ログイン時間チップ(トップページのみ存在する要素。無ければ何もしない)。
      // 他のページに遷移してもこの/api/auth/meの結果を使って同じログイン時刻を表示できる。
      const loginTimeEl = document.getElementById("loginTimeValue");
      if (loginTimeEl) {
        loginTimeEl.textContent = formatLoginTime(user.lastLoginAt) ?? "-";
      }
    } catch (e) {
      console.error(e);
    }
  });

  /** LocalDateTimeのJSON表現("2026-08-29T10:15:30.123"等)を "MM/DD HH:mm" に整形する */
  function formatLoginTime(iso) {
    if (!iso) return null;
    const [datePart, timePart] = iso.split("T");
    if (!datePart || !timePart) return null;
    const [, m, d] = datePart.split("-");
    const hm = timePart.substring(0, 5);
    return `${m}/${d} ${hm}`;
  }

  function originalFetchWithCsrf(url, init) {
    // window.fetch は上で既に差し替え済みなので、そのまま使えばCSRFヘッダも付与される
    return window.fetch(url, init);
  }

  async function logout() {
    try {
      await window.fetch("/api/auth/logout", { method: "POST" });
    } catch (e) {
      console.error(e);
    } finally {
      window.location.href = LOGIN_PAGE;
    }
  }

  function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s ?? "";
    return div.innerHTML;
  }
})();
