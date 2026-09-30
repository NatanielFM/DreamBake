// admin.js - Gestión Completa de Categorías y Alerta v1
document.addEventListener('DOMContentLoaded', () => {

  // ================= 0. TOGGLE DEL MENÚ LATERAL =================
  const sidebarToggleBtn = document.getElementById('sidebar-toggle');
  const backdrop = document.getElementById('sidebar-backdrop');
  const body = document.body;
  
  if (sidebarToggleBtn) {
    sidebarToggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      body.classList.toggle('sidebar-toggled');
      if (document.getElementById('productos').classList.contains('active')) {
        setTimeout(calculateItemsPerPage, 300);
      }
    });
  }

  if (backdrop) {
    backdrop.addEventListener('click', () => {
      body.classList.remove('sidebar-toggled');
    });
  }

  let isMobile = window.innerWidth <= 880;
  window.addEventListener('resize', () => {
    const currentlyMobile = window.innerWidth <= 880;
    if (isMobile !== currentlyMobile) {
      body.classList.remove('sidebar-toggled');
      isMobile = currentlyMobile;
    }
    if (document.getElementById('productos').classList.contains('active')) {
      calculateItemsPerPage();
    }
  });

  // ================= 1. NAVEGACIÓN Y TABS =================
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      
      btn.classList.add('active');
      document.getElementById(btn.dataset.tab).classList.add('active');
      
      if (window.innerWidth <= 880) body.classList.remove('sidebar-toggled');
      
      if (btn.dataset.tab === 'productos') {
        renderCatalog(); 
        setTimeout(calculateItemsPerPage, 50); 
      } else if (btn.dataset.tab === 'galeria') {
        loadGalleryFromServer();
      }
    });
  });

  // ================= 2. MODALES Y ALERTA V1 =================
  window.showCustomPrompt = function(message, defaultValue = '', type = 'text') {
    return new Promise((resolve) => {
      document.getElementById('custom-prompt-title').innerText = 'Ingreso de datos';
      document.getElementById('custom-prompt-message').innerText = message;
      const input = document.getElementById('custom-prompt-input');
      input.type = type;
      input.value = defaultValue;
      document.getElementById('modal-custom-prompt').hidden = false;
      input.focus();

      const btnConfirm = document.getElementById('btn-custom-prompt-confirm');
      const btnCancel = document.getElementById('btn-custom-prompt-cancel');

      const newBtnConfirm = btnConfirm.cloneNode(true);
      const newBtnCancel = btnCancel.cloneNode(true);
      btnConfirm.replaceWith(newBtnConfirm);
      btnCancel.replaceWith(newBtnCancel);

      newBtnConfirm.addEventListener('click', () => { document.getElementById('modal-custom-prompt').hidden = true; resolve(document.getElementById('custom-prompt-input').value); });
      newBtnCancel.addEventListener('click', () => { document.getElementById('modal-custom-prompt').hidden = true; resolve(null); });
    });
  };

  window.showConfirmModal = function(title, message, okText, okBtnClass, onConfirm) {
    document.getElementById('confirm-title').innerText = title;
    document.getElementById('confirm-message').innerText = message;
    
    const btnOk = document.getElementById('btn-confirm-ok');
    btnOk.innerText = okText;
    btnOk.className = `btn ${okBtnClass} btn-uniform`;

    document.getElementById('modal-confirm').hidden = false;

    const newBtnOk = btnOk.cloneNode(true);
    btnOk.replaceWith(newBtnOk);
    const btnCancel = document.getElementById('btn-confirm-cancel');
    const newBtnCancel = btnCancel.cloneNode(true);
    btnCancel.replaceWith(newBtnCancel);

    newBtnOk.addEventListener('click', () => {
      document.getElementById('modal-confirm').hidden = true;
      onConfirm();
    });

    newBtnCancel.addEventListener('click', () => {
      document.getElementById('modal-confirm').hidden = true;
    });
  }

  // ALERTA V1 (Diseño Estandarizado)
  function showErrorAlert(errors) {
    let msg = "";
    if (typeof errors === 'object') {
      for (const key in errors) {
        msg += `${errors[key]}\n`;
      }
    } else {
      msg += errors;
    }
    
    document.getElementById('alert-message').innerText = msg.trim();
    document.getElementById('modal-alert').hidden = false;
  }

  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.modal-overlay').hidden = true;
    });
  });

  // ================= 3. HEADER =================
  const logoutHeaderBtn = document.getElementById('btn-logout-header');
  if (logoutHeaderBtn) {
    logoutHeaderBtn.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Estás seguro que deseas salir?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

  // ================= 4. GALERÍA Y SUBIDA DE IMÁGENES =================
  let dbGallery = [];
  const fileInput = document.getElementById('hidden-file-input');
  
  async function loadGalleryFromServer() {
    try {
      const res = await fetch('/api/gallery');
      if (res.ok) {
        dbGallery = await res.json();
        renderGallery('main-gallery-grid', false);
        renderGallery('picker-gallery-grid', true);
      }
    } catch (e) {}
  }

  function renderGallery(containerId, isPicker) {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    const uploadBtn = container.querySelector('.upload-item');
    container.innerHTML = '';
    if (uploadBtn) container.appendChild(uploadBtn);

    dbGallery.forEach(img => {
      const div = document.createElement('div');
      div.className = 'gallery-item';
      div.innerHTML = `<img src="${img.url}">`;
      
      if (isPicker) {
        div.addEventListener('click', () => {
          document.getElementById('prod-image-preview').innerHTML = `<img src="${img.url}">`;
          document.getElementById('modal-gallery-picker').hidden = true;
        });
      }
      container.appendChild(div);
    });
  }

  document.getElementById('btn-upload-img').addEventListener('click', () => fileInput.click());
  document.getElementById('btn-gallery-upload-new').addEventListener('click', () => fileInput.click());
  document.getElementById('btn-gallery-img').addEventListener('click', () => {
    loadGalleryFromServer();
    document.getElementById('modal-gallery-picker').hidden = false;
  });

  fileInput.addEventListener('change', async (e) => {
    if (e.target.files.length > 0) {
      const file = e.target.files[0];
      const formData = new FormData();
      formData.append("file", file);

      try {
        const res = await fetch('/api/gallery', { method: 'POST', body: formData });
        if (res.ok) {
          const newImage = await res.json();
          document.getElementById('prod-image-preview').innerHTML = `<img src="${newImage.url}">`;
          loadGalleryFromServer();
        } else {
          showErrorAlert("No se pudo subir la imagen.");
        }
      } catch (e) {
        showErrorAlert("Fallo en la conexión al servidor de imágenes.");
      }
    }
  });

  // ================= 5. GESTIÓN DE CATEGORÍAS (TIPOS) =================
  let dbCategories = [];
  const prodSelectCategory = document.getElementById('prod-category');

  // SVG Icons
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
    // 1. Filtros (Chips)
    const filterContainer = document.getElementById('filter-container');
    filterContainer.innerHTML = `<button class="chip active" data-filter="all">Todos</button>`;
    
    // 2. Select del Formulario
    prodSelectCategory.innerHTML = '';

    dbCategories.forEach(c => {
      const chip = document.createElement('button');
      chip.className = 'chip';
      chip.dataset.filter = c.name;
      chip.innerText = c.name;
      if(currentCategoryFilter === c.name) chip.classList.add('active');
      filterContainer.appendChild(chip);

      prodSelectCategory.innerHTML += `<option value="${c.name}">${c.name}</option>`;
    });

    assignChipEvents();
    renderCategoryListModal(); // Dibuja la lista del modal
  }

  // Dibujar lista dentro del Modal "Configuración"
  function renderCategoryListModal() {
    const list = document.getElementById('category-list');
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

      // Evento Editar
      li.querySelector('.btn-edit').addEventListener('click', async () => {
        const newName = await window.showCustomPrompt("Escribe el nuevo nombre para este tipo:", c.name);
        if (newName && newName.trim() !== "" && newName !== c.name) {
          await fetch(`/api/categories/${c.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName.trim() })
          });
          loadCategoriesFromServer();
          loadProductsFromServer(); // Recargar productos porque cambió un nombre de categoría
        }
      });

      // Evento Subir
      li.querySelector('.btn-up').addEventListener('click', () => {
        if (index > 0) reorderCategory(index, index - 1);
      });

      // Evento Bajar
      li.querySelector('.btn-down').addEventListener('click', () => {
        if (index < dbCategories.length - 1) reorderCategory(index, index + 1);
      });

      // Evento Eliminar
      if (c.deletable) {
        li.querySelector('.btn-delete').addEventListener('click', () => {
          showConfirmModal('Eliminar Tipo', `¿Seguro que deseas eliminar "${c.name}"? Sus productos pasarán a "Sin tipo".`, 'Eliminar', 'btn-red', async () => {
            await fetch(`/api/categories/${c.id}`, { method: 'DELETE' });
            loadCategoriesFromServer();
            loadProductsFromServer(); 
          });
        });
      }

      list.appendChild(li);
    });
  }

  // Intercambiar elementos en el array y enviar actualización al servidor
  async function reorderCategory(oldIndex, newIndex) {
    // Intercambio en array local
    const temp = dbCategories[oldIndex];
    dbCategories[oldIndex] = dbCategories[newIndex];
    dbCategories[newIndex] = temp;

    // Repintar rápido para UX fluida
    renderCategoryListModal();

    // Extraer la lista de IDs en el nuevo orden
    const orderedIds = dbCategories.map(c => c.id);

    try {
      await fetch('/api/categories/reorder', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderedIds)
      });
      loadCategoriesFromServer(); // Confirmar con la DB
    } catch (e) { showErrorAlert("No se pudo guardar el nuevo orden."); }
  }

  // Abrir Modal de Categorías (Botón Engranaje)
  document.getElementById('btn-open-cat-modal').addEventListener('click', () => {
    document.getElementById('modal-category').hidden = false;
    document.getElementById('add-cat-container').style.display = 'none';
    document.getElementById('btn-show-add-cat').style.display = 'block';
  });

  // Mostrar input de crear nuevo tipo
  document.getElementById('btn-show-add-cat').addEventListener('click', (e) => {
    e.target.style.display = 'none';
    document.getElementById('add-cat-container').style.display = 'flex';
    document.getElementById('new-cat-name').focus();
  });

  // Guardar nueva categoría
  document.getElementById('btn-add-cat').addEventListener('click', async () => {
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
        showErrorAlert("Ese tipo de producto ya existe.");
      }
    } catch(e) {}
  });

  // ================= 6. CONEXIÓN API REST: PRODUCTOS =================
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

  document.getElementById('btn-add-new-product').addEventListener('click', () => {
    productForm.reset();
    document.getElementById('prod-id').value = '';
    document.getElementById('prod-image-preview').innerHTML = '<span>Sin Imagen</span>';
    document.getElementById('btn-delete').disabled = true; 
    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
  });

  productForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('prod-id').value;
    
    let imageUrl = '';
    const previewImg = document.querySelector('#prod-image-preview img');
    if (previewImg && !previewImg.src.startsWith('data:')) {
        imageUrl = new URL(previewImg.src).pathname; 
    }

    const productData = {
      name: document.getElementById('prod-name').value,
      price: parseFloat(document.getElementById('prod-price').value),
      enabled: document.getElementById('prod-enabled').checked,
      description: document.getElementById('prod-desc').value,
      categoryName: document.getElementById('prod-category').value,
      imageUrl: imageUrl
    };

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
        showErrorAlert(errors);
      } else {
        showErrorAlert("Ocurrió un error inesperado al guardar.");
      }
    } catch (e) {
      showErrorAlert("Error de conexión al servidor.");
    }
  });

  document.getElementById('btn-delete').addEventListener('click', () => {
    showConfirmModal('Eliminar producto', 'Esta acción no se puede deshacer. ¿Eliminar este producto del sistema?', 'Eliminar', 'btn-red', async () => {
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

  document.getElementById('btn-discard').addEventListener('click', () => {
    showConfirmModal('Descartar cambios', 'Se perderán los datos no guardados. ¿Deseas continuar?', 'Descartar', 'btn-grey', () => {
      activeViewer.style.display = 'none';
      emptyViewer.style.display = 'flex';
    });
  });

  // ================= 7. CATÁLOGO Y PAGINACIÓN =================
  function calculateItemsPerPage() {
    const grid = document.getElementById('product-grid');
    if (!grid) return;

    let gridWidth = grid.clientWidth;
    if (gridWidth <= 0) {
      const sidebarW = (window.innerWidth > 880 && !body.classList.contains('sidebar-toggled')) ? 220 : 0;
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

  document.getElementById('search-product').addEventListener('input', (e) => {
    currentSearchQuery = e.target.value.toLowerCase();
    currentPage = 1; 
    renderCatalog();
  });

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
    grid.innerHTML = '';
    
    if (paginatedItems.length === 0) {
      grid.innerHTML = '<p style="grid-column: 1/-1; text-align:center; padding: 2rem; color: #666;">No hay productos creados. ¡Agrega uno arriba!</p>';
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
        // Alerta v1 solucionada - No pisamos variables que no existen
    }

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
    
    document.querySelector('.dashboard').scrollTo({ top: 0, behavior: 'smooth' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ================= 8. INICIO =================
  loadCategoriesFromServer();
  loadProductsFromServer();
  setTimeout(calculateItemsPerPage, 100);

});