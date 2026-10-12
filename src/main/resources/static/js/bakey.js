/* =========================================================
   BAKEY — ventana del asistente (paso 5.5). Lo usan admin.html y baker.html.
   ---------------------------------------------------------
   - Botón flotante abajo a la derecha con el avatar (un cupcake con cara).
   - Panel de chat: mensajes en burbujas, "Bakey está escribiendo…", caja de texto
     (Enter envía, Shift+Enter hace salto de línea) y "Nueva conversación".
   - Las tarjetas de acciones (paso 5.4) se muestran con botones Confirmar / Cancelar.
   - La conversación se guarda en el SERVIDOR (GET /api/bakey/history), no en el navegador.

   SEGURIDAD: el texto que escribe el modelo nunca se pone con innerHTML. El mini formato
   (negrita, listas, saltos de línea) se arma creando elementos y poniendo el texto con
   textContent, así un "<img onerror=…>" se ve como texto y nunca se ejecuta.
   El único innerHTML de este archivo es el de los íconos SVG fijos (constantes escritas acá).
   ========================================================= */
document.addEventListener('DOMContentLoaded', () => {
  const panelRole = document.body.dataset.panel === 'admin' ? 'ADMIN' : 'PASTELERO';

  // ---------- Íconos (SVG fijos) ----------
  const AVATAR = `
    <svg class="bk-avatar" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <path class="bk-stem" d="M24 8.5c0-2 1-3.5 3-4.5"/>
      <circle class="bk-cherry" cx="24" cy="10" r="3.6"/>
      <path class="bk-frost" d="M9.5 25.5c-2.6 0-4.3-1.9-4.1-4.3.2-2.3 2.1-3.9 4.4-3.8.4-3.9 3.7-6.6 7.6-6.2 1.6-2.4 4.2-3.6 7-3.2 3.1.4 5.4 2.6 5.9 5.5 3.4-.4 6.5 1.9 7 5.2 2.5.3 4.4 2.3 4.2 4.7-.2 2.3-2 4.1-4.3 4.1z"/>
      <path class="bk-cup" d="M9.5 25.5h29l-3.6 16.3a3 3 0 0 1-2.9 2.4H16a3 3 0 0 1-2.9-2.4z"/>
      <path class="bk-stripe" d="M17 27.5l1.6 14.5M24 27.5v14.5M31 27.5l-1.6 14.5"/>
      <circle class="bk-cheek" cx="16.6" cy="34.8" r="1.8"/>
      <circle class="bk-cheek" cx="31.4" cy="34.8" r="1.8"/>
      <circle class="bk-eye" cx="19.5" cy="31.5" r="1.7"/>
      <circle class="bk-eye" cx="28.5" cy="31.5" r="1.7"/>
      <path class="bk-smile" d="M21.2 35.2q2.8 2.4 5.6 0"/>
    </svg>`;
  const ICON = {
    close: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M6 6l12 12M18 6 6 18"/></svg>',
    reset: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg>',
    send: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4 20-7z"/></svg>',
    action: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>'
  };

  // Sugerencias de la bienvenida según el rol (el pastelero no carga montos ni ve ventas)
  const SUGGESTIONS = panelRole === 'ADMIN'
    ? ['¿Qué entrego hoy?', '¿Qué insumos están bajos?', 'Recordame algo', 'Cargar un pedido']
    : ['¿Qué entrego hoy?', '¿Qué insumos están bajos?', 'Recordame algo', '¿Qué vence esta semana?'];
  // Lo que se le manda a Bakey al tocar cada sugerencia (más completo que el texto del botón)
  const SUGGESTION_MESSAGES = {
    'Recordame algo': 'Quiero que me recuerdes algo',
    'Cargar un pedido': 'Quiero cargar un pedido nuevo'
  };

  // ---------- Armado del botón y del panel ----------
  const fab = document.createElement('button');
  fab.type = 'button';
  fab.className = 'bk-fab';
  fab.setAttribute('aria-label', 'Preguntale a Bakey');
  fab.setAttribute('aria-controls', 'bakey-panel');
  fab.setAttribute('aria-expanded', 'false');
  fab.innerHTML = AVATAR + '<span class="bk-fab__tip" aria-hidden="true">Preguntale a Bakey</span>';

  const panel = document.createElement('section');
  panel.id = 'bakey-panel';
  panel.className = 'bk-panel';
  panel.hidden = true;
  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-labelledby', 'bk-title');
  panel.innerHTML = `
    <header class="bk-head">
      <div class="bk-head__avatar">${AVATAR}</div>
      <div class="bk-head__text">
        <h2 class="bk-head__title" id="bk-title">Bakey</h2>
        <p class="bk-status" id="bk-status">Disponible</p>
      </div>
      <button type="button" class="bk-icon-btn" id="bk-reset" aria-label="Nueva conversación" title="Nueva conversación">${ICON.reset}</button>
      <button type="button" class="bk-icon-btn bk-close" id="bk-close" aria-label="Cerrar el chat" title="Cerrar">${ICON.close}</button>
    </header>
    <div class="bk-messages" id="bk-messages" role="log" aria-live="polite" aria-relevant="additions"></div>
    <div class="bk-typing" id="bk-typing" hidden>
      <span class="bk-dots" aria-hidden="true"><span></span><span></span><span></span></span>
      <span>Bakey está escribiendo…</span>
    </div>
    <form class="bk-form" id="bk-form">
      <label for="bk-input" class="visually-hidden">Escribile a Bakey</label>
      <textarea id="bk-input" class="bk-input" rows="1" maxlength="2000" placeholder="Escribile a Bakey…"></textarea>
      <button type="submit" class="bk-send" id="bk-send" aria-label="Enviar">${ICON.send}</button>
    </form>
    <p class="bk-hint">Enter envía · Shift + Enter hace un salto de línea</p>`;

  document.body.append(panel, fab);

  const messages = panel.querySelector('#bk-messages');
  const typing = panel.querySelector('#bk-typing');
  const statusEl = panel.querySelector('#bk-status');
  const form = panel.querySelector('#bk-form');
  const input = panel.querySelector('#bk-input');
  const sendBtn = panel.querySelector('#bk-send');

  let busy = false;
  let configured = true;

  // ---------- Formato seguro del texto de Bakey ----------
  // Texto con **negrita** → nodos de texto y <strong>, siempre con textContent
  function inline(parent, text) {
    String(text).split(/(\*\*[^*]+\*\*)/g).forEach(part => {
      if (!part) return;
      if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
        const strong = document.createElement('strong');
        strong.textContent = part.slice(2, -2);
        parent.appendChild(strong);
      } else {
        parent.appendChild(document.createTextNode(part.replace(/\*/g, '')));
      }
    });
  }

  // Párrafos, listas ("- ", "* ", "1. ") y saltos de línea
  function formatted(text) {
    const box = document.createDocumentFragment();
    let list = null, listType = null, paragraph = null;
    String(text || '').split(/\r?\n/).forEach(raw => {
      const line = raw.trimEnd();
      const bullet = line.match(/^\s*[-*•]\s+(.*)$/);
      const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);
      if (bullet || numbered) {
        const type = bullet ? 'ul' : 'ol';
        if (!list || listType !== type) {
          list = document.createElement(type);
          listType = type;
          box.appendChild(list);
        }
        const li = document.createElement('li');
        inline(li, (bullet || numbered)[1]);
        list.appendChild(li);
        paragraph = null;
        return;
      }
      list = null;
      if (!line.trim()) { paragraph = null; return; }   // línea vacía = párrafo nuevo
      if (!paragraph) {
        paragraph = document.createElement('p');
        box.appendChild(paragraph);
      } else {
        paragraph.appendChild(document.createElement('br'));
      }
      inline(paragraph, line);
    });
    return box;
  }

  // ---------- Mensajes ----------
  function scrollToEnd() {
    messages.scrollTop = messages.scrollHeight;
  }

  function addUser(text) {
    const div = document.createElement('div');
    div.className = 'bk-msg bk-msg--user';
    div.textContent = text;   // la persona: texto tal cual (white-space: pre-wrap respeta los saltos)
    messages.appendChild(div);
    scrollToEnd();
  }

  function addBakey(text, isError) {
    const div = document.createElement('div');
    div.className = 'bk-msg bk-msg--bakey' + (isError ? ' bk-msg--error' : '');
    div.appendChild(formatted(text));
    messages.appendChild(div);
    scrollToEnd();
  }

  // Tarjeta de una acción que propuso Bakey (paso 5.4): nada cambia hasta tocar Confirmar
  const STATE_TEXT = {
    CONFIRMADA: 'Listo, se hizo.',
    CANCELADA: 'Cancelada: no se hizo ningún cambio.',
    VENCIDA: 'Venció: pedíselo de nuevo a Bakey.',
    FALLO: 'No se pudo hacer.'
  };

  function addCard(card) {
    const box = document.createElement('div');
    box.className = 'bk-card';
    box.dataset.state = card.state || 'PENDIENTE';

    const head = document.createElement('div');
    head.className = 'bk-card__head';
    head.innerHTML = ICON.action;   // ícono fijo
    const title = document.createElement('span');
    title.textContent = card.title;
    head.appendChild(title);

    const lines = document.createElement('ul');
    lines.className = 'bk-card__lines';
    (card.lines || []).forEach(l => {
      const li = document.createElement('li');
      li.textContent = l;
      lines.appendChild(li);
    });

    const foot = document.createElement('div');
    foot.className = 'bk-card__foot';
    box.append(head, lines, foot);
    renderCardFoot(box, foot, card);
    messages.appendChild(box);
    scrollToEnd();
  }

  function renderCardFoot(box, foot, card) {
    foot.replaceChildren();
    const state = card.state || 'PENDIENTE';
    box.dataset.state = state;
    if (state === 'PENDIENTE') {
      const expires = document.createElement('p');
      expires.className = 'bk-card__expires';
      expires.textContent = `Revisá los datos. Vence a las ${card.expiresAt}.`;
      const buttons = document.createElement('div');
      buttons.className = 'bk-card__buttons';
      // Clases btn-pink / btn-grey: baker.html oculta btn-red, btn-edit y btn-delete
      const ok = document.createElement('button');
      ok.type = 'button';
      ok.className = 'btn btn-pink';
      ok.textContent = 'Confirmar';
      const cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'btn btn-grey';
      cancel.textContent = 'Cancelar';
      ok.addEventListener('click', () => decide(card, 'confirm', box, foot, [ok, cancel]));
      cancel.addEventListener('click', () => decide(card, 'cancel', box, foot, [ok, cancel]));
      buttons.append(ok, cancel);
      foot.append(expires, buttons);
    } else {
      const result = document.createElement('p');
      result.className = 'bk-card__result';
      result.textContent = card.result || STATE_TEXT[state] || '';
      foot.appendChild(result);
    }
  }

  async function decide(card, what, box, foot, buttons) {
    buttons.forEach(b => { b.disabled = true; });
    try {
      const res = await fetch(`/api/bakey/actions/${encodeURIComponent(card.id)}/${what}`, { method: 'POST' });
      const body = await readJson(res);
      let state;
      if (what === 'cancel') state = res.ok ? 'CANCELADA' : 'FALLO';
      else if (res.ok) state = 'CONFIRMADA';
      else if (res.status === 410) state = 'VENCIDA';
      else state = 'FALLO';
      renderCardFoot(box, foot, { ...card, state, result: body && body.message });
      // Avisa al resto del panel que algo cambió (por ejemplo, los recordatorios se recargan)
      if (what === 'confirm' && res.ok) document.dispatchEvent(new CustomEvent('bakey:action-done'));
    } catch (e) {
      buttons.forEach(b => { b.disabled = false; });
      addBakey('No me pude conectar. Revisá la conexión y probá de nuevo.', true);
    }
  }

  // ---------- Bienvenida ----------
  function showWelcome() {
    const div = document.createElement('div');
    div.className = 'bk-msg bk-msg--bakey bk-welcome';
    const hello = document.createElement('p');
    hello.textContent = configured
      ? '¡Hola! Soy Bakey, el asistente de la pastelería. Puedo contarte qué hay que entregar, cómo está el stock o ayudarte a cargar cosas (siempre te pido confirmación). ¿En qué te ayudo?'
      : 'Todavía no estoy configurado. Pedile al administrador que cargue la clave de Gemini.';
    div.appendChild(hello);
    if (configured) {
      const chips = document.createElement('div');
      chips.className = 'bk-chips';
      SUGGESTIONS.forEach(s => {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'bk-chip';
        chip.textContent = s;
        chip.addEventListener('click', () => send(SUGGESTION_MESSAGES[s] || s));
        chips.appendChild(chip);
      });
      div.appendChild(chips);
    }
    messages.appendChild(div);
  }

  // ---------- Historial (en el servidor) ----------
  async function loadHistory() {
    try {
      const res = await fetch('/api/bakey/history');
      const body = await readJson(res);
      if (!res.ok || !body) throw new Error('sin historial');
      configured = body.configured !== false;
      setStatus(configured ? 'Disponible' : 'No configurado');
      messages.replaceChildren();
      const list = body.messages || [];
      if (!list.length) showWelcome();
      list.forEach(m => {
        if (m.role === 'user') addUser(m.text);
        else {
          addBakey(m.text);
          (m.cards || []).forEach(addCard);
        }
      });
      input.disabled = !configured;
      sendBtn.disabled = !configured;
    } catch (e) {
      messages.replaceChildren();
      addBakey('No pude cargar la conversación. Recargá la página y probá de nuevo.', true);
    }
  }

  function setStatus(text) {
    statusEl.textContent = text;
    statusEl.classList.toggle('is-off', text === 'No configurado');
  }

  // ---------- Enviar ----------
  async function send(text) {
    const message = String(text || '').trim();
    if (!message || busy || !configured) return;
    // Si todavía estaba la bienvenida, se saca (empieza la charla)
    messages.querySelector('.bk-welcome')?.remove();
    addUser(message);
    input.value = '';
    autosize();
    busy = true;
    sendBtn.disabled = true;
    typing.hidden = false;
    setStatus('Escribiendo…');
    scrollToEnd();
    try {
      const res = await fetch('/api/bakey/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message })
      });
      const body = await readJson(res);
      if (!body) {
        addBakey('Se cerró tu sesión. Recargá la página para volver a entrar.', true);
      } else {
        addBakey(body.reply, !res.ok);
        (body.actions || []).forEach(a => addCard({ ...a, state: 'PENDIENTE' }));
      }
    } catch (e) {
      addBakey('No me pude conectar. Revisá la conexión y probá de nuevo.', true);
    } finally {
      busy = false;
      sendBtn.disabled = false;
      typing.hidden = true;
      setStatus('Disponible');
      input.focus();
      document.dispatchEvent(new CustomEvent('bakey:message-done'));
    }
  }

  // Si la sesión venció, fetch sigue la redirección al login y llega HTML: eso no es JSON
  async function readJson(res) {
    const type = res.headers.get('content-type') || '';
    if (!type.includes('application/json')) return null;
    try { return await res.json(); } catch (e) { return null; }
  }

  // ---------- Abrir y cerrar ----------
  function isOpen() { return !panel.hidden; }

  function open() {
    panel.hidden = false;
    // Un cuadro después, para que el navegador anime de "cerrado" a "abierto"
    requestAnimationFrame(() => panel.classList.add('is-open'));
    fab.setAttribute('aria-expanded', 'true');
    if (window.matchMedia('(max-width: 47.99rem)').matches) fab.classList.add('is-hidden');
    loadHistory().finally(() => { if (!input.disabled) input.focus(); });
  }

  function close() {
    panel.classList.remove('is-open');
    fab.setAttribute('aria-expanded', 'false');
    fab.classList.remove('is-hidden');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    setTimeout(() => { if (!panel.classList.contains('is-open')) panel.hidden = true; }, reduced ? 0 : 320);
    fab.focus();
  }

  fab.addEventListener('click', () => (isOpen() ? close() : open()));
  panel.querySelector('#bk-close').addEventListener('click', close);

  panel.querySelector('#bk-reset').addEventListener('click', () => {
    window.showConfirmModal('Nueva conversación', '¿Borrar la conversación con Bakey y empezar de nuevo?', 'Borrar', 'btn-pink', async () => {
      try {
        await fetch('/api/bakey/reset', { method: 'POST' });
      } finally {
        messages.replaceChildren();
        showWelcome();
        input.focus();
      }
    });
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    send(input.value);
  });

  // Enter envía; Shift+Enter hace salto de línea (y no se envía mientras se escribe con acentos/IME)
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      send(input.value);
    }
  });

  // La caja crece con el texto (hasta 140 px; después aparece la barra)
  function autosize() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 140) + 'px';
  }
  input.addEventListener('input', autosize);

  // ---------- Tarjeta "Uso de Bakey hoy" (paso 5.6, solo existe en el Resumen del admin) ----------
  const usageCard = document.getElementById('bakey-usage');
  async function loadUsage() {
    if (!usageCard) return;
    try {
      const res = await fetch('/api/bakey/usage');
      const u = await readJson(res);
      if (!res.ok || !u) return;
      const set = (id, value) => { document.getElementById(id).textContent = value; };
      set('bk-usage-messages', u.mensajes);
      set('bk-usage-proposed', u.accionesPropuestas);
      set('bk-usage-confirmed', u.accionesConfirmadas);
      set('bk-usage-errors', u.errores);
      document.getElementById('bk-usage-sub').textContent = !u.configured
        ? 'Bakey no está configurado: falta la clave de Gemini.'
        : (u.erroresPorCupo > 0
            ? `Hoy Gemini no respondió ${u.erroresPorCupo} ${u.erroresPorCupo === 1 ? 'vez' : 'veces'} por falta de cupo o mucha demanda (plan gratis): se resuelve esperando un minuto.`
            : 'Cuántas veces se usó el asistente hoy (se reinicia cada día).');
      usageCard.hidden = false;
    } catch (e) { /* sin datos: la tarjeta queda oculta */ }
  }
  loadUsage();
  // Se actualiza al volver al Resumen y después de cada mensaje o acción
  document.querySelector('.tab-btn[data-tab="resumen"]')?.addEventListener('click', loadUsage);
  document.addEventListener('bakey:action-done', loadUsage);
  document.addEventListener('bakey:message-done', loadUsage);

  // Escape cierra el chat, salvo que haya una ventana del panel abierta encima (esa se cierra primero)
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape' || !isOpen()) return;
    const modalOpen = [...document.querySelectorAll('.modal-overlay')].some(m => !m.hidden);
    if (!modalOpen) close();
  });
});
