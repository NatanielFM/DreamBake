// /js/admin/products.js - Módulo de Productos y Categorías
document.addEventListener('DOMContentLoaded', () => {
  // Paso 6.8: precios con formato argentino y nombres escapados
  const money = (n) => window.DreamFormat ? window.DreamFormat.money(n) : '$ ' + Number(n || 0).toLocaleString('es-AR');
  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

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
    if(filterContainer) filterContainer.innerHTML = `<button type="button" class="chip active" data-filter="all">Todos</button>`;
    if(prodSelectCategory) prodSelectCategory.innerHTML = '';

    dbCategories.forEach(c => {
      if(filterContainer) {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'chip';
        chip.dataset.filter = c.name;
        chip.innerText = c.name;
        if(currentCategoryFilter === c.name) chip.classList.add('active');
        filterContainer.appendChild(chip);
      }
      if(prodSelectCategory) {
        prodSelectCategory.innerHTML += `<option value="${esc(c.name)}">${esc(c.name)}</option>`;
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
        <span class="cat-name">${esc(c.name)}</span>
        <div class="cat-actions">
            <button type="button" class="cat-action-btn btn-edit" title="Modificar" aria-label="Cambiar el nombre de ${esc(c.name)}">${iconEdit}</button>
            <button type="button" class="cat-action-btn btn-up" title="Subir" ${index === 0 ? 'disabled' : ''}>${iconUp}</button>
            <button type="button" class="cat-action-btn btn-down" title="Bajar" ${index === dbCategories.length - 1 ? 'disabled' : ''}>${iconDown}</button>
            ${c.deletable ? `<button type="button" class="cat-action-btn btn-delete" title="Eliminar" aria-label="Eliminar ${esc(c.name)}">${iconDelete}</button>` : ''}
        </div>
      `;

      li.querySelector('.btn-edit').addEventListener('click', async () => {
        const newName = await window.showCustomPrompt("Escribí el nuevo nombre de la categoría:", c.name);
        if (newName && newName.trim() !== "" && newName !== c.name) {
          try {
            const res = await fetch(`/api/categories/${c.id}`, {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ name: newName.trim() })
            });
            if (!res.ok) await window.DreamUI.fail(res, 'No se pudo cambiar el nombre del tipo.');
          } catch (e) { window.DreamUI.offline(); }
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
          window.showConfirmModal('Eliminar tipo', `¿Seguro que querés eliminar "${c.name}"? Sus productos pasan a "Sin tipo".`, 'Eliminar', 'btn-red', async () => {
            try {
              const res = await fetch(`/api/categories/${c.id}`, { method: 'DELETE' });
              if (!res.ok) await window.DreamUI.fail(res, 'No se pudo eliminar el tipo.');
            } catch (e) { window.DreamUI.offline(); }
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
    btnAddCat.addEventListener('click', () => window.DreamUI.busy(btnAddCat, async () => {
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
          await window.DreamUI.fail(res, 'Ese tipo de producto ya existe.');
        }
      } catch(e) { window.DreamUI.offline(); }
    }));
  }

  // ================= PRODUCTOS =================
  let dbProducts = [];
  let currentCategoryFilter = 'all';
  let currentSearchQuery = '';
  let currentPage = 1;
  let itemsPerPage = 12;

  // Producto sin foto: cupcake de la marca dibujado en SVG (data:, sin pedir nada a internet).
  // Antes se usaba via.placeholder.com y, al guardar, se tomaba "/300" como si fuera la foto.
  const NO_PHOTO = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 30"><rect width="40" height="30" fill="#fbe9f0"/><g transform="translate(10 2) scale(0.5)"><path fill="#481729" d="M10.5 21h19l-2.6 11.3A2.6 2.6 0 0 1 24.4 34.4h-8.8a2.6 2.6 0 0 1-2.5-2.1L10.5 21Z"/><path fill="#e0487f" d="M9.6 22.4c-2 0-3.6-1.6-3.6-3.6 0-1.8 1.3-3.3 3-3.6.4-4 3.8-7.1 7.8-7.1 1.2 0 2.4.3 3.4.8 1.2-.9 2.7-1.4 4.2-1.4 4 0 7.3 3.2 7.4 7.2 1.4.6 2.3 2 2.3 3.6 0 2.3-1.8 4.1-4.1 4.1H9.6Z"/><circle fill="#cf1810" cx="21.5" cy="5.6" r="3"/></g></svg>');

  const emptyViewer = document.getElementById('empty-viewer');
  const activeViewer = document.getElementById('active-viewer');
  const productForm = document.getElementById('product-form');

  let productsLoaded = false;
  async function loadProductsFromServer() {
    const grid = document.getElementById('product-grid');
    if (!productsLoaded) window.DreamUI?.boxLoading(grid, 'Cargando productos…');
    try {
      const res = await fetch('/api/products');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      dbProducts = await res.json();
      productsLoaded = true;
      renderCatalog();
    } catch (e) {
      if (!productsLoaded) window.DreamUI?.boxMessage(grid, 'No se pudieron cargar los productos.', 'Revisá la conexión y recargá la página.', true);
    }
  }

  const btnAddNew = document.getElementById('btn-add-new-product');
  if(btnAddNew) {
    btnAddNew.addEventListener('click', () => {
      productForm.reset();
      document.getElementById('prod-id').value = '';
      document.getElementById('prod-image-preview').innerHTML = '<span>Sin foto</span>';
      document.getElementById('btn-delete').disabled = true;
      updateMarginHint();
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

      // Botón bloqueado mientras se guarda (un doble clic creaba el producto dos veces)
      await window.DreamUI.busy(e.submitter || productForm.querySelector('button[type="submit"]'), async () => {
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
          } else if (res.status === 400 && (res.headers.get('content-type') || '').includes('json')) {
            const errors = await res.json();
            window.showErrorAlert(errors.message || errors);
          } else {
            await window.DreamUI.fail(res, 'No se pudo guardar el producto.');
          }
        } catch (err) {
          window.DreamUI.offline();
        }
      });
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
          } else {
            await window.DreamUI.fail(res, 'No se pudo eliminar el producto.');
          }
        } catch (e) { window.DreamUI.offline(); }
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
    // Solo los chips de Productos: antes usaba '.chip' de toda la página y
    // desmarcaba también los filtros de Inventario y Clientes
    document.querySelectorAll('#filter-container .chip').forEach(chip => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('#filter-container .chip').forEach(c => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
        currentCategoryFilter = e.currentTarget.dataset.filter;
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
      grid.innerHTML = (currentSearchQuery || currentCategoryFilter !== 'all')
        ? '<p class="empty-state grid-span"><strong>Ningún producto coincide</strong>Probá con otra palabra o elegí "Todos".</p>'
        : '<p class="empty-state grid-span"><strong>Todavía no hay productos</strong>Agregá el primero con el botón "Agregar producto".</p>';
    } else {
      paginatedItems.forEach(p => {
        const div = document.createElement('div');
        div.className = `product-card-admin ${!p.enabled ? 'disabled' : ''}`;
        // Se puede abrir con el teclado (Tab + Enter), no solo con el mouse
        div.setAttribute('role', 'button');
        div.tabIndex = 0;
        div.setAttribute('aria-label', `Editar ${p.name}${!p.enabled ? ' (oculto en la tienda)' : ''}`);

        const imgSrc = p.imageUrl && p.imageUrl.trim() !== '' ? p.imageUrl : NO_PHOTO;

        div.innerHTML = `
          <img src="${imgSrc}" alt="" loading="lazy">
          <div class="card-overlay">
            <div class="card-name">${esc(p.name)}</div>
            <div class="card-price">${money(p.price)}</div>
          </div>
        `;
        div.addEventListener('click', () => loadProductIntoViewer(p, imgSrc));
        div.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); loadProductIntoViewer(p, imgSrc); } });
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
    btnPrev.type = 'button';
    btnPrev.setAttribute('aria-label', 'Página anterior');
    btnPrev.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg>';
    btnPrev.disabled = currentPage === 1;
    btnPrev.addEventListener('click', () => { currentPage--; renderCatalog(); });
    container.appendChild(btnPrev);

    for (let i = 1; i <= totalPages; i++) {
      const btnPage = document.createElement('button');
      btnPage.className = `page-btn ${i === currentPage ? 'active' : ''}`;
      btnPage.type = 'button';
      if (i === currentPage) btnPage.setAttribute('aria-current', 'page');
      btnPage.innerText = i;
      btnPage.addEventListener('click', () => { currentPage = i; renderCatalog(); });
      container.appendChild(btnPage);
    }

    const btnNext = document.createElement('button');
    btnNext.className = 'page-btn';
    btnNext.type = 'button';
    btnNext.setAttribute('aria-label', 'Página siguiente');
    btnNext.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>';
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
    document.getElementById('prod-image-preview').innerHTML = `<img src="${imgSrc}" alt="Foto actual del producto">`;
    document.getElementById('btn-delete').disabled = false;

    if (p.category) {
        document.getElementById('prod-category').value = p.category.name;
    }

    if (p.recipe) {
      document.getElementById('prod-recipe').value = p.recipe.id;
    } else {
      document.getElementById('prod-recipe').value = '';
    }
    updateMarginHint();

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

  // ================= 9. COSTO Y GANANCIA DEL PRODUCTO =================
  // Debajo de "Receta": cuánto cuestan los insumos de la receta y cuánto queda de ganancia
  // con el precio escrito. El costo lo calcula el servidor (Recipe.getEstimatedCost) con el
  // costo por unidad de cada insumo que se carga en Inventario.
  let recipesForMargin = [];

  function updateMarginHint() {
    const hint = document.getElementById('prod-margin');
    if (!hint) return;
    const recipe = recipesForMargin.find(r => String(r.id) === document.getElementById('prod-recipe').value);
    const price = parseFloat(document.getElementById('prod-price').value);
    hint.classList.remove('is-negative');
    if (!recipe) { hint.textContent = ''; return; }
    if (!recipe.estimatedCost) { hint.textContent = 'Los insumos de esta receta todavía no tienen costo. Cargalo en Inventario para ver la ganancia.'; return; }
    const cost = recipe.estimatedCost;
    const fmt = (n) => money(Math.round(n));
    let text = `Costo de los insumos: ${fmt(cost)}${recipe.costComplete ? '' : ' (faltan costos de algunos insumos)'}`;
    if (!isNaN(price) && price > 0) {
      const profit = price - cost;
      text += ` · Ganancia: ${fmt(profit)} (${Math.round(profit / price * 100)}%)`;
      hint.classList.toggle('is-negative', profit < 0);
    }
    hint.textContent = text;
  }
  document.getElementById('prod-recipe')?.addEventListener('change', updateMarginHint);
  document.getElementById('prod-price')?.addEventListener('input', updateMarginHint);

  document.addEventListener('recipesLoaded', (e) => {
    recipesForMargin = e.detail;
    const prodRecipeSelect = document.getElementById('prod-recipe');
    if (!prodRecipeSelect) return;

    const currentVal = prodRecipeSelect.value;

    let html = '<option value="">Sin receta asignada</option>';
    e.detail.forEach(r => {
      html += `<option value="${r.id}">${r.name}</option>`;
    });
    prodRecipeSelect.innerHTML = html;

    if (currentVal) prodRecipeSelect.value = currentVal;
    updateMarginHint();
  });

  loadCategoriesFromServer();
  loadProductsFromServer();
  setTimeout(calculateItemsPerPage, 100);

});