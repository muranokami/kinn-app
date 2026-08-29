window.addEventListener("DOMContentLoaded", () => {
  document.getElementById("loginForm").addEventListener("submit", handleLogin);
});

async function readErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body?.message || fallback;
  } catch {
    return fallback;
  }
}

async function handleLogin(e) {
  e.preventDefault();
  const statusEl = document.getElementById("loginStatus");
  const companyName = document.getElementById("companyName").value.trim();
  const loginId = document.getElementById("loginId").value.trim();
  const password = document.getElementById("password").value;

  statusEl.textContent = "";
  statusEl.classList.remove("error");

  if (!companyName || !loginId || !password) {
    statusEl.textContent = "会社名・ユーザーID・パスワードを入力してください";
    statusEl.classList.add("error");
    return;
  }

  statusEl.textContent = "ログイン中...";
  try {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ companyName, loginId, password }),
    });
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, "会社名、ユーザーID、またはパスワードが正しくありません。"));
    }
    const user = await res.json();
    window.location.href = user.mustChangePassword ? "change-password.html" : "index.html";
  } catch (err) {
    console.error(err);
    statusEl.textContent = err.message || "ログインに失敗しました";
    statusEl.classList.add("error");
  }
}
