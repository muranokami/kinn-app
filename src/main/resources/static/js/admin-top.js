// ------------------------------------------------------------------
// 管理者メニュー: 自社の会社コード表示(同僚の入社登録用)。
// /api/auth/me はADMINにのみcompanyCodeを返す(AuthUserDto参照)。
// ------------------------------------------------------------------

window.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("companyCodeCopyBtn").addEventListener("click", copyCompanyCode);

  try {
    const res = await fetch("/api/auth/me");
    if (!res.ok) return;
    const user = await res.json();
    if (user.companyCode) {
      document.getElementById("companyCodeDisplay").textContent = user.companyCode;
      document.getElementById("companyCodeBox").hidden = false;
    }
  } catch (e) {
    console.error(e);
  }
});

async function copyCompanyCode() {
  const value = document.getElementById("companyCodeDisplay").textContent;
  if (!value || value === "-") return;
  try {
    await navigator.clipboard.writeText(value);
  } catch (e) {
    console.error(e);
  }
}
