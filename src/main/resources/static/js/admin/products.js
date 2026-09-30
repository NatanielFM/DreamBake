// /js/admin/products.js - Módulo de Productos y Categorías
document.addEventListener('DOMContentLoaded', () => {

  // ================= CATEGORÍAS (TIPOS) =================
  let dbCategories = [];
  const prodSelectCategory = document.getElementById('prod-category');

  const iconEdit = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>`;
  const iconUp = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"></path></svg>`;
  const iconDown = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12l7 7 7-7"></path></svg>`;
  const iconDelete = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

  async function loadCategoriesFromServer() {
    try {
      const res = await fetch('/api/categories');
      if (res.ok) {
        dbCategories = await res.json();
        updateCategoryUI();
      }
    } catch (e) {}
  }

  function updateCategoryUI() {
    const filterContainer = document.getElementById('filter-container');
    if(filterContainer) filterContainer.innerHTML = `<button class="chip active" data-filter="all">Todos</button>`;
    if(prodSelectCategory) prodSelectCategory.innerHTML = '';

    dbCategories.forEach(c => {
      if(filterContainer) {
        const chip = document.createElement('button');
        chip.className = 'chip';
        chip.dataset.filter = c.name;
        chip.innerText = c.name;
        if(currentCategoryFilter === c.name) chip.classList.add('active');
        filterContainer.appendChild(chip);
      }
      if(prodSelectCategory) {
        prodSelectCategory.innerHTML += `<option value="${c.name}">${c.name}</option>`;
      }
    });

    assignChipEvents();
    renderCategoryListModal(); 
  }

  function renderCategoryListModal() {
    const list = document.getElementById('category-list');
    if(!list) return;
    list.innerHTML = '';

    dbCategories.forEach((c, index) => {
      const li = document.createElement('li');
      li.innerHTML = `
        <span class="cat-name">${c.name}</span>
        <div class="cat-actions">
            <button type="button" class="cat-action-btn btn-edit" title="Modificar">${iconEdit}</button>
            <button type="button" class="cat-action-btn btn-up" title="Subir" ${index === 0 ? 'disabled' : ''}>${iconUp}</button>
            <button type="button" class="cat-action-btn btn-down" title="Bajar" ${index === dbCategories.length - 1 ? 'disabled' : ''}>${iconDown}</button>
            ${c.deletable ? `<button type="button" class="cat-action-btn btn-delete" title="Eliminar">${iconDelete}</button>` : ''}
        </div>
      `;

      li.querySelector('.btn-edit').addEventListener('click', async () => {
        const newName = await window.showCustomPrompt("Escribe el nuevo nombre para este tipo:", c.name);
        if (newName && newName.trim() !== "" && newName !== c.name) {
          await fetch(`/api/categories/${c.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName.trim() })
          });
          loadCategoriesFromServer();
          loadProductsFromServer(); 
        }
      });

      li.querySelector('.btn-up').addEventListener('click', () => {
        if (index > 0) reorderCategory(index, index - 1);
      });

      li.querySelector('.btn-down').addEventListener('click', () => {
        if (index < dbCategories.length - 1) reorderCategory(index, index + 1);
      });

      if (c.deletable) {
        li.querySelector('.btn-delete').addEventListener('click', () => {
          window.showConfirmModal('Eliminar Tipo', `¿Seguro que deseas eliminar "${c.name}"? Sus productos pasarán a "Sin tipo".`, 'Eliminar', 'btn-red', async () => {
            await fetch(`/api/categories/${c.id}`, { method: 'DELETE' });
            loadCategoriesFromServer();
            loadProductsFromServer(); 
          });
        });
      }
      list.appendChild(li);
    });
  }

  async function reorderCategory(oldIndex, newIndex) {
    const temp = dbCategories[oldIndex];
    dbCategories[oldIndex] = dbCategories[newIndex];
    dbCategories[newIndex] = temp;
    renderCategoryListModal();

    const orderedIds = dbCategories.map(c => c.id);
    try {
      await fetch('/api/categories/reorder', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderedIds)
      });
      loadCategoriesFromServer(); 
    } catch (e) { window.showErrorAlert("No se pudo guardar el nuevo orden."); }
  }

  const btnOpenCat = document.getElementById('btn-open-cat-modal');
  if(btnOpenCat) {
    btnOpenCat.addEventListener('click', () => {
      document.getElementById('modal-category').hidden = false;
      document.getElementById('add-cat-container').style.display = 'none';
      document.getElementById('btn-show-add-cat').style.display = 'block';
    });
  }

  const btnShowAddCat = document.getElementById('btn-show-add-cat');
  if(btnShowAddCat) {
    btnShowAddCat.addEventListener('click', (e) => {
      e.target.style.display = 'none';
      document.getElementById('add-cat-container').style.display = 'flex';
      document.getElementById('new-cat-name').focus();
    });
  }

  const btnAddCat = document.getElementById('btn-add-cat');
  if(btnAddCat) {
    btnAddCat.addEventListener('click', async () => {
      const input = document.getElementById('new-cat-name');
      if (!input.value.trim()) return;
      try {
        const res = await fetch('/api/categories', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: input.value.trim() })
        });
        if (res.ok) {
          input.value = '';
          document.getElementById('add-cat-container').style.display = 'none';
          document.getElementById('btn-show-add-cat').style.display = 'block';
          loadCategoriesFromServer(); 
        } else {
          window.showErrorAlert("Ese tipo de producto ya existe.");
        }
      } catch(e) {}
    });
  }

  // ================= PRODUCTOS =================
  let dbProducts = [];
  let currentCategoryFilter = 'all';
  let currentSearchQuery = '';
  let currentPage = 1;
  let itemsPerPage = 12; 

  const emptyViewer = document.getElementById('empty-viewer');
  const activeViewer = document.getElementById('active-viewer');
  const productForm = document.getElementById('product-form');
  
  async function loadProductsFromServer() {
    try {
      const res = await fetch('/api/products');
      if (res.ok) {
        dbProducts = await res.json();
        renderCatalog(); 
      }
    } catch (e) {}
  }

  const btnAddNew = document.getElementById('btn-add-new-product');
  if(btnAddNew) {
    btnAddNew.addEventListener('click', () => {
      productForm.reset();
      document.getElementById('prod-id').value = '';
      document.getElementById('prod-image-preview').innerHTML = '<span>Sin Imagen</span>';
      document.getElementById('btn-delete').disabled = true; 
      emptyViewer.style.display = 'none';
      activeViewer.style.display = 'block';
    });
  }

  if(productForm) {
    productForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('prod-id').value;
      
      let imageUrl = '';
      const previewImg = document.querySelector('#prod-image-preview img');
      if (previewImg && !previewImg.src.startsWith('data:')) {
          imageUrl = new URL(previewImg.src).pathname; 
      }

      const recipeId = document.getElementById('prod-recipe').value;

      const productData = {
        name: document.getElementById('prod-name').value,
        price: parseFloat(document.getElementById('prod-price').value),
        enabled: document.getElementById('prod-enabled').checked,
        description: document.getElementById('prod-desc').value,
        categoryName: document.getElementById('prod-category').value,
        imageUrl: imageUrl
      };

      if (recipeId) {
        productData.recipe = { id: parseInt(recipeId) };
      } else {
        productData.recipe = null;
      }

      const url = id ? `/api/products/${id}` : '/api/products';
      const method = id ? 'PUT' : 'POST';

      try {
        const res = await fetch(url, {
          method: method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(productData)
        });
        
        if (res.ok) {
          activeViewer.style.display = 'none';
          emptyViewer.style.display = 'flex';
          loadProductsFromServer();
        } else if (res.status === 400) {
          const errors = await res.json();
          window.showErrorAlert(errors);
        } else {
          window.showErrorAlert("Ocurrió un error inesperado al guardar.");
        }
      } catch (e) {
        window.showErrorAlert("Error de conexión al servidor.");
      }
    });
  }

  const btnDel = document.getElementById('btn-delete');
  if(btnDel) {
    btnDel.addEventListener('click', () => {
      window.showConfirmModal('Eliminar producto', 'Esta acción no se puede deshacer. ¿Eliminar este producto del sistema?', 'Eliminar', 'btn-red', async () => {
        const id = document.getElementById('prod-id').value;
        if (!id) return;
        try {
          const res = await fetch(`/api/products/${id}`, { method: 'DELETE' });
          if (res.ok) {
            activeViewer.style.display = 'none';
            emptyViewer.style.display = 'flex';
            loadProductsFromServer();
          }
        } catch (e) {}
      });
    });
  }

  const btnDisc = document.getElementById('btn-discard');
  if(btnDisc) {
    btnDisc.addEventListener('click', () => {
      window.showConfirmModal('Descartar cambios', 'Se perderán los datos no guardados. ¿Deseas continuar?', 'Descartar', 'btn-grey', () => {
        activeViewer.style.display = 'none';
        emptyViewer.style.display = 'flex';
      });
    });
  }

  // ================= CATÁLOGO =================
  function calculateItemsPerPage() {
    const grid = document.getElementById('product-grid');
    if (!grid) return;

    let gridWidth = grid.clientWidth;
    if (gridWidth <= 0) {
      const sidebarW = (window.innerWidth > 880 && !document.body.classList.contains('sidebar-toggled')) ? 220 : 0;
      gridWidth = window.innerWidth - sidebarW - 48; 
    }
    if (gridWidth <= 0) gridWidth = 400;

    const minColWidth = 220; 
    const gap = 24; 
    
    let cols = Math.floor((gridWidth + gap) / (minColWidth + gap));
    if (cols < 1) cols = 1;

    let newItemsPerPage = cols * 3;
    if (newItemsPerPage < 6) newItemsPerPage = 6; 

    if (newItemsPerPage !== itemsPerPage) {
      itemsPerPage = newItemsPerPage;
      currentPage = 1; 
      renderCatalog();
    }
  }

  function assignChipEvents() {
    document.querySelectorAll('.chip').forEach(chip => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
        e.target.classList.add('active');
        currentCategoryFilter = e.target.dataset.filter;
        currentPage = 1; 
        renderCatalog();
      });
    });
  }

  const searchProd = document.getElementById('search-product');
  if(searchProd) {
    searchProd.addEventListener('input', (e) => {
      currentSearchQuery = e.target.value.toLowerCase();
      currentPage = 1; 
      renderCatalog();
    });
  }

  function renderCatalog() {
    if (!itemsPerPage || itemsPerPage < 1) itemsPerPage = 12;

    let filtered = dbProducts.filter(p => {
      const matchCat = currentCategoryFilter === 'all' || (p.category && p.category.name === currentCategoryFilter);
      const matchSearch = p.name.toLowerCase().includes(currentSearchQuery);
      return matchCat && matchSearch;
    });

    const totalPages = Math.ceil(filtered.length / itemsPerPage);
    if (currentPage > totalPages && totalPages > 0) currentPage = totalPages;

    const startIdx = (currentPage - 1) * itemsPerPage;
    const paginatedItems = filtered.slice(startIdx, startIdx + itemsPerPage);

    const grid = document.getElementById('product-grid');
    if(!grid) return;
    grid.innerHTML = '';
    
    if (paginatedItems.length === 0) {
      grid.innerHTML = '<p style="grid-column: 1/-1; text-align:center; padding: 2rem; color: #666;">No hay productos creados.</p>';
    } else {
      paginatedItems.forEach(p => {
        const div = document.createElement('div');
        div.className = `product-card-admin ${!p.enabled ? 'disabled' : ''}`;
        
        const imgSrc = p.imageUrl && p.imageUrl.trim() !== '' ? p.imageUrl : 'https://via.placeholder.com/300?text=Sin+Foto';

        div.innerHTML = `
          <img src="${imgSrc}" alt="${p.name}">
          <div class="card-overlay">
            <div class="card-name">${p.name}</div>
            <div class="card-price">$${p.price}</div>
          </div>
        `;
        div.addEventListener('click', () => loadProductIntoViewer(p, imgSrc));
        grid.appendChild(div);
      });
    }

    renderPaginationControls(totalPages);
  }

  function renderPaginationControls(totalPages) {
    const container = document.getElementById('pagination-controls');
    if(!container) return;
    container.innerHTML = '';
    if (totalPages <= 1) return;

    const btnPrev = document.createElement('button');
    btnPrev.className = 'page-btn';
    btnPrev.innerText = '<';
    btnPrev.disabled = currentPage === 1;
    btnPrev.addEventListener('click', () => { currentPage--; renderCatalog(); });
    container.appendChild(btnPrev);

    for (let i = 1; i <= totalPages; i++) {
      const btnPage = document.createElement('button');
      btnPage.className = `page-btn ${i === currentPage ? 'active' : ''}`;
      btnPage.innerText = i;
      btnPage.addEventListener('click', () => { currentPage = i; renderCatalog(); });
      container.appendChild(btnPage);
    }

    const btnNext = document.createElement('button');
    btnNext.className = 'page-btn';
    btnNext.innerText = '>';
    btnNext.disabled = currentPage === totalPages;
    btnNext.addEventListener('click', () => { currentPage++; renderCatalog(); });
    container.appendChild(btnNext);
  }

  function loadProductIntoViewer(p, imgSrc) {
    document.getElementById('prod-id').value = p.id;
    document.getElementById('prod-name').value = p.name;
    document.getElementById('prod-price').value = p.price;
    document.getElementById('prod-desc').value = p.description || '';
    document.getElementById('prod-enabled').checked = p.enabled;
    document.getElementById('prod-image-preview').innerHTML = `<img src="${imgSrc}">`;
    document.getElementById('btn-delete').disabled = false; 

    if (p.category) {
        document.getElementById('prod-category').value = p.category.name;
    }

    if (p.recipe) {
      document.getElementById('prod-recipe').value = p.recipe.id;
    } else {
      document.getElementById('prod-recipe').value = '';
    }

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
    
    document.querySelector('.dashboard').scrollTo({ top: 0, behavior: 'smooth' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ================= 8. EVENTOS DE LAYOUT Y CARGA INICIAL =================
  document.addEventListener('tabChanged', e => {
    if (e.detail === 'productos') {
      renderCatalog();
      setTimeout(calculateItemsPerPage, 50);
    }
  });

  document.addEventListener('layoutChanged', () => {
    if (document.getElementById('productos').classList.contains('active')) {
      setTimeout(calculateItemsPerPage, 300);
    }
  });

  window.addEventListener('resize', () => {
    if (document.getElementById('productos').classList.contains('active')) {
      calculateItemsPerPage();
    }
  });

  document.addEventListener('recipesLoaded', (e) => {
    const prodRecipeSelect = document.getElementById('prod-recipe');
    if (!prodRecipeSelect) return;
    
    const currentVal = prodRecipeSelect.value;
    
    let html = '<option value="">Sin receta asignada</option>';
    e.detail.forEach(r => {
      html += `<option value="${r.id}">${r.name}</option>`;
    });
    prodRecipeSelect.innerHTML = html;
    
    if (currentVal) prodRecipeSelect.value = currentVal;
  });

  loadCategoriesFromServer();
  loadProductsFromServer();
  setTimeout(calculateItemsPerPage, 100);

});