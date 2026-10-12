// /js/admin/cake-config.js - Gestión Modular del Creador de Tortas
document.addEventListener('DOMContentLoaded', () => {

  let allSizes = [];
  let allOptions = [];
  let currentOptionFilter = 'all';

  const sizesTbody = document.getElementById('cake-sizes-tbody');
  const optionsTbody = document.getElementById('cake-options-tbody');

  if (!sizesTbody || !optionsTbody) return;

  // Paso 6.8: precios con formato argentino y nombres escapados
  const money = (n) => window.DreamFormat ? window.DreamFormat.money(n) : '$ ' + Number(n || 0).toLocaleString('es-AR');
  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

  // SVGs uniformes de 20x20 como en Inventario y Clientes
  const svgEdit = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>`;
  const svgTrash = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

  let configLoaded = false;
  async function fetchConfig() {
    if (!configLoaded) {
      window.DreamUI?.tableLoading(sizesTbody, 6, 'Cargando tamaños…');
      window.DreamUI?.tableLoading(optionsTbody, 4, 'Cargando sabores…');
    }
    try {
      const res = await fetch('/api/cake-config/admin');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      allSizes = data.sizes || [];
      allOptions = data.options || [];
      configLoaded = true;
      renderSizes();
      renderOptions();
    } catch (e) {
      if (!configLoaded) {
        window.DreamUI?.tableMessage(sizesTbody, 6, 'No se pudo cargar el armador.', 'Revisá la conexión y recargá la página.', true);
        window.DreamUI?.tableMessage(optionsTbody, 4, 'No se pudieron cargar los sabores.', '', true);
      }
    }
  }

  // Interruptor "En la tienda" (mismo estilo que el de Productos), con nombre accesible.
  // kind = "size" u "option": lo usa el listener de abajo para saber qué guardar.
  function storeSwitch(checked, kind, id, name) {
    return `<label class="toggle-switch">
              <input type="checkbox" ${checked ? 'checked' : ''} data-cake-toggle="${kind}" data-id="${id}" aria-label="Mostrar ${esc(name)} en la tienda">
              <span class="slider"></span>
              <span class="switch-text" aria-hidden="true"><span class="switch-on">Sí</span><span class="switch-off">No</span></span>
            </label>`;
  }

  function renderSizes() {
    sizesTbody.innerHTML = '';
    if (allSizes.length === 0) {
      sizesTbody.innerHTML = '<tr><td colspan="6" class="cell-empty">Todavía no hay tamaños. Sin tamaños, el cliente no puede armar su torta.</td></tr>';
      return;
    }
    allSizes.forEach(s => {
      sizesTbody.innerHTML += `
        <tr>
          <td class="cell-title cell-strong">${esc(s.name)}</td>
          <td data-label="Precio" class="cell-money">${money(s.price)}</td>
          <td data-label="Rellenos">Hasta ${s.maxRellenos}</td>
          <td data-label="Bizcochuelos">${s.mixSponge ? '2 sabores' : '1 sabor'}</td>
          <td data-label="En la tienda">${storeSwitch(s.enabled, 'size', s.id, s.name)}</td>
          <td class="cell-actions"><div class="action-buttons-container">
            <button type="button" class="btn btn-grey btn-sm" data-cake-action="edit-size" data-id="${s.id}">${svgEdit} Editar</button>
            <button type="button" class="btn btn-red btn-sm" data-cake-action="delete-size" data-id="${s.id}">${svgTrash} Eliminar</button>
          </div></td>
        </tr>
      `;
    });
  }

  function renderOptions() {
    optionsTbody.innerHTML = '';
    const filtered = allOptions.filter(o => currentOptionFilter === 'all' || o.category === currentOptionFilter);
    if (filtered.length === 0) {
      optionsTbody.innerHTML = '<tr><td colspan="4" class="cell-empty">No hay sabores de este tipo todavía.</td></tr>';
      return;
    }

    filtered.forEach(o => {
      let badgeLabel = o.category;
      if (o.category === 'Sponge') badgeLabel = 'Bizcochuelo';
      else if (o.category === 'Crema') badgeLabel = 'Relleno para crema';
      else if (o.category === 'Fondant') badgeLabel = 'Relleno para fondant';

      optionsTbody.innerHTML += `
        <tr>
          <td class="cell-title cell-strong">${esc(o.name)}</td>
          <td data-label="Tipo"><span class="tag-badge">${badgeLabel}</span></td>
          <td data-label="En la tienda">${storeSwitch(o.enabled, 'option', o.id, o.name)}</td>
          <td class="cell-actions"><div class="action-buttons-container">
            <button type="button" class="btn btn-grey btn-sm" data-cake-action="edit-option" data-id="${o.id}">${svgEdit} <span class="btn-label">Editar <span class="visually-hidden">${o.name}</span></span></button>
            <button type="button" class="btn btn-red btn-sm" data-cake-action="delete-option" data-id="${o.id}">${svgTrash} <span class="btn-label">Eliminar <span class="visually-hidden">${o.name}</span></span></button>
          </div></td>
        </tr>
      `;
    });
  }

  // Botones e interruptores de las dos tablas (antes eran onclick/onchange dentro del HTML,
  // que la política de seguridad ya no permite)
  document.getElementById('armador')?.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-cake-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    if (btn.dataset.cakeAction === 'edit-size') window.openEditSize(id);
    else if (btn.dataset.cakeAction === 'delete-size') window.confirmDeleteSize(id);
    else if (btn.dataset.cakeAction === 'edit-option') window.openEditOption(id);
    else if (btn.dataset.cakeAction === 'delete-option') window.confirmDeleteOption(id);
  });
  document.getElementById('armador')?.addEventListener('change', (e) => {
    const input = e.target.closest('[data-cake-toggle]');
    if (!input) return;
    const id = Number(input.dataset.id);
    if (input.dataset.cakeToggle === 'size') window.toggleSizeEnabled(id, input.checked);
    else window.toggleOptionEnabled(id, input.checked);
  });

  // Filtros por Chips
  document.querySelectorAll('#cake-option-chips .chip').forEach(chip => {
    chip.addEventListener('click', (e) => {
      document.querySelectorAll('#cake-option-chips .chip').forEach(c => c.classList.remove('active'));
      e.currentTarget.classList.add('active');
      currentOptionFilter = e.currentTarget.dataset.cat;
      renderOptions();
    });
  });

  // --- CRUD TAMAÑOS ---
  document.getElementById('btn-new-size')?.addEventListener('click', () => {
    document.getElementById('modal-size-title').innerText = 'Nuevo tamaño';
    document.getElementById('size-id').value = '';
    document.getElementById('size-name').value = '';
    document.getElementById('size-price').value = '';
    document.getElementById('size-max').value = 2;
    document.getElementById('size-mix').checked = false;
    document.getElementById('size-enabled').checked = true;
    document.getElementById('modal-cake-size').hidden = false;
  });

  window.openEditSize = function(id) {
    const s = allSizes.find(item => item.id === id);
    if (!s) return;
    document.getElementById('modal-size-title').innerText = 'Editar tamaño';
    document.getElementById('size-id').value = s.id;
    document.getElementById('size-name').value = s.name;
    document.getElementById('size-price').value = s.price;
    document.getElementById('size-max').value = s.maxRellenos;
    document.getElementById('size-mix').checked = s.mixSponge;
    document.getElementById('size-enabled').checked = s.enabled;
    document.getElementById('modal-cake-size').hidden = false;
  };

  document.getElementById('form-cake-size')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('size-id').value;
    const payload = {
      name: document.getElementById('size-name').value.trim(),
      price: parseInt(document.getElementById('size-price').value, 10),
      maxRellenos: parseInt(document.getElementById('size-max').value, 10),
      mixSponge: document.getElementById('size-mix').checked,
      enabled: document.getElementById('size-enabled').checked
    };

    const url = id ? `/api/cake-config/sizes/${id}` : '/api/cake-config/sizes';
    const method = id ? 'PUT' : 'POST';

    await window.DreamUI.busy(e.submitter || e.target.querySelector('button[type="submit"]'), async () => {
      try {
        const res = await fetch(url, {
          method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        if (res.ok) {
          document.getElementById('modal-cake-size').hidden = true;
          fetchConfig();
        } else {
          await window.DreamUI.fail(res, 'No se pudo guardar el tamaño. Revisá los datos.');
        }
      } catch (err) { window.DreamUI.offline(); }
    });
  });

  window.toggleSizeEnabled = async function(id, isEnabled) {
    const s = allSizes.find(item => item.id === id);
    if (!s) return;
    s.enabled = isEnabled;
    try {
      const res = await fetch(`/api/cake-config/sizes/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(s)
      });
      if (!res.ok) throw res;
    } catch (err) {
      // No se guardó: el interruptor vuelve a como estaba, así la pantalla muestra lo real
      s.enabled = !isEnabled;
      fetchConfig();
      if (err instanceof Response) await window.DreamUI.fail(err, 'No se pudo cambiar. Probá de nuevo.');
      else window.DreamUI.offline();
    }
  };

  window.confirmDeleteSize = function(id) {
    window.showConfirmModal('Eliminar tamaño', '¿Seguro que querés eliminar este tamaño de torta?', 'Eliminar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/cake-config/sizes/${id}`, { method: 'DELETE' });
        if (res.ok) fetchConfig();
        else await window.DreamUI.fail(res, 'No se pudo eliminar el tamaño.');
      } catch (e) { window.DreamUI.offline(); }
    });
  };

  // --- CRUD OPCIONES (BIZCOCHUELOS Y RELLENOS) ---
  document.getElementById('btn-new-option')?.addEventListener('click', () => {
    document.getElementById('modal-option-title').innerText = 'Nuevo sabor';
    document.getElementById('option-id').value = '';
    document.getElementById('option-name').value = '';
    document.getElementById('option-category').value = currentOptionFilter !== 'all' ? currentOptionFilter : 'Crema';
    document.getElementById('option-enabled').checked = true;
    document.getElementById('modal-cake-option').hidden = false;
  });

  window.openEditOption = function(id) {
    const o = allOptions.find(item => item.id === id);
    if (!o) return;
    document.getElementById('modal-option-title').innerText = 'Editar sabor';
    document.getElementById('option-id').value = o.id;
    document.getElementById('option-name').value = o.name;
    document.getElementById('option-category').value = o.category;
    document.getElementById('option-enabled').checked = o.enabled;
    document.getElementById('modal-cake-option').hidden = false;
  };

  document.getElementById('form-cake-option')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('option-id').value;
    const payload = {
      name: document.getElementById('option-name').value.trim(),
      category: document.getElementById('option-category').value,
      enabled: document.getElementById('option-enabled').checked
    };

    const url = id ? `/api/cake-config/options/${id}` : '/api/cake-config/options';
    const method = id ? 'PUT' : 'POST';

    await window.DreamUI.busy(e.submitter || e.target.querySelector('button[type="submit"]'), async () => {
      try {
        const res = await fetch(url, {
          method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        if (res.ok) {
          document.getElementById('modal-cake-option').hidden = true;
          fetchConfig();
        } else {
          await window.DreamUI.fail(res, 'No se pudo guardar el sabor. Revisá los datos.');
        }
      } catch (err) { window.DreamUI.offline(); }
    });
  });

  window.toggleOptionEnabled = async function(id, isEnabled) {
    const o = allOptions.find(item => item.id === id);
    if (!o) return;
    o.enabled = isEnabled;
    try {
      const res = await fetch(`/api/cake-config/options/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(o)
      });
      if (!res.ok) throw res;
    } catch (err) {
      // No se guardó: el interruptor vuelve a como estaba, así la pantalla muestra lo real
      o.enabled = !isEnabled;
      fetchConfig();
      if (err instanceof Response) await window.DreamUI.fail(err, 'No se pudo cambiar. Probá de nuevo.');
      else window.DreamUI.offline();
    }
  };

  window.confirmDeleteOption = function(id) {
    window.showConfirmModal('Eliminar sabor', '¿Seguro que querés eliminar este sabor o relleno?', 'Eliminar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/cake-config/options/${id}`, { method: 'DELETE' });
        if (res.ok) fetchConfig();
        else await window.DreamUI.fail(res, 'No se pudo eliminar el sabor.');
      } catch (e) { window.DreamUI.offline(); }
    });
  };

  fetchConfig();
});