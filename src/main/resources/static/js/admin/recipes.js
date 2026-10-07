// /js/admin/recipes.js - Módulo de Recetas
document.addEventListener('DOMContentLoaded', () => {
  const tbody = document.getElementById('recipes-tbody');
  const modalForm = document.getElementById('modal-recipe-form');
  const form = document.getElementById('recipe-form');
  const itemsContainer = document.getElementById('recipe-items-container');
  const btnAddRow = document.getElementById('btn-add-recipe-item');
  const btnAddRecipe = document.getElementById('btn-add-recipe');
  
  let dbRecipes = [];
  let dbInventory = [];

  // El costo se muestra solo en el panel del admin (el del pastelero no ve dinero)
  const showCost = document.body.dataset.panel === 'admin';
  const money = (n) => '$' + Number(n || 0).toLocaleString('es-AR', { maximumFractionDigits: 2 });

  // "Costo: $1.250" o, si algún insumo no tiene costo cargado, "Costo parcial: $800"
  function costLabel(r) {
    if (!r.items || r.items.length === 0) return '';
    if (!r.costComplete && !r.estimatedCost) return 'Sin costo (cargalo en Inventario)';
    return `${r.costComplete ? 'Costo' : 'Costo parcial'}: ${money(r.estimatedCost)}`;
  }

  async function loadInventoryForRecipes() {
    try {
      const res = await fetch('/api/inventory');
      if (res.ok) dbInventory = await res.json();
    } catch(e) {}
  }

  async function loadRecipes() {
    try {
      const res = await fetch('/api/recipes');
      if (res.ok) {
        dbRecipes = await res.json();
        renderRecipes();
        document.dispatchEvent(new CustomEvent('recipesLoaded', { detail: dbRecipes }));
      }
    } catch(e) {}
  }

  function renderRecipes() {
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (dbRecipes.length === 0) {
      tbody.innerHTML = '<tr><td colspan="2" class="cell-empty">Todavía no hay recetas cargadas.</td></tr>';
      return;
    }

    dbRecipes.forEach(r => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="cell-title cell-strong">${r.name}<span class="cell-soft recipe-count">${(r.items || []).length} ${(r.items || []).length === 1 ? 'insumo' : 'insumos'}${showCost && costLabel(r) ? ` · ${costLabel(r)}` : ''}</span></td>
        <td class="cell-actions"><div class="action-buttons-container">
          <button type="button" class="btn btn-grey btn-sm btn-view">Ver receta</button>
          <button type="button" class="btn btn-grey btn-sm btn-edit">Editar</button>
          <button type="button" class="btn btn-red btn-sm btn-delete">Eliminar</button>
        </div></td>
      `;
      tr.querySelector('.btn-view').addEventListener('click', () => viewRecipe(r));
      tr.querySelector('.btn-edit').addEventListener('click', () => openRecipeModal(r));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteRecipe(r.id, r.name));
      tbody.appendChild(tr);
    });
  }

  function createItemRow(item = null) {
    const row = document.createElement('div');
    row.className = 'recipe-item-row';   // estilos en admin.css
    
    let options = '<option value="">Elegí un insumo…</option>';
    dbInventory.forEach(inv => {
      const selected = item && item.inventoryItem && item.inventoryItem.id === inv.id ? 'selected' : '';
      options += `<option value="${inv.id}" ${selected}>${inv.name} (${inv.unit})</option>`;
    });

    const qty = item ? item.quantityRequired : '';

    row.innerHTML = `
      <select class="form-input recipe-inv-select" required aria-label="Insumo">
        ${options}
      </select>
      <input type="number" class="form-input recipe-qty-input" required step="0.01" min="0.01" placeholder="Cantidad" aria-label="Cantidad" inputmode="decimal" value="${qty}">
      <button type="button" class="btn btn-grey btn-icon btn-remove-row" aria-label="Quitar este insumo" title="Quitar">&times;</button>
    `;
    
    row.querySelector('.btn-remove-row').addEventListener('click', () => row.remove());
    itemsContainer.appendChild(row);
  }

  function openRecipeModal(r = null) {
    form.reset();
    itemsContainer.innerHTML = '';
    if (r) {
      document.getElementById('recipe-modal-title').innerText = 'Editar receta';
      document.getElementById('recipe-id').value = r.id;
      document.getElementById('recipe-name').value = r.name;
      document.getElementById('recipe-details').value = r.details || '';
      
      if (r.items && r.items.length > 0) {
        r.items.forEach(item => createItemRow(item));
      } else {
        createItemRow();
      }
    } else {
      document.getElementById('recipe-modal-title').innerText = 'Nueva receta';
      document.getElementById('recipe-id').value = '';
      createItemRow();
    }
    modalForm.hidden = false;
  }

  window.viewRecipe = function(r) {
    document.getElementById('view-recipe-title').innerText = r.name;
    document.getElementById('view-recipe-details').innerText = r.details || 'Sin preparación cargada.';
    
    const list = document.getElementById('view-recipe-ingredients');
    list.innerHTML = '';
    if (r.items && r.items.length > 0) {
      r.items.forEach(item => {
        const inv = dbInventory.find(i => i.id === item.inventoryItem.id);
        const name = inv ? inv.name : 'Insumo desconocido';
        const unit = inv ? inv.unit : '';
        const li = document.createElement('li');
        li.innerText = `${item.quantityRequired} ${unit} de ${name}`;
        // Admin: cuánto cuesta esa cantidad (cantidad × costo por unidad del insumo)
        if (showCost && inv && inv.unitCost != null) li.innerText += ` · ${money(item.quantityRequired * inv.unitCost)}`;
        list.appendChild(li);
      });
      if (showCost) {
        const total = document.createElement('li');
        total.className = 'recipe-cost-total';
        total.innerText = costLabel(r);
        list.appendChild(total);
      }
    } else {
      list.innerHTML = '<li>Todavía no tiene insumos cargados.</li>';
    }
    
    document.getElementById('modal-view-recipe').hidden = false;
  }

  if (btnAddRecipe) {
    btnAddRecipe.addEventListener('click', () => {
      openRecipeModal();
    });
  }

  if (btnAddRow) {
    btnAddRow.addEventListener('click', () => createItemRow());
  }

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      
      const id = document.getElementById('recipe-id').value;
      
      const items = [];
      const rows = itemsContainer.querySelectorAll('div');
      rows.forEach(row => {
        const invId = row.querySelector('.recipe-inv-select').value;
        const qty = row.querySelector('.recipe-qty-input').value;
        if (invId && qty) {
          items.push({
            inventoryItem: { id: parseInt(invId) },
            quantityRequired: parseFloat(qty)
          });
        }
      });

      const payload = {
        name: document.getElementById('recipe-name').value,
        details: document.getElementById('recipe-details').value,
        items: items
      };

      const url = id ? `/api/recipes/${id}` : '/api/recipes';
      const method = id ? 'PUT' : 'POST';

      try {
        const res = await fetch(url, {
          method: method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        if (res.ok) {
          modalForm.hidden = true;
          loadRecipes();
        } else {
          window.showErrorAlert("Error al guardar la receta.");
        }
      } catch(err) {
        window.showErrorAlert("Error de conexión.");
      }
    });
  }

  function deleteRecipe(id, name) {
    window.showConfirmModal('Eliminar Receta', `¿Seguro que deseas eliminar la receta "${name}"?`, 'Eliminar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/recipes/${id}`, { method: 'DELETE' });
        if (res.ok) {
          loadRecipes();
        } else {
          window.showErrorAlert("No se pudo eliminar.");
        }
      } catch(e) {}
    });
  }

  loadInventoryForRecipes();
  loadRecipes();

  // Al abrir Recetas o Productos se vuelven a pedir las recetas: el costo de los insumos
  // pudo cambiar en Inventario (y con él el costo de cada receta y la ganancia del producto)
  document.addEventListener('tabChanged', async (e) => {
    if (e.detail === 'recetas' || e.detail === 'productos') {
      await loadInventoryForRecipes();
      loadRecipes();
    }
  });

  document.addEventListener('inventoryUpdated', async () => {
    await loadInventoryForRecipes();
    loadRecipes();   // el costo de las recetas depende del costo de los insumos
    // Update any existing selects in the open modal
    const selects = itemsContainer.querySelectorAll('.recipe-inv-select');
    selects.forEach(select => {
      const currentValue = select.value;
      let options = '<option value="">Elegí un insumo…</option>';
      dbInventory.forEach(inv => {
        const selected = currentValue == inv.id ? 'selected' : '';
        options += `<option value="${inv.id}" ${selected}>${inv.name} (${inv.unit})</option>`;
      });
      select.innerHTML = options;
    });
  });
});
