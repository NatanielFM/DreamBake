// /js/admin/loyalty.js - Clientes → Fidelización: saludos de cumpleaños con cupón de regalo
// Usa los modales de main.js (showErrorAlert y showConfirmModal) para los avisos y confirmaciones.
document.addEventListener('DOMContentLoaded', () => {
  const block = document.getElementById('loyalty-block');
  if (!block) return;

  const enabledInput = document.getElementById('loyalty-enabled');
  const percentageInput = document.getElementById('loyalty-percentage');
  const btnSave = document.getElementById('btn-loyalty-save');
  const btnRun = document.getElementById('btn-loyalty-run');
  const hint = document.getElementById('loyalty-hint');
  const tbody = document.getElementById('loyalty-tbody');

  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

  // Hace un pedido al servidor y devuelve { ok, status, data }
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

  // ================= CONFIGURACIÓN =================
  function showHint(settings) {
    const parts = [];
    if (!settings.enabled) parts.push('Los saludos automáticos están pausados: no se manda ninguno solo (el botón "Enviar saludo ahora" sigue funcionando).');
    if (!settings.emailConfigured) parts.push('El correo todavía no está configurado en config/application.properties: no se pueden mandar emails.');
    hint.textContent = parts.join(' ');
  }

  async function loadSettings() {
    const r = await call('/api/loyalty/settings');
    if (!r.ok) return;
    enabledInput.checked = r.data.enabled;
    percentageInput.value = r.data.percentage;
    showHint(r.data);
  }

  // El interruptor se guarda en cuanto se mueve
  enabledInput.addEventListener('change', async () => {
    const wanted = enabledInput.checked;
    const r = await call('/api/loyalty/settings', 'PUT', { enabled: wanted });
    if (!r.ok) {
      enabledInput.checked = !wanted;
      window.showErrorAlert(r.data.message || 'No se pudo guardar el cambio.');
      return;
    }
    showHint(r.data);
  });

  btnSave.addEventListener('click', async () => {
    const value = Number(percentageInput.value);
    if (!Number.isInteger(value) || value < 1 || value > 100) {
      window.showErrorAlert('El descuento tiene que ser un número entero entre 1 y 100.');
      percentageInput.focus();
      return;
    }
    const r = await call('/api/loyalty/settings', 'PUT', { percentage: value });
    if (r.ok) window.showErrorAlert(`Listo: los próximos cupones de cumpleaños van a tener ${r.data.percentage}% de descuento. Los que ya se mandaron no cambian.`, 'Guardado');
    else window.showErrorAlert(r.data.message || 'No se pudo guardar el descuento.');
  });

  // "Revisar cumpleaños de hoy": lo mismo que hace la tarea de las 9:00, sin esperar
  btnRun.addEventListener('click', async () => {
    btnRun.disabled = true;
    const original = btnRun.textContent;
    btnRun.textContent = 'Revisando…';
    const r = await call('/api/loyalty/birthdays/run', 'POST');
    btnRun.disabled = false;
    btnRun.textContent = original;
    window.showErrorAlert(r.data.message || 'No se pudo revisar.', r.ok ? 'Cumpleaños de hoy' : 'No se pudo revisar');
    loadBirthdays();
  });

  // ================= PRÓXIMOS CUMPLEAÑOS =================
  function whenText(item) {
    const [, m, d] = item.date.split('-').map(Number);
    const date = `${d} de ${MONTHS[m - 1]}`;
    // Todo en un solo <span> para que en el celular (tarjeta) quede en el mismo renglón
    if (item.daysLeft === 0) return `<span><strong>Hoy</strong><span class="cell-soft"> · ${date}</span></span>`;
    if (item.daysLeft === 1) return `<span><strong>Mañana</strong><span class="cell-soft"> · ${date}</span></span>`;
    return `<span><strong>${date}</strong><span class="cell-soft"> · en ${item.daysLeft} días</span></span>`;
  }

  async function loadBirthdays() {
    const r = await call('/api/loyalty/birthdays');
    if (!r.ok) {
      tbody.innerHTML = '<tr><td colspan="4" class="cell-empty">No se pudieron cargar los cumpleaños.</td></tr>';
      return;
    }
    if (r.data.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4" class="cell-empty">Ningún cliente cumple años en los próximos 30 días. Cargá la fecha de cumpleaños en la ficha del cliente.</td></tr>';
      return;
    }
    tbody.innerHTML = r.data.map(b => {
      const canEmail = b.consent && b.hasEmail;
      const emailWhy = !b.consent ? 'No aceptó recibir promociones' : 'No tiene email cargado';
      const status = b.consent
        ? `<span class="status-badge is-small status-sent">Recibe promociones</span>`
        : `<span class="cell-soft">No acepta promociones</span>`;
      const greeted = b.greetedThisYear ? `<span class="cell-soft">Saludo de este año enviado</span>` : '';
      return `<tr data-id="${b.customerId}">
        <td class="cell-title cell-strong">${esc(b.name)}</td>
        <td data-label="Cumpleaños">${whenText(b)}</td>
        <td data-label="Emails"><span class="status-stack">${status}${greeted}</span></td>
        <td class="cell-actions"><div class="loyalty-actions">
          <button type="button" class="btn btn-grey btn-sm" data-loyalty="send" ${canEmail ? '' : `disabled title="${emailWhy}"`}>Enviar saludo ahora</button>
          <button type="button" class="btn btn-grey btn-sm" data-loyalty="whatsapp" ${b.hasPhone ? '' : 'disabled title="El teléfono no sirve para WhatsApp"'}>Saludar por WhatsApp</button>
        </div></td>
      </tr>`;
    }).join('');
  }

  // Un solo "oyente" para los botones de todas las filas
  tbody.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-loyalty]');
    if (!btn || btn.disabled) return;
    const id = btn.closest('tr').dataset.id;
    const name = btn.closest('tr').querySelector('.cell-title').textContent;
    if (btn.dataset.loyalty === 'send') sendGreeting(id, name, btn, false);
    else openWhatsapp(id, btn);
  });

  // "Enviar saludo ahora"
  async function sendGreeting(id, name, btn, force) {
    btn.disabled = true;
    const original = btn.textContent;
    btn.textContent = 'Enviando…';
    const r = await call(`/api/loyalty/birthdays/${id}/send?force=${force}`, 'POST');
    btn.disabled = false;
    btn.textContent = original;
    if (r.status === 409) {
      // Ya se le mandó este año: se pregunta antes de repetirlo
      window.showConfirmModal('Saludo ya enviado',
        `A ${name} ya se le mandó el saludo de cumpleaños este año. ¿Querés mandárselo de nuevo?`,
        'Enviar igual', 'btn-pink', () => sendGreeting(id, name, btn, true));
      return;
    }
    if (r.ok) {
      window.showErrorAlert(`${r.data.message} Su cupón de regalo es ${r.data.code}.`, 'Saludo enviado');
    } else {
      window.showErrorAlert(r.data.message || 'No se pudo enviar el saludo.', 'No se pudo enviar');
    }
    loadBirthdays();
  }

  // "Saludar por WhatsApp": el servidor arma el mensaje con el cupón y el link wa.me
  async function openWhatsapp(id, btn) {
    // La ventana se abre YA (en el clic): si se abriera después de esperar al servidor,
    // el navegador la bloquearía como ventana emergente.
    const win = window.open('', '_blank');
    btn.disabled = true;
    const r = await call(`/api/loyalty/birthdays/${id}/whatsapp`, 'POST');
    btn.disabled = false;
    if (!r.ok) {
      if (win) win.close();
      window.showErrorAlert(r.data.message || 'No se pudo preparar el mensaje.');
      return;
    }
    if (win) {
      win.opener = null;
      win.location.href = r.data.url;
    } else {
      window.showErrorAlert('El navegador bloqueó la ventana. Permití las ventanas emergentes de este sitio y probá de nuevo.');
    }
  }

  // Se carga al abrir la pestaña Clientes
  document.addEventListener('tabChanged', (e) => {
    if (e.detail === 'clientes') {
      loadSettings();
      loadBirthdays();
    }
  });
});
