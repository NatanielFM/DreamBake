// notifications-simple.js — Campanita de avisos de los dos paneles (admin y pastelero).
//
// Paso 6.6: antes solo contaba los pedidos nuevos; ahora muestra todos los avisos que arma el servidor
// (GET /api/notifications, NotificationService): pedidos nuevos, stock bajo, insumos por vencer, entregas
// de mañana, recordatorios vencidos y, solo para la administración, backup fallido y cumpleaños de hoy.
//   - El número rojo cuenta los NO leídos. Tocar un aviso lo marca como leído y abre la pestaña.
//   - "Marcar todas como leídas" manda las claves que se están viendo (POST /api/notifications/read).
//   - Se actualiza cada 60 segundos (también en segundo plano) y al volver a la pestaña, sin recargar la página.
//   - Sonido suave opcional para pedidos nuevos: apagado por defecto, se recuerda en este navegador.
// Todo el texto se pone con textContent (los nombres de clientes vienen de la tienda: nunca innerHTML).

document.addEventListener('DOMContentLoaded', () => {
  const btnBell = document.getElementById('btn-notifications');
  const dropdown = document.getElementById('notif-dropdown');
  const badge = document.getElementById('notif-badge');
  const list = document.getElementById('notif-list');
  const readAllBtn = document.getElementById('notif-read-all');
  const soundBox = document.getElementById('notif-sound');
  if (!btnBell || !dropdown || !list) return;

  const REFRESH_MS = 60000;
  const SOUND_KEY = 'dreamBakeNotifSound';
  let items = [];
  let knownOrderKeys = null;   // pedidos ya vistos en esta página (null = todavía no se cargó la primera vez)

  // Íconos chicos por tipo de aviso (SVG fijos, sin datos adentro)
  const ICONS = {
    ORDER: '<path d="M5 8h14l-1.2 11.1a2 2 0 0 1-2 1.9H8.2a2 2 0 0 1-2-1.9L5 8Z"/><path d="M9 10V7a3 3 0 0 1 6 0v3"/>',
    STOCK: '<path d="M21 8 12 3 3 8v8l9 5 9-5V8Z"/><path d="M3 8l9 5 9-5M12 13v8"/>',
    EXPIRING: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    TOMORROW: '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    REMINDER: '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
    BACKUP: '<path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>',
    BIRTHDAY: '<path d="M4 21h16v-8H4v8ZM4 13h16M12 8v5M12 4.5c.8.7 1 1.6.5 2.3a1.1 1.1 0 0 1-1.6 0c-.4-.7 0-1.6 1.1-2.3Z"/>'
  };

  // ---------- Abrir y cerrar ----------
  function setOpen(open) {
    dropdown.hidden = !open;
    btnBell.setAttribute('aria-expanded', open ? 'true' : 'false');
  }
  btnBell.addEventListener('click', (e) => {
    e.stopPropagation();
    setOpen(dropdown.hidden);
    if (!dropdown.hidden) load();   // al abrir, datos frescos
  });
  document.addEventListener('click', (e) => {
    if (!dropdown.hidden && !dropdown.contains(e.target) && !btnBell.contains(e.target)) setOpen(false);
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !dropdown.hidden) { setOpen(false); btnBell.focus(); }
  });

  // ---------- Sonido opcional ----------
  let soundOn = false;
  try { soundOn = localStorage.getItem(SOUND_KEY) === '1'; } catch (err) { /* sin almacenamiento: queda apagado */ }
  if (soundBox) {
    soundBox.checked = soundOn;
    soundBox.addEventListener('change', () => {
      soundOn = soundBox.checked;
      try { localStorage.setItem(SOUND_KEY, soundOn ? '1' : '0'); } catch (err) { /* nada */ }
      if (soundOn) chime();   // se escucha una vez para saber cómo suena
    });
  }

  // Dos notas cortas y suaves con Web Audio (sin archivos de sonido)
  function chime() {
    try {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      const ctx = new Ctx();
      [[880, 0], [1175, 0.16]].forEach(([freq, start]) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.0001, ctx.currentTime + start);
        gain.gain.exponentialRampToValueAtTime(0.08, ctx.currentTime + start + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + 0.35);
        osc.connect(gain).connect(ctx.destination);
        osc.start(ctx.currentTime + start);
        osc.stop(ctx.currentTime + start + 0.4);
      });
      setTimeout(() => ctx.close(), 1000);
    } catch (err) { /* el navegador no dejó reproducir: no pasa nada */ }
  }

  // ---------- Dibujar ----------
  function render() {
    const unread = items.filter(i => !i.read).length;
    if (badge) {
      badge.textContent = unread > 99 ? '99+' : String(unread);
      badge.style.display = unread > 0 ? 'inline-flex' : 'none';
    }
    btnBell.setAttribute('aria-label', unread > 0 ? `Avisos: ${unread} sin leer` : 'Avisos: no hay nuevos');
    if (readAllBtn) readAllBtn.hidden = unread === 0;

    list.replaceChildren();
    if (items.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'notif-empty';
      empty.textContent = 'No hay avisos. Todo en orden.';
      list.append(empty);
      return;
    }
    items.forEach(n => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'notif-item' + (n.read ? ' is-read' : '') + ' notif-item--' + n.type.toLowerCase();
      btn.dataset.key = n.key;
      btn.dataset.tab = n.tab || '';

      const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      icon.setAttribute('viewBox', '0 0 24 24');
      icon.setAttribute('aria-hidden', 'true');
      icon.setAttribute('class', 'notif-icon');
      icon.innerHTML = ICONS[n.type] || ICONS.REMINDER;   // SVG fijo de arriba, no datos del servidor

      const body = document.createElement('span');
      body.className = 'notif-body';
      const title = document.createElement('strong');
      title.textContent = n.title;
      const text = document.createElement('span');
      text.textContent = n.text;
      body.append(title, text);
      if (!n.read) {
        const dot = document.createElement('span');
        dot.className = 'visually-hidden';
        dot.textContent = ' (sin leer)';
        title.append(dot);
      }
      btn.append(icon, body);
      list.append(btn);
    });
  }

  // ---------- Datos ----------
  async function load() {
    try {
      const res = await fetch('/api/notifications', { headers: { 'Accept': 'application/json' } });
      if (!res.ok || !(res.headers.get('content-type') || '').includes('json')) return;   // sesión vencida
      const data = await res.json();
      items = Array.isArray(data.items) ? data.items : [];

      // ¿Llegó un pedido nuevo desde la última vez? (no suena en la primera carga de la página)
      const orderKeys = items.filter(i => i.sound).map(i => i.key);
      if (knownOrderKeys && soundOn && orderKeys.some(k => !knownOrderKeys.has(k))) chime();
      knownOrderKeys = new Set(orderKeys);

      render();
    } catch (err) {
      // Sin conexión por un rato: se reintenta en la próxima vuelta, sin molestar
    }
  }

  async function markRead(keys) {
    if (!keys.length) return;
    items.forEach(i => { if (keys.includes(i.key)) i.read = true; });
    render();   // se ve al instante; el servidor lo guarda atrás
    try {
      await fetch('/api/notifications/read', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ keys })
      });
    } catch (err) { /* si falla, en la próxima actualización vuelve a aparecer como no leído */ }
  }

  // Abre la pestaña del aviso. "pedidos/nuevos" = pestaña Pedidos y la solapa Nuevos.
  function openTab(target) {
    if (!target) return;
    const [tab, sub] = target.split('/');
    document.querySelector(`.tab-btn[data-tab="${tab}"]`)?.click();
    if (sub) document.querySelector(`.order-tab-btn[data-tab="${sub}"]`)?.click();
  }

  list.addEventListener('click', (e) => {
    const btn = e.target.closest('.notif-item');
    if (!btn) return;
    markRead([btn.dataset.key]);
    openTab(btn.dataset.tab);
    setOpen(false);
  });

  if (readAllBtn) {
    readAllBtn.addEventListener('click', () => markRead(items.filter(i => !i.read).map(i => i.key)));
  }

  // Si otra parte del panel cambia algo (Bakey confirmó una acción, se guardó un recordatorio…), se actualiza
  document.addEventListener('bakey:action-done', load);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) load(); });

  load();
  // Sigue actualizando aunque la pestaña del navegador esté en segundo plano: así el sonido avisa
  // de un pedido nuevo mientras la persona usa otra cosa (es un pedido liviano por minuto).
  setInterval(load, REFRESH_MS);
});
