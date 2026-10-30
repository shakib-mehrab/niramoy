/* NIRAMOY site: theme + language toggles, reveal-on-scroll, live version, feature story-scroll, screenshot railway.
   Story-scroll and railway follow the Ad Deen site. No libraries. */
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const root = document.documentElement;
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (_) { /* private mode */ } }
  };
  const bnDigits = (s) => String(s).replace(/\d/g, (c) => "০১২৩৪৫৬৭৮৯"[c]);
  const isBn = () => root.dataset.lang === "bn";

  // ---------- Theme (system default, remembered toggle) ----------
  const dark = window.matchMedia("(prefers-color-scheme: dark)");
  dark.addEventListener?.("change", (e) => { if (!store.get("theme")) root.dataset.theme = e.matches ? "dark" : "light"; });
  $("#themeToggle")?.addEventListener("click", () => {
    const next = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = next;
    store.set("theme", next);
  });

  // ---------- Language (English default, Bangla anytime) ----------
  $("#langToggle")?.addEventListener("click", () => {
    const next = isBn() ? "en" : "bn";
    root.dataset.lang = next;
    root.lang = next;
    store.set("lang", next);
    paintVersion();
    active = -1; request();
  });

  // ---------- Nav border on scroll ----------
  const nav = $(".nav");
  const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 8);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // ---------- Reveal on scroll ----------
  const reveals = $$(".reveal");
  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add("is-in"); io.unobserve(en.target); } });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add("is-in"));
  }

  // ---------- Live version from the release manifest ----------
  let version = null;
  function paintVersion() {
    const meta = $("#dlMeta");
    if (!meta || !version) return;
    meta.innerHTML = isBn()
      ? `<span>সংস্করণ ${bnDigits(version)} · অ্যান্ড্রয়েড ৭.০+ · আজীবন ফ্রি</span>`
      : `<span>Version ${version} · Android 7.0+ · Free forever</span>`;
  }
  // Release assets don't send CORS headers; the REST API does (60 requests/hour per visitor is plenty).
  fetch("https://api.github.com/repos/shakib-mehrab/niramoy/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : null))
    .then((m) => { if (m && m.tag_name) { version = m.tag_name.replace(/^v/, ""); paintVersion(); } })
    .catch(() => { /* offline / rate-limited: keep the static text */ });

  // ---------- Feature story-scroll + screenshot railway (one rAF loop) ----------
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const story = $("#storyScroll");
  const steps = story ? $$(".chat", story) : [];
  const screens = story ? $$(".story-scroll__screens img", story) : [];
  const counter = $("#storyIndex");
  let active = -1;

  const rail = $("#rail");
  const train = $("#railTrain");
  const track = $("#railTrack");
  const bar = $("#railProgress");
  const cars = train ? Array.from(train.children) : [];
  const railStatic = () => reduce || window.innerHeight < 520;

  function updateStory() {
    if (!steps.length) return;
    const mid = window.innerHeight * 0.55;
    let idx = 0;
    steps.forEach((s, k) => { if (s.getBoundingClientRect().top < mid) idx = k; });
    const first = steps[0].getBoundingClientRect();
    const list = steps[0].parentElement.getBoundingClientRect();
    const p = Math.min(1, Math.max(0, (mid - first.top) / Math.max(1, list.height - first.height / 2)));
    steps[0].parentElement.style.setProperty("--p", p.toFixed(3));
    if (idx === active) return;
    active = idx;
    steps.forEach((s, k) => {
      s.classList.toggle("is-active", k === idx);
      s.classList.toggle("is-past", k < idx);
    });
    const shot = +steps[idx].dataset.shot;
    screens.forEach((img, k) => img.classList.toggle("is-on", k === shot));
    if (counter) {
      const n = String(idx + 1).padStart(2, "0");
      counter.textContent = isBn() ? bnDigits(n) : n;
    }
  }

  function updateRail() {
    if (!rail) return;
    if (railStatic()) { rail.classList.add("is-static"); return; }
    rail.classList.remove("is-static");
    const r = rail.getBoundingClientRect();
    const vh = window.innerHeight, vw = window.innerWidth;
    const p = Math.min(1, Math.max(0, -r.top / Math.max(1, r.height - vh)));
    // At p = 1 the last screen rests in the middle of the viewport.
    const last = cars[cars.length - 1];
    const endX = vw / 2 - (last.offsetLeft + last.offsetWidth / 2);
    const x = p * endX;
    train.style.transform = `translate3d(${x}px,0,0)`;
    track.style.backgroundPosition = `0 3px, 0 12px, ${x}px 0`;
    bar.style.transform = `scaleX(${p})`;
    const half = vw / 2;
    cars.forEach((c) => {
      const center = x + c.offsetLeft + c.offsetWidth / 2;
      const d = (center - half) / half;
      let op, ty, rot, sc;
      if (d > 0) {
        const a = Math.min(1, d);
        op = 1 - a * 0.85; ty = a * 46; rot = a * 5; sc = 1 - a * 0.08;
      } else {
        const a = Math.min(1, -d);
        op = 1 - a * 0.55; ty = 0; rot = 0; sc = 1 - a * 0.1;
      }
      c.style.opacity = op.toFixed(3);
      c.style.transform = `translate3d(0,${ty.toFixed(1)}px,0) rotate(${rot.toFixed(2)}deg) scale(${sc.toFixed(3)})`;
    });
  }

  let ticking = false;
  const frame = () => { ticking = false; updateStory(); updateRail(); };
  function request() { if (!ticking) { ticking = true; requestAnimationFrame(frame); } }
  window.addEventListener("scroll", request, { passive: true });
  window.addEventListener("resize", request);
  window.addEventListener("load", request);
  request();
})();
