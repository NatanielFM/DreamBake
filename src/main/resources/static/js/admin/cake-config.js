// /js/admin/cake-config.js - Gestión Modular del Creador de Tortas
document.addEventListener('DOMContentLoaded', () => {

  let allSizes = [];
  let allOptions = [];
  let currentOptionFilter = 'all';

  const sizesTbody = document.getElementById('cake-sizes-tbody');
  const optionsTbody = document.getElementById('cake-options-tbody');

  if (!sizesTbody || !optionsTbody) return;

  // SVGs uniformes de 20x20 como en Inventario y Clientes
  const svgEdit = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>`;
  const svgTrash = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

  async function fetchConfig() {
    try {
      const res = await fetch('/api/cake-config/admin');
      if (res.ok) {
        const data = await res.json();
        allSizes = data.sizes || [];
        allOptions = data.options || [];
        renderSizes();
        renderOptions();
      }
    } catch (e) {
      console.error("Error al cargar la configuración de tortas:", e);
    }
  }

  function renderSizes() {
    sizesTbody.innerHTML = '';
    allSizes.forEach(s => {
      sizesTbody.innerHTML += `
        <tr>
          <td style="font-weight:600;">${s.name}</td>
          <td style="font-weight:700; color:var(--accent);">$${Number(s.price).toLocaleString('es-AR')}</td>
          <td style="text-align:center;">Hasta ${s.maxRellenos}</td>
          <td style="text-align:center;">${s.mixSponge ? 'Sí (2 sabores)' : 'No (1 sabor)'}</td>
          <td style="text-align:center;">
            <input type="checkbox" ${s.enabled ? 'checked' : ''} onchange="toggleSizeEnabled(${s.id}, this.checked)" style="transform:scale(1.2); cursor:pointer;">
          </td>
          <td style="text-align:center;">
            <div style="display:flex; justify-content:center; gap:8px;">
              <button class="btn btn-pink" style="padding:6px 12px; display:inline-flex; align-items:center; gap:6px; font-size:0.85rem;" onclick="openEditSize(${s.id})">
                ${svgEdit} Editar
              </button>
              <button class="btn btn-red" style="padding:6px 12px; display:inline-flex; align-items:center; gap:6px; font-size:0.85rem;" onclick="confirmDeleteSize(${s.id})">
                ${svgTrash} Eliminar
              </button>
            </div>
          </td>
        </tr>
      `;
    });
  }

  function renderOptions() {
    optionsTbody.innerHTML = '';
    const filtered = allOptions.filter(o => currentOptionFilter === 'all' || o.category === currentOptionFilter);

    filtered.forEach(o => {
      let badgeLabel = o.category;
      if (o.category === 'Sponge') badgeLabel = 'Bizcochuelo';
      else if (o.category === 'Crema') badgeLabel = 'Relleno (Crema)';
      else if (o.category === 'Fondant') badgeLabel = 'Relleno (Fondant)';

      optionsTbody.innerHTML += `
        <tr>
          <td style="font-weight:600;">${o.name}</td>
          <td style="text-align:center;"><span class="tag-badge">${badgeLabel}</span></td>
          <td style="text-align:center;">
            <input type="checkbox" ${o.enabled ? 'checked' : ''} onchange="toggleOptionEnabled(${o.id}, this.checked)" style="transform:scale(1.2); cursor:pointer;">
          </td>
          <td style="text-align:center;">
            <div style="display:flex; justify-content:center; gap:8px;">
              <button class="btn btn-pink" style="padding:6px 12px; display:inline-flex; align-items:center; gap:6px; font-size:0.85rem;" onclick="openEditOption(${o.id})">
                ${svgEdit} Editar
              </button>
              <button class="btn btn-red" style="padding:6px 12px; display:inline-flex; align-items:center; gap:6px; font-size:0.85rem;" onclick="confirmDeleteOption(${o.id})">
                ${svgTrash} Eliminar
              </button>
            </div>
          </td>
        </tr>
      `;
    });
  }

  // Filtros por Chips
  document.querySelectorAll('#cake-option-chips .chip').forEach(chip => {
    chip.addEventListener('click', (e) => {
      document.querySelectorAll('#cake-option-chips .chip').forEach(c => c.classList.remove('active'));
      e.target.classList.add('active');
      currentOptionFilter = e.target.dataset.cat;
      renderOptions();
    });
  });

  // --- CRUD TAMAÑOS ---
  document.getElementById('btn-new-size')?.addEventListener('click', () => {
    document.getElementById('modal-size-title').innerText = 'Nuevo Tamaño de Torta';
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
    document.getElementById('modal-size-title').innerText = 'Editar Tamaño de Torta';
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

    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      document.getElementById('modal-cake-size').hidden = true;
      fetchConfig();
    }
  });

  window.toggleSizeEnabled = async function(id, isEnabled) {
    const s = allSizes.find(item => item.id === id);
    if (!s) return;
    s.enabled = isEnabled;
    await fetch(`/api/cake-config/sizes/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(s)
    });
  };

  window.confirmDeleteSize = function(id) {
    if (typeof window.showConfirmModal === 'function') {
      window.showConfirmModal('Eliminar Tamaño', '¿Estás seguro de que deseas eliminar este tamaño de torta?', 'Eliminar', 'btn-red', async () => {
        await fetch(`/api/cake-config/sizes/${id}`, { method: 'DELETE' });
        fetchConfig();
      });
    } else if (confirm('¿Eliminar este tamaño de torta?')) {
      fetch(`/api/cake-config/sizes/${id}`, { method: 'DELETE' }).then(() => fetchConfig());
    }
  };

  // --- CRUD OPCIONES (BIZCOCHUELOS Y RELLENOS) ---
  document.getElementById('btn-new-option')?.addEventListener('click', () => {
    document.getElementById('modal-option-title').innerText = 'Nuevo Sabor / Relleno';
    document.getElementById('option-id').value = '';
    document.getElementById('option-name').value = '';
    document.getElementById('option-category').value = currentOptionFilter !== 'all' ? currentOptionFilter : 'Crema';
    document.getElementById('option-enabled').checked = true;
    document.getElementById('modal-cake-option').hidden = false;
  });

  window.openEditOption = function(id) {
    const o = allOptions.find(item => item.id === id);
    if (!o) return;
    document.getElementById('modal-option-title').innerText = 'Editar Sabor / Relleno';
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

    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      document.getElementById('modal-cake-option').hidden = true;
      fetchConfig();
    }
  });

  window.toggleOptionEnabled = async function(id, isEnabled) {
    const o = allOptions.find(item => item.id === id);
    if (!o) return;
    o.enabled = isEnabled;
    await fetch(`/api/cake-config/options/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(o)
    });
  };

  window.confirmDeleteOption = function(id) {
    if (typeof window.showConfirmModal === 'function') {
      window.showConfirmModal('Eliminar Opción', '¿Estás seguro de que deseas eliminar este sabor o relleno?', 'Eliminar', 'btn-red', async () => {
        await fetch(`/api/cake-config/options/${id}`, { method: 'DELETE' });
        fetchConfig();
      });
    } else if (confirm('¿Eliminar este sabor o relleno?')) {
      fetch(`/api/cake-config/options/${id}`, { method: 'DELETE' }).then(() => fetchConfig());
    }
  };

  fetchConfig();
});