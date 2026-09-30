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
      tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;">No hay recetas registradas.</td></tr>';
      return;
    }

    dbRecipes.forEach(r => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="font-weight: bold;">${r.name}</td>
        <td class="action-buttons-container" style="justify-content: center; display: flex; gap: 8px;">
          <button class="btn btn-grey btn-sm btn-view">Ver receta</button>
          <button class="btn btn-grey btn-sm btn-edit">Editar</button>
          <button class="btn btn-red btn-sm btn-delete">Eliminar</button>
        </td>
      `;
      tr.querySelector('.btn-view').addEventListener('click', () => viewRecipe(r));
      tr.querySelector('.btn-edit').addEventListener('click', () => openRecipeModal(r));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteRecipe(r.id, r.name));
      tbody.appendChild(tr);
    });
  }

  function createItemRow(item = null) {
    const row = document.createElement('div');
    row.style.display = 'flex';
    row.style.gap = '10px';
    row.style.alignItems = 'center';
    
    let options = '<option value="">Seleccione insumo...</option>';
    dbInventory.forEach(inv => {
      const selected = item && item.inventoryItem && item.inventoryItem.id === inv.id ? 'selected' : '';
      options += `<option value="${inv.id}" ${selected}>${inv.name} (${inv.unit})</option>`;
    });

    const qty = item ? item.quantityRequired : '';

    row.innerHTML = `
      <select class="form-input recipe-inv-select" required style="flex: 2;">
        ${options}
      </select>
      <input type="number" class="form-input recipe-qty-input" required step="0.01" min="0.01" placeholder="Cantidad" value="${qty}" style="flex: 1;">
      <button type="button" class="btn btn-red btn-sm btn-remove-row" style="height: 42px;">&times;</button>
    `;
    
    row.querySelector('.btn-remove-row').addEventListener('click', () => row.remove());
    itemsContainer.appendChild(row);
  }

  function openRecipeModal(r = null) {
    form.reset();
    itemsContainer.innerHTML = '';
    if (r) {
      document.getElementById('recipe-modal-title').innerText = 'Editar Receta';
      document.getElementById('recipe-id').value = r.id;
      document.getElementById('recipe-name').value = r.name;
      document.getElementById('recipe-details').value = r.details || '';
      
      if (r.items && r.items.length > 0) {
        r.items.forEach(item => createItemRow(item));
      } else {
        createItemRow();
      }
    } else {
      document.getElementById('recipe-modal-title').innerText = 'Agregar Receta';
      document.getElementById('recipe-id').value = '';
      createItemRow();
    }
    modalForm.hidden = false;
  }

  window.viewRecipe = function(r) {
    document.getElementById('view-recipe-title').innerText = r.name;
    document.getElementById('view-recipe-details').innerText = r.details || 'Sin detalles adicionales.';
    
    const list = document.getElementById('view-recipe-ingredients');
    list.innerHTML = '';
    if (r.items && r.items.length > 0) {
      r.items.forEach(item => {
        const inv = dbInventory.find(i => i.id === item.inventoryItem.id);
        const name = inv ? inv.name : 'Insumo desconocido';
        const unit = inv ? inv.unit : '';
        const li = document.createElement('li');
        li.innerText = `${item.quantityRequired} ${unit} de ${name}`;
        list.appendChild(li);
      });
    } else {
      list.innerHTML = '<li>Sin ingredientes registrados.</li>';
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

  document.addEventListener('inventoryUpdated', async () => {
    await loadInventoryForRecipes();
    // Update any existing selects in the open modal
    const selects = itemsContainer.querySelectorAll('.recipe-inv-select');
    selects.forEach(select => {
      const currentValue = select.value;
      let options = '<option value="">Seleccione insumo...</option>';
      dbInventory.forEach(inv => {
        const selected = currentValue == inv.id ? 'selected' : '';
        options += `<option value="${inv.id}" ${selected}>${inv.name} (${inv.unit})</option>`;
      });
      select.innerHTML = options;
    });
  });
});
