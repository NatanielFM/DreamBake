// admin.js - Actualizado con Paginación, Filtros cruzados y Modales Custom
document.addEventListener('DOMContentLoaded', () => {

  // ================= 1. NAVEGACIÓN Y TABS =================
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById(btn.dataset.tab).classList.add('active');
    });
  });

  // ================= 2. MODAL DE CONFIRMACIÓN UNIVERSAL =================
  function showConfirmModal(title, message, okText, okBtnClass, onConfirm) {
    document.getElementById('confirm-title').innerText = title;
    document.getElementById('confirm-message').innerText = message;
    
    const btnOk = document.getElementById('btn-confirm-ok');
    btnOk.innerText = okText;
    btnOk.className = `btn ${okBtnClass} btn-uniform`;

    document.getElementById('modal-confirm').hidden = false;

    // Remover listeners previos clonando los botones (truco limpio de JS)
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

  // Cerrar modales con la [X]
  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.modal-overlay').hidden = true;
    });
  });

  // Logout Custom
  const logoutSidebar = document.getElementById('btn-logout-sidebar');
  if (logoutSidebar) {
    logoutSidebar.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Estás seguro que deseas salir del panel de administración?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

  // ================= 3. LÓGICA DEL VISOR DE PRODUCTOS =================
  const emptyViewer = document.getElementById('empty-viewer');
  const activeViewer = document.getElementById('active-viewer');
  const btnAddProduct = document.getElementById('btn-add-new-product');
  const btnDiscard = document.getElementById('btn-discard');
  const btnDelete = document.getElementById('btn-delete');
  const productForm = document.getElementById('product-form');
  const prodSelectCategory = document.getElementById('prod-category');
  const prodIdInput = document.getElementById('prod-id');

  // Abrir creador (Formulario Vacío)
  btnAddProduct.addEventListener('click', () => {
    productForm.reset();
    prodIdInput.value = '';
    document.getElementById('prod-image-preview').innerHTML = '<span>Sin Imagen</span>';
    btnDelete.disabled = true; // No se puede eliminar algo nuevo
    
    // Cargar opciones de categoría
    populateCategories();

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
  });

  // Botón Descartar
  btnDiscard.addEventListener('click', () => {
    showConfirmModal('Descartar cambios', 'Se perderán los datos no guardados. ¿Deseas continuar?', 'Descartar', 'btn-grey', () => {
      activeViewer.style.display = 'none';
      emptyViewer.style.display = 'flex';
    });
  });

  // Botón Eliminar
  btnDelete.addEventListener('click', () => {
    showConfirmModal('Eliminar producto', 'Esta acción no se puede deshacer. ¿Eliminar este producto del sistema?', 'Eliminar', 'btn-red', () => {
      alert("Lógica de borrado lista para conectar con Backend");
      activeViewer.style.display = 'none';
      emptyViewer.style.display = 'flex';
    });
  });

  // Manejo de Pop-up de Configuración de Tipos
  let lastSelectedCategory = "";
  prodSelectCategory.addEventListener('change', (e) => {
    if (e.target.value === 'config') {
      document.getElementById('modal-category').hidden = false;
      e.target.value = lastSelectedCategory; // Revertir visualmente
    } else {
      lastSelectedCategory = e.target.value;
    }
  });

  function populateCategories() {
    const cats = ['Sin tipo', 'Tortas', 'Dulces', 'Souvenir']; // Simulado
    prodSelectCategory.innerHTML = cats.map(c => `<option value="${c}">${c}</option>`).join('');
    prodSelectCategory.innerHTML += `<option value="config" class="config-option">⚙️ Configuración...</option>`;
    lastSelectedCategory = cats[0];
  }

  // ================= 4. CATÁLOGO, BÚSQUEDA Y PAGINACIÓN =================
  
  // Array de prueba extendido para probar paginación
  const dummyProducts = Array.from({length: 25}, (_, i) => ({
    id: i + 1, 
    name: `Producto de prueba ${i + 1}`, 
    price: 1500 * (i + 1), 
    cat: i % 2 === 0 ? 'Tortas' : (i % 3 === 0 ? 'Souvenir' : 'Dulces'), 
    img: `https://picsum.photos/seed/${i}/300/300`, 
    enabled: i % 5 !== 0 // Algunos desactivados
  }));

  let currentCategoryFilter = 'all';
  let currentSearchQuery = '';
  let currentPage = 1;
  const itemsPerPage = 10; // Número de productos por página

  // Filtros interactivos
  document.querySelectorAll('.chip').forEach(chip => {
    chip.addEventListener('click', (e) => {
      document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
      e.target.classList.add('active');
      currentCategoryFilter = e.target.dataset.filter;
      currentPage = 1; // Volver a pag 1 al filtrar
      updateCatalog();
    });
  });

  // Buscador por nombre
  document.getElementById('search-product').addEventListener('input', (e) => {
    currentSearchQuery = e.target.value.toLowerCase();
    currentPage = 1; // Volver a pag 1 al buscar
    updateCatalog();
  });

  function updateCatalog() {
    // 1. Aplicar Filtros
    let filtered = dummyProducts.filter(p => {
      const matchCat = currentCategoryFilter === 'all' || p.cat === currentCategoryFilter;
      const matchSearch = p.name.toLowerCase().includes(currentSearchQuery);
      return matchCat && matchSearch;
    });

    // 2. Paginación
    const totalPages = Math.ceil(filtered.length / itemsPerPage);
    if (currentPage > totalPages && totalPages > 0) currentPage = totalPages;

    const startIdx = (currentPage - 1) * itemsPerPage;
    const paginatedItems = filtered.slice(startIdx, startIdx + itemsPerPage);

    // 3. Renderizar Grilla
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

    // 4. Renderizar Controles de Paginación
    renderPaginationControls(totalPages);
  }

  function renderPaginationControls(totalPages) {
    const container = document.getElementById('pagination-controls');
    container.innerHTML = '';

    if (totalPages <= 1) return; // No mostrar si hay 1 sola página

    // Botón Anterior
    const btnPrev = document.createElement('button');
    btnPrev.className = 'page-btn';
    btnPrev.innerText = '<';
    btnPrev.disabled = currentPage === 1;
    btnPrev.addEventListener('click', () => { currentPage--; updateCatalog(); });
    container.appendChild(btnPrev);

    // Números
    for (let i = 1; i <= totalPages; i++) {
      const btnPage = document.createElement('button');
      btnPage.className = `page-btn ${i === currentPage ? 'active' : ''}`;
      btnPage.innerText = i;
      btnPage.addEventListener('click', () => { currentPage = i; updateCatalog(); });
      container.appendChild(btnPage);
    }

    // Botón Siguiente
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
    btnDelete.disabled = false; // Habilitar borrado

    populateCategories(); // Llenar opciones
    prodSelectCategory.value = p.cat;

    emptyViewer.style.display = 'none';
    activeViewer.style.display = 'block';
    
    // Auto-scroll suave hacia arriba para ver el form en mobile
    document.querySelector('.dashboard').scrollTo({ top: 0, behavior: 'smooth' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // Inicializar todo
  updateCatalog();

  // ================= 5. INPUT FILE Y GALERÍA =================
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