// /js/admin/inventory.js - Módulo de Inventario Unificado y Avanzado
document.addEventListener('DOMContentLoaded', () => {

  let dbInventory = [];
  const inventoryTbody = document.getElementById('inventory-tbody');
  const searchInventory = document.getElementById('search-inventory');
  const filterInventory = document.getElementById('filter-inventory');

  // SVGs de acciones
  const iconConsume = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"></line></svg>`;
  const iconEdit = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>`;
  const iconDelete = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

  function formatDateToLatam(dateStr) {
    if (!dateStr) return 'N/A';
    const [year, month, day] = dateStr.split('-');
    return `${day}/${month}/${year}`;
  }

  async function loadInventoryFromServer() {
    try {
      const res = await fetch('/api/inventory');
      if (res.ok) {
        dbInventory = await res.json();
        renderInventory();
      }
    } catch (e) { console.error(e); }
  }

  function renderInventory() {
    if (!inventoryTbody) return;
    inventoryTbody.innerHTML = '';
    
    const query = searchInventory ? searchInventory.value.toLowerCase() : '';
    const filter = filterInventory ? filterInventory.value : 'all';

    let filtered = dbInventory.filter(item => {
      if (query && !item.name.toLowerCase().includes(query)) return false;
      const isLowStock = item.currentStock <= item.minimumStock;
      const isExpiring = isExpiringSoon(item.expirationDate);
      if (filter === 'low' && !isLowStock) return false;
      if (filter === 'expiring' && !isExpiring) return false;
      return true;
    });

    if (filtered.length === 0) {
      inventoryTbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem; color:#666;">No se encontraron insumos.</td></tr>`;
      return;
    }

    filtered.forEach(item => {
      const tr = document.createElement('tr');
      const isLowStock = item.currentStock <= item.minimumStock;
      const isExpiring = isExpiringSoon(item.expirationDate);
      
      let stockClass = isLowStock ? 'text-danger' : '';
      let expClass = isExpiring ? 'text-warning' : '';
      
      let dateAddText = formatDateToLatam(item.dateAdded);
      let expText = item.expirationDate ? formatDateToLatam(item.expirationDate) : 'No especificado';

      tr.innerHTML = `
        <td style="font-weight: 600;">${item.name}</td>
        <td class="${stockClass}">${item.currentStock} ${item.unit}</td>
        <td>${item.minimumStock} ${item.unit}</td>
        <td style="color: #666; font-size:0.9em;">${dateAddText}</td>
        <td class="${expClass}">${expText}</td>
        <td style="text-align: right; display:flex; gap:5px; justify-content:flex-end;">
          <button class="cat-action-btn btn-consume" title="Descontar Stock" style="color: var(--accent);">${iconConsume}</button>
          <button class="cat-action-btn btn-edit" title="Modificar">${iconEdit}</button>
          <button class="cat-action-btn btn-delete" title="Eliminar">${iconDelete}</button>
        </td>
      `;

      // Eventos
      tr.querySelector('.btn-consume').addEventListener('click', () => openManageInventory(item, 'CONSUME'));
      tr.querySelector('.btn-edit').addEventListener('click', () => openInventoryForm(item));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteInventoryItem(item));

      inventoryTbody.appendChild(tr);
    });
  }

  function isExpiringSoon(dateString) {
    if (!dateString) return false;
    const diffDays = Math.ceil((new Date(dateString) - new Date()) / (1000 * 60 * 60 * 24));
    return diffDays >= 0 && diffDays <= 7;
  }

  if(searchInventory) searchInventory.addEventListener('input', renderInventory);
  if(filterInventory) filterInventory.addEventListener('change', renderInventory);

  // ================= GESTIÓN RÁPIDA (BOTÓN CENTRAL) =================
  const btnOpenManage = document.getElementById('btn-open-manage-inv');
  if(btnOpenManage) {
    btnOpenManage.addEventListener('click', () => openManageInventory());
  }

  function populateManageSelect(preselectId = null) {
    const select = document.getElementById('manage-item-id');
    select.innerHTML = '';
    
    if (dbInventory.length === 0) {
      select.innerHTML = `<option value="" disabled>No hay insumos creados</option>`;
      return;
    }

    dbInventory.forEach(item => {
      const exp = item.expirationDate ? ` (Vence: ${formatDateToLatam(item.expirationDate)})` : '';
      const stock = ` - Stock: ${item.currentStock} ${item.unit}`;
      select.innerHTML += `<option value="${item.id}" data-unit="${item.unit}">${item.name}${stock}${exp}</option>`;
    });

    if(preselectId) {
      select.value = preselectId;
    }
    updateManageUnitDisplay();
  }

  function updateManageUnitDisplay() {
    const select = document.getElementById('manage-item-id');
    if (select.options.length > 0 && select.selectedIndex >= 0) {
      const unit = select.options[select.selectedIndex].getAttribute('data-unit');
      document.getElementById('manage-unit-display').value = unit || '';
    }
  }

  document.getElementById('manage-item-id').addEventListener('change', updateManageUnitDisplay);

  document.querySelectorAll('input[name="manage_action"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      document.getElementById('manage-exp-container').style.display = e.target.value === 'ADD' ? 'block' : 'none';
    });
  });

  function openManageInventory(preselectItem = null, action = 'CONSUME') {
    populateManageSelect(preselectItem ? preselectItem.id : null);
    document.querySelector(`input[name="manage_action"][value="${action}"]`).checked = true;
    document.getElementById('manage-exp-container').style.display = action === 'ADD' ? 'block' : 'none';
    document.getElementById('manage-amount').value = '';
    document.getElementById('manage-exp').value = '';
    document.getElementById('modal-manage-inventory').hidden = false;
  }

  document.getElementById('manage-inv-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('manage-item-id').value;
    if (!id) {
       window.showErrorAlert("Debes crear un insumo primero.");
       return;
    }

    const action = document.querySelector('input[name="manage_action"]:checked').value;
    const amount = parseFloat(document.getElementById('manage-amount').value);
    const expDate = document.getElementById('manage-exp').value || null;

    try {
      const res = await fetch(`/api/inventory/${id}/manage`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: action, amount: amount, expirationDate: expDate })
      });
      if (res.ok) {
        document.getElementById('modal-manage-inventory').hidden = true;
        loadInventoryFromServer();
      } else {
        window.showErrorAlert(await res.text());
      }
    } catch(e) { window.showErrorAlert("Error de red"); }
  });


  // ================= FORMULARIO CLÁSICO (Agregar Insumo Nuevo desde cero) =================
  const inventoryForm = document.getElementById('inventory-form');
  const btnAddInv = document.getElementById('btn-add-inventory');
  
  if(btnAddInv) {
    btnAddInv.addEventListener('click', () => {
      inventoryForm.reset();
      document.getElementById('inv-id').value = '';
      document.getElementById('inv-modal-title').innerText = "Agregar Insumo / Lote";
      document.getElementById('modal-inventory-form').hidden = false;
    });
  }

  function openInventoryForm(item) {
    inventoryForm.reset();
    document.getElementById('inv-id').value = item.id;
    document.getElementById('inv-name').value = item.name;
    document.getElementById('inv-current').value = item.currentStock;
    document.getElementById('inv-min').value = item.minimumStock;
    document.getElementById('inv-unit').value = item.unit;
    document.getElementById('inv-exp').value = item.expirationDate || '';
    
    document.getElementById('inv-modal-title').innerText = "Modificar Insumo";
    document.getElementById('modal-inventory-form').hidden = false;
  }

  if(inventoryForm) {
    inventoryForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('inv-id').value;
      const data = {
        name: document.getElementById('inv-name').value,
        currentStock: parseFloat(document.getElementById('inv-current').value),
        minimumStock: parseFloat(document.getElementById('inv-min').value),
        unit: document.getElementById('inv-unit').value,
        expirationDate: document.getElementById('inv-exp').value || null
      };

      const url = id ? `/api/inventory/${id}` : '/api/inventory';
      const method = id ? 'PUT' : 'POST';

      try {
        const res = await fetch(url, {
          method: method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        });
        if (res.ok) {
          document.getElementById('modal-inventory-form').hidden = true;
          loadInventoryFromServer();
        } else if (res.status === 400) {
          window.showErrorAlert(await res.json());
        }
      } catch(e) { window.showErrorAlert("Error de conexión"); }
    });
  }

  function deleteInventoryItem(item) {
    window.showConfirmModal('Eliminar Insumo', `¿Seguro que deseas eliminar "${item.name}" del inventario?`, 'Eliminar', 'btn-red', async () => {
      await fetch(`/api/inventory/${item.id}`, { method: 'DELETE' });
      loadInventoryFromServer();
    });
  }

  // ================= PEDIDO DE COMPRA (Magia de Impresión / PDF Nativos) =================
  const btnOrder = document.getElementById('btn-generate-order');
  if(btnOrder) {
    btnOrder.addEventListener('click', () => {
      const lowStockItems = dbInventory.filter(i => i.currentStock <= i.minimumStock);
      
      if (lowStockItems.length === 0) {
        window.showErrorAlert("¡Tu inventario está perfecto! No hay insumos con stock bajo para pedir.");
        return;
      }

      let printHtml = `
        <div style="font-family: Arial, sans-serif; color: #222; max-width: 800px; margin: 0 auto;">
          <div style="border-bottom: 3px solid var(--accent, #e0487f); margin-bottom: 20px; padding-bottom: 10px;">
            <h1 style="margin: 0; color: #333;">Pedido de Compra</h1>
            <p style="margin: 5px 0 0 0; color: #666;">Sistema DreamBake</p>
          </div>
          
          <table style="width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 14px;">
            <tr>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Insumo</th>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Stock Actual</th>
              <th style="border: 1px solid #ddd; padding: 12px; background: #f8f9fa; text-align: left;">Vencimiento</th>
            </tr>
      `;

      lowStockItems.forEach(i => {
        let exp = i.expirationDate ? formatDateToLatam(i.expirationDate) : 'No especificado';
        printHtml += `
          <tr>
            <td style="border: 1px solid #ddd; padding: 12px; font-weight: bold;">${i.name}</td>
            <td style="border: 1px solid #ddd; padding: 12px; color: #dc3545; font-weight: bold;">${i.currentStock} ${i.unit}</td>
            <td style="border: 1px solid #ddd; padding: 12px; color: #666;">${exp}</td>
          </tr>
        `;
      });

      printHtml += `</table></div>`;

      // Inyectar HTML en el área oculta de impresión
      const printArea = document.getElementById('print-area');
      printArea.innerHTML = printHtml;

      // Truco maestro para el encabezado nativo del navegador
      const originalTitle = document.title;
      document.title = "Archivo generado automáticamente";

      // Abrir diálogo de impresión (El usuario puede elegir "Guardar como PDF")
      window.print();

      // Restaurar estado
      document.title = originalTitle;
      printArea.innerHTML = '';
    });
  }

  // Escuchar cambio de pestaña
  document.addEventListener('tabChanged', e => {
    if (e.detail === 'inventario') loadInventoryFromServer();
  });

  // Carga Inicial
  loadInventoryFromServer();
});