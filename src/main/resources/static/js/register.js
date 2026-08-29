let companyLookupLoadedFor = null;

// 「新しく会社を登録する」で登録が完了し、会社コード案内(#companyCodePanel)を
// 表示している間だけtrueにする。この間にタブを閉じる・ブラウザの戻るボタンで
// 離脱するなど、ページ遷移が起きそうになったらbeforeunloadで確認ダイアログを出し、
// 会社コードを一度も見ないまま離脱してしまう事故を防ぐ(下のhandleRegister参照)。
let companyCodePendingAck = false;
window.addEventListener("beforeunload", (e) => {
  if (!companyCodePendingAck) return;
  e.preventDefault();
  e.returnValue = "";
});

window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("registerForm").addEventListener("submit", handleRegister);
  document.getElementById("modeCreate").addEventListener("change", applyModeVisibility);
  document.getElementById("modeJoin").addEventListener("change", applyModeVisibility);

  const codeInput = document.getElementById("companyCode");
  codeInput.addEventListener("blur", () => loadCompanyByCode(codeInput.value));
  codeInput.addEventListener("change", () => loadCompanyByCode(codeInput.value));

  document.getElementById("companyCodeCopyBtn").addEventListener("click", copyCompanyCode);
  document.getElementById("companyCodeProceedBtn").addEventListener("click", () => {
    // 「ログイン画面へ進む」で明示的に離脱する場合は、以後beforeunloadで
    // 確認を出す必要が無いため解除してから遷移する
    companyCodePendingAck = false;
    window.location.href = "login.html";
  });

  applyModeVisibility();
});

function currentMode() {
  return document.querySelector('input[name="registerMode"]:checked').value;
}

/** 「新しく会社を登録する」/「既存の会社に参加する」で入力項目の表示を切り替える */
function applyModeVisibility() {
  const isCreate = currentMode() === "CREATE";
  document.getElementById("createFields").hidden = !isCreate;
  document.getElementById("joinFields").hidden = isCreate;
}

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

/**
 * 入力された会社コードから、参加先の会社名・部署の選択肢を取得する
 * (「既存の会社に参加する」を選んだ場合のみ)。
 */
async function loadCompanyByCode(rawCode) {
  const code = rawCode.trim().toUpperCase();
  const select = document.getElementById("departmentSelect");
  const note = document.getElementById("companyLookupNote");

  if (!code) {
    select.innerHTML = '<option value="">会社コードを入力すると部署が表示されます</option>';
    note.textContent = "";
    companyLookupLoadedFor = null;
    return;
  }
  if (code === companyLookupLoadedFor) return;

  try {
    const res = await fetch(`/api/auth/company-lookup?companyCode=${encodeURIComponent(code)}`);
    if (!res.ok) {
      select.innerHTML = '<option value="">-</option>';
      note.textContent = await readErrorMessage(res, "会社コードが正しくありません");
      companyLookupLoadedFor = null;
      return;
    }
    const lookup = await res.json();
    companyLookupLoadedFor = code;

    if (!lookup.departmentNames || lookup.departmentNames.length === 0) {
      select.innerHTML = '<option value="">この会社にはまだ部署がありません</option>';
      note.textContent = `「${lookup.companyName}」に参加します。まだ部署が登録されていないため、管理者に部署の追加を依頼してください。`;
      return;
    }
    select.innerHTML = '<option value="">選択してください</option>' +
      lookup.departmentNames.map((d) => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join("");
    note.textContent = `「${lookup.companyName}」に参加します。所属する部署を選択してください。`;
  } catch (e) {
    console.error(e);
    note.textContent = "会社コードの確認に失敗しました。";
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const statusEl = document.getElementById("registerStatus");
  const mode = currentMode();

  const loginId = document.getElementById("loginId").value.trim();
  const password = document.getElementById("password").value;
  const confirmPassword = document.getElementById("confirmPassword").value;
  const fullName = document.getElementById("fullName").value.trim();
  const email = document.getElementById("email").value.trim();

  const payload = { mode, loginId, password, confirmPassword, fullName, email };

  if (mode === "CREATE") {
    payload.companyName = document.getElementById("companyName").value.trim();
    payload.departmentName = document.getElementById("departmentNewInput").value.trim();
    if (!payload.companyName || !payload.departmentName) {
      statusEl.textContent = "会社名・部署名を入力してください";
      statusEl.classList.add("error");
      return;
    }
  } else {
    payload.companyCode = document.getElementById("companyCode").value.trim().toUpperCase();
    payload.departmentName = document.getElementById("departmentSelect").value.trim();
    if (!payload.companyCode || !payload.departmentName) {
      statusEl.textContent = "会社コード・部署を入力してください";
      statusEl.classList.add("error");
      return;
    }
  }

  statusEl.textContent = "";
  statusEl.classList.remove("error");

  if (!loginId || !password || !confirmPassword || !fullName || !email) {
    statusEl.textContent = "すべての項目を入力してください";
    statusEl.classList.add("error");
    return;
  }
  if (password !== confirmPassword) {
    statusEl.textContent = "パスワードが一致しません";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "登録中...";

  // 送信中(サーバーの応答待ち)は送信ボタンと「ログイン画面へ戻る」リンクを
  // 操作不可にする。これが無いと、応答が返る前にリンクから離脱された場合、
  // 登録リクエスト自体はサーバー側で成立してしまうのに、会社コードの案内画面を
  // 一度も表示できないまま終わってしまう恐れがあるため(新しく会社を登録した
  // 直後にしか一度に案内できない値のため、確実に見てもらう必要がある)。
  const submitBtn = e.target.querySelector('button[type="submit"]');
  const backLink = document.getElementById("backToLoginLink");
  submitBtn.disabled = true;
  backLink.setAttribute("aria-disabled", "true");

  try {
    let res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    // 422: 同じ名前の会社が既に登録されている(AuthService#register参照)。
    // 別会社としてあえて同じ名前で登録したい場合もあるため、ここでハード ブロックはせず、
    // 確認ダイアログでユーザーの意思を確認できたら confirmDuplicateName=true を付けて
    // 同じ内容をもう一度送信する(パスワード等を入力し直させない)。
    if (res.status === 422) {
      const message = await readErrorMessage(res, "同じ名前の会社が既に登録されています。");
      const proceed = window.confirm(message + "\n\nこのまま新しい別会社として登録しますか?");
      if (!proceed) {
        statusEl.textContent = "登録を中止しました。会社名をご確認いただくか、既存の会社に参加する場合は会社コードをご利用ください。";
        statusEl.classList.add("error");
        submitBtn.disabled = false;
        backLink.removeAttribute("aria-disabled");
        return;
      }
      res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...payload, confirmDuplicateName: true }),
      });
    }

    if (!res.ok) {
      throw new Error(await readErrorMessage(res, "登録に失敗しました"));
    }
    const user = await res.json();

    if (mode === "CREATE" && user.companyCode) {
      // 会社コードはこの直後の応答でしか一度に確認できないわけではない(管理者トップにも
      // 常設表示するが)、登録直後は控えてもらう案内を優先し、自動遷移はしない。
      document.getElementById("registerForm").hidden = true;
      document.getElementById("companyCodeValue").textContent = user.companyCode;
      document.getElementById("companyCodePanel").hidden = false;
      // 「ログイン画面へ進む」を押すまでは、タブを閉じる・戻るボタン等での
      // 離脱にbeforeunloadで確認を挟む(上の宣言・イベントリスナー参照)
      companyCodePendingAck = true;
      return;
    }

    statusEl.textContent = "登録しました。ログイン画面に移動します...";
    statusEl.classList.remove("error");
    setTimeout(() => {
      window.location.href = "login.html";
    }, 800);
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "登録に失敗しました";
    statusEl.classList.add("error");
    // 失敗時は再度操作できるように戻す
    submitBtn.disabled = false;
    backLink.removeAttribute("aria-disabled");
  }
}

async function copyCompanyCode() {
  const value = document.getElementById("companyCodeValue").textContent;
  if (!value || value === "-") return;
  try {
    await navigator.clipboard.writeText(value);
  } catch (e) {
    console.error(e);
  }
}

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}
