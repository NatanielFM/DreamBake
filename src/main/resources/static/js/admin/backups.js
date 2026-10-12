/**
 * js/admin/backups.js — Pestaña "Backups" (paso 4.3). Solo en admin.html.
 *
 * - Muestra el estado (última copia, espacio en disco, cuántas hay) y la lista: GET /api/backups.
 * - "Hacer backup ahora": POST /api/backups/run.
 * - "Descargar": avisa que el archivo tiene datos personales y lo baja con GET /api/backups/{id}/download
 *   (el servidor deja la descarga en la auditoría).
 * - "Restaurar": ventana #modal-restore. El botón se habilita recién con el tilde "Entiendo", la palabra
 *   RESTAURAR y la contraseña. El servidor vuelve a comprobar todo (RestoreService) y, si sale bien,
 *   cierra la sesión: hay que volver a ingresar.
 *
 * Todo el texto que viene del servidor se pone con textContent (nunca como HTML).
 */

document.addEventListener('DOMContentLoaded', () => {
  const section = document.getElementById('backups');
  if (!section) return;

  const CONFIRM_WORD = 'RESTAURAR';

  const tbody = document.getElementById('backup-tbody');
  const alertBox = document.getElementById('backup-panel-alert');
  const alertText = document.getElementById('backup-panel-alert-text');
  const lastEl = document.getElementById('backup-last');
  const lastSub = document.getElementById('backup-last-sub');
  const usedEl = document.getElementById('backup-used');
  const freeEl = document.getElementById('backup-free');
  const countEl = document.getElementById('backup-count');
  const btnNow = document.getElementById('btn-backup-now');

  const modal = document.getElementById('modal-restore');
  const form = document.getElementById('form-restore');
  const understood = document.getElementById('restore-understood');
  const confirmInput = document.getElementById('restore-confirm');
  const password = document.getElementById('restore-password');
  const submit = document.getElementById('restore-submit');
  const done = document.getElementById('restore-done');
  const errorEl = document.getElementById('restore-error');
  const progress = document.getElementById('restore-progress');
  const dateEl = document.getElementById('restore-date');
  const cancelBtn = form.querySelector('[data-close-modal]');

  let backups = [];
  let current = null;   // copia elegida para restaurar
  let busy = false;     // restaurando (no se puede tocar nada)

  // ===================== Formatos =====================

  // "2026-10-11T03:00:00" (hora de Argentina, sin zona) → "11/10/2026 03:00"
  function fmtDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d)) return iso;
    const date = d.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const time = d.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
    return date + ' ' + time;
  }

  // 314650 → "307,3 KB"
  function fmtSize(bytes) {
    if (bytes == null) return '—';
    const units = ['bytes', 'KB', 'MB', 'GB', 'TB'];
    let value = bytes;
    let i = 0;
    while (value >= 1024 && i < units.length - 1) { value /= 1024; i++; }
    return (i === 0 ? value : value.toLocaleString('es-AR', { maximumFractionDigits: 1 })) + ' ' + units[i];
  }

  // ===================== Cargar y dibujar =====================

  let backupsLoaded = false;
  async function load() {
    if (!backupsLoaded) window.DreamUI?.tableLoading(tbody, 5, 'Cargando copias de seguridad…');   // paso 6.8
    try {
      const res = await fetch('/api/backups');
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'No se pudo cargar el estado de las copias.');
      backupsLoaded = true;
      render(data);
    } catch (err) {
      backups = [];
      tbody.replaceChildren(emptyRow(err.message));
    }
  }

  function render(data) {
    backups = data.backups || [];

    // Aviso rojo: desactivadas / más de 48 h sin copia / el último intento falló
    const lines = [];
    if (data.warning) {
      lines.push(['strong', data.warning.title], ['span', data.warning.detail], ['span', data.warning.action]);
    } else if (data.lastAttempt && data.lastAttempt.status === 'FAILED') {
      lines.push(['strong', 'La última copia (' + fmtDate(data.lastAttempt.createdAt) + ') falló.'],
                 ['span', data.lastAttempt.error || ''],
                 ['span', 'Las copias anteriores siguen guardadas. Revisá el email con el aviso o probá "Hacer backup ahora".']);
    }
    alertText.replaceChildren(...lines.map(([tag, text]) => {
      const el = document.createElement(tag);
      el.textContent = text;
      return el;
    }));
    alertBox.hidden = lines.length === 0;

    // Tarjetas
    if (data.lastBackup) {
      lastEl.textContent = fmtDate(data.lastBackup.createdAt);
      lastSub.textContent = data.lastBackup.typeLabel + ' · ' + fmtSize(data.lastBackup.sizeBytes);
    } else {
      lastEl.textContent = 'Ninguna todavía';
      lastSub.textContent = '';
    }
    usedEl.textContent = data.configured ? fmtSize(data.usedBytes) : '—';
    freeEl.textContent = data.configured ? 'Libres en el disco: ' + fmtSize(data.freeBytes) : '';
    countEl.textContent = String(backups.length);
    btnNow.disabled = !data.configured;

    // Lista
    if (!backups.length) {
      tbody.replaceChildren(emptyRow(data.configured ? 'Todavía no hay copias guardadas.'
                                                     : 'Las copias de seguridad están desactivadas.'));
      return;
    }
    tbody.replaceChildren(...backups.map(row));
  }

  function row(b) {
    const tr = document.createElement('tr');
    tr.append(
      cell('Fecha', fmtDate(b.createdAt)),
      badgeCell(b),
      cell('Tamaño', fmtSize(b.sizeBytes)),
      actionsCell(b)
    );
    return tr;
  }

  function cell(label, text) {
    const td = document.createElement('td');
    td.dataset.label = label;
    td.textContent = text;
    return td;
  }

  function badgeCell(b) {
    const td = document.createElement('td');
    td.dataset.label = 'Tipo';
    const badge = document.createElement('span');
    badge.className = 'audit-badge' + (b.type === 'PRE_RESTORE' ? ' audit-badge--alert' : '');
    badge.textContent = b.typeLabel;
    td.append(badge);
    return td;
  }

  function actionsCell(b) {
    const td = document.createElement('td');
    td.className = 'cell-actions';
    const wrap = document.createElement('div');
    wrap.className = 'action-buttons-container';
    const download = document.createElement('button');
    download.type = 'button';
    download.className = 'btn btn-grey btn-sm';
    download.dataset.backupDownload = b.id;
    download.textContent = 'Descargar';
    const restore = document.createElement('button');
    restore.type = 'button';
    restore.className = 'btn btn-red btn-sm';
    restore.dataset.backupRestore = b.id;
    restore.textContent = 'Restaurar';
    wrap.append(download, restore);
    td.append(wrap);
    return td;
  }

  function emptyRow(text) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 4;
    td.className = 'audit-empty';
    td.textContent = text;
    tr.append(td);
    return tr;
  }

  // ===================== Acciones de la lista =====================

  tbody.addEventListener('click', (e) => {
    const down = e.target.closest('[data-backup-download]');
    if (down) return askDownload(Number(down.dataset.backupDownload));
    const rest = e.target.closest('[data-backup-restore]');
    if (rest) return openRestore(Number(rest.dataset.backupRestore));
  });

  function askDownload(id) {
    window.showConfirmModal('Descargar copia de seguridad',
      'El archivo tiene todos los datos del negocio y de los clientes, y la clave del correo. '
        + 'Guardalo en un lugar seguro y no lo compartas.',
      'Descargar', 'btn-pink', () => {
        // Un link temporal: el navegador baja el archivo con la sesión actual (como el CSV de la auditoría)
        const a = document.createElement('a');
        a.href = '/api/backups/' + encodeURIComponent(id) + '/download';
        a.download = '';
        document.body.append(a);
        a.click();
        a.remove();
      });
  }

  btnNow.addEventListener('click', async () => {
    const label = btnNow.innerHTML;
    btnNow.disabled = true;
    btnNow.textContent = 'Haciendo la copia…';
    try {
      const res = await fetch('/api/backups/run', { method: 'POST' });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        window.showErrorAlert('Se guardó la copia ' + data.fileName + ' (' + fmtSize(data.sizeBytes) + ').', 'Listo');
      } else {
        window.showErrorAlert(data.error || 'No se pudo hacer la copia.', 'No se pudo hacer la copia');
      }
    } catch (err) {
      window.showErrorAlert('No se pudo conectar con el servidor.', 'No se pudo hacer la copia');
    } finally {
      btnNow.innerHTML = label;   // el ícono SVG original (HTML fijo del template, no datos)
      await load();
    }
  });

  // ===================== Restaurar =====================

  function openRestore(id) {
    current = backups.find((b) => b.id === id);
    if (!current) return;
    form.reset();
    dateEl.textContent = fmtDate(current.createdAt);
    errorEl.hidden = true;
    progress.hidden = true;
    progress.textContent = 'Restaurando… no cierres esta ventana.';
    submit.hidden = false;
    done.hidden = true;
    cancelBtn.hidden = false;
    setBusy(false);
    modal.hidden = false;
    understood.focus();
  }

  // El botón Restaurar se habilita solo con las tres cosas completas
  function validate() {
    submit.disabled = busy || !(understood.checked
      && confirmInput.value.trim() === CONFIRM_WORD
      && password.value.length > 0);
  }
  [understood, confirmInput, password].forEach((el) => el.addEventListener('input', validate));
  understood.addEventListener('change', validate);

  function setBusy(value) {
    busy = value;
    [understood, confirmInput, password, cancelBtn].forEach((el) => { el.disabled = value; });
    validate();
  }

  // Mientras restaura, Esc y la X no cierran la ventana (el servidor sigue trabajando igual)
  modal.addEventListener('keydown', (e) => {
    if (busy && e.key === 'Escape') e.stopPropagation();
  }, true);
  modal.querySelector('.close-modal').addEventListener('click', (e) => {
    if (busy) { e.preventDefault(); e.stopImmediatePropagation(); }
  }, true);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    validate();
    if (submit.disabled || !current) return;
    setBusy(true);
    errorEl.hidden = true;
    progress.hidden = false;
    try {
      const res = await fetch('/api/backups/' + encodeURIComponent(current.id) + '/restore', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          password: password.value,
          confirm: confirmInput.value.trim(),
          understood: understood.checked
        })
      });
      const data = await res.json().catch(() => ({}));
      password.value = '';
      if (res.ok) {
        // La sesión se cerró en el servidor. Si hubo avisos, se muestran antes de ir a ingresar.
        const warnings = data.warnings || [];
        if (!warnings.length) {
          window.location.href = data.redirect || '/login?restored';
          return;
        }
        progress.textContent = [data.message, ...warnings].join(' ');
        submit.hidden = true;
        cancelBtn.hidden = true;
        done.hidden = false;
        busy = false;
        return;
      }
      errorEl.textContent = data.error || 'No se pudo restaurar.';
      errorEl.hidden = false;
      progress.hidden = true;
      setBusy(false);
    } catch (err) {
      errorEl.textContent = 'No se pudo conectar con el servidor. Esperá un minuto y recargá la página.';
      errorEl.hidden = false;
      progress.hidden = true;
      setBusy(false);
    }
  });

  // ===================== Carga al abrir la pestaña =====================

  document.addEventListener('tabChanged', (e) => {
    if (e.detail === 'backups') load();
  });
});
