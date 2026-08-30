// ------------------------------------------------------------------
// パスワード変更画面。
// ・管理者による強制リセット直後(mustChangePassword=true)の強制変更
// ・本人による任意のパスワード変更
// のどちらもこの1画面・1APIで扱う。現在のパスワードの入力は求めない
// (忘れてしまった現在のパスワードが分からず変更もできない、という手詰まりを避けるため。
// ユーザーIDの入力・一致確認をもって本人確認とする)。
// ------------------------------------------------------------------

// バックエンド(ChangePasswordRequestDto)の@Patternと必ず同じ正規表現・基準にする
// (RegisterRequestDto.passwordと同じ強度基準。片方だけ緩いと実質的にザルになるため)。
// loginIdは既存ユーザーの本人確認用の入力(新規作成ではない)であり、過去に緩い基準で
// 作成されたIDも変更なく使い続けられる必要があるため、ここでは文字種チェックを行わない。
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$/;

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("changePasswordForm").addEventListener("submit", handleChangePassword);
  await applyAccessState();
});

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

/**
 * ・管理者による強制リセット直後(mustChangePassword=true)なら案内を表示してフォームは使える状態にする。
 * ・それ以外で一般ユーザー(ADMIN以外)がこの画面を開いた場合は、フォーム自体を隠す
 *   (一般ユーザーは自分でパスワードを変更できない仕様のため。送信してもAPI側で拒否されるが、
 *   操作できない理由が画面上で分かるようにする)。
 */
async function applyAccessState() {
  try {
    const res = await fetch("/api/auth/me");
    if (!res.ok) return;
    const user = await res.json();
    if (user.mustChangePassword) {
      document.getElementById("forcedNote").hidden = false;
      return;
    }
    if (user.role !== "ADMIN") {
      document.getElementById("notAllowedNote").hidden = false;
      document.getElementById("changePasswordForm").hidden = true;
    }
  } catch (e) {
    console.error(e);
  }
}

async function handleChangePassword(e) {
  e.preventDefault();
  const statusEl = document.getElementById("changeStatus");
  const loginId = document.getElementById("loginId").value.trim();
  const newPassword = document.getElementById("newPassword").value;
  const confirmPassword = document.getElementById("confirmPassword").value;

  statusEl.textContent = "";
  statusEl.classList.remove("error");

  if (!loginId || !newPassword || !confirmPassword) {
    statusEl.textContent = "すべての項目を入力してください";
    statusEl.classList.add("error");
    return;
  }
  if (newPassword.length < 8 || !PASSWORD_PATTERN.test(newPassword)) {
    statusEl.textContent = "新しいパスワードは英字の大文字・小文字・数字をすべて含む8文字以上で入力してください";
    statusEl.classList.add("error");
    return;
  }
  if (newPassword !== confirmPassword) {
    statusEl.textContent = "新しいパスワードが一致しません";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "変更中...";
  try {
    const res = await fetch("/api/auth/change-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginId, newPassword, confirmPassword }),
    });
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, "パスワードの変更に失敗しました"));
    }
    statusEl.textContent = "パスワードを変更しました";
    setTimeout(() => {
      window.location.href = "index.html";
    }, 800);
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "パスワードの変更に失敗しました";
    statusEl.classList.add("error");
  }
}
