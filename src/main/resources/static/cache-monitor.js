let allKeys = [];
let activeGroup = "All";
let activeStrategy = "All";
let sortCol = "ttlSeconds";
let sortDir = -1;
let autoOn = true;
let cdVal = 10;
let cdTimer = null;
let expanded = new Set();
let valueCache = {};
let selectedKeys = new Set();
let visibleKeys = [];

const MAX_TTL = 7 * 86400;
const AUX_SFX = [":lock", ":fresh", ":refreshing", ":delta"];

const GROUP_ICONS = {
  All: "folder_open",
  Explore: "folder",
  Internal: "folder",
  Movies: "folder",
  "Rate Limits": "folder",
  Search: "folder",
  TV: "folder",
};

const STRAT_ICONS = {
  All: "grid_view",
  XFetch: "bolt",
  SWR: "sync",
  "Spring Cache": "inventory_2",
  Auxiliary: "layers",
  "Redis Direct": "storage",
  Bucket4J: "speed",
};

const STRAT_LABEL = {
  XFetch: "XFETCH",
  SWR: "SWR",
  "Spring Cache": "SPRING CACHE",
  "Redis Direct": "REDIS DIRECT",
  Bucket4J: "BUCKET4J",
  Auxiliary: "AUXILIARY",
};

const STATUS_ORD = {
  hot: 0,
  persistent: 1,
  warm: 2,
  stale: 3,
  auxiliary: 4,
  expired: 5,
};
const STATUS_LBL = {
  hot: "HOT",
  warm: "WARM",
  stale: "STALE",
  persistent: "PERSIST",
  auxiliary: "AUX",
  expired: "EXPIRED",
};

function statusClass(status) {
  switch (status) {
    case "hot":
      return "bg-status-hot-bg text-status-hot-text border-mono-border";
    case "stale":
    case "expired":
      return "bg-status-stale-bg text-status-stale-text border-mono-border";
    case "auxiliary":
      return "bg-status-aux-bg text-status-aux-text border-mono-border";
    default:
      return "bg-status-hot-bg text-status-hot-text border-mono-border";
  }
}

function addLog(msg, type = "info") {
  const el = document.getElementById("logsBody");
  const now = new Date();
  const ts = now.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
  const div = document.createElement("div");
  div.className = "flex gap-3 items-start group";
  let msgHtml = escHtml(msg);
  if (type === "ok") {
    msgHtml = `<span class="text-[#86efac]">${msgHtml}</span>`;
  } else if (type === "err") {
    msgHtml = `<span class="text-[#AA7777]">${msgHtml}</span>`;
  } else if (type === "action") {
    msgHtml = `<span class="text-[#fde68a]">${msgHtml}</span>`;
  }
  div.innerHTML = `<span class="text-mono-text-tertiary shrink-0 select-none">${ts}</span><div class="text-mono-text-secondary group-hover:text-mono-text-primary transition-colors">${msgHtml}</div>`;
  el.appendChild(div);
  el.scrollTop = el.scrollHeight;
}

function clearLogs() {
  document.getElementById("logsBody").innerHTML = "";
}

async function fetchStats() {
  try {
    const res = await fetch("/api/test/cache/stats");
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    hideErr();
    applyData(data);
    document.getElementById("lastUpdated").textContent =
      new Date().toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      });
    addLog(
      `Refreshed <span class="text-white font-semibold">${data.summary?.totalKeys ?? 0} keys</span>`,
      "info",
    );
  } catch (e) {
    showErr(e.message);
    addLog("Fetch failed: " + e.message, "err");
  }
}

function applyData(data) {
  allKeys = data.keys || [];
  valueCache = {};
  const s = data.summary || {};
  document.getElementById("sTotal").textContent = s.totalKeys ?? "\u2014";
  document.getElementById("sCache").textContent = s.cacheKeys ?? "\u2014";
  document.getElementById("sAux").textContent = s.auxKeys ?? "\u2014";
  document.getElementById("sHot").textContent = allKeys.filter(
    (k) => k.status === "hot",
  ).length;
  document.getElementById("sStale").textContent = allKeys.filter(
    (k) => k.status === "stale" || k.status === "expired",
  ).length;
  buildGroups(s.groups || {});
  buildStrategies();
  renderTable();
}

function buildGroups(groups) {
  const el = document.getElementById("groupList");
  const allCount = allKeys.filter((k) => k.strategy !== "Auxiliary").length;
  const names = ["All", ...Object.keys(groups).sort()];
  el.innerHTML = names
    .map((name) => {
      const cnt = name === "All" ? allCount : groups[name] || 0;
      const isActive = name === activeGroup;
      const icon = GROUP_ICONS[name] || "folder";
      const safeN = escAttr(name);
      const selectBtn = `<button onclick="event.stopPropagation();event.preventDefault();selectGroup('${safeN}')" title="Select all in ${escHtml(name)}" class="opacity-0 group-hover:opacity-100 transition-opacity text-mono-text-tertiary hover:text-white p-0.5 rounded focus:outline-none"><span class="material-symbols-outlined text-[13px] leading-none">check_box</span></button>`;
      if (isActive) {
        return `<li><a onclick="setGroup('${safeN}');return false" class="flex items-center justify-between px-3 py-1.5 rounded-md bg-[#1F1F1F] text-sm font-medium shadow-subtle border border-[#2A2A2A] group cursor-pointer" href="#">
              <div class="flex items-center gap-2.5"><span class="material-symbols-outlined text-[16px] text-white">${name === "All" ? "folder_open" : icon}</span><span class="text-white">${escHtml(name)}</span></div>
              <div class="flex items-center gap-1.5">${selectBtn}<span class="text-xs text-mono-text-secondary font-mono">${cnt}</span></div>
          </a></li>`;
      }
      return `<li><a onclick="setGroup('${safeN}');return false" class="flex items-center justify-between px-3 py-1.5 rounded-md text-mono-text-secondary hover:bg-[#161616] hover:text-mono-text-primary text-sm transition-all group cursor-pointer" href="#">
          <div class="flex items-center gap-2.5"><span class="material-symbols-outlined text-[16px] text-mono-text-tertiary group-hover:text-mono-text-secondary transition-colors">${icon}</span><span>${escHtml(name)}</span></div>
          <div class="flex items-center gap-1.5">${selectBtn}<span class="text-xs text-mono-text-tertiary group-hover:text-mono-text-secondary font-mono">${cnt}</span></div>
      </a></li>`;
    })
    .join("");
}

function setGroup(name) {
  activeGroup = name;
  const groups = {};
  allKeys.forEach((k) => {
    if (k.group) groups[k.group] = (groups[k.group] || 0) + 1;
  });
  buildGroups(groups);
  renderTable();
}

function buildStrategies() {
  const el = document.getElementById("strategyList");
  const counts = {};
  allKeys.forEach((k) => {
    counts[k.strategy] = (counts[k.strategy] || 0) + 1;
  });
  const order = [
    "All",
    "XFetch",
    "SWR",
    "Spring Cache",
    "Redis Direct",
    "Bucket4J",
    "Auxiliary",
  ];
  const names = order.filter((s) => s === "All" || counts[s]);
  el.innerHTML = names
    .map((name) => {
      const cnt = name === "All" ? allKeys.length : counts[name] || 0;
      const isActive = name === activeStrategy;
      const icon = STRAT_ICONS[name] || "grid_view";
      if (isActive) {
        return `<li><a onclick="setStrategy('${escAttr(name)}');return false" class="flex items-center justify-between px-3 py-1.5 rounded-md bg-[#1F1F1F] text-sm font-medium shadow-subtle border border-[#2A2A2A] group cursor-pointer" href="#">
              <div class="flex items-center gap-2.5"><span class="material-symbols-outlined text-[16px] text-white">${icon}</span><span class="text-white">${escHtml(name)}</span></div>
              <span class="text-xs text-mono-text-secondary font-mono">${cnt}</span>
          </a></li>`;
      }
      return `<li><a onclick="setStrategy('${escAttr(name)}');return false" class="flex items-center justify-between px-3 py-1.5 rounded-md text-mono-text-secondary hover:bg-[#161616] hover:text-mono-text-primary text-sm transition-all group cursor-pointer" href="#">
          <div class="flex items-center gap-2.5"><span class="material-symbols-outlined text-[16px] text-mono-text-tertiary group-hover:text-mono-text-secondary transition-colors">${icon}</span><span>${escHtml(name)}</span></div>
          <span class="text-xs text-mono-text-tertiary group-hover:text-mono-text-secondary font-mono">${cnt}</span>
      </a></li>`;
    })
    .join("");
}

function setStrategy(name) {
  activeStrategy = name;
  buildStrategies();
  renderTable();
}

function renderTable() {
  const q = document.getElementById("searchInput").value.toLowerCase();
  let rows = allKeys.filter((k) => {
    const gm = activeGroup === "All" || k.group === activeGroup;
    const stm = activeStrategy === "All" || k.strategy === activeStrategy;
    const sm = !q || k.key.toLowerCase().includes(q);
    return gm && stm && sm;
  });
  rows = rows.slice().sort((a, b) => {
    let av = a[sortCol],
      bv = b[sortCol];
    if (sortCol === "status") {
      av = STATUS_ORD[av] ?? 9;
      bv = STATUS_ORD[bv] ?? 9;
    }
    if (sortCol === "ttlSeconds") {
      av =
        av === null
          ? -Infinity
          : av === -1
            ? Infinity
            : av === -2
              ? -Infinity
              : av;
      bv =
        bv === null
          ? -Infinity
          : bv === -1
            ? Infinity
            : bv === -2
              ? -Infinity
              : bv;
    }
    if (av == null) av = "";
    if (bv == null) bv = "";
    if (av < bv) return -sortDir;
    if (av > bv) return sortDir;
    return 0;
  });
  visibleKeys = rows.map((k) => k.key);
  const tbody = document.getElementById("tableBody");
  if (rows.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7" class="px-6 py-16 text-center text-mono-text-tertiary text-sm">No keys match the current filter.</td></tr>`;
    document.getElementById("rowCount").textContent = "";
    updateSelectionUI();
    return;
  }
  tbody.innerHTML = rows.flatMap((k) => buildRows(k)).join("");
  const n = rows.length;
  document.getElementById("rowCount").textContent =
    `${n} ${n === 1 ? "key" : "keys"}`;
  updateSelectionUI();
}

function buildRows(k) {
  const isExp = expanded.has(k.key);
  let keyBase = k.key;
  let keySfx = "";
  for (const sfx of AUX_SFX) {
    if (k.key.endsWith(sfx)) {
      keyBase = k.key.slice(0, k.key.length - sfx.length);
      keySfx = sfx;
      break;
    }
  }
  const safeKey = escAttr(k.key);
  const stratLabel = STRAT_LABEL[k.strategy] || escHtml(k.strategy);
  const statusLabel = STATUS_LBL[k.status] || k.status;
  const sCls = statusClass(k.status);
  const isSel = selectedKeys.has(k.key);
  let rowBg = isExp ? " bg-[#1A1A1A]" : "";
  if (isSel) rowBg = " bg-[#1C1C1C]";

  const dataRow = `<tr class="group hover:bg-[#1A1A1A] transition-colors cursor-pointer${rowBg}" onclick="toggleExpand('${safeKey}')">
      <td class="pl-6 pr-2 py-3.5 w-10 text-center" onclick="event.stopPropagation()"><input type="checkbox" class="cache-check" ${isSel ? "checked" : ""} onchange="toggleSelectKey('${safeKey}')"></td>
      <td class="px-3 py-3.5 font-mono text-mono-text-primary text-[13px] truncate">${escHtml(keyBase)}${keySfx ? ` <span class="text-mono-text-tertiary">${escHtml(keySfx)}</span>` : ""}</td>
      <td class="px-6 py-3.5 text-mono-text-secondary text-[13px] w-32 truncate">${escHtml(k.group)}</td>
      <td class="px-6 py-3.5 text-[13px] text-mono-text-primary w-32 truncate">${stratLabel}</td>
      <td class="px-6 py-3.5 text-[13px] text-mono-text-primary font-mono w-32">${buildTtl(k)}</td>
      <td class="px-6 py-3.5 text-center w-24"><span class="px-2.5 py-1 rounded ${sCls} text-[10px] font-bold border">${statusLabel}</span></td>
      <td class="px-6 py-3.5 w-10 text-center"><button class="text-mono-text-tertiary hover:text-[#AA7777] opacity-0 group-hover:opacity-100 transition-all text-[11px]" onclick="event.stopPropagation();deleteKey('${safeKey}')" title="Delete">&#x2715;</button></td>
  </tr>`;

  if (!isExp) return [dataRow];

  const expandRow = `<tr class="bg-[#0D0D0D] border-b border-mono-border"><td colspan="7" class="px-10 py-5">
      <div class="ep-section">
          <div class="text-[9px] font-bold tracking-widest uppercase text-mono-text-tertiary mb-2">Metadata</div>
          <div class="font-mono text-[11px] leading-[1.9]"><div><span class="t-brace">{</span></div>${buildMetaTree(k)}<div><span class="t-brace">}</span></div></div>
      </div>
      <div class="ep-section">
          <div class="text-[9px] font-bold tracking-widest uppercase text-mono-text-tertiary mb-2 flex items-center gap-2">Cached Data ${buildSizeLabel(k.key)}</div>
          ${buildDataPreview(k.key)}
      </div>
  </td></tr>`;
  return [dataRow, expandRow];
}

function buildMetaTree(k) {
  return [
    ["key", k.key],
    ["group", k.group],
    ["strategy", k.strategy],
    ["ttlSeconds", k.ttlSeconds],
    ["status", k.status],
  ]
    .map(([key, val]) => {
      let v;
      if (val == null) v = `<span class="t-null">null</span>`;
      else if (typeof val === "number") v = `<span class="t-num">${val}</span>`;
      else v = `<span class="t-str">"${escHtml(String(val))}"</span>`;
      return `<div style="padding-left:16px"><span class="t-key">${key}</span><span class="t-colon">:</span>${v}</div>`;
    })
    .join("");
}

function buildSizeLabel(key) {
  const vc = valueCache[key];
  if (!vc || !vc.found) return "";
  const b = vc.sizeBytes || 0;
  return b >= 1024
    ? `<span class="font-mono text-[9px] text-mono-text-tertiary">${(b / 1024).toFixed(1)} KB</span>`
    : `<span class="font-mono text-[9px] text-mono-text-tertiary">${b} B</span>`;
}

function buildDataPreview(key) {
  const vc = valueCache[key];
  if (!vc)
    return `<div class="text-[11px] text-mono-text-tertiary italic">Loading...</div>`;
  if (!vc.found && vc.error)
    return `<div class="text-[11px] text-[#AA7777]">Error: ${escHtml(vc.error)}</div>`;
  if (!vc.found)
    return `<div class="text-[11px] text-mono-text-tertiary">Key not found (may have expired)</div>`;
  try {
    const pretty = JSON.stringify(JSON.parse(vc.value), null, 2);
    return `<pre class="ep-json">${syntaxHighlight(pretty)}</pre>`;
  } catch {
    return `<pre class="ep-json">${escHtml(vc.value)}</pre>`;
  }
}

function syntaxHighlight(json) {
  return escHtml(json).replace(
    /("(\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    (match) => {
      let cls = "t-num";
      if (/^"/.test(match)) cls = /:$/.test(match) ? "t-key" : "t-str";
      else if (/true|false/.test(match)) cls = "t-num";
      else if (/null/.test(match)) cls = "t-null";
      return `<span class="${cls}">${match}</span>`;
    },
  );
}

async function toggleExpand(key) {
  if (expanded.has(key)) {
    expanded.delete(key);
    renderTable();
    return;
  }
  expanded.add(key);
  renderTable();
  if (!valueCache[key]) {
    try {
      const res = await fetch(
        `/api/test/cache/value?key=${encodeURIComponent(key)}`,
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      valueCache[key] = await res.json();
    } catch (e) {
      valueCache[key] = { found: false, error: e.message };
    }
    renderTable();
  }
}

function buildTtl(k) {
  const t = k.ttlSeconds;
  if (t === -1) return `<span class="text-[#60a5fa]">&#x221E; persist</span>`;
  if (t === -2 || t === null)
    return `<span class="text-mono-text-tertiary">&mdash;</span>`;
  return fmtTtl(t);
}

async function clearAll() {
  if (!confirm("Clear ALL cache keys?")) return;
  addLog("Clearing all keys\u2026", "action");
  try {
    const res = await fetch("/api/test/cache/all", { method: "DELETE" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    addLog(`Cleared ${data.cleared} key${data.cleared !== 1 ? "s" : ""}`, "ok");
    expanded.clear();
    selectedKeys.clear();
    await fetchStats();
  } catch (e) {
    addLog("Clear all failed: " + e.message, "err");
  }
}

async function clearPattern() {
  const pat = document.getElementById("patternInput").value.trim();
  if (!pat) {
    addLog("Pattern is empty", "err");
    return;
  }
  if (!confirm(`Clear keys matching "${pat}"?`)) return;
  addLog(`Clearing pattern: ${pat}`, "action");
  try {
    const res = await fetch(
      `/api/test/cache/pattern?pattern=${encodeURIComponent(pat)}`,
      { method: "DELETE" },
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    addLog(
      `Cleared ${data.cleared} key${data.cleared !== 1 ? "s" : ""} matching "${pat}"`,
      "ok",
    );
    document.getElementById("patternInput").value = "";
    expanded.clear();
    selectedKeys.clear();
    await fetchStats();
  } catch (e) {
    addLog("Clear pattern failed: " + e.message, "err");
  }
}

async function deleteKey(key) {
  addLog(`Deleting: ${key}`, "action");
  try {
    const res = await fetch(
      `/api/test/cache/key?key=${encodeURIComponent(key)}`,
      { method: "DELETE" },
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    addLog(
      data.deleted ? `Deleted: ${key}` : `Not found: ${key}`,
      data.deleted ? "ok" : "err",
    );
    expanded.delete(key);
    selectedKeys.delete(key);
    await fetchStats();
  } catch (e) {
    addLog("Delete failed: " + e.message, "err");
  }
}

function sortBy(col) {
  if (sortCol === col) sortDir *= -1;
  else {
    sortCol = col;
    sortDir = col === "ttlSeconds" ? -1 : 1;
  }
  document.querySelectorAll("[data-col]").forEach((el) => {
    el.textContent = "\u21D5";
    el.className = "text-[8px] opacity-40";
    if (el.dataset.col === col) {
      el.textContent = sortDir === 1 ? "\u2191" : "\u2193";
      el.className = "text-[8px]";
    }
  });
  renderTable();
}

function toggleAuto() {
  autoOn = !autoOn;
  const btn = document.getElementById("autoBtn");
  if (autoOn) {
    btn.className =
      "px-3 py-1 bg-[#2A2A2A] rounded-md shadow-sm text-white font-medium text-xs border border-[#3A3A3A]";
    startCd();
  } else {
    btn.className =
      "px-3 py-1 text-mono-text-secondary hover:text-white font-medium text-xs transition-colors";
    stopCd();
  }
  addLog(autoOn ? "Auto-refresh on" : "Auto-refresh off", "info");
}

function startCd() {
  stopCd();
  cdVal = 10;
  updCd();
  cdTimer = setInterval(() => {
    cdVal--;
    updCd();
    if (cdVal <= 0) {
      fetchStats();
      cdVal = 10;
    }
  }, 1000);
}

function stopCd() {
  clearInterval(cdTimer);
  cdTimer = null;
  document.getElementById("countdown").textContent = "";
}

function updCd() {
  document.getElementById("countdown").textContent = `${cdVal}s`;
}

function showErr(msg) {
  const e = document.getElementById("errBar");
  e.textContent = msg;
  e.classList.add("on");
}

function hideErr() {
  document.getElementById("errBar").classList.remove("on");
}

function fmtTtl(s) {
  if (s >= 86400)
    return `${Math.floor(s / 86400)}d ${Math.floor((s % 86400) / 3600)}h`;
  if (s >= 3600)
    return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
  if (s >= 60) return `${Math.floor(s / 60)}m ${s % 60}s`;
  return `${s}s`;
}

function escHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function escAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/'/g, "&#39;")
    .replace(/"/g, "&quot;");
}

function toggleSelectKey(key) {
  if (selectedKeys.has(key)) selectedKeys.delete(key);
  else selectedKeys.add(key);
  updateSelectionUI();
}

function toggleSelectAll() {
  const allSelected =
    visibleKeys.length > 0 && visibleKeys.every((k) => selectedKeys.has(k));
  if (allSelected) {
    visibleKeys.forEach((k) => selectedKeys.delete(k));
  } else {
    visibleKeys.forEach((k) => selectedKeys.add(k));
  }
  renderTable();
}

function selectGroup(groupName) {
  const keys = allKeys
    .filter((k) =>
      groupName === "All" ? k.strategy !== "Auxiliary" : k.group === groupName,
    )
    .map((k) => k.key);
  const allAlreadySelected =
    keys.length > 0 && keys.every((k) => selectedKeys.has(k));
  if (allAlreadySelected) {
    keys.forEach((k) => selectedKeys.delete(k));
    addLog(
      `Deselected ${keys.length} key${keys.length !== 1 ? "s" : ""} in "${groupName}"`,
      "info",
    );
  } else {
    keys.forEach((k) => selectedKeys.add(k));
    addLog(
      `Selected ${keys.length} key${keys.length !== 1 ? "s" : ""} in "${groupName}"`,
      "info",
    );
  }
  renderTable();
}

function clearSelection() {
  selectedKeys.clear();
  renderTable();
}

async function deleteSelected() {
  const keys = [...selectedKeys];
  if (keys.length === 0) return;
  if (
    !confirm(
      `Delete ${keys.length} selected key${keys.length !== 1 ? "s" : ""}?`,
    )
  )
    return;
  addLog(
    `Deleting ${keys.length} selected key${keys.length !== 1 ? "s" : ""}\u2026`,
    "action",
  );
  try {
    const res = await fetch("/api/test/cache/batch", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(keys),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const cleared = data.cleared ?? 0;
    keys.forEach((k) => {
      expanded.delete(k);
      selectedKeys.delete(k);
    });
    addLog(
      `Deleted ${cleared} of ${keys.length} key${keys.length !== 1 ? "s" : ""}`,
      cleared === keys.length ? "ok" : "err",
    );
  } catch (e) {
    addLog("Batch delete failed: " + e.message, "err");
  }
  await fetchStats();
}

function updateSelectionUI() {
  const n = selectedKeys.size;
  const bar = document.getElementById("selectionBar");
  const countEl = document.getElementById("selectionCount");
  const chk = document.getElementById("selectAllChk");
  if (n > 0) {
    bar.classList.remove("hidden");
    countEl.textContent = `${n} selected`;
  } else {
    bar.classList.add("hidden");
  }
  if (chk) {
    const visCount = visibleKeys.length;
    const selVisible = visibleKeys.filter((k) => selectedKeys.has(k)).length;
    chk.indeterminate = selVisible > 0 && selVisible < visCount;
    chk.checked = visCount > 0 && selVisible === visCount;
  }
}

// Init
addLog("Cache Monitor initialized", "info");
fetchStats();
startCd();
