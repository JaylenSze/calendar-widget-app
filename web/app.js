/* Tilecal — calendar widgets for your home screen. No build step, no dependencies. */
(() => {
  'use strict';

  // ---------- constants ----------
  const STORE_KEY = 'tilecal.v1';
  const FONTS = {
    inter: 'Inter, system-ui, sans-serif',
    serif: '"DM Serif Display", Georgia, serif',
    rounded: 'Nunito, "Segoe UI", sans-serif',
    mono: '"Space Mono", ui-monospace, monospace',
  };
  const THEMES = {
    paper:    { name: 'Paper',    bg: '#ffffff', fg: '#1b1d22', accent: '#e5484d' },
    midnight: { name: 'Midnight', bg: '#15171e', fg: '#f1f2f6', accent: '#ff6b6b' },
    sage:     { name: 'Sage',     bg: '#dfe8dc', fg: '#24342a', accent: '#4f7a5a' },
    ocean:    { name: 'Ocean',    bg: '#0f2a44', fg: '#e7f0fa', accent: '#4cc3ff' },
    butter:   { name: 'Butter',   bg: '#fff4c7', fg: '#3a2f12', accent: '#e08a00' },
    lilac:    { name: 'Lilac',    bg: '#ece6ff', fg: '#2a2147', accent: '#7c5cff' },
    sunset:   { name: 'Sunset',   bg: 'linear-gradient(160deg,#ff9a76,#ff5f8f)', fg: '#ffffff', accent: '#ffffff' },
    mono:     { name: 'Mono',     bg: '#000000', fg: '#ffffff', accent: '#ffffff' },
  };
  const TYPES = {
    month:     { name: 'Month',         desc: 'Classic month grid with event dots', theme: 'paper' },
    today:     { name: 'Today',         desc: 'Big date with what’s next', theme: 'midnight' },
    week:      { name: 'Week ahead',    desc: 'Seven days at a glance', theme: 'sage' },
    agenda:    { name: 'Agenda',        desc: 'Your upcoming events in a list', theme: 'lilac' },
    countdown: { name: 'Countdown',     desc: 'Days until something you care about', theme: 'sunset' },
    year:      { name: 'Year progress', desc: 'Every day of the year as a dot', theme: 'ocean' },
  };
  const EVENT_COLORS = ['#e5484d', '#f76b15', '#ffc53d', '#30a46c', '#0090ff', '#8e4ec6', '#e93d82', '#8b8d98'];
  const WALLPAPERS = [
    'linear-gradient(135deg,#f6d6c8,#c9d7f5)',
    'linear-gradient(160deg,#a8e6cf,#3d8b7d)',
    'linear-gradient(160deg,#1f2340,#5b3a7a)',
    'linear-gradient(135deg,#ffd194,#d1913c)',
    'linear-gradient(135deg,#e0e0e0,#9ea4ad)',
    'linear-gradient(160deg,#0f2027,#2c5364)',
    'radial-gradient(circle at 30% 20%,#ffb3c7,#7f7fd5)',
  ];

  // ---------- date helpers ----------
  const pad = n => String(n).padStart(2, '0');
  const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const parseYmd = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  const startOfDay = d => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const dayDiff = (a, b) => Math.round((startOfDay(b) - startOfDay(a)) / 86400000);
  const monthName = (d, style = 'long') => d.toLocaleDateString(undefined, { month: style });
  const weekdayName = (d, style = 'long') => d.toLocaleDateString(undefined, { weekday: style });
  const isLeap = y => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
  function isoWeek(d) {
    const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const day = t.getUTCDay() || 7;
    t.setUTCDate(t.getUTCDate() + 4 - day);
    const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
    return Math.ceil(((t - y0) / 86400000 + 1) / 7);
  }
  function startOfWeek(d, weekStart) {
    const diff = (d.getDay() - weekStart + 7) % 7;
    return addDays(d, -diff);
  }
  function weekdayLetters(weekStart) {
    const base = new Date(2024, 0, 7 + weekStart); // Jan 7 2024 is a Sunday
    return Array.from({ length: 7 }, (_, i) => weekdayName(addDays(base, i), 'narrow'));
  }
  function fmtTime(t) {
    if (!t) return 'All day';
    if (state.settings.h24) return t;
    const [h, m] = t.split(':').map(Number);
    return `${((h + 11) % 12) + 1}:${pad(m)}${h < 12 ? 'am' : 'pm'}`;
  }
  function relDay(dateStr) {
    const n = dayDiff(new Date(), parseYmd(dateStr));
    if (n === 0) return 'Today';
    if (n === 1) return 'Tomorrow';
    const d = parseYmd(dateStr);
    if (n > 1 && n < 7) return weekdayName(d);
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  // ---------- misc helpers ----------
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const uid = () => Math.random().toString(36).slice(2, 10);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function firstHex(css) { const m = String(css).match(/#[0-9a-f]{6}/i); return m ? m[0] : '#ffffff'; }
  function luminance(hex) {
    const n = parseInt(hex.slice(1), 16);
    const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; });
    return .2126 * ch[0] + .7152 * ch[1] + .0722 * ch[2];
  }
  const contrastText = hex => (luminance(hex) > .45 ? '#111111' : '#ffffff');
  let toastTimer;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('show'), 2200);
  }

  // ---------- state ----------
  function makeWidget(type, size, themeKey) {
    const th = THEMES[themeKey || TYPES[type].theme];
    const w = { id: uid(), type, size, bg: th.bg, fg: th.fg, accent: th.accent, font: 'inter', radius: 22, opts: {} };
    if (type === 'countdown') {
      const y = new Date().getFullYear();
      let target = new Date(y, 11, 25);
      if (dayDiff(new Date(), target) < 0) target = new Date(y + 1, 11, 25);
      w.opts = { title: 'Holidays', date: ymd(target) };
    }
    return w;
  }
  function defaultState() {
    const t = new Date();
    const ev = (off, title, time, color) => ({ id: uid(), title, date: ymd(addDays(t, off)), time, color });
    return {
      settings: { weekStart: 0, weekNums: false, h24: false, wallpaper: WALLPAPERS[0] },
      widgets: [
        makeWidget('today', 's'),
        makeWidget('countdown', 's'),
        makeWidget('month', 'm'),
        makeWidget('week', 'm'),
        makeWidget('agenda', 'l'),
        makeWidget('year', 's'),
        makeWidget('month', 's', 'butter'),
      ],
      events: [
        ev(0, 'Coffee with Priya', '09:30', EVENT_COLORS[1]),
        ev(0, 'Gym', '18:00', EVENT_COLORS[3]),
        ev(1, 'Design review', '14:00', EVENT_COLORS[4]),
        ev(3, 'Dentist', '11:15', EVENT_COLORS[0]),
        ev(5, 'Movie night', '20:00', EVENT_COLORS[5]),
        ev(9, 'Mom’s birthday', '', EVENT_COLORS[6]),
      ],
    };
  }
  function load() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (raw) {
        const s = JSON.parse(raw);
        if (s && Array.isArray(s.widgets) && Array.isArray(s.events)) {
          s.settings = Object.assign(defaultState().settings, s.settings);
          return s;
        }
      }
    } catch (e) { /* storage unavailable or corrupt */ }
    return defaultState();
  }
  let state = load();
  function save() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state)); } catch (e) { /* ignore */ }
  }
  function sortedEvents() {
    return [...state.events].sort((a, b) => (a.date + (a.time || '00:00')).localeCompare(b.date + (b.time || '00:00')));
  }
  function upcoming(n, from = new Date()) {
    const today = ymd(from);
    return sortedEvents().filter(e => e.date >= today).slice(0, n);
  }
  function eventsOn(dateStr) { return sortedEvents().filter(e => e.date === dateStr); }
  function eventDates() { return new Set(state.events.map(e => e.date)); }

  // ---------- widget renderers ----------
  function monthGridHTML(ref, { headers = true } = {}) {
    const ws = Number(state.settings.weekStart);
    const showWk = state.settings.weekNums;
    const first = new Date(ref.getFullYear(), ref.getMonth(), 1);
    const start = startOfWeek(first, ws);
    const daysInMonth = new Date(ref.getFullYear(), ref.getMonth() + 1, 0).getDate();
    const weeks = Math.ceil((dayDiff(start, first) + daysInMonth) / 7);
    const todayStr = ymd(new Date());
    const marks = eventDates();
    let html = `<div class="mg" style="--cols:${showWk ? 8 : 7}">`;
    if (headers) {
      if (showWk) html += `<div class="h"></div>`;
      html += weekdayLetters(ws).map(l => `<div class="h">${l}</div>`).join('');
    }
    for (let w = 0; w < weeks; w++) {
      if (showWk) html += `<div class="wk">${isoWeek(addDays(start, w * 7 + 3))}</div>`;
      for (let i = 0; i < 7; i++) {
        const d = addDays(start, w * 7 + i);
        const s = ymd(d);
        const cls = ['d'];
        if (d.getMonth() !== ref.getMonth()) cls.push('out');
        if (s === todayStr) cls.push('today');
        if (marks.has(s)) cls.push('has');
        html += `<div class="${cls.join(' ')}"><span>${d.getDate()}</span></div>`;
      }
    }
    return html + '</div>';
  }

  function eventsHTML(list, { showDay = true, empty = 'Nothing scheduled' } = {}) {
    if (!list.length) return `<div class="no-ev">${empty}</div>`;
    return `<div class="evs">${list.map(e => `
      <div class="ev" style="--c:${esc(e.color || '')}"><i></i><div>
        <b>${esc(e.title)}</b>
        <small>${showDay ? esc(relDay(e.date)) + ' · ' : ''}${esc(fmtTime(e.time))}</small>
      </div></div>`).join('')}</div>`;
  }

  const R = {
    month(w, now) {
      const title = `<div class="month-title"><b>${monthName(now)}</b><span class="dim up">${now.getFullYear()}</span></div>`;
      if (w.size === 's') return `<div class="mg-wrap">${monthName(now, 'short') ? `<div class="month-title"><b>${monthName(now, 'short')}</b></div>` : ''}${monthGridHTML(now)}</div>`;
      if (w.size === 'm') {
        const next = upcoming(1)[0];
        return `<div class="split">
          <div class="left">
            <div><div class="big-wd">${weekdayName(now, 'short')}</div><div class="big-day" style="font-size:52px">${now.getDate()}</div></div>
            ${next ? eventsHTML([next]) : `<div class="no-ev">No events</div>`}
          </div>
          <div class="right mg-wrap">${title}${monthGridHTML(now)}</div>
        </div>`;
      }
      return `<div class="mg-wrap" style="flex:1.4">${title}${monthGridHTML(now)}</div>
        <div style="height:10px"></div>${eventsHTML(upcoming(3))}`;
    },

    today(w, now) {
      const head = `<div class="big-wd">${weekdayName(now)}</div>`;
      const day = `<div class="big-day">${now.getDate()}</div>`;
      const sub = `<div class="dim" style="font-weight:600">${monthName(now)} ${now.getFullYear()}</div>`;
      const todays = eventsOn(ymd(now));
      if (w.size === 's') {
        return `${head}<div style="flex:1;display:flex;align-items:center">${day}</div>
          <div class="dim" style="font-size:12px;font-weight:600">${todays.length ? `${todays.length} event${todays.length > 1 ? 's' : ''} today` : monthName(now)}</div>`;
      }
      if (w.size === 'm') {
        return `<div class="split">
          <div class="left">${head}${day}${sub}</div>
          <div class="right"><div class="up dim" style="margin-bottom:8px">Up next</div>${eventsHTML(upcoming(3))}</div>
        </div>`;
      }
      return `${head}${day}${sub}<div style="height:16px"></div>
        <div class="up dim" style="margin-bottom:8px">Up next</div>${eventsHTML(upcoming(5))}`;
    },

    week(w, now) {
      const ws = Number(state.settings.weekStart);
      const start = w.size === 'l' ? startOfWeek(now, ws) : startOfWeek(now, ws);
      const todayStr = ymd(now);
      const cols = Array.from({ length: 7 }, (_, i) => {
        const d = addDays(start, i);
        const s = ymd(d);
        const evs = eventsOn(s);
        const max = w.size === 'l' ? 5 : 3;
        const marks = w.size === 'l'
          ? evs.slice(0, max).map(e => `<div class="lbl" style="color:${s === todayStr ? 'inherit' : esc(e.color)}">${esc(e.title)}</div>`).join('')
          : `<div class="dots">${evs.slice(0, max).map(e => `<i style="--c:${esc(e.color)}"></i>`).join('')}</div>`;
        return `<div class="col ${s === todayStr ? 'today' : ''}">
          <div class="up" style="font-size:9px;opacity:.7">${weekdayName(d, 'narrow')}</div>
          <div class="n" ${w.size === 's' ? 'style="font-size:12px"' : ''}>${d.getDate()}</div>
          ${w.size === 's' ? '' : marks}
        </div>`;
      }).join('');
      const header = `<div class="month-title"><b>${w.size === 's' ? 'This week' : `Week of ${start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`}</b>
        ${w.size !== 's' ? `<span class="dim up">Wk ${isoWeek(addDays(start, 3))}</span>` : ''}</div>`;
      const footer = w.size === 's' ? `<div class="no-ev" style="margin-top:6px">${(() => { const n = upcoming(1)[0]; return n ? `Next: ${esc(n.title)}` : 'Clear week'; })()}</div>` : '';
      const grid = w.size === 's'
        ? `<div class="week" style="gap:1px;flex:0">${cols}</div>`
        : `<div class="week">${cols}</div>`;
      return header + grid + footer + (w.size === 'l' ? `<div style="height:10px"></div>${eventsHTML(upcoming(3))}` : '');
    },

    agenda(w, now) {
      if (w.size === 's') {
        return `<div class="up acc" style="margin-bottom:8px">Next up</div>${eventsHTML(upcoming(2), { empty: 'All clear ✨' })}`;
      }
      if (w.size === 'm') {
        return `<div class="month-title"><b>Agenda</b><span class="dim up">${now.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}</span></div>
          ${eventsHTML(upcoming(3), { empty: 'Nothing coming up' })}`;
      }
      const list = upcoming(9);
      if (!list.length) return `<div class="month-title"><b>Agenda</b></div><div class="no-ev">Nothing coming up</div>`;
      const groups = [];
      list.forEach(e => { const g = groups[groups.length - 1]; if (g && g.date === e.date) g.items.push(e); else groups.push({ date: e.date, items: [e] }); });
      let html = `<div class="month-title"><b>Agenda</b><span class="dim up">${list.length} upcoming</span></div><div class="evs" style="gap:10px">`;
      let shown = 0;
      for (const g of groups) {
        if (shown >= 7) break;
        html += `<div><div class="up dim" style="margin-bottom:4px">${esc(relDay(g.date))}</div>${eventsHTML(g.items.slice(0, 7 - shown), { showDay: false })}</div>`;
        shown += g.items.length;
      }
      return html + '</div>';
    },

    countdown(w, now) {
      const target = w.opts.date ? parseYmd(w.opts.date) : now;
      const n = dayDiff(now, target);
      const title = esc(w.opts.title || 'Countdown');
      const label = n === 0 ? 'Today!' : n > 0 ? `day${n === 1 ? '' : 's'} until` : `day${n === -1 ? '' : 's'} since`;
      const num = n === 0 ? '🎉' : Math.abs(n);
      const dateLbl = target.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
      if (w.size === 's') {
        return `<div class="up dim">${label}</div><div style="flex:1;display:flex;align-items:center"><div class="cd-num">${num}</div></div>
          <div style="font-weight:700;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${title}</div>`;
      }
      if (w.size === 'm') {
        return `<div class="split" style="align-items:center">
          <div class="left" style="justify-content:center"><div class="cd-num" style="font-size:72px">${num}</div></div>
          <div class="right" style="justify-content:center;gap:4px">
            <div class="up dim">${label}</div>
            <div style="font-weight:800;font-size:22px;line-height:1.1">${title}</div>
            <div class="dim" style="font-size:12px">${dateLbl}</div>
          </div></div>`;
      }
      return `<div class="up dim">${label}</div><div style="font-weight:800;font-size:26px">${title}</div>
        <div style="flex:1;display:flex;align-items:center"><div class="cd-num">${num}</div></div>
        <div class="dim" style="font-weight:600">${dateLbl}</div>`;
    },

    year(w, now) {
      const y = now.getFullYear();
      const total = isLeap(y) ? 366 : 365;
      const day = dayDiff(new Date(y, 0, 1), now) + 1;
      const pct = Math.floor((day / total) * 100);
      const head = `<div class="month-title"><b>${y}</b><span class="dim up">${pct}%</span></div>`;
      if (w.size === 's') {
        return `${head}<div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:8px">
          <div class="cd-num" style="font-size:44px">${pct}%</div><div class="bar"><div style="width:${pct}%"></div></div></div>
          <div class="dim" style="font-size:12px;font-weight:600">Day ${day} of ${total}</div>`;
      }
      const cols = w.size === 'm' ? 28 : 25;
      const dots = Array.from({ length: total }, (_, i) => `<i class="${i + 1 < day ? 'past' : i + 1 === day ? 'now' : ''}"></i>`).join('');
      const grid = `<div class="dots-grid" style="grid-template-columns:repeat(${cols},1fr)">${dots}</div>`;
      if (w.size === 'm') {
        return `<div class="split">
          <div class="left"><div><b class="acc up" style="font-size:13px">${y}</b><div class="cd-num" style="font-size:40px;margin-top:6px">${pct}%</div></div>
            <div class="dim" style="font-size:12px;font-weight:600">${total - day} days left</div></div>
          <div class="right" style="justify-content:center">${grid}</div></div>`;
      }
      return `${head}${grid}<div class="dim" style="font-size:13px;font-weight:600;margin-top:8px">Day ${day} of ${total} · ${total - day} days left</div>`;
    },
  };

  function widgetEl(w, now = new Date()) {
    const el = document.createElement('div');
    el.className = `w ${w.size}`;
    const accentText = contrastText(firstHex(w.accent));
    el.style.cssText = `--wbg:${w.bg};--wfg:${w.fg};--wacc:${w.accent};--wtodayfg:${accentText};--wfont:${FONTS[w.font] || FONTS.inter};--r:${w.radius}px`;
    el.innerHTML = R[w.type](w, now);
    return el;
  }

  // ---------- home board ----------
  let dragId = null;
  function renderBoard() {
    const board = $('#board');
    board.innerHTML = '';
    $('#board-empty').hidden = state.widgets.length > 0;
    state.widgets.forEach(w => {
      const slot = document.createElement('div');
      slot.className = `slot ${w.size}`;
      slot.draggable = true;
      slot.dataset.id = w.id;
      slot.tabIndex = 0;
      slot.setAttribute('role', 'button');
      slot.setAttribute('aria-label', `Edit ${TYPES[w.type].name} widget`);
      slot.appendChild(widgetEl(w));
      slot.addEventListener('click', () => openEditor(w.id));
      slot.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openEditor(w.id); } });
      slot.addEventListener('dragstart', e => { dragId = w.id; slot.classList.add('dragging'); e.dataTransfer.effectAllowed = 'move'; });
      slot.addEventListener('dragend', () => { dragId = null; $$('.slot').forEach(s => s.classList.remove('dragging', 'drop-target')); });
      slot.addEventListener('dragover', e => { if (dragId && dragId !== w.id) { e.preventDefault(); slot.classList.add('drop-target'); } });
      slot.addEventListener('dragleave', () => slot.classList.remove('drop-target'));
      slot.addEventListener('drop', e => {
        e.preventDefault();
        const from = state.widgets.findIndex(x => x.id === dragId);
        const to = state.widgets.findIndex(x => x.id === w.id);
        if (from < 0 || to < 0) return;
        const [moved] = state.widgets.splice(from, 1);
        state.widgets.splice(to, 0, moved);
        save(); renderBoard();
      });
      board.appendChild(slot);
    });
  }

  // ---------- gallery ----------
  let gallerySize = 'm';
  function renderGallery() {
    const g = $('#gallery');
    g.innerHTML = '';
    Object.entries(TYPES).forEach(([type, info]) => {
      const item = document.createElement('div');
      item.className = 'gallery-item';
      item.innerHTML = `<div class="stage"></div>
        <div class="meta"><div><strong>${info.name}</strong><span>${info.desc}</span></div>
        <button class="btn primary">Add</button></div>`;
      $('.stage', item).appendChild(widgetEl(makeWidget(type, gallerySize)));
      $('button', item).addEventListener('click', () => {
        state.widgets.push(makeWidget(type, gallerySize));
        save(); renderBoard();
        toast(`${info.name} widget added`);
      });
      g.appendChild(item);
    });
  }
  $('#gallery-size').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    gallerySize = b.dataset.size;
    $$('#gallery-size button').forEach(x => x.classList.toggle('on', x === b));
    renderGallery();
  });

  // ---------- editor ----------
  let editingId = null;
  const editing = () => state.widgets.find(w => w.id === editingId);
  function openEditor(id) {
    editingId = id;
    const w = editing(); if (!w) return;
    $('#ed-font').value = w.font;
    $('#ed-radius').value = w.radius;
    $('#ed-countdown').hidden = w.type !== 'countdown';
    if (w.type === 'countdown') { $('#ed-cd-title').value = w.opts.title || ''; $('#ed-cd-date').value = w.opts.date || ''; }
    $('.sheet-head h2').textContent = `Edit ${TYPES[w.type].name}`;
    syncEditor();
    $('#scrim').hidden = false;
    $('#sheet').classList.add('open');
    $('#sheet').setAttribute('aria-hidden', 'false');
  }
  function closeEditor() {
    editingId = null;
    $('#scrim').hidden = true;
    $('#sheet').classList.remove('open');
    $('#sheet').setAttribute('aria-hidden', 'true');
  }
  function syncEditor() {
    const w = editing(); if (!w) return;
    $$('#ed-size button').forEach(b => b.classList.toggle('on', b.dataset.size === w.size));
    $('#ed-bg').value = firstHex(w.bg);
    $('#ed-fg').value = firstHex(w.fg);
    $('#ed-accent').value = firstHex(w.accent);
    $$('#ed-themes .theme-chip').forEach(c => {
      const t = THEMES[c.dataset.theme];
      c.classList.toggle('on', t.bg === w.bg && t.fg === w.fg && t.accent === w.accent);
    });
    const p = $('#sheet-preview'); p.innerHTML = ''; p.appendChild(widgetEl(w));
  }
  function updateWidget(fn) {
    const w = editing(); if (!w) return;
    fn(w); save(); syncEditor(); renderBoard();
  }
  $('#ed-themes').innerHTML = Object.entries(THEMES).map(([k, t]) =>
    `<button class="theme-chip" data-theme="${k}" style="background:${t.bg};color:${t.fg}" title="${t.name}">
      <span style="border-bottom:2px solid ${t.accent}">${t.name}</span></button>`).join('');
  $('#ed-themes').addEventListener('click', e => {
    const c = e.target.closest('.theme-chip'); if (!c) return;
    const t = THEMES[c.dataset.theme];
    updateWidget(w => { w.bg = t.bg; w.fg = t.fg; w.accent = t.accent; });
  });
  $('#ed-size').addEventListener('click', e => { const b = e.target.closest('button'); if (b) updateWidget(w => { w.size = b.dataset.size; }); });
  $('#ed-bg').addEventListener('input', e => updateWidget(w => { w.bg = e.target.value; }));
  $('#ed-fg').addEventListener('input', e => updateWidget(w => { w.fg = e.target.value; }));
  $('#ed-accent').addEventListener('input', e => updateWidget(w => { w.accent = e.target.value; }));
  $('#ed-font').addEventListener('change', e => updateWidget(w => { w.font = e.target.value; }));
  $('#ed-radius').addEventListener('input', e => updateWidget(w => { w.radius = Number(e.target.value); }));
  $('#ed-cd-title').addEventListener('input', e => updateWidget(w => { w.opts.title = e.target.value; }));
  $('#ed-cd-date').addEventListener('change', e => { if (e.target.value) updateWidget(w => { w.opts.date = e.target.value; }); });
  $('#ed-dup').addEventListener('click', () => {
    const w = editing(); if (!w) return;
    const copy = JSON.parse(JSON.stringify(w)); copy.id = uid();
    state.widgets.splice(state.widgets.indexOf(w) + 1, 0, copy);
    save(); renderBoard(); toast('Widget duplicated'); openEditor(copy.id);
  });
  $('#ed-del').addEventListener('click', () => {
    const w = editing(); if (!w) return;
    state.widgets = state.widgets.filter(x => x !== w);
    save(); renderBoard(); closeEditor(); toast('Widget removed');
  });
  $('#sheet-close').addEventListener('click', closeEditor);
  $('#scrim').addEventListener('click', closeEditor);
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && editingId) closeEditor(); });

  // ---------- events view ----------
  let calRef = new Date();
  let selDate = ymd(new Date());
  let selColor = EVENT_COLORS[4];
  function renderCalendar() {
    const ws = Number(state.settings.weekStart);
    $('#cal-title').textContent = `${monthName(calRef)} ${calRef.getFullYear()}`;
    const first = new Date(calRef.getFullYear(), calRef.getMonth(), 1);
    const start = startOfWeek(first, ws);
    const todayStr = ymd(new Date());
    let html = weekdayLetters(ws).map(l => `<div class="h">${l}</div>`).join('');
    for (let i = 0; i < 42; i++) {
      const d = addDays(start, i), s = ymd(d);
      const evs = eventsOn(s);
      const cls = [d.getMonth() !== calRef.getMonth() && 'out', s === todayStr && 'today', s === selDate && 'sel'].filter(Boolean).join(' ');
      html += `<button type="button" class="${cls}" data-date="${s}" aria-label="${d.toDateString()}${evs.length ? `, ${evs.length} events` : ''}">
        ${d.getDate()}<span class="pips">${evs.slice(0, 3).map(e => `<i style="--c:${esc(e.color)}"></i>`).join('')}</span></button>`;
    }
    $('#cal-grid').innerHTML = html;
  }
  function renderEventList() {
    const list = sortedEvents().filter(e => e.date >= ymd(new Date()) || e.date === selDate);
    $('#event-list').innerHTML = list.length ? list.map(e => `
      <li style="--c:${esc(e.color)}"><i></i><div><b>${esc(e.title)}</b><small>${esc(relDay(e.date))} · ${esc(fmtTime(e.time))}</small></div>
      <button type="button" data-del="${e.id}" aria-label="Delete ${esc(e.title)}">✕</button></li>`).join('')
      : `<li class="none">No upcoming events. Add one above.</li>`;
  }
  function renderEvents() {
    renderCalendar(); renderEventList();
    $('#event-form [name=date]').value = selDate;
    $('#day-title').textContent = `Add event · ${parseYmd(selDate).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}`;
  }
  $('#event-colors').innerHTML = EVENT_COLORS.map(c => `<button type="button" class="swatch" data-c="${c}" style="background:${c}" aria-label="Color ${c}"></button>`).join('');
  function syncEventColor() { $$('#event-colors .swatch').forEach(s => s.classList.toggle('on', s.dataset.c === selColor)); }
  $('#event-colors').addEventListener('click', e => { const s = e.target.closest('.swatch'); if (s) { selColor = s.dataset.c; syncEventColor(); } });
  $('#cal-grid').addEventListener('click', e => {
    const b = e.target.closest('button[data-date]'); if (!b) return;
    selDate = b.dataset.date;
    const d = parseYmd(selDate);
    if (d.getMonth() !== calRef.getMonth()) calRef = new Date(d.getFullYear(), d.getMonth(), 1);
    renderEvents();
  });
  $('#cal-prev').addEventListener('click', () => { calRef = new Date(calRef.getFullYear(), calRef.getMonth() - 1, 1); renderCalendar(); });
  $('#cal-next').addEventListener('click', () => { calRef = new Date(calRef.getFullYear(), calRef.getMonth() + 1, 1); renderCalendar(); });
  $('#event-form').addEventListener('submit', e => {
    e.preventDefault();
    const f = new FormData(e.target);
    const title = String(f.get('title')).trim();
    if (!title) return;
    state.events.push({ id: uid(), title, date: String(f.get('date')), time: String(f.get('time') || ''), color: selColor });
    selDate = String(f.get('date'));
    save(); e.target.reset(); renderEvents(); renderBoard(); toast('Event added');
  });
  $('#event-list').addEventListener('click', e => {
    const b = e.target.closest('[data-del]'); if (!b) return;
    state.events = state.events.filter(x => x.id !== b.dataset.del);
    save(); renderEvents(); renderBoard();
  });

  // ICS import (single events; recurring rules are imported as their first occurrence)
  function parseICS(text) {
    const lines = text.replace(/\r\n[ \t]/g, '').replace(/\n[ \t]/g, '').split(/\r?\n/);
    const out = []; let cur = null;
    for (const line of lines) {
      if (line === 'BEGIN:VEVENT') cur = {};
      else if (line === 'END:VEVENT') { if (cur && cur.title && cur.date) out.push(cur); cur = null; }
      else if (cur) {
        const i = line.indexOf(':'); if (i < 0) continue;
        const key = line.slice(0, i), val = line.slice(i + 1);
        const name = key.split(';')[0];
        if (name === 'SUMMARY') cur.title = val.replace(/\\([,;\\])/g, '$1').replace(/\\n/gi, ' ').slice(0, 80);
        if (name === 'DTSTART') {
          const m = val.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?/);
          if (!m) continue;
          if (!m[4]) { cur.date = `${m[1]}-${m[2]}-${m[3]}`; cur.time = ''; }
          else {
            const d = m[7] ? new Date(Date.UTC(+m[1], m[2] - 1, +m[3], +m[4], +m[5])) : new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5]);
            cur.date = ymd(d); cur.time = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
          }
        }
      }
    }
    return out;
  }
  $('#ics-input').addEventListener('change', async e => {
    const file = e.target.files[0]; if (!file) return;
    try {
      const evs = parseICS(await file.text());
      const seen = new Set(state.events.map(x => `${x.title}|${x.date}|${x.time}`));
      let added = 0;
      evs.forEach((x, i) => {
        const k = `${x.title}|${x.date}|${x.time}`;
        if (seen.has(k)) return;
        seen.add(k); added++;
        state.events.push({ id: uid(), ...x, color: EVENT_COLORS[i % EVENT_COLORS.length] });
      });
      save(); renderEvents(); renderBoard();
      toast(`Imported ${added} event${added === 1 ? '' : 's'}`);
    } catch (err) { toast('Could not read that file'); }
    e.target.value = '';
  });

  // ---------- settings ----------
  function applyWallpaper() { document.documentElement.style.setProperty('--wallpaper', state.settings.wallpaper); }
  $('#set-wallpaper').innerHTML = WALLPAPERS.map(w => `<button type="button" class="swatch wide" data-w="${w}" style="background:${w}" aria-label="Wallpaper"></button>`).join('');
  function syncSettings() {
    $('#set-weekstart').value = String(state.settings.weekStart);
    $('#set-weeknums').checked = !!state.settings.weekNums;
    $('#set-24h').checked = !!state.settings.h24;
    $$('#set-wallpaper .swatch').forEach(s => s.classList.toggle('on', s.dataset.w === state.settings.wallpaper));
  }
  function renderAll() { applyWallpaper(); renderBoard(); renderGallery(); renderEvents(); syncSettings(); syncEventColor(); }
  $('#set-weekstart').addEventListener('change', e => { state.settings.weekStart = Number(e.target.value); save(); renderAll(); });
  $('#set-weeknums').addEventListener('change', e => { state.settings.weekNums = e.target.checked; save(); renderAll(); });
  $('#set-24h').addEventListener('change', e => { state.settings.h24 = e.target.checked; save(); renderAll(); });
  $('#set-wallpaper').addEventListener('click', e => { const s = e.target.closest('.swatch'); if (s) { state.settings.wallpaper = s.dataset.w; save(); renderAll(); } });
  $('#export-btn').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = `tilecal-backup-${ymd(new Date())}.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  });
  $('#import-btn').addEventListener('change', async e => {
    const file = e.target.files[0]; if (!file) return;
    try {
      const s = JSON.parse(await file.text());
      if (!Array.isArray(s.widgets) || !Array.isArray(s.events)) throw new Error('bad');
      s.widgets = s.widgets.filter(w => TYPES[w.type]);
      s.settings = Object.assign(defaultState().settings, s.settings);
      state = s; save(); renderAll(); toast('Backup restored');
    } catch (err) { toast('That isn’t a Tilecal backup'); }
    e.target.value = '';
  });
  $('#reset-btn').addEventListener('click', () => {
    if (!confirm('Reset all widgets, events and settings?')) return;
    state = defaultState(); save(); renderAll(); toast('Reset to defaults');
  });

  // ---------- navigation ----------
  function go(view) {
    $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.view === view));
    $$('.view').forEach(v => v.classList.toggle('active', v.id === `view-${view}`));
    if (view === 'gallery') renderGallery();
    if (view === 'events') renderEvents();
    window.scrollTo({ top: 0 });
  }
  $$('.tab').forEach(t => t.addEventListener('click', () => go(t.dataset.view)));
  document.addEventListener('click', e => { const g = e.target.closest('[data-goto]'); if (g) go(g.dataset.goto); });

  // Keep widgets current across midnight.
  let lastDay = ymd(new Date());
  setInterval(() => { const d = ymd(new Date()); if (d !== lastDay) { lastDay = d; renderAll(); } }, 30000);

  renderAll();
})();
