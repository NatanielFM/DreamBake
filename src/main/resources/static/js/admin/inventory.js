// /js/admin/inventory.js - Módulo de Inventario (Con SVGs más grandes)
document.addEventListener('DOMContentLoaded', () => {
  // Paso 6.8: formato argentino y textos escapados
  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const money = (n) => window.DreamFormat ? window.DreamFormat.money(n) : '$ ' + Number(n || 0).toLocaleString('es-AR');
  const num = (n) => window.DreamFormat ? window.DreamFormat.number(n) : String(n);   // 1500.5 → "1.500,5"

  // ================= COSTO POR UNIDAD (solo panel del admin) =================
  // La columna "Costo" existe solo en admin.html; el panel del pastelero no muestra dinero.
  const showCost = Boolean(document.querySelector('#inventario th[data-col="cost"]'));
  const UNIT_SINGULAR = { Kg: 'kg', Gramos: 'gramo', Litros: 'litro', Mililitros: 'mililitro', Unidades: 'unidad', Cajas: 'caja' };
  const unitSingular = (unit) => UNIT_SINGULAR[unit] || unit;

  function costText(item) {
    if (item.unitCost == null) return 'Sin cargar';
    return `${money(item.unitCost)} / ${esc(unitSingular(item.unit))}`;
  }

  // Texto de ayuda del formulario: "Lo que cuesta 1 kg"
  function updateCostHint() {
    const label = document.getElementById('inv-cost-unit');
    if (label && invUnitSelect) label.textContent = `1 ${unitSingular(invUnitSelect.value)}`;
  }


  let dbInventory = [];
  const inventoryTbody = document.getElementById('inventory-tbody');
  const searchInventory = document.getElementById('search-inventory');
  const filterInventory = document.getElementById('filter-inventory');

  // Estado de Ordenamiento
  let sortCol = ''; 
  let sortAsc = true;

  // SVGs explícitos con tamaños aumentados (width="20" height="20")
  const iconEdit = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>`;
  const iconDelete = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>`;

  // Función para generar el SVG de ordenamiento con colores dinámicos y mayor tamaño
  function getSortIcon(col) {
    let colorUp = '#ccc'; 
    let colorDown = '#ccc';

    if (sortCol === col) {
      if (sortAsc) {
        colorUp = 'var(--accent)'; 
        colorDown = '#eee'; 
      } else {
        colorUp = '#eee';
        colorDown = 'var(--accent)';
      }
    }

    // SVG aumentado a width="22" height="22" para que las flechas se lean claras
    return `<svg class="sort-svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="7 9 12 4 17 9" stroke="${colorUp}"></polyline>
              <polyline points="7 15 12 20 17 15" stroke="${colorDown}"></polyline>
            </svg>`;
  }

  function formatDateToLatam(dateStr) {
    if (!dateStr) return 'N/A';
    const [year, month, day] = dateStr.split('-');
    return `${day}/${month}/${year}`;
  }

  function isExpired(dateString) {
    if (!dateString) return false;
    const expDate = new Date(dateString);
    const today = new Date();
    today.setHours(0,0,0,0);
    return expDate < today;
  }

  function isExpiringSoon(dateString) {
    if (!dateString) return false;
    const expDate = new Date(dateString);
    const today = new Date();
    today.setHours(0,0,0,0);
    const diffDays = Math.ceil((expDate - today) / (1000 * 60 * 60 * 24));
    return diffDays >= 0 && diffDays <= 7;
  }

  let invLoaded = false;
  async function loadInventoryFromServer() {
    const cols = showCost ? 7 : 6;
    if (!invLoaded) window.DreamUI?.tableLoading(inventoryTbody, cols, 'Cargando insumos…');
    try {
      const res = await fetch('/api/inventory');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      dbInventory = await res.json();
      invLoaded = true;
      renderInventory();
    } catch (e) {
      if (!invLoaded) window.DreamUI?.tableMessage(inventoryTbody, cols, 'No se pudo cargar el inventario.', 'Revisá la conexión y recargá la página.', true);
    }
  }

  // Lógica de Eventos de Ordenamiento (Sort)
  const thName = document.getElementById('th-sort-name');
  const thDate = document.getElementById('th-sort-date');

  if (thName) thName.addEventListener('click', () => setSort('name'));
  if (thDate) thDate.addEventListener('click', () => setSort('dateAdded'));

  function setSort(col) {
    if (sortCol === col) {
      sortAsc = !sortAsc;
    } else {
      sortCol = col;
      sortAsc = true;
    }
    renderInventory();
  }

  let currentInvFilter = 'all';

  function renderInventory() {
    if (!inventoryTbody) return;
    inventoryTbody.innerHTML = '';
    
    // Inyectamos el título limpio junto al nuevo SVG
    if (thName) thName.innerHTML = `Insumo <span class="sort-icon">${getSortIcon('name')}</span>`;
    if (thDate) thDate.innerHTML = `Última actualización <span class="sort-icon">${getSortIcon('dateAdded')}</span>`;

    const query = searchInventory ? searchInventory.value.toLowerCase() : '';

    let filtered = dbInventory.filter(item => {
      if (query && !item.name.toLowerCase().includes(query)) return false;
      const isLowStock = item.currentStock <= item.minimumStock;
      const expiring = isExpiringSoon(item.expirationDate);
      const expired = isExpired(item.expirationDate);

      if (currentInvFilter === 'low' && !isLowStock) return false;
      if (currentInvFilter === 'expiring' && !expiring && !expired) return false;
      return true;
    });

    if (sortCol) {
      filtered.sort((a, b) => {
        let valA = a[sortCol];
        let valB = b[sortCol];
        
        if (sortCol === 'name') {
          valA = valA.toLowerCase();
          valB = valB.toLowerCase();
        } else if (sortCol === 'dateAdded') {
          valA = valA ? new Date(valA).getTime() : 0;
          valB = valB ? new Date(valB).getTime() : 0;
        }

        if (valA < valB) return sortAsc ? -1 : 1;
        if (valA > valB) return sortAsc ? 1 : -1;
        return 0;
      });
    }

    if (filtered.length === 0) {
      const hasQuery = searchInventory && searchInventory.value.trim() !== '';
      const emptyText = hasQuery ? 'Ningún insumo coincide con la búsqueda.'
        : currentInvFilter === 'low' ? 'Ningún insumo tiene stock bajo.'
        : currentInvFilter === 'expiring' ? 'Ningún insumo vence en los próximos 7 días.'
        : 'Todavía no hay insumos cargados. Agregá el primero con "Nuevo insumo".';
      inventoryTbody.innerHTML = `<tr><td colspan="${showCost ? 7 : 6}" class="cell-empty">${emptyText}</td></tr>`;
      return;
    }

    filtered.forEach(item => {
      const tr = document.createElement('tr');
      const isLowStock = item.currentStock <= item.minimumStock;
      const expiring = isExpiringSoon(item.expirationDate);
      const expired = isExpired(item.expirationDate);
      
      if (expired) {
        tr.className = 'tr-expired';
      } else if (isLowStock) {
        tr.className = 'tr-low-stock';
      }

      let expClass = (expiring || expired) ? 'text-danger' : '';
      let dateAddText = formatDateToLatam(item.dateAdded);
      let expText = item.expirationDate ? formatDateToLatam(item.expirationDate) : 'Sin fecha';

      // El estado va escrito (no solo con el color de la fila)
      let stateBadge = '';
      if (expired) stateBadge = '<span class="status-badge is-small status-cancelled">Vencido</span>';
      else if (expiring) stateBadge = '<span class="status-badge is-small status-pending">Vence pronto</span>';
      if (isLowStock) stateBadge += '<span class="status-badge is-small status-pending">Stock bajo</span>';

      // data-label: en el celular cada fila se ve como tarjeta con "Etiqueta: valor"
      tr.innerHTML = `
        <td class="cell-title"><span class="cell-strong">${esc(item.name)}</span>${stateBadge ? `<div class="status-stack">${stateBadge}</div>` : ''}</td>
        <td data-label="Stock actual" class="${isLowStock ? 'text-danger' : ''}">${num(item.currentStock)} ${esc(item.unit)}</td>
        <td data-label="Stock mínimo">${num(item.minimumStock)} ${esc(item.unit)}</td>
        ${showCost ? `<td data-label="Costo" class="${item.unitCost == null ? 'cell-soft' : 'cell-money'}">${costText(item)}</td>` : ''}
        <td data-label="Actualizado" class="cell-soft">${dateAddText}</td>
        <td data-label="Vence" class="${expClass}">${expText}</td>
        <td class="cell-actions"><div class="action-buttons-container">
          <button type="button" class="btn btn-grey btn-sm btn-edit">${iconEdit} Editar</button>
          <button type="button" class="btn btn-red btn-sm btn-delete">${iconDelete} Eliminar</button>
        </div></td>
      `;

      tr.querySelector('.btn-edit').addEventListener('click', () => openInventoryForm(item));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteInventoryItem(item));

      inventoryTbody.appendChild(tr);
    });
  }

  if(searchInventory) searchInventory.addEventListener('input', renderInventory);

  document.querySelectorAll('#inventory-filters .chip').forEach(chip => {
    chip.addEventListener('click', (e) => {
      document.querySelectorAll('#inventory-filters .chip').forEach(c => c.classList.remove('active'));
      e.currentTarget.classList.add('active');
      currentInvFilter = e.currentTarget.dataset.filter;
      renderInventory();
    });
  });

  // ================= FORMULARIO (Agregar / Editar) Y VALIDACIÓN DE DECIMALES =================
  const inventoryForm = document.getElementById('inventory-form');
  const btnAddInv = document.getElementById('btn-add-inventory');
  
  const invUnitSelect = document.getElementById('inv-unit');
  const invCurrentInput = document.getElementById('inv-current');
  const invMinInput = document.getElementById('inv-min');

  function updateStepBasedOnUnit() {
      updateCostHint();
      if (['Unidades', 'Cajas'].includes(invUnitSelect.value)) {
          invCurrentInput.step = "1";
          invMinInput.step = "1";
      } else {
          invCurrentInput.step = "0.01";
          invMinInput.step = "0.01";
      }
  }

  if(invUnitSelect) {
      invUnitSelect.addEventListener('change', updateStepBasedOnUnit);
  }

  if(btnAddInv) {
    btnAddInv.addEventListener('click', () => {
      inventoryForm.reset();
      document.getElementById('inv-id').value = '';
      document.getElementById('inv-modal-title').innerText = "Nuevo insumo";
      updateStepBasedOnUnit(); 
      document.getElementById('modal-inventory-form').hidden = false;
    });
  }

  function openInventoryForm(item) {
    inventoryForm.reset();
    document.getElementById('inv-id').value = item.id;
    document.getElementById('inv-name').value = item.name;
    document.getElementById('inv-current').value = item.currentStock;
    document.getElementById('inv-min').value = item.minimumStock || '';
    document.getElementById('inv-unit').value = item.unit;
    document.getElementById('inv-exp').value = item.expirationDate || '';
    const costInput = document.getElementById('inv-cost');
    if (costInput) costInput.value = item.unitCost ?? '';
    
    updateStepBasedOnUnit(); 
    
    document.getElementById('inv-modal-title').innerText = "Editar insumo";
    document.getElementById('modal-inventory-form').hidden = false;
  }

  if(inventoryForm) {
    inventoryForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('inv-id').value;
      const unit = document.getElementById('inv-unit').value;
      
      const currentStock = parseFloat(document.getElementById('inv-current').value);
      const minStockVal = document.getElementById('inv-min').value;
      const minimumStock = minStockVal ? parseFloat(minStockVal) : 0.0;

      if (['Unidades', 'Cajas'].includes(unit)) {
          if (!Number.isInteger(currentStock) || !Number.isInteger(minimumStock)) {
              window.showErrorAlert("Los insumos medidos en 'Unidades' o 'Cajas' no pueden tener valores decimales.");
              return; 
          }
      }

      const data = {
        name: document.getElementById('inv-name').value,
        currentStock: currentStock,
        minimumStock: minimumStock,
        unit: unit,
        expirationDate: document.getElementById('inv-exp').value || null
      };
      // Costo por unidad (el campo solo existe en el panel del admin)
      const costInput = document.getElementById('inv-cost');
      if (costInput) data.unitCost = costInput.value === '' ? null : parseFloat(costInput.value);

      const url = id ? `/api/inventory/${id}` : '/api/inventory';
      const method = id ? 'PUT' : 'POST';

      // Botón bloqueado mientras se guarda: un doble clic creaba el insumo dos veces
      await window.DreamUI.busy(e.submitter || inventoryForm.querySelector('button[type="submit"]'), async () => {
        try {
          const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
          });
          if (res.ok) {
            document.getElementById('modal-inventory-form').hidden = true;
            loadInventoryFromServer();
            document.dispatchEvent(new CustomEvent('inventoryUpdated'));
          } else if (res.status === 400 && (res.headers.get('content-type') || '').includes('json')) {
            const body = await res.json();
            window.showErrorAlert(body.message || body);   // errores por campo de la validación
          } else {
            await window.DreamUI.fail(res, 'No se pudo guardar el insumo.');
          }
        } catch(err) { window.DreamUI.offline(); }
      });
    });
  }

  function deleteInventoryItem(item) {
    window.showConfirmModal('Eliminar insumo', `¿Seguro que querés eliminar "${item.name}" del inventario?`, 'Eliminar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/inventory/${item.id}`, { method: 'DELETE' });
        if (res.ok) loadInventoryFromServer();
        else await window.DreamUI.fail(res, 'No se pudo eliminar el insumo (puede estar en una receta).');
      } catch (e) { window.DreamUI.offline(); }
    });
  }

  // ================= PEDIDO DE COMPRA (PDF Nativo) =================
  const btnOrder = document.getElementById('btn-generate-order');
  if(btnOrder) {
    btnOrder.addEventListener('click', () => {
      window.logDownload?.('lista-compras'); // auditoría: quién descargó la lista de compras
      
      const alertItems = dbInventory.filter(i => {
         return (i.currentStock <= i.minimumStock) || isExpiringSoon(i.expirationDate) || isExpired(i.expirationDate);
      });
      
      if (alertItems.length === 0) {
        window.showErrorAlert("¡Tu inventario está perfecto! No hay insumos con stock bajo ni por vencer.");
        return;
      }

      let printHtml = `
        <div style="font-family: Arial, sans-serif; color: #222; max-width: 900px; margin: 0 auto; padding: 2cm;">
          <div style="border-bottom: 3px solid var(--accent, #e0487f); margin-bottom: 20px; padding-bottom: 10px;">
            <h1 style="margin: 0; color: #333;">Pedido de Compra Sugerido</h1>
            <p style="margin: 5px 0 0 0; color: #666;">Sistema DreamBake - Generado el ${window.DreamFormat ? window.DreamFormat.date(new Date()) : new Date().toLocaleDateString('es-AR')}</p>
          </div>
          
          <table style="width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 15px;">
            <tr>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Insumo</th>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Stock Actual</th>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Vencimiento</th>
            </tr>
      `;

      alertItems.forEach(i => {
        let isExp = isExpired(i.expirationDate) || isExpiringSoon(i.expirationDate);
        let expStyle = isExp ? 'color: red; font-weight: bold;' : 'color: #666;';
        let expText = i.expirationDate ? formatDateToLatam(i.expirationDate) : 'No especificado';
        if (isExpired(i.expirationDate)) expText += ' (Vencido)';

        let isLow = i.currentStock <= i.minimumStock;
        let stockStyle = isLow ? 'color: red; font-weight: bold;' : 'color: #333;';

        printHtml += `
          <tr>
            <td style="border: 1px solid #ddd; padding: 12px; font-weight: bold;">${esc(i.name)}</td>
            <td style="border: 1px solid #ddd; padding: 12px; ${stockStyle}">${num(i.currentStock)} ${esc(i.unit)}</td>
            <td style="border: 1px solid #ddd; padding: 12px; ${expStyle}">${expText}</td>
          </tr>
        `;
      });

      printHtml += `</table></div>`;

      const printArea = document.getElementById('print-area');
      printArea.innerHTML = printHtml;

      const originalTitle = document.title;
      document.title = "Archivo generado automaticamente";

      window.print();

      document.title = originalTitle;
      printArea.innerHTML = '';
    });
  }

  document.addEventListener('tabChanged', e => {
    if (e.detail === 'inventario') loadInventoryFromServer();
  });

  loadInventoryFromServer();
});