// ------------------------------------------------------------------
// トップページ演出スクリプト
// ------------------------------------------------------------------
const WEEKDAY_LABELS = ["日", "月", "火", "水", "木", "金", "土"];

const CHEER_MESSAGES = [
  "水分補給、忘れずに！ 💧",
  "たまには背伸びしてリフレッシュ。 🙆",
  "今日の一歩が明日の自分をつくる。 👣",
  "睡眠時間、しっかり確保しよう。 😴",
  "小さな達成でも自分をほめてあげて。 🌟",
  "深呼吸ひとつで、集中力アップ。 🌬️",
  "予定を書き出すと、頭の中がすっきり。 📝",
  "無理せず、自分のペースでいこう。 🐢",
];

// 東京(気象APIの座標フォールバック用。位置情報が使えない/拒否された場合はここを使う)
const WEATHER_FALLBACK = { lat: 35.6762, lon: 139.6503, label: "東京" };

// WMO Weather interpretation codes(Open-Meteoが返すweather_code)を簡易な日本語+絵文字に変換
const WMO_WEATHER = {
  0: { label: "快晴", icon: "☀️" },
  1: { label: "晴れ", icon: "🌤️" },
  2: { label: "薄曇り", icon: "⛅" },
  3: { label: "曇り", icon: "☁️" },
  45: { label: "霧", icon: "🌫️" }, 48: { label: "霧", icon: "🌫️" },
  51: { label: "小雨", icon: "🌦️" }, 53: { label: "小雨", icon: "🌦️" }, 55: { label: "雨", icon: "🌧️" },
  56: { label: "着氷性の雨", icon: "🌧️" }, 57: { label: "着氷性の雨", icon: "🌧️" },
  61: { label: "雨", icon: "🌧️" }, 63: { label: "雨", icon: "🌧️" }, 65: { label: "大雨", icon: "🌧️" },
  66: { label: "着氷性の雨", icon: "🌧️" }, 67: { label: "着氷性の雨", icon: "🌧️" },
  71: { label: "雪", icon: "❄️" }, 73: { label: "雪", icon: "❄️" }, 75: { label: "大雪", icon: "❄️" },
  77: { label: "雪", icon: "❄️" },
  80: { label: "にわか雨", icon: "🌦️" }, 81: { label: "にわか雨", icon: "🌦️" }, 82: { label: "激しいにわか雨", icon: "⛈️" },
  85: { label: "にわか雪", icon: "🌨️" }, 86: { label: "にわか雪", icon: "🌨️" },
  95: { label: "雷雨", icon: "⛈️" }, 96: { label: "雷雨", icon: "⛈️" }, 99: { label: "雷雨", icon: "⛈️" },
};

window.addEventListener("DOMContentLoaded", () => {
  setGreeting();
  setTodayLabel();
  setupCardTilt();
  setupCheerButton();
  startClock();
  loadWeather();
  loadTaskAlertSummary();
  loadHealthAlertSummary();
});

// --- ライブ時計(1秒ごとに更新) ---
function startClock() {
  const el = document.getElementById("clockValue");
  if (!el) return;
  const tick = () => {
    const now = new Date();
    const hh = String(now.getHours()).padStart(2, "0");
    const mm = String(now.getMinutes()).padStart(2, "0");
    const ss = String(now.getSeconds()).padStart(2, "0");
    el.textContent = `${hh}:${mm}:${ss}`;
  };
  tick();
  setInterval(tick, 1000);
}

// --- 天気(Open-Meteo。APIキー不要でブラウザから直接呼び出す) ---
// 現在地はブラウザのgeolocationで取得し、権限が得られない/失敗した場合は東京にフォールバックする。
// 取得に失敗してもページ全体は止めず、チップにその旨だけ表示する。
function loadWeather() {
  const valueEl = document.getElementById("weatherValue");
  if (!valueEl) return;

  if (!("geolocation" in navigator)) {
    fetchAndRenderWeather(WEATHER_FALLBACK.lat, WEATHER_FALLBACK.lon, WEATHER_FALLBACK.label);
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => fetchAndRenderWeather(pos.coords.latitude, pos.coords.longitude, null),
    () => fetchAndRenderWeather(WEATHER_FALLBACK.lat, WEATHER_FALLBACK.lon, WEATHER_FALLBACK.label),
    { timeout: 6000 }
  );
}

async function fetchAndRenderWeather(lat, lon, placeLabel) {
  const valueEl = document.getElementById("weatherValue");
  const iconEl = document.getElementById("weatherIcon");
  try {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code&timezone=auto`;
    // 天気APIは認証不要の外部APIのため、素のfetchを使う(auth-common.jsのCSRF付与はPOST等の
    // 変更系メソッドにしか効かず、このGETリクエストには影響しない)。
    const res = await fetch(url);
    if (!res.ok) throw new Error(`weather api status ${res.status}`);
    const data = await res.json();
    const temp = data?.current?.temperature_2m;
    const code = data?.current?.weather_code;
    const info = WMO_WEATHER[code] || { label: "天気", icon: "🌡️" };
    if (iconEl) iconEl.textContent = info.icon;
    const place = placeLabel ? `${placeLabel} ` : "";
    valueEl.textContent = typeof temp === "number"
      ? `${place}${info.label} ${Math.round(temp)}°C`
      : `${place}${info.label}`;
  } catch (e) {
    console.error(e);
    valueEl.textContent = "天気を取得できませんでした";
  }
}

// --- タスク締め切りアラート(件数のみの軽量表示。一覧取得はtask.html側で行う) ---
async function loadTaskAlertSummary() {
  const banner = document.getElementById("taskAlertBanner");
  if (!banner) return;
  try {
    const res = await fetch("/api/tasks/alerts");
    if (!res.ok) return;
    const data = await res.json();
    const dueToday = data.dueTodayCount || 0;
    const overdue = data.overdueCount || 0;
    if (dueToday === 0 && overdue === 0) return;

    const parts = [];
    if (dueToday > 0) parts.push(`今日が締め切りのタスクが${dueToday}件`);
    if (overdue > 0) parts.push(`期限を過ぎているタスクが${overdue}件`);
    document.getElementById("taskAlertText").textContent = parts.join("、") + "あります";
    banner.hidden = false;
  } catch (e) {
    console.error(e);
  }
}

// --- 健康アラート(本日分のみ。件数だけの軽量表示。詳細はhealth-top.html側で確認する) ---
// メール・Slack等の外部送信はせず、本人がこのページを開いた時だけアプリ内で表示する
// (HealthAlertService#evaluateAndGetAlertsは呼ぶたびに本日分を再評価するだけで、
// 同日・同種別の重複登録はしない=何度開いても副作用はない)。
async function loadHealthAlertSummary() {
  const banner = document.getElementById("healthAlertBanner");
  if (!banner) return;
  try {
    const res = await fetch("/api/health/alerts?days=1");
    if (!res.ok) return;
    const alerts = await res.json();
    const todayStr = new Date().toISOString().slice(0, 10);
    const todayCount = (alerts || []).filter((a) => a.triggeredDate === todayStr).length;
    if (todayCount === 0) return;

    document.getElementById("healthAlertText").textContent =
      `健康チェックで注意が必要な項目が${todayCount}件あります`;
    banner.hidden = false;
  } catch (e) {
    console.error(e);
  }
}

// --- 時間帯に応じた挨拶 ---
function setGreeting() {
  const hour = new Date().getHours();
  let text;
  if (hour < 5) text = "夜遅くまでお疲れさまです 🌙";
  else if (hour < 11) text = "おはようございます！ 今日も一日がんばりましょう ☀️";
  else if (hour < 17) text = "こんにちは！ ひと休みも忘れずに ☕";
  else if (hour < 22) text = "お疲れさまです！ 今日の記録をつけましょう 🌆";
  else text = "夜遅くまでお疲れさまです 🌙";

  document.getElementById("greeting").textContent = text;
}

// --- 今日の日付表示 ---
function setTodayLabel() {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth() + 1;
  const d = now.getDate();
  const w = WEEKDAY_LABELS[now.getDay()];
  document.getElementById("todayLabel").textContent = `${y}年${m}月${d}日(${w})`;
}

// --- カードの3Dチルト演出 ---
function setupCardTilt() {
  const cards = document.querySelectorAll("[data-tilt]");
  cards.forEach((card) => {
    card.addEventListener("mousemove", (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const rotateX = ((y / rect.height) - 0.5) * -8;
      const rotateY = ((x / rect.width) - 0.5) * 8;
      card.style.transform = `perspective(700px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-4px)`;
    });
    card.addEventListener("mouseleave", () => {
      card.style.transform = "";
    });
  });
}

// --- 応援ボタン: メッセージ表示 + 紙吹雪 ---
function setupCheerButton() {
  const btn = document.getElementById("cheerBtn");
  const msg = document.getElementById("cheerMsg");
  btn.addEventListener("click", () => {
    const text = CHEER_MESSAGES[Math.floor(Math.random() * CHEER_MESSAGES.length)];
    msg.textContent = text;
    burstConfetti(btn.getBoundingClientRect());
  });
}

// --- 軽量な紙吹雪アニメーション(外部ライブラリなし) ---
function burstConfetti(originRect) {
  const canvas = document.getElementById("confetti");
  const ctx = canvas.getContext("2d");
  const dpr = window.devicePixelRatio || 1;
  canvas.width = window.innerWidth * dpr;
  canvas.height = window.innerHeight * dpr;
  canvas.style.width = window.innerWidth + "px";
  canvas.style.height = window.innerHeight + "px";
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

  const colors = ["#C33B2E", "#1F3A5F", "#3E6B52", "#D9A441"];
  const originX = originRect.left + originRect.width / 2;
  const originY = originRect.top;

  const particles = Array.from({ length: 60 }, () => ({
    x: originX,
    y: originY,
    vx: (Math.random() - 0.5) * 8,
    vy: -Math.random() * 8 - 3,
    size: Math.random() * 6 + 4,
    color: colors[Math.floor(Math.random() * colors.length)],
    rotation: Math.random() * Math.PI,
    spin: (Math.random() - 0.5) * 0.4,
    gravity: 0.28,
    life: 0,
    maxLife: 70 + Math.random() * 20,
  }));

  function tick() {
    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
    let alive = false;

    particles.forEach((p) => {
      if (p.life >= p.maxLife) return;
      alive = true;
      p.life++;
      p.vy += p.gravity;
      p.x += p.vx;
      p.y += p.vy;
      p.rotation += p.spin;

      ctx.save();
      ctx.globalAlpha = Math.max(0, 1 - p.life / p.maxLife);
      ctx.translate(p.x, p.y);
      ctx.rotate(p.rotation);
      ctx.fillStyle = p.color;
      ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
      ctx.restore();
    });

    if (alive) {
      requestAnimationFrame(tick);
    } else {
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
    }
  }

  requestAnimationFrame(tick);
}
