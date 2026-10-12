/**
 * js/admin/audit.js — Pestaña "Registro de actividad" (paso 3.4). Solo en admin.html.
 *
 * - Pide al servidor una página por vez (50 registros, los más recientes primero) con los filtros
 *   puestos: GET /api/audit/logs. El navegador nunca recibe todo el registro.
 * - Al tocar una fila muestra el detalle: quién, cuándo, desde dónde y el ANTES y el DESPUÉS en una
 *   tabla legible (no el JSON crudo).
 * - Descargas con los mismos filtros: /api/audit/export/csv y /api/audit/export/pdf (el servidor
 *   registra cada descarga en la propia auditoría).
 * - "Verificar integridad": POST /api/audit/verify (recorre la cadena de hashes).
 *
 * Solo lectura: no hay ninguna opción para editar ni borrar registros.
 * Todo el texto que viene del servidor se pone con textContent (nunca como HTML).
 */

document.addEventListener('DOMContentLoaded', () => {
  const section = document.getElementById('registro');
  if (!section) return;

  const tbody = document.getElementById('audit-tbody');
  const form = document.getElementById('audit-filters');
  const summary = document.getElementById('audit-summary');
  const pager = document.getElementById('audit-pagination');
  const pageInfo = document.getElementById('audit-page-info');
  const btnPrev = document.getElementById('audit-prev');
  const btnNext = document.getElementById('audit-next');
  const fields = {
    from: document.getElementById('audit-from'),
    to: document.getElementById('audit-to'),
    actor: document.getElementById('audit-actor'),
    module: document.getElementById('audit-module'),
    action: document.getElementById('audit-action'),
    q: document.getElementById('audit-q')
  };

  // Nombres en español (los mismos que usa el servidor en AuditText)
  const MODULES = {
    PEDIDOS: 'Pedidos', PRODUCTOS: 'Productos', INVENTARIO: 'Inventario', RECETAS: 'Recetas',
    CLIENTES: 'Clientes', CUPONES: 'Cupones', GALERIA: 'Galería', EQUIPO: 'Equipo',
    ARMADOR: 'Armador de tortas', FIDELIZACION: 'Fidelización', SISTEMA: 'Sistema',
    SEGURIDAD: 'Seguridad', BAKEY: 'Bakey', RECORDATORIOS: 'Recordatorios'
  };
  const ACTIONS = {
    CREATE: 'Alta', UPDATE: 'Modificación', DELETE: 'Baja', STATUS_CHANGE: 'Cambio de estado',
    LOGIN: 'Inicio de sesión', LOGIN_FAILED: 'Ingreso fallido', LOGOUT: 'Cierre de sesión',
    EXPORT: 'Exportación', DOWNLOAD: 'Descarga', CONFIG_CHANGE: 'Configuración',
    EMAIL_CAMPAIGN: 'Envío de emails', BACKUP: 'Copia de seguridad', RESTORE: 'Restauración', ASSISTANT_ACTION: 'Acción de Bakey'
  };
  // Nombres legibles de los datos que aparecen en "details" (antes y después)
  const FIELDS = {
    cliente: 'Cliente', telefono: 'Teléfono', email: 'Email', entrega: 'Fecha de entrega', turno: 'Turno',
    metodo: 'Retiro o envío', direccion: 'Dirección', estado: 'Estado', total: 'Total', sena: 'Seña',
    productos: 'Productos', nombre: 'Nombre', precio: 'Precio', descripcion: 'Descripción',
    visible: 'Se muestra en la tienda', categoria: 'Categoría', foto: 'Foto', receta: 'Receta (n°)',
    stock: 'Stock', minimo: 'Stock mínimo', unidad: 'Unidad', vencimiento: 'Vencimiento', costo: 'Costo por unidad',
    codigo: 'Código', tipo: 'Tipo', activo: 'Activo', descuento: 'Descuento (%)', limite: 'Límite de usos',
    texto: 'Texto', rellenos: 'Cantidad de rellenos', mezclaBizcochuelos: 'Mezcla de bizcochuelos',
    habilitado: 'Puede entrar', rol: 'Rol', usuario: 'Usuario (email)', cumpleanos: 'Cumpleaños', promociones: 'Acepta promociones',
    saludosAutomaticos: 'Saludos automáticos', descuentoCumpleanos: 'Descuento de cumpleaños (%)',
    maximoEmailsPorDia: 'Máximo de emails por día', whatsapp: 'WhatsApp', cuenta: 'Cuenta de correo',
    servidor: 'Servidor de correo', forzado: 'Se confirmó sin stock suficiente', faltabaStock: 'Faltaba stock',
    insumos: 'Insumos', insumo: 'Insumo', formato: 'Formato', registros: 'Registros incluidos',
    totalConFiltros: 'Total con esos filtros', fechas: 'Fechas', filtros: 'Filtros',
    selloRegistro: 'Sello: registro n°', selloHash: 'Sello: hash', emailIntentado: 'Email intentado',
    motivo: 'Motivo', asunto: 'Asunto', audiencia: 'A quién', cupon: 'Cupón',
    titulo: 'Qué hay que hacer', notas: 'Notas', vence: 'Fecha y hora', pedido: 'Pedido (n°)',
    hecho: 'Hecho', dueno: 'De quién es', creadoPorBakey: 'Lo creó Bakey', recordatoriosBorrados: 'Recordatorios borrados'
  };
  const MONEY_FIELDS = new Set(['total', 'sena', 'precio', 'costo']);
  const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });

  let page = 0;
  let totalPages = 0;
  let loadedOnce = false;
  let rowsById = new Map();

  // Opciones fijas de los filtros
  fillSelect(fields.module, MODULES);
  fillSelect(fields.action, ACTIONS);

  // Se carga recién la primera vez que se abre la pestaña (no en cada carga del panel)
  document.addEventListener('tabChanged', (e) => {
    if (e.detail !== 'registro') return;
    if (!loadedOnce) {
      loadedOnce = true;
      loadActors();
    }
    load(page);
  });

  // ---------- Filtros ----------
  form.addEventListener('submit', (e) => { e.preventDefault(); load(0); });
  [fields.from, fields.to, fields.actor, fields.module, fields.action].forEach((el) =>
    el.addEventListener('change', () => load(0)));
  document.getElementById('audit-clear').addEventListener('click', () => {
    Object.values(fields).forEach((el) => { el.value = ''; });
    load(0);
  });
  btnPrev.addEventListener('click', () => { if (page > 0) load(page - 1); });
  btnNext.addEventListener('click', () => { if (page < totalPages - 1) load(page + 1); });

  function filterParams() {
    const params = new URLSearchParams();
    Object.entries(fields).forEach(([key, el]) => {
      const v = el.value.trim();
      if (v) params.set(key, v);
    });
    return params;
  }

  async function loadActors() {
    try {
      const res = await fetch('/api/audit/actors');
      if (!res.ok) return;
      const actors = await res.json();
      actors.forEach((a) => fields.actor.append(new Option(a.label, a.value)));
    } catch (e) { /* el filtro queda solo con "Todos" */ }
  }

  // ---------- Tabla ----------
  async function load(newPage) {
    if (fields.from.value && fields.to.value && fields.from.value > fields.to.value) {
      window.showErrorAlert('La fecha "Desde" no puede ser posterior a "Hasta".');
      return;
    }
    const params = filterParams();
    params.set('page', String(newPage));
    summary.textContent = 'Cargando…';
    // Paso 6.8: la tabla también muestra "Cargando…" mientras no tiene datos
    if (!tbody.querySelector('tr:not(.state-row)')) window.DreamUI?.tableLoading(tbody, 7, 'Cargando el registro…');
    try {
      const res = await fetch('/api/audit/logs?' + params.toString());
      if (!res.ok) throw new Error(String(res.status));
      const data = await res.json();
      page = data.page;
      totalPages = data.totalPages;
      render(data);
    } catch (e) {
      summary.textContent = '';
      tbody.replaceChildren(emptyRow('No se pudo cargar el registro. Probá de nuevo.'));
      pager.hidden = true;
    }
  }

  function render(data) {
    rowsById = new Map();
    if (!data.items.length) {
      summary.textContent = '';
      tbody.replaceChildren(emptyRow(hasFilters() ? 'No hay registros con estos filtros.' : 'Todavía no hay actividad registrada.'));
      pager.hidden = true;
      return;
    }
    const first = data.page * data.pageSize + 1;
    const last = first + data.items.length - 1;
    summary.textContent = `Mostrando ${first}–${last} de ${data.totalElements} registros. Tocá una fila para ver el detalle.`;

    const rows = data.items.map((item) => {
      rowsById.set(String(item.id), item);
      const tr = document.createElement('tr');
      tr.className = 'audit-row';
      tr.tabIndex = 0;
      tr.dataset.auditId = item.id;
      tr.setAttribute('aria-label', `Ver el detalle del registro ${item.id}`);
      tr.append(
        cell('Fecha y hora', formatDateTime(item.timestamp)),
        cell('Usuario', item.actorName || '—', item.actorEmail && item.actorEmail !== item.actorName ? item.actorEmail : null),
        cell('Rol', item.roleLabel || '—'),
        cell('Módulo', item.moduleLabel),
        actionCell(item),
        cell('Descripción', item.description, null, 'audit-desc')
      );
      return tr;
    });
    tbody.replaceChildren(...rows);

    pager.hidden = data.totalPages <= 1;
    pageInfo.textContent = `Página ${data.page + 1} de ${data.totalPages}`;
    btnPrev.disabled = data.page === 0;
    btnNext.disabled = data.page >= data.totalPages - 1;
  }

  function cell(label, text, sub, cls) {
    const td = document.createElement('td');
    td.dataset.label = label;
    if (cls) td.className = cls;
    if (sub) {
      // Un solo contenedor: en el celular la celda es "etiqueta | contenido" y el email va abajo del nombre
      const wrap = document.createElement('span');
      wrap.className = 'audit-user';
      const main = document.createElement('span');
      main.textContent = text;
      const small = document.createElement('small');
      small.className = 'audit-sub';
      // El email puede cortarse después de la "@" (<wbr>) en vez de letra por letra
      const at = sub.indexOf('@');
      if (at > 0) {
        small.append(sub.slice(0, at + 1), document.createElement('wbr'), sub.slice(at + 1));
      } else {
        small.textContent = sub;
      }
      wrap.append(main, small);
      td.append(wrap);
    } else {
      td.textContent = text;
    }
    return td;
  }

  // La acción como etiqueta; los ingresos fallidos y las bajas se marcan en rojo
  function actionCell(item) {
    const td = document.createElement('td');
    td.dataset.label = 'Acción';
    const badge = document.createElement('span');
    badge.className = 'audit-badge' + (['LOGIN_FAILED', 'DELETE', 'RESTORE'].includes(item.action) ? ' audit-badge--alert' : '');
    badge.textContent = item.actionLabel;
    td.append(badge);
    return td;
  }

  function emptyRow(text) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 6;
    td.className = 'audit-empty';
    td.textContent = text;
    tr.append(td);
    return tr;
  }

  function hasFilters() {
    return Object.values(fields).some((el) => el.value.trim() !== '');
  }

  // Tocar (o Enter / espacio sobre) una fila abre el detalle
  tbody.addEventListener('click', (e) => {
    const tr = e.target.closest('tr[data-audit-id]');
    if (tr) openDetail(rowsById.get(tr.dataset.auditId));
  });
  tbody.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    const tr = e.target.closest('tr[data-audit-id]');
    if (!tr) return;
    e.preventDefault();
    openDetail(rowsById.get(tr.dataset.auditId));
  });

  // ---------- Detalle (antes y después legible) ----------
  const detailModal = document.getElementById('modal-audit-detail');
  const detailBody = document.getElementById('audit-detail-body');

  function openDetail(item) {
    if (!item) return;
    const parts = [];

    const desc = el('p', 'audit-detail__desc', item.description);
    parts.push(desc);

    const meta = el('dl', 'audit-meta');
    addMeta(meta, 'Fecha y hora', formatDateTime(item.timestamp, true));
    addMeta(meta, 'Usuario', [item.actorName, item.actorEmail && item.actorEmail !== item.actorName ? item.actorEmail : null].filter(Boolean).join(' · ') || '—');
    addMeta(meta, 'Rol', item.roleLabel || '—');
    addMeta(meta, 'Módulo y acción', `${item.moduleLabel} · ${item.actionLabel}`);
    if (item.entityType || item.entityId) addMeta(meta, 'Sobre', [item.entityType, item.entityId ? `n° ${item.entityId}` : null].filter(Boolean).join(' '));
    if (item.ipAddress) addMeta(meta, 'Desde (IP)', item.ipAddress);
    if (item.userAgent) addMeta(meta, 'Navegador', browserName(item.userAgent));
    addMeta(meta, 'N° de registro', String(item.id));
    parts.push(meta);

    parts.push(...renderDetails(item.details));

    const hash = el('p', 'audit-hash');
    hash.append(el('span', null, 'Huella (hash) de este registro: '), el('code', null, item.hash));
    parts.push(hash);

    detailBody.replaceChildren(...parts);
    detailModal.hidden = false;
    detailModal.querySelector('.close-modal')?.focus();
  }

  function addMeta(dl, label, value) {
    dl.append(el('dt', null, label), el('dd', null, value));
  }

  // Convierte el JSON de "details" en tablas y listas fáciles de leer
  function renderDetails(raw) {
    if (!raw) return [el('p', 'audit-muted', 'No hay más detalles para esta acción.')];
    let data;
    try { data = JSON.parse(raw); } catch (e) { return [el('p', null, raw)]; }
    if (data === null || typeof data !== 'object') return [el('p', null, String(data))];

    const out = [];
    const hasChange = 'antes' in data || 'despues' in data;
    if (hasChange) {
      out.push(el('h4', 'audit-detail__title', 'Qué cambió'));
      out.push(changeTable(data.antes, data.despues));
    }
    const rest = Object.entries(data).filter(([k]) => k !== 'antes' && k !== 'despues');
    if (rest.length) {
      out.push(el('h4', 'audit-detail__title', hasChange ? 'Más datos' : 'Datos'));
      const dl = el('dl', 'audit-meta');
      rest.forEach(([key, value]) => {
        dl.append(el('dt', null, fieldName(key)));
        const dd = document.createElement('dd');
        renderValue(dd, key, value);
        dl.append(dd);
      });
      out.push(dl);
    }
    return out;
  }

  // Tabla Dato | Antes | Después (si es un alta solo hay "después"; si es una baja, solo "antes")
  function changeTable(before, after) {
    const b = before && typeof before === 'object' ? before : {};
    const a = after && typeof after === 'object' ? after : {};
    const keys = [...new Set([...Object.keys(b), ...Object.keys(a)])];
    if (!keys.length) return el('p', 'audit-muted', 'Se guardó sin cambios.');

    const table = el('table', 'audit-changes');
    const head = document.createElement('thead');
    const hr = document.createElement('tr');
    ['Dato', 'Antes', 'Después'].forEach((t) => { const th = el('th', null, t); th.scope = 'col'; hr.append(th); });
    head.append(hr);
    const body = document.createElement('tbody');
    keys.forEach((key) => {
      const tr = document.createElement('tr');
      const th = el('th', null, fieldName(key));
      th.scope = 'row';
      const tdB = document.createElement('td');
      tdB.dataset.label = 'Antes';
      renderValue(tdB, key, before == null ? undefined : b[key]);
      const tdA = document.createElement('td');
      tdA.dataset.label = 'Después';
      renderValue(tdA, key, after == null ? undefined : a[key]);
      if (before != null && after != null && JSON.stringify(b[key]) !== JSON.stringify(a[key])) tr.className = 'is-changed';
      tr.append(th, tdB, tdA);
      body.append(tr);
    });
    table.append(head, body);
    return table;
  }

  // Un valor: listas como renglones, objetos como "dato: valor", sí/no, plata y fechas en formato local
  function renderValue(container, key, value) {
    if (value === undefined) { container.textContent = '—'; container.classList.add('audit-muted'); return; }
    if (Array.isArray(value)) {
      if (!value.length) { container.textContent = '(ninguno)'; return; }
      const ul = el('ul', 'audit-list');
      value.forEach((v) => {
        if (v && typeof v === 'object' && ('antes' in v || 'despues' in v)) {
          // Ej. insumos descontados: "Harina (kg): 3 → 1"
          const name = v.insumo || v.nombre || '';
          ul.append(el('li', null, `${name}${v.unidad ? ` (${v.unidad})` : ''}: ${formatValue('antes', v.antes)} → ${formatValue('despues', v.despues)}`));
        } else if (v && typeof v === 'object') {
          ul.append(el('li', null, Object.entries(v).map(([k, x]) => `${fieldName(k)}: ${formatValue(k, x)}`).join(' · ')));
        } else {
          ul.append(el('li', null, formatValue(key, v)));
        }
      });
      container.append(ul);
      return;
    }
    if (value && typeof value === 'object') {
      container.textContent = Object.entries(value).map(([k, x]) => `${fieldName(k)}: ${formatValue(k, x)}`).join(' · ');
      return;
    }
    container.textContent = formatValue(key, value);
  }

  function formatValue(key, value) {
    if (value === null || value === '' || value === 'null') return '—';
    if (value === true || value === 'true') return 'Sí';
    if (value === false || value === 'false') return 'No';
    if (MONEY_FIELDS.has(key) && !Number.isNaN(Number(value))) return money.format(Number(value));
    if (typeof value === 'number') return value.toLocaleString('es-AR');
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [y, m, d] = value.split('-');
      return `${d}/${m}/${y}`;
    }
    return String(value);
  }

  function fieldName(key) {
    if (FIELDS[key]) return FIELDS[key];
    // camelCase → "Camel case"
    const words = key.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  // "Chrome en Windows" en vez de la línea técnica completa del navegador
  function browserName(ua) {
    const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox'
      : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : /curl/i.test(ua) ? 'curl' : null;
    const os = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iPhone / iPad' : /Windows/.test(ua) ? 'Windows'
      : /Mac OS X/.test(ua) ? 'Mac' : /Linux/.test(ua) ? 'Linux' : null;
    if (!browser) return ua.length > 80 ? ua.slice(0, 80) + '…' : ua;
    return os ? `${browser} en ${os}` : browser;
  }

  // ---------- Descargas (con los filtros puestos) ----------
  document.getElementById('btn-audit-csv').addEventListener('click', () => download('csv'));
  document.getElementById('btn-audit-pdf').addEventListener('click', () => download('pdf'));

  function download(format) {
    if (fields.from.value && fields.to.value && fields.from.value > fields.to.value) {
      window.showErrorAlert('La fecha "Desde" no puede ser posterior a "Hasta".');
      return;
    }
    const link = document.createElement('a');
    link.href = `/api/audit/export/${format}?` + filterParams().toString();
    link.rel = 'noopener';
    document.body.append(link);
    link.click();
    link.remove();
    // La descarga queda registrada en el propio registro: se recarga para que aparezca
    setTimeout(() => load(0), 1500);
  }

  // ---------- Verificar integridad ----------
  const verifyModal = document.getElementById('modal-audit-verify');
  const verifyBody = document.getElementById('audit-verify-body');

  document.getElementById('btn-audit-verify').addEventListener('click', async () => {
    verifyBody.replaceChildren(el('p', 'audit-muted', 'Revisando la cadena de registros…'));
    verifyModal.hidden = false;
    try {
      const res = await fetch('/api/audit/verify', { method: 'POST' });
      if (!res.ok) throw new Error(String(res.status));
      const r = await res.json();
      const box = el('div', 'audit-verify' + (r.intact ? '' : ' audit-verify--broken'));
      box.append(icon(r.intact));
      const text = document.createElement('div');
      text.append(el('strong', null, r.intact ? 'El registro está íntegro' : 'El registro fue alterado'));
      text.append(el('p', null, r.message));
      if (r.intact) {
        text.append(el('p', 'audit-muted', 'Se recalculó la huella (hash) de cada registro y todas coinciden: nadie modificó ni borró registros desde la base de datos.'));
      } else {
        text.append(el('p', null, `El primer registro con problemas es el n° ${r.firstBrokenId}. Ese y los que siguen no son confiables. `
          + 'Revisá quién tiene acceso a la base de datos y compará con la última copia de seguridad. Ya se avisó por email a los administradores.'));
      }
      box.append(text);
      verifyBody.replaceChildren(box);
    } catch (e) {
      verifyBody.replaceChildren(el('p', null, 'No se pudo verificar ahora. Probá de nuevo en unos minutos.'));
    }
  });

  function icon(ok) {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    const paths = ok
      ? ['M12 3 4 6v6c0 4.5 3.4 8.3 8 9 4.6-.7 8-4.5 8-9V6l-8-3Z', 'm9 12 2 2 4-4']
      : ['M12 3 2 20h20L12 3Z', 'M12 10v4M12 17v.5'];
    paths.forEach((d) => { const p = document.createElementNS(ns, 'path'); p.setAttribute('d', d); svg.append(p); });
    return svg;
  }

  // ---------- Ayudas ----------
  function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function fillSelect(select, map) {
    Object.entries(map).forEach(([value, label]) => select.append(new Option(label, value)));
  }

  // "2026-10-10T21:43:09.720" → "10/10/2026 21:43" (con segundos en el detalle)
  function formatDateTime(iso, withSeconds) {
    if (!iso) return '—';
    const [date, time = ''] = iso.split('T');
    const [y, m, d] = date.split('-');
    const t = time.slice(0, withSeconds ? 8 : 5);
    return `${d}/${m}/${y} ${t}`;
  }
});
