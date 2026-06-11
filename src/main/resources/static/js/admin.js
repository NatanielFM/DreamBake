// admin.js - Conectado 100% con Base de Datos MySQL (REST API)
document.addEventListener('DOMContentLoaded', () => {

  // ================= 0. TOGGLE DEL MENÚ LATERAL =================
  const sidebarToggleBtn = document.getElementById('sidebar-toggle');
  const adminLayout = document.querySelector('.admin-layout');
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

  const backdrop = document.getElementById('sidebar-backdrop');
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
      }
    });
  });

  // ================= 2. MODAL DE CONFIRMACIÓN =================
  function showConfirmModal(title, message, okText, okBtnClass, onConfirm) {
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

  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.modal-overlay').hidden = true;
    });
  });

  // ================= 3. HEADER =================
  const logoutHeaderBtn = document.getElementById('btn-logout-header');
  if (logoutHeaderBtn) {
    logoutHeaderBtn.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Deseas salir del panel?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

  // ================= 4. CONEXIÓN API REST: PRODUCTOS =================
  let dbProducts = [];
  let currentCategoryFilter = 'all';
  let currentSearchQuery = '';
  let currentPage = 1;
  let itemsPerPage = 12; 

  const emptyViewer = document.getElementById('empty-viewer');
  const activeViewer = document.getElementById('active-viewer');
  const productForm = document.getElementById('product-form');
  const prodSelectCategory = document.getElementById('prod-category');
  
  // FETCH: Cargar Productos
  async function loadProductsFromServer() {
    try {
      const res = await fetch('/api/products');
      if (res.ok) {
        dbProducts = await res.json();
        renderCatalog(); // Pintar grilla actualizada
      }
    } catch (e) {
      console.error("Error cargando productos", e);
    }
  }

  // FETCH: Cargar Categorías
  async function loadCategoriesFromServer() {
    try {
      const res = await fetch('/api/categories');
      if (res.ok) {
        const categories = await res.json();
        
        // Actualizar chips de filtros (Buscador)
        const filterContainer = document.getElementById('filter-container');
        filterContainer.innerHTML = `<button class="chip active" data-filter="all">Todos</button>`;
        
        // Actualizar Select (Formulario)
        prodSelectCategory.innerHTML = '';

        categories.forEach(c => {
          // Chips
          const chip = document.createElement('button');
          chip.className = 'chip';
          chip.dataset.filter = c.name;
          chip.innerText = c.name;
          filterContainer.appendChild(chip);

          // Formulario
          prodSelectCategory.innerHTML += `<option value="${c.name}">${c.name}</option>`;
        });

        prodSelectCategory.innerHTML += `<option value="config" class="config-option">⚙️ Configuración...</option>`;
        
        // Re-asignar eventos a chips nuevos
        assignChipEvents();
      }
    } catch (e) {
      console.error("Error cargando categorías", e);
    }
  }

  // Creador de productos
  document.getElementById('btn-add-new-product').addEventListener('click', () => {
    productForm.reset();
    document.getElementById('prod-id').value = '';
    document.getElementById('prod-image-preview').innerHTML = '<span>Sin Imagen</span>';
    document.getElementById('btn-delete').disabled = true; 
    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
  });

  // Guardar (POST o PUT)
  productForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('prod-id').value;
    
    // Objeto JS armado igual que ProductRequest en Java
    const productData = {
      name: document.getElementById('prod-name').value,
      price: parseFloat(document.getElementById('prod-price').value),
      enabled: document.getElementById('prod-enabled').checked,
      description: document.getElementById('prod-desc').value,
      categoryName: document.getElementById('prod-category').value,
      imageUrl: document.querySelector('#prod-image-preview img')?.src || ''
    };

    const method = id ? 'PUT' : 'POST';
    const url = id ? `/api/products/${id}` : '/api/products';

    try {
      const res = await fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(productData)
      });
      if (res.ok) {
        activeViewer.style.display = 'none';
        emptyViewer.style.display = 'flex';
        loadProductsFromServer(); // Recarga limpia desde MySQL
      }
    } catch (e) {
      alert("Error al guardar en base de datos.");
    }
  });

  // Eliminar (DELETE)
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
      } catch (e) {
        alert("Error eliminando producto.");
      }
    });
  });

  // Descartar
  document.getElementById('btn-discard').addEventListener('click', () => {
    showConfirmModal('Descartar cambios', 'Se perderán los datos no guardados. ¿Deseas continuar?', 'Descartar', 'btn-grey', () => {
      activeViewer.style.display = 'none';
      emptyViewer.style.display = 'flex';
    });
  });

  let lastSelectedCategory = "";
  prodSelectCategory.addEventListener('change', (e) => {
    if (e.target.value === 'config') {
      document.getElementById('modal-category').hidden = false;
      e.target.value = lastSelectedCategory; 
    } else {
      lastSelectedCategory = e.target.value;
    }
  });

  // Crear categoría (POST)
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
        document.getElementById('modal-category').hidden = true;
        loadCategoriesFromServer(); // Refresca filtros
      }
    } catch(e) {
      alert("Error guardando categoría");
    }
  });

  // ================= 5. CATÁLOGO, BUSCADOR Y PAGINACIÓN =================
  
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
    // Filtro contra los datos que vinieron de la DB
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
        
        // Manejamos si no tiene imagen asignada
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

    // Seleccionar categoría correspondiente
    if (p.category) {
        document.getElementById('prod-category').value = p.category.name;
        lastSelectedCategory = p.category.name;
    }

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
    
    document.querySelector('.dashboard').scrollTo({ top: 0, behavior: 'smooth' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ================= 6. INICIALIZACIÓN Y CARGA DESDE BACKEND =================
  loadCategoriesFromServer();
  loadProductsFromServer();
  setTimeout(calculateItemsPerPage, 100);

  // ================= 7. INPUT FILE (Subir imágenes temporal) =================
  const fileInput = document.getElementById('hidden-file-input');
  
  document.getElementById('btn-upload-img').addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
      const file = e.target.files[0];
      const reader = new FileReader();
      reader.onload = function(event) {
        document.getElementById('prod-image-preview').innerHTML = `<img src="${event.target.result}">`;
      };
      reader.readAsDataURL(file);
    }
  });

});