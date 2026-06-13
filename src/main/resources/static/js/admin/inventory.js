// /js/admin/inventory.js - Módulo de Inventario
document.addEventListener('DOMContentLoaded', () => {

  let dbInventory = [];
  const inventoryTbody = document.getElementById('inventory-tbody');
  const inventoryForm = document.getElementById('inventory-form');
  const searchInventory = document.getElementById('search-inventory');
  const filterInventory = document.getElementById('filter-inventory');

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
      inventoryTbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding:2rem; color:#666;">No se encontraron insumos.</td></tr>`;
      return;
    }

    filtered.forEach(item => {
      const tr = document.createElement('tr');
      const isLowStock = item.currentStock <= item.minimumStock;
      const isExpiring = isExpiringSoon(item.expirationDate);
      
      let stockClass = isLowStock ? 'text-danger' : '';
      let expClass = isExpiring ? 'text-warning' : '';
      let expText = item.expirationDate ? item.expirationDate : 'N/A';

      tr.innerHTML = `
        <td style="font-weight: 600;">${item.name}</td>
        <td class="${stockClass}">${item.currentStock} ${item.unit}</td>
        <td>${item.minimumStock} ${item.unit}</td>
        <td class="${expClass}">${expText}</td>
        <td style="text-align: right; display:flex; gap:5px; justify-content:flex-end;">
          <button class="btn btn-pink btn-sm btn-consume" title="Descontar Stock">➖ Restar</button>
          <button class="btn btn-grey btn-sm btn-edit" title="Modificar">✏️</button>
          <button class="btn btn-grey btn-sm btn-delete" title="Eliminar" style="color:var(--danger)">🗑️</button>
        </td>
      `;

      tr.querySelector('.btn-consume').addEventListener('click', () => openQuickConsume(item));
      tr.querySelector('.btn-edit').addEventListener('click', () => openInventoryForm(item));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteInventoryItem(item));

      inventoryTbody.appendChild(tr);
    });
  }

  function isExpiringSoon(dateString) {
    if (!dateString) return false;
    const expDate = new Date(dateString);
    const today = new Date();
    const diffDays = Math.ceil((expDate - today) / (1000 * 60 * 60 * 24));
    return diffDays >= 0 && diffDays <= 7;
  }

  if(searchInventory) searchInventory.addEventListener('input', renderInventory);
  if(filterInventory) filterInventory.addEventListener('change', renderInventory);

  const btnAddInv = document.getElementById('btn-add-inventory');
  if(btnAddInv) {
    btnAddInv.addEventListener('click', () => {
      inventoryForm.reset();
      document.getElementById('inv-id').value = '';
      document.getElementById('inv-modal-title').innerText = "Agregar Insumo";
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

  function openQuickConsume(item) {
    document.getElementById('consume-item-id').value = item.id;
    document.getElementById('consume-item-name').innerText = item.name;
    document.getElementById('consume-item-unit').innerText = item.unit;
    document.getElementById('consume-amount').value = '';
    document.getElementById('modal-quick-consume').hidden = false;
    setTimeout(() => document.getElementById('consume-amount').focus(), 100);
  }

  const btnConsume = document.getElementById('btn-confirm-consume');
  if(btnConsume) {
    btnConsume.addEventListener('click', async () => {
      const id = document.getElementById('consume-item-id').value;
      const amount = parseFloat(document.getElementById('consume-amount').value);
      
      if (!amount || amount <= 0) return;

      try {
        const res = await fetch(`/api/inventory/${id}/consume`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ amount: amount })
        });
        if (res.ok) {
          document.getElementById('modal-quick-consume').hidden = true;
          loadInventoryFromServer();
        } else {
          window.showErrorAlert("Error al descontar stock.");
        }
      } catch(e) {}
    });
  }

  function deleteInventoryItem(item) {
    window.showConfirmModal('Eliminar Insumo', `¿Seguro que deseas eliminar "${item.name}" del inventario?`, 'Eliminar', 'btn-red', async () => {
      await fetch(`/api/inventory/${item.id}`, { method: 'DELETE' });
      loadInventoryFromServer();
    });
  }

  const btnOrder = document.getElementById('btn-generate-order');
  if(btnOrder) {
    btnOrder.addEventListener('click', () => {
      const lowStockItems = dbInventory.filter(i => i.currentStock <= i.minimumStock);
      
      if (lowStockItems.length === 0) {
        window.showErrorAlert("¡Tu inventario está perfecto! No hay insumos con stock bajo.");
        return;
      }

      let printHtml = `
        <html><head><title>Pedido de Compra - DreamBake</title><style>
          body { font-family: Arial, sans-serif; padding: 20px; }
          table { width: 100%; border-collapse: collapse; margin-top: 20px; }
          th, td { border: 1px solid #ccc; padding: 10px; text-align: left; }
          th { background: #f4f4f4; }
        </style></head><body>
          <h2>Lista de Pedido de Compra</h2>
          <p>Fecha: ${new Date().toLocaleDateString()}</p>
          <table><tr><th>Insumo</th><th>Stock Actual</th><th>Sugerido a comprar</th></tr>
      `;

      lowStockItems.forEach(i => {
        let toBuy = (i.minimumStock * 2) - i.currentStock;
        if (toBuy < 0) toBuy = i.minimumStock; 
        printHtml += `<tr><td>${i.name}</td><td style="color:red;">${i.currentStock} ${i.unit}</td><td><strong>${toBuy} ${i.unit}</strong></td></tr>`;
      });

      printHtml += `</table><script>window.print();</script></body></html>`;
      const printWin = window.open('', '_blank');
      printWin.document.write(printHtml);
      printWin.document.close();
    });
  }

  // Escuchar cambio de pestaña
  document.addEventListener('tabChanged', e => {
    if (e.detail === 'inventario') loadInventoryFromServer();
  });

  // Carga Inicial
  loadInventoryFromServer();
});