/* =========================================================
   RECORDATORIOS (paso 5.2) — lo usan admin.html y baker.html
   ---------------------------------------------------------
   - Caja "Recordatorios de hoy": los de hoy y los vencidos (el admin ve también los del equipo).
   - Botón "Nuevo recordatorio" → ventana #modal-reminder.
   - Cada minuto pregunta al servidor si a alguno de MIS recordatorios le llegó la hora y muestra
     un aviso arriba a la derecha (una sola vez por recordatorio en este navegador).
   Todo el texto se pone con textContent (nunca innerHTML con datos): no se puede inyectar código.
   ========================================================= */
document.addEventListener('DOMContentLoaded', () => {
  const box = document.getElementById('reminders-box');
  if (!box) return;

  const list = document.getElementById('reminders-list');
  const empty = document.getElementById('reminders-empty');
  const modal = document.getElementById('modal-reminder');
  const form = document.getElementById('reminder-form');
  const CHECK_EVERY_MS = 60 * 1000;
  const SEEN_KEY = 'dreamBakeReminderAlerts';   // ids de avisos ya cerrados en este navegador
  const baseTitle = document.title;

  // ---------- Íconos (SVG del mismo estilo que el resto del panel) ----------
  const SVG_NS = 'http://www.w3.org/2000/svg';
  function icon(paths, size = 18) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('width', size); svg.setAttribute('height', size);
    svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round'); svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true'); svg.setAttribute('focusable', 'false');
    paths.forEach(d => {
      const p = document.createElementNS(SVG_NS, 'path');
      p.setAttribute('d', d);
      svg.appendChild(p);
    });
    return svg;
  }
  const ICON_CHECK = ['M20 6 9 17l-5-5'];
  const ICON_UNDO = ['M3 7v6h6', 'M21 17a9 9 0 0 0-15-6.7L3 13'];
  const ICON_TRASH = ['M3 6h18', 'M8 6V4h8v2', 'M19 6l-1 14H6L5 6'];
  const ICON_BELL = ['M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9', 'M13.73 21a2 2 0 0 1-3.46 0'];
  const ICON_SPARK = ['M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z'];

  // ---------- Fechas ----------
  // El servidor manda "2026-10-12T10:00:00" (hora de Argentina, sin zona): se arma la fecha local tal cual
  function parseLocal(text) {
    const [d, t = '00:00'] = String(text).split('T');
    const [y, m, day] = d.split('-').map(Number);
    const [h, mi] = t.split(':').map(Number);
    return new Date(y, m - 1, day, h, mi);
  }
  const two = n => String(n).padStart(2, '0');
  const hourText = date => `${two(date.getHours())}:${two(date.getMinutes())}`;
  // "10:00" si es hoy; "Ayer 15:00" o "10/10 15:00" si es de otro día
  function whenText(date) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const day = new Date(date); day.setHours(0, 0, 0, 0);
    const diffDays = Math.round((today - day) / 86400000);
    if (diffDays === 0) return hourText(date);
    if (diffDays === 1) return `Ayer ${hourText(date)}`;
    return `${two(date.getDate())}/${two(date.getMonth() + 1)} ${hourText(date)}`;
  }

  // ---------- Avisos ya vistos (localStorage puede no estar disponible: siempre con try/catch) ----------
  function seenIds() {
    try { return new Set(JSON.parse(localStorage.getItem(SEEN_KEY) || '[]')); } catch (e) { return new Set(); }
  }
  function markSeen(id) {
    try {
      const ids = [...seenIds(), id].slice(-200);   // se guardan los últimos 200
      localStorage.setItem(SEEN_KEY, JSON.stringify(ids));
    } catch (e) { /* sin almacenamiento: el aviso puede volver a salir al recargar */ }
  }

  // ---------- Caja de recordatorios ----------
  async function loadReminders() {
    try {
      const res = await fetch('/api/reminders?scope=today');
      if (!res.ok) return;
      render(await res.json());
    } catch (e) {
      console.error('No se pudieron cargar los recordatorios', e);
    }
  }

  function render(reminders) {
    list.replaceChildren();
    // Pendientes primero (por hora), después los que ya se hicieron hoy
    reminders.sort((a, b) => (a.done - b.done) || (parseLocal(a.dueAt) - parseLocal(b.dueAt)));
    reminders.forEach(r => list.appendChild(item(r)));
    empty.hidden = reminders.length > 0;
  }

  function badge(text, cls) {
    const span = document.createElement('span');
    span.className = `reminder-badge ${cls || ''}`.trim();
    span.textContent = text;
    return span;
  }

  function item(r) {
    const due = parseLocal(r.dueAt);
    const li = document.createElement('li');
    li.className = 'reminder-item' + (r.overdue ? ' is-overdue' : '') + (r.done ? ' is-done' : '');

    const time = document.createElement('div');
    time.className = 'reminder-time';
    time.textContent = whenText(due);

    const main = document.createElement('div');
    main.className = 'reminder-main';
    const title = document.createElement('strong');
    title.className = 'reminder-title';
    title.textContent = r.title;
    main.appendChild(title);
    if (r.notes) {
      const notes = document.createElement('p');
      notes.className = 'reminder-notes';
      notes.textContent = r.notes;
      main.appendChild(notes);
    }
    const meta = document.createElement('div');
    meta.className = 'reminder-meta';
    if (r.overdue) meta.appendChild(badge('Vencido', 'is-danger'));
    if (r.done) meta.appendChild(badge('Hecho', 'is-done'));
    if (r.relatedOrderId) meta.appendChild(badge(`Pedido #${r.relatedOrderId}`));
    if (!r.mine) meta.appendChild(badge(`De ${r.ownerName}`));
    if (r.createdByAssistant) {
      const b = badge('Bakey');
      b.prepend(icon(ICON_SPARK, 12));
      meta.appendChild(b);
    }
    if (meta.childElementCount) main.appendChild(meta);

    const actions = document.createElement('div');
    actions.className = 'reminder-actions';
    // Clases propias (no btn-edit / btn-delete / btn-red): baker.html oculta esas
    const doneBtn = document.createElement('button');
    doneBtn.type = 'button';
    doneBtn.className = 'btn btn-grey btn-sm reminder-done';
    doneBtn.append(icon(r.done ? ICON_UNDO : ICON_CHECK), r.done ? 'Deshacer' : 'Hecho');
    doneBtn.addEventListener('click', () => setDone(r, !r.done));
    const delBtn = document.createElement('button');
    delBtn.type = 'button';
    delBtn.className = 'reminder-delete';
    delBtn.setAttribute('aria-label', `Borrar el recordatorio ${r.title}`);
    delBtn.title = 'Borrar';
    delBtn.appendChild(icon(ICON_TRASH));
    delBtn.addEventListener('click', () => confirmDelete(r));
    actions.append(doneBtn, delBtn);

    li.append(time, main, actions);
    return li;
  }

  async function setDone(r, done) {
    try {
      const res = await fetch(`/api/reminders/${r.id}/done`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ done })
      });
      if (!res.ok) {
        window.showErrorAlert('No se pudo actualizar el recordatorio. Recargá la página y probá de nuevo.');
        return;
      }
      if (done) closeAlert(r.id);
      loadReminders();
    } catch (e) { window.showErrorAlert('Error de conexión'); }
  }

  function confirmDelete(r) {
    window.showConfirmModal('Borrar recordatorio', `¿Seguro que querés borrar "${r.title}"?`, 'Borrar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/reminders/${r.id}`, { method: 'DELETE' });
        if (!res.ok) window.showErrorAlert('No se pudo borrar el recordatorio.');
        closeAlert(r.id);
        loadReminders();
      } catch (e) { window.showErrorAlert('Error de conexión'); }
    });
  }

  // Mensaje corto debajo de la lista que se va solo a los 8 segundos
  const status = document.getElementById('reminders-status');
  let statusTimer = null;
  function showStatus(text) {
    if (!status) return;
    status.textContent = text;
    status.hidden = false;
    clearTimeout(statusTimer);
    statusTimer = setTimeout(() => { status.hidden = true; }, 8000);
  }

  // ---------- Nuevo recordatorio ----------
  document.getElementById('btn-new-reminder').addEventListener('click', () => {
    form.reset();
    // Propuesta: hoy, en la próxima hora en punto
    const next = new Date();
    next.setHours(next.getHours() + 1, 0, 0, 0);
    const dateInput = document.getElementById('rem-date');
    const todayText = (d => `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}`)(new Date());
    dateInput.min = todayText;
    dateInput.value = `${next.getFullYear()}-${two(next.getMonth() + 1)}-${two(next.getDate())}`;
    document.getElementById('rem-time').value = hourText(next);
    modal.hidden = false;
    document.getElementById('rem-title').focus();
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const title = document.getElementById('rem-title').value.trim();
    const date = document.getElementById('rem-date').value;
    const time = document.getElementById('rem-time').value;
    const order = document.getElementById('rem-order').value;
    if (!title) { window.showErrorAlert('Escribí qué hay que hacer.'); return; }
    if (!date || !time) { window.showErrorAlert('Elegí el día y la hora.'); return; }

    const saveBtn = document.getElementById('rem-save');
    saveBtn.disabled = true;
    try {
      const res = await fetch('/api/reminders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title,
          notes: document.getElementById('rem-notes').value,
          dueAt: `${date}T${time}`,
          relatedOrderId: order ? Number(order) : null
        })
      });
      if (res.ok) {
        modal.hidden = true;
        loadReminders();
        // Si es para otro día no aparece en "Recordatorios de hoy": se avisa que quedó guardado
        const saved = parseLocal(`${date}T${time}`);
        if (saved.toDateString() !== new Date().toDateString()) {
          showStatus(`Listo, quedó guardado para el ${two(saved.getDate())}/${two(saved.getMonth() + 1)} a las ${hourText(saved)}. Va a aparecer acá ese día.`);
        }
      } else {
        const body = await res.json().catch(() => ({}));
        window.showErrorAlert(body.message || 'No se pudo guardar el recordatorio.');
      }
    } catch (err) {
      window.showErrorAlert('Error de conexión');
    } finally {
      saveBtn.disabled = false;
    }
  });

  // ---------- Aviso cuando llega la hora ----------
  const alertsRegion = document.createElement('div');
  alertsRegion.className = 'reminder-alerts';
  document.body.appendChild(alertsRegion);

  function updateTabTitle() {
    const n = alertsRegion.childElementCount;
    document.title = n ? `(${n}) Recordatorio · ${baseTitle}` : baseTitle;
  }

  function closeAlert(id) {
    markSeen(id);
    const card = alertsRegion.querySelector(`[data-reminder-id="${id}"]`);
    if (card) card.remove();
    updateTabTitle();
  }

  function showAlert(r) {
    if (alertsRegion.querySelector(`[data-reminder-id="${r.id}"]`)) return;   // ya está en pantalla
    const card = document.createElement('div');
    card.className = 'reminder-alert';
    card.setAttribute('role', 'alert');
    card.dataset.reminderId = r.id;

    const text = document.createElement('div');
    text.className = 'reminder-alert__text';
    const label = document.createElement('span');
    label.className = 'reminder-alert__label';
    label.textContent = `Recordatorio · ${whenText(parseLocal(r.dueAt))}`;
    const title = document.createElement('strong');
    title.textContent = r.title;
    text.append(label, title);
    if (r.notes) {
      const notes = document.createElement('span');
      notes.className = 'reminder-alert__notes';
      notes.textContent = r.notes;
      text.appendChild(notes);
    }

    const actions = document.createElement('div');
    actions.className = 'reminder-alert__actions';
    const doneBtn = document.createElement('button');
    doneBtn.type = 'button';
    doneBtn.className = 'btn btn-pink btn-sm';
    doneBtn.textContent = 'Marcar hecho';
    doneBtn.addEventListener('click', () => setDone(r, true));
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn btn-grey btn-sm';
    closeBtn.textContent = 'Cerrar';
    closeBtn.addEventListener('click', () => closeAlert(r.id));
    actions.append(doneBtn, closeBtn);

    card.append(icon(ICON_BELL, 22), text, actions);
    alertsRegion.appendChild(card);
    updateTabTitle();
  }

  async function checkAlerts() {
    try {
      const res = await fetch('/api/reminders/alerts');
      if (!res.ok) return;
      const due = await res.json();
      const seen = seenIds();
      due.filter(r => !seen.has(r.id)).forEach(showAlert);
    } catch (e) {
      console.error('No se pudieron revisar los recordatorios', e);
    }
  }

  // Cada minuto: avisos nuevos y la lista al día (los que pasan a "Vencido")
  function tick() {
    checkAlerts();
    loadReminders();
  }
  tick();
  setInterval(tick, CHECK_EVERY_MS);
  // Si se confirmó una acción de Bakey (por ejemplo, crear un recordatorio), la caja se actualiza ya
  document.addEventListener('bakey:action-done', loadReminders);
  // Al volver a la pestaña después de un rato, se revisa enseguida
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') tick();
  });
});
