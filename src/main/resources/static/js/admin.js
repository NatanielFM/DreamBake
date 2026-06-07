// admin.js - Paginación Dinámica, Menú Colapsable, y Lógica de UI
document.addEventListener('DOMContentLoaded', () => {

  // ================= 0. TOGGLE DEL MENÚ LATERAL =================
  const sidebarToggleBtn = document.getElementById('sidebar-toggle');
  const adminLayout = document.querySelector('.admin-layout');
  
  if (sidebarToggleBtn && adminLayout) {
    sidebarToggleBtn.addEventListener('click', () => {
      adminLayout.classList.toggle('sidebar-collapsed');
    });
  }

  // ================= 1. NAVEGACIÓN Y TABS =================
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById(btn.dataset.tab).classList.add('active');
      
      // Si entramos a productos, recalcular grid por si cambió el tamaño
      if (btn.dataset.tab === 'productos') {
        calculateItemsPerPage();
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

  // ================= 3. LOGOUT DESDE EL HEADER =================
  const logoutHeaderBtn = document.getElementById('btn-logout-header');
  if (logoutHeaderBtn) {
    logoutHeaderBtn.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Estás seguro que deseas salir del panel de administración?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

  // Campanita de Notificaciones
  const bellBtn = document.getElementById('btn-notifications');
  if(bellBtn) {
    bellBtn.addEventListener('click', () => {
      alert("No hay notificaciones nuevas por ahora.");
    });
  }

  // ================= 4. VISOR DE PRODUCTOS =================
  const emptyViewer = document.getElementById('empty-viewer');
  const activeViewer = document.getElementById('active-viewer');
  const btnAddProduct = document.getElementById('btn-add-new-product');
  const btnDiscard = document.getElementById('btn-discard');
  const btnDelete = document.getElementById('btn-delete');
  const productForm = document.getElementById('product-form');
  const prodSelectCategory = document.getElementById('prod-category');
  const prodIdInput = document.getElementById('prod-id');

  btnAddProduct.addEventListener('click', () => {
    productForm.reset();
    prodIdInput.value = '';
    document.getElementById('prod-image-preview').innerHTML = '<span>Sin Imagen</span>';
    btnDelete.disabled = true; 
    populateCategories();
    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
  });

  btnDiscard.addEventListener('click', () => {
    showConfirmModal('Descartar cambios', 'Se perderán los datos no guardados. ¿Deseas continuar?', 'Descartar', 'btn-grey', () => {
      activeViewer.style.display = 'none';
      emptyViewer.style.display = 'flex';
    });
  });

  btnDelete.addEventListener('click', () => {
    showConfirmModal('Eliminar producto', 'Esta acción no se puede deshacer. ¿Eliminar este producto del sistema?', 'Eliminar', 'btn-red', () => {
      alert("Lógica de borrado lista para Backend");
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

  function populateCategories() {
    const cats = ['Sin tipo', 'Tortas', 'Dulces', 'Souvenir']; 
    prodSelectCategory.innerHTML = cats.map(c => `<option value="${c}">${c}</option>`).join('');
    prodSelectCategory.innerHTML += `<option value="config" class="config-option">⚙️ Configuración...</option>`;
    lastSelectedCategory = cats[0];
  }

  // ================= 5. CATÁLOGO Y PAGINACIÓN DINÁMICA =================
  const dummyProducts = Array.from({length: 40}, (_, i) => ({
    id: i + 1, 
    name: `Producto de prueba ${i + 1}`, 
    price: 1500 * (i + 1), 
    cat: i % 2 === 0 ? 'Tortas' : (i % 3 === 0 ? 'Souvenir' : 'Dulces'), 
    img: `https://picsum.photos/seed/${i}/300/300`, 
    enabled: i % 5 !== 0
  }));

  let currentCategoryFilter = 'all';
  let currentSearchQuery = '';
  let currentPage = 1;
  let itemsPerPage = 12; // Valor inicial que se recalculará dinámicamente

  // --- LÓGICA DE RECALCULO DE COLUMNAS (MAGIA RESPONSIVE) ---
  function calculateItemsPerPage() {
    const grid = document.getElementById('product-grid');
    if (!grid || grid.offsetParent === null) return; // Si no existe o está oculto, salir

    const gridWidth = grid.clientWidth;
    const minColWidth = 220; // Exactamente el minmax() de nuestro CSS
    const gap = 24; // 1.5rem gap en CSS = 24px
    
    // Matemática: Columnas = (AnchoTotal + Gap) / (AnchoMínimo + Gap)
    let cols = Math.floor((gridWidth + gap) / (minColWidth + gap));
    if (cols < 1) cols = 1;

    // Queremos 3 filas perfectas (ej: 4 cols = 12 items, 5 cols = 15 items)
    let newItemsPerPage = cols * 3;
    if (newItemsPerPage < 6) newItemsPerPage = 6; // Mínimo absoluto

    // Si la cantidad de items por página cambia, recalculamos el catálogo
    if (newItemsPerPage !== itemsPerPage) {
      itemsPerPage = newItemsPerPage;
      currentPage = 1; // Volvemos a página 1 para evitar bugs de index
      updateCatalog();
    }
  }

  // Observar cambios en el tamaño del dashboard (Se dispara al abrir/cerrar sidebar o resize ventana)
  const dashboardEl = document.querySelector('.dashboard');
  if (dashboardEl) {
    const resizeObserver = new ResizeObserver(() => {
      window.requestAnimationFrame(() => {
        if (document.getElementById('productos').classList.contains('active')) {
          calculateItemsPerPage();
        }
      });
    });
    resizeObserver.observe(dashboardEl);
  }

  // --- FIN LÓGICA RECALCULO ---

  document.querySelectorAll('.chip').forEach(chip => {
    chip.addEventListener('click', (e) => {
      document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
      e.target.classList.add('active');
      currentCategoryFilter = e.target.dataset.filter;
      currentPage = 1; 
      updateCatalog();
    });
  });

  document.getElementById('search-product').addEventListener('input', (e) => {
    currentSearchQuery = e.target.value.toLowerCase();
    currentPage = 1; 
    updateCatalog();
  });

  function updateCatalog() {
    let filtered = dummyProducts.filter(p => {
      const matchCat = currentCategoryFilter === 'all' || p.cat === currentCategoryFilter;
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
      grid.innerHTML = '<p style="grid-column: 1/-1; text-align:center; padding: 2rem; color: #666;">No se encontraron productos.</p>';
    } else {
      paginatedItems.forEach(p => {
        const div = document.createElement('div');
        div.className = `product-card-admin ${!p.enabled ? 'disabled' : ''}`;
        div.innerHTML = `
          <img src="${p.img}" alt="${p.name}">
          <div class="card-overlay">
            <div class="card-name">${p.name}</div>
            <div class="card-price">$${p.price}</div>
          </div>
        `;
        div.addEventListener('click', () => loadProductIntoViewer(p));
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
    btnPrev.addEventListener('click', () => { currentPage--; updateCatalog(); });
    container.appendChild(btnPrev);

    for (let i = 1; i <= totalPages; i++) {
      const btnPage = document.createElement('button');
      btnPage.className = `page-btn ${i === currentPage ? 'active' : ''}`;
      btnPage.innerText = i;
      btnPage.addEventListener('click', () => { currentPage = i; updateCatalog(); });
      container.appendChild(btnPage);
    }

    const btnNext = document.createElement('button');
    btnNext.className = 'page-btn';
    btnNext.innerText = '>';
    btnNext.disabled = currentPage === totalPages;
    btnNext.addEventListener('click', () => { currentPage++; updateCatalog(); });
    container.appendChild(btnNext);
  }

  function loadProductIntoViewer(p) {
    prodIdInput.value = p.id;
    document.getElementById('prod-name').value = p.name;
    document.getElementById('prod-price').value = p.price;
    document.getElementById('prod-enabled').checked = p.enabled;
    document.getElementById('prod-image-preview').innerHTML = `<img src="${p.img}">`;
    btnDelete.disabled = false; 

    populateCategories();
    prodSelectCategory.value = p.cat;

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
    
    document.querySelector('.dashboard').scrollTo({ top: 0, behavior: 'smooth' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // Primera carga forzada para medir layout
  setTimeout(calculateItemsPerPage, 100);

  // ================= 6. INPUT FILE Y GALERÍA =================
  const fileInput = document.getElementById('hidden-file-input');
  
  document.getElementById('btn-upload-img').addEventListener('click', () => fileInput.click());
  document.getElementById('btn-gallery-upload-new').addEventListener('click', () => fileInput.click());
  document.getElementById('btn-gallery-img').addEventListener('click', () => {
    document.getElementById('modal-gallery-picker').hidden = false;
  });

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