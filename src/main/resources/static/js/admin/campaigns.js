// /js/admin/campaigns.js - Clientes → Fidelización → Campañas de promociones por email
// Lista de campañas con su estado y métricas, y el editor con vista previa real del email.
// Usa los modales de main.js (showErrorAlert y showConfirmModal). Nada de onclick en el HTML (CSP).
document.addEventListener('DOMContentLoaded', () => {
  const block = document.getElementById('campaigns-block');
  if (!block) return;

  const tbody = document.getElementById('campaigns-tbody');
  const modal = document.getElementById('modal-campaign');
  const form = document.getElementById('campaign-form');
  const $ = (id) => document.getElementById(id);
  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

  const STATUS = {
    DRAFT: ['Borrador', 'tag-badge', false],
    SCHEDULED: ['Programada', 'status-badge is-small status-agendado', true],
    SENDING: ['Enviándose', 'status-badge is-small status-progress', true],
    SENT: ['Enviada', 'status-badge is-small status-sent', true],
    CANCELLED: ['Cancelada', 'status-badge is-small status-cancelled', true]
  };
  const AUDIENCE = {
    ALL_CONSENTED: 'Todos los que aceptan promociones',
    TAG: 'Etiqueta',
    BIRTHDAY_THIS_MONTH: 'Cumplen años este mes',
    INACTIVE_90_DAYS: 'Sin comprar hace +90 días'
  };

  let campaigns = [];
  let coupons = [];
  let imageUrl = null;          // foto elegida de la galería (o null)
  let audienceCount = 0;
  let readOnly = false;
  let pollTimer = null;
  let previewTimer = null;

  // Pedido al servidor que devuelve { ok, status, data }
  async function call(url, method = 'GET', body) {
    try {
      const res = await fetch(url, {
        method,
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined
      });
      const data = await res.json().catch(() => ({}));
      return { ok: res.ok, status: res.status, data };
    } catch (e) {
      return { ok: false, status: 0, data: { message: 'No se pudo conectar con el servidor.' } };
    }
  }

  // "2026-10-09T13:05:00" → "09/10/2026 13:05"
  function fmt(dt) {
    if (!dt) return '';
    const [d, t] = dt.split('T');
    return `${d.split('-').reverse().join('/')} ${(t || '').slice(0, 5)}`;
  }

  // ================= LISTA =================
  async function loadCampaigns() {
    if (!tbody.querySelector('tr:not(.state-row)')) window.DreamUI?.tableLoading(tbody, 6, 'Cargando campañas…');
    const r = await call('/api/campaigns');
    if (!r.ok) {
      tbody.innerHTML = '<tr><td colspan="6" class="cell-empty">No se pudieron cargar las campañas.</td></tr>';
      return;
    }
    campaigns = r.data;
    renderCampaigns();
    // Mientras alguna se esté enviando, la lista se actualiza sola cada 10 segundos
    clearTimeout(pollTimer);
    if (campaigns.some(c => c.status === 'SENDING')) pollTimer = setTimeout(loadCampaigns, 10000);
  }

  function renderCampaigns() {
    if (campaigns.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="cell-empty">Todavía no hay campañas. Creá la primera con "Nueva campaña".</td></tr>';
      return;
    }
    tbody.innerHTML = campaigns.map(c => {
      const [label, cls] = STATUS[c.status] || [c.status, 'tag-badge'];
      const note = c.statusNote ? `<span class="cell-soft">${esc(c.statusNote)}</span>` : '';
      const who = `<span>${AUDIENCE[c.audience]}${c.audience === 'TAG' && c.tagName ? `: ${esc(c.tagName)}` : ''}</span>`
        + (c.couponCode ? `<span class="cell-soft">Cupón ${esc(c.couponCode)}</span>` : '');
      let progress = '<span class="cell-soft">—</span>';
      if (c.status === 'SENDING' || c.status === 'SENT' || (c.status === 'CANCELLED' && c.sentCount + c.failedCount > 0)) {
        const done = c.sentCount + c.failedCount;
        const pct = c.totalRecipients ? Math.min(100, Math.round(done * 100 / c.totalRecipients)) : 0;
        progress = `<span class="campaign-progress">
            <span>${c.sentCount} de ${c.totalRecipients} enviados${c.failedCount ? ` · ${c.failedCount} fallidos` : ''}</span>
            <span class="campaign-bar" role="img" aria-label="${pct}% enviado"><span style="width:${pct}%"></span></span>
          </span>`;
      }
      const when = c.status === 'SCHEDULED' ? `Programada: ${fmt(c.scheduledAt)}`
        : c.sentAt ? `Enviada: ${fmt(c.sentAt)}` : `Creada: ${fmt(c.createdAt)}`;
      const actions = [];
      if (c.status === 'DRAFT' || c.status === 'SCHEDULED') actions.push(`<button type="button" class="btn btn-grey btn-sm" data-campaign="edit">Editar</button>`);
      else actions.push(`<button type="button" class="btn btn-grey btn-sm" data-campaign="view">Ver</button>`);
      if (c.status === 'SCHEDULED' || c.status === 'SENDING') actions.push(`<button type="button" class="btn btn-red btn-sm" data-campaign="cancel">Cancelar</button>`);
      if (c.status === 'DRAFT' || c.status === 'CANCELLED') actions.push(`<button type="button" class="btn btn-red btn-sm" data-campaign="delete">Borrar</button>`);
      return `<tr data-id="${c.id}">
        <td class="cell-title"><span class="campaign-lines"><strong>${esc(c.name)}</strong><span class="cell-soft">${esc(c.subject)}</span></span></td>
        <td data-label="Estado"><span class="status-stack"><span class="${cls}">${label}</span>${note}</span></td>
        <td data-label="A quién"><span class="campaign-lines">${who}</span></td>
        <td data-label="Progreso">${progress}</td>
        <td data-label="Fecha" class="cell-soft">${when}</td>
        <td class="cell-actions"><div class="campaign-actions-cell">${actions.join('')}</div></td>
      </tr>`;
    }).join('');
  }

  tbody.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-campaign]');
    if (!btn) return;
    const id = btn.closest('tr').dataset.id;
    const c = campaigns.find(x => String(x.id) === id);
    const action = btn.dataset.campaign;
    if (action === 'edit') openEditor(id, false);
    else if (action === 'view') openEditor(id, true);
    else if (action === 'cancel') {
      window.showConfirmModal('Cancelar campaña',
        c.status === 'SENDING' ? `"${c.name}" deja de enviarse. A los que ya les llegó, ya les llegó.` : `"${c.name}" no se va a enviar.`,
        'Cancelar campaña', 'btn-red', async () => {
          const r = await call(`/api/campaigns/${id}/cancel`, 'POST');
          if (!r.ok) window.showErrorAlert(r.data.message || 'No se pudo cancelar.');
          loadCampaigns();
        });
    } else if (action === 'delete') {
      window.showConfirmModal('Borrar campaña', `"${c.name}" se borra de la lista. No se puede deshacer.`, 'Borrar', 'btn-red', async () => {
        const r = await call(`/api/campaigns/${id}`, 'DELETE');
        if (!r.ok) window.showErrorAlert(r.data.message || 'No se pudo borrar.');
        loadCampaigns();
      });
    }
  });

  // ================= MÁXIMO DE EMAILS POR DÍA =================
  async function loadDailyLimit() {
    const r = await call('/api/loyalty/settings');
    if (r.ok) $('campaign-daily-limit').value = r.data.dailyLimit;
  }

  $('btn-save-daily-limit').addEventListener('click', async () => {
    const value = Number($('campaign-daily-limit').value);
    if (!Number.isInteger(value) || value < 1 || value > 2000) {
      window.showErrorAlert('El máximo de emails por día tiene que ser un número entre 1 y 2000.');
      return;
    }
    const r = await call('/api/loyalty/settings', 'PUT', { dailyLimit: value });
    if (r.ok) window.showErrorAlert(`Listo: se mandan como máximo ${r.data.dailyLimit} emails por día.`, 'Guardado');
    else window.showErrorAlert(r.data.message || 'No se pudo guardar.');
  });

  // ================= EDITOR =================
  // Datos de los selects: etiquetas y cupones que todavía sirven
  async function loadOptions() {
    const [tags, cps] = await Promise.all([call('/api/tags'), call('/api/coupons')]);
    const tagSelect = $('campaign-tag');
    tagSelect.innerHTML = (tags.ok ? tags.data : []).map(t => `<option value="${t.id}">${esc(t.name)}</option>`).join('');
    const today = window.DreamFormat ? window.DreamFormat.todayISO() : new Date().toISOString().slice(0, 10);   // fecha de acá, no la de Greenwich
    coupons = (cps.ok ? cps.data : []).filter(c => c.active && (c.unlimited || (c.usageLimit || 0) > 0)
      && (!c.expirationDate || c.expirationDate >= today));
    $('campaign-coupon').innerHTML = '<option value="">Sin cupón</option>' + coupons.map(c =>
      `<option value="${c.id}">${esc(c.code)}${c.type === 'DESCUENTO' && c.discountPercentage ? ` (${c.discountPercentage}%)` : ''}</option>`).join('');
  }

  function resetForm() {
    form.reset();
    $('campaign-id').value = '';
    imageUrl = null;
    showImage();
    $('campaign-gallery').hidden = true;
    $('campaign-schedule').hidden = true;
    $('campaign-tag-group').hidden = true;
    $('campaign-note').hidden = true;
    $('campaign-preview-error').hidden = true;
    $('campaign-preview-frame').srcdoc = '';
  }

  function setReadOnly(value) {
    readOnly = value;
    form.querySelectorAll('input, textarea, select, button').forEach(el => { el.disabled = value; });
    $('btn-campaign-save').closest('.campaign-actions').hidden = value;
    $('btn-campaign-image').closest('.campaign-image-actions').hidden = value;
  }

  async function openEditor(id, viewOnly) {
    resetForm();
    await loadOptions();
    $('campaign-modal-title').textContent = !id ? 'Nueva campaña' : (viewOnly ? 'Campaña enviada' : 'Editar campaña');
    if (id) {
      const r = await call(`/api/campaigns/${id}`);
      if (!r.ok) { window.showErrorAlert('No se pudo abrir la campaña.'); return; }
      const c = r.data;
      $('campaign-id').value = c.id;
      $('campaign-name').value = c.name || '';
      $('campaign-subject').value = c.subject || '';
      $('campaign-title').value = c.title || '';
      $('campaign-text').value = c.bodyText || '';
      $('campaign-button-text').value = c.buttonText || '';
      $('campaign-button-url').value = c.buttonUrl || '';
      $('campaign-audience').value = c.audience || 'ALL_CONSENTED';
      if (c.tagId) $('campaign-tag').value = c.tagId;
      // Un cupón que ya no sirve no aparece en la lista: se agrega para que se vea cuál era
      if (c.couponId && ![...$('campaign-coupon').options].some(o => o.value === String(c.couponId))) {
        $('campaign-coupon').insertAdjacentHTML('beforeend', `<option value="${c.couponId}">Cupón que ya no sirve</option>`);
      }
      $('campaign-coupon').value = c.couponId || '';
      if (c.scheduledAt) $('campaign-when').value = c.scheduledAt.slice(0, 16);
      imageUrl = c.imageUrl || null;
      showImage();
      if (c.statusNote) {
        $('campaign-note').textContent = c.statusNote;
        $('campaign-note').hidden = false;
      }
    }
    setReadOnly(viewOnly);
    $('campaign-tag-group').hidden = $('campaign-audience').value !== 'TAG';
    modal.hidden = false;
    $('campaign-name').focus();
    await updateCount();
    if (viewOnly && id) {
      const p = await call(`/api/campaigns/${id}/preview`);
      if (p.ok) $('campaign-preview-frame').srcdoc = p.data.html;
    } else {
      refreshPreview();
    }
  }

  $('btn-new-campaign').addEventListener('click', () => openEditor(null, false));

  // Lo que está escrito en el formulario (solo texto: el servidor arma el HTML y lo escapa)
  function collect() {
    const audience = $('campaign-audience').value;
    return {
      name: $('campaign-name').value,
      subject: $('campaign-subject').value,
      title: $('campaign-title').value,
      bodyText: $('campaign-text').value,
      imageUrl: imageUrl,
      buttonText: $('campaign-button-text').value,
      buttonUrl: $('campaign-button-url').value,
      audience: audience,
      tagId: audience === 'TAG' && $('campaign-tag').value ? Number($('campaign-tag').value) : null,
      couponId: $('campaign-coupon').value ? Number($('campaign-coupon').value) : null
    };
  }

  // ----- Vista previa real (el mismo HTML que se manda), 600 ms después de dejar de escribir -----
  function refreshPreview() {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(async () => {
      const r = await call('/api/campaigns/preview', 'POST', collect());
      const errorBox = $('campaign-preview-error');
      if (r.ok) {
        errorBox.hidden = true;
        $('campaign-preview-frame').srcdoc = r.data.html;
      } else {
        // Mientras falte algo (por ejemplo, el asunto) se muestra qué falta
        errorBox.textContent = r.data.message || 'No se pudo armar la vista previa.';
        errorBox.hidden = false;
      }
    }, 600);
  }
  form.addEventListener('input', () => { if (!readOnly) refreshPreview(); });

  // ----- Audiencia: cuántos clientes la reciben -----
  async function updateCount() {
    const audience = $('campaign-audience').value;
    const tagId = $('campaign-tag').value;
    const q = `audience=${audience}` + (audience === 'TAG' && tagId ? `&tagId=${tagId}` : '');
    const r = await call(`/api/campaigns/audience-count?${q}`);
    audienceCount = r.ok ? r.data.count : 0;
    $('campaign-count').textContent = audienceCount === 1 ? 'Le llega a 1 cliente.'
      : `Le llega a ${audienceCount} clientes.` + (audienceCount === 0 ? ' Solo cuentan los que aceptaron promociones y tienen email.' : '');
    updateCouponHint();
  }

  $('campaign-audience').addEventListener('change', () => {
    $('campaign-tag-group').hidden = $('campaign-audience').value !== 'TAG';
    updateCount();
  });
  $('campaign-tag').addEventListener('change', updateCount);

  // ----- Cupón: aviso si tiene menos usos que destinatarios -----
  function updateCouponHint() {
    const hint = $('campaign-coupon-hint');
    const c = coupons.find(x => String(x.id) === $('campaign-coupon').value);
    if (!c) { hint.textContent = 'Se muestra en el email, en un recuadro, para usar en el carrito.'; return; }
    const parts = [];
    if (!c.unlimited && c.usageLimit < audienceCount) {
      parts.push(`Ojo: este cupón tiene ${c.usageLimit} ${c.usageLimit === 1 ? 'uso' : 'usos'} y la campaña le llega a ${audienceCount} clientes.`);
    }
    if (c.expirationDate) parts.push(`Vence el ${c.expirationDate.split('-').reverse().join('/')}.`);
    hint.textContent = parts.join(' ') || 'Se muestra en el email, en un recuadro, para usar en el carrito.';
  }
  $('campaign-coupon').addEventListener('change', updateCouponHint);

  // ----- Foto de la galería -----
  function showImage() {
    $('campaign-image-preview').innerHTML = imageUrl ? `<img src="${esc(imageUrl)}" alt="Foto elegida">` : '<span class="cell-soft">Sin foto</span>';
    $('btn-campaign-image-remove').hidden = !imageUrl;
  }

  $('btn-campaign-image').addEventListener('click', async () => {
    const grid = $('campaign-gallery');
    if (!grid.hidden) { grid.hidden = true; return; }
    const r = await call('/api/gallery');
    const images = r.ok ? r.data : [];
    grid.innerHTML = images.length === 0
      ? '<p class="cell-soft">La galería está vacía. Subí fotos en la pestaña Galería.</p>'
      : images.map(img => `<button type="button" class="gallery-item" data-url="${esc(img.url)}" aria-label="Usar esta foto"><img src="${esc(img.url)}" alt="" loading="lazy"></button>`).join('');
    grid.hidden = false;
  });

  $('campaign-gallery').addEventListener('click', (e) => {
    const item = e.target.closest('button[data-url]');
    if (!item) return;
    imageUrl = item.dataset.url;
    $('campaign-gallery').hidden = true;
    showImage();
    refreshPreview();
  });

  $('btn-campaign-image-remove').addEventListener('click', () => {
    imageUrl = null;
    showImage();
    refreshPreview();
  });

  // ----- Guardar (todas las acciones guardan primero) -----
  async function save() {
    const id = $('campaign-id').value;
    const r = await call(id ? `/api/campaigns/${id}` : '/api/campaigns', id ? 'PUT' : 'POST', collect());
    if (!r.ok) {
      window.showErrorAlert(r.data.message || 'No se pudo guardar la campaña.');
      return null;
    }
    $('campaign-id').value = r.data.id;
    return r.data;
  }

  // Deshabilita un botón mientras trabaja y le cambia el texto
  async function busy(btn, text, task) {
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = text;
    try { await task(); } finally { btn.disabled = false; btn.textContent = original; }
  }

  $('btn-campaign-save').addEventListener('click', (e) => busy(e.currentTarget, 'Guardando…', async () => {
    const saved = await save();
    if (saved) {
      modal.hidden = true;
      loadCampaigns();
    }
  }));

  $('btn-campaign-test').addEventListener('click', (e) => busy(e.currentTarget, 'Enviando prueba…', async () => {
    const saved = await save();
    if (!saved) return;
    const r = await call(`/api/campaigns/${saved.id}/test`, 'POST');
    window.showErrorAlert(r.data.message || 'No se pudo enviar la prueba.', r.ok ? 'Prueba enviada' : 'No se pudo enviar');
    loadCampaigns();
  }));

  $('btn-campaign-schedule').addEventListener('click', () => {
    const box = $('campaign-schedule');
    box.hidden = !box.hidden;
    if (!box.hidden) $('campaign-when').focus();
  });

  $('btn-campaign-schedule-confirm').addEventListener('click', (e) => busy(e.currentTarget, 'Programando…', async () => {
    const when = $('campaign-when').value;
    if (!when) { window.showErrorAlert('Elegí la fecha y la hora de envío.'); return; }
    const saved = await save();
    if (!saved) return;
    const r = await call(`/api/campaigns/${saved.id}/schedule`, 'POST', { scheduledAt: when });
    if (!r.ok) { window.showErrorAlert(r.data.message || 'No se pudo programar.'); return; }
    modal.hidden = true;
    loadCampaigns();
    window.showErrorAlert(`La campaña se va a enviar el ${fmt(r.data.scheduledAt)} (puede demorar hasta 5 minutos).`, 'Campaña programada');
  }));

  $('btn-campaign-send').addEventListener('click', (e) => busy(e.currentTarget, 'Preparando…', async () => {
    const saved = await save();
    if (!saved) return;
    await updateCount();
    if (audienceCount === 0) {
      window.showErrorAlert('Esta audiencia no tiene clientes que acepten promociones (o no tienen email).');
      return;
    }
    const minutes = Math.max(1, Math.round(audienceCount * 2 / 60));
    window.showConfirmModal('Enviar campaña',
      `"${saved.subject}" le va a llegar a ${audienceCount} ${audienceCount === 1 ? 'cliente' : 'clientes'}. `
      + `Sale de a un email cada 2 segundos (unos ${minutes} min). ¿La enviamos?`,
      'Enviar ahora', 'btn-pink', async () => {
        const r = await call(`/api/campaigns/${saved.id}/send`, 'POST');
        if (!r.ok) { window.showErrorAlert(r.data.message || 'No se pudo enviar.'); return; }
        modal.hidden = true;
        loadCampaigns();
        window.showErrorAlert('La campaña se está enviando. Podés seguir usando el panel: el progreso se actualiza solo.', 'Enviando');
      });
  }));

  // Se carga al abrir la pestaña Clientes
  document.addEventListener('tabChanged', (e) => {
    if (e.detail === 'clientes') {
      loadCampaigns();
      loadDailyLimit();
    } else {
      clearTimeout(pollTimer);
    }
  });
});
