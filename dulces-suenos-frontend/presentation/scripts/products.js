// products.js
// Carga productos desde /api/products (GET) y renderiza la grilla.
// Preparado para que el panel de administración agregue productos por backend.

document.addEventListener('DOMContentLoaded', () => {
  const grid = document.getElementById('productsGrid');
  const emptyState = document.getElementById('emptyState');
  const searchInput = document.getElementById('search');
  const categoryFilter = document.getElementById('categoryFilter');
  const modal = document.getElementById('productModal');
  const modalContent = document.getElementById('modalContent');
  const modalTitle = document.getElementById('modalTitle');

  let products = []; // cache local
  let categories = new Set();

  // Render helpers
  function setLoading(isLoading) {
    if (!grid) return;
    grid.setAttribute('aria-busy', isLoading ? 'true' : 'false');
  }

  function renderEmpty(show) {
    if (!emptyState) return;
    emptyState.classList.toggle('visually-hidden', !show);
    grid.classList.toggle('visually-hidden', show);
  }

  function createCard(product) {
    const card = document.createElement('article');
    card.className = 'product-card';
    card.setAttribute('tabindex', '0');
    card.setAttribute('role', 'button');
    card.setAttribute('aria-label', `${product.title || 'Producto'} — Ver detalles`);

    const media = document.createElement('div');
    media.className = 'product-media';
    const img = document.createElement('img');
    img.alt = product.title || 'Imagen de producto';
    img.loading = 'lazy';
    img.src = product.image || 'media/placeholder-product.jpg';
    media.appendChild(img);

    const body = document.createElement('div');
    body.className = 'product-body';
    const title = document.createElement('h3');
    title.className = 'product-title';
    title.textContent = product.title || 'Sin título';
    const price = document.createElement('div');
    price.className = 'product-price';
    price.textContent = product.price ? `$ ${product.price}` : 'Consultar precio';
    const meta = document.createElement('div');
    meta.className = 'product-meta';
    meta.textContent = product.category || '';

    body.appendChild(title);
    body.appendChild(price);
    body.appendChild(meta);

    card.appendChild(media);
    card.appendChild(body);

    // abrir modal al click o Enter
    card.addEventListener('click', () => openModal(product));
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') openModal(product);
    });

    return card;
  }

  function renderGrid(list) {
    if (!grid) return;
    grid.innerHTML = '';
    if (!list || list.length === 0) {
      renderEmpty(true);
      return;
    }
    renderEmpty(false);
    const frag = document.createDocumentFragment();
    list.forEach(p => frag.appendChild(createCard(p)));
    grid.appendChild(frag);
  }

  // Modal
  function openModal(product) {
    if (!modal) return;
    modalTitle.textContent = product.title || 'Producto';
    modalContent.innerHTML = `
      <div style="display:flex;gap:1rem;flex-wrap:wrap;">
        <div style="flex:1 1 260px;min-width:200px;">
          <img src="${product.image || 'media/placeholder-product.jpg'}" alt="${product.title || ''}" style="width:100%;height:auto;border-radius:8px;">
        </div>
        <div style="flex:1 1 260px;">
          <p style="font-weight:700;color:var(--accent);margin:0 0 0.5rem;">${product.price ? `$ ${product.price}` : 'Consultar precio'}</p>
          <p style="color:var(--muted);margin:0 0 0.75rem;">${product.category || ''}</p>
          <p style="margin:0 0 1rem;">${product.description || 'Sin descripción.'}</p>
          <button class="btn btn-primary" id="addToCart">Agregar al carrito</button>
        </div>
      </div>
    `;
    modal.setAttribute('aria-hidden', 'false');

    // handler para cerrar con backdrop o botón
    modal.querySelectorAll('[data-dismiss="modal"]').forEach(el => {
      el.addEventListener('click', closeModal);
    });
    modal.querySelector('.modal-close')?.addEventListener('click', closeModal);

    const addBtn = document.getElementById('addToCart');
    if (addBtn) addBtn.addEventListener('click', () => {
      alert('Producto agregado al carrito (simulado).');
      closeModal();
    });
  }

  function closeModal() {
    if (!modal) return;
    modal.setAttribute('aria-hidden', 'true');
    modalContent.innerHTML = '';
  }

  // Filtrado y búsqueda
  function applyFilters() {
    const q = searchInput?.value.trim().toLowerCase() || '';
    const cat = categoryFilter?.value || '';
    let list = products.slice();
    if (cat) list = list.filter(p => (p.category || '').toLowerCase() === cat.toLowerCase());
    if (q) list = list.filter(p => (p.title || '').toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q));
    renderGrid(list);
  }

  // Cargar productos desde API
  async function loadProducts() {
    setLoading(true);
    try {
      const res = await fetch('/api/products');
      if (!res.ok) {
        products = [];
      } else {
        products = await res.json();
      }
    } catch (err) {
      console.warn('No se pudo cargar productos, usando lista vacía.', err);
      products = [];
    } finally {
      setLoading(false);
      categories = new Set(products.map(p => p.category).filter(Boolean));
      populateCategories();
      applyFilters();
    }
  }

  function populateCategories() {
    if (!categoryFilter) return;
    const current = categoryFilter.value || '';
    categoryFilter.innerHTML = '<option value="">Todas las categorías</option>';
    Array.from(categories).sort().forEach(cat => {
      const opt = document.createElement('option');
      opt.value = cat;
      opt.textContent = cat;
      categoryFilter.appendChild(opt);
    });
    if (current) categoryFilter.value = current;
  }

  // Eventos
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      clearTimeout(searchInput._deb);
      searchInput._deb = setTimeout(applyFilters, 220);
    });
  }
  if (categoryFilter) categoryFilter.addEventListener('change', applyFilters);

  // Cerrar modal con Escape y clic fuera
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modal && modal.getAttribute('aria-hidden') === 'false') closeModal();
  });
  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target.classList.contains('modal-backdrop')) closeModal();
    });
  }

  // Inicial
  document.getElementById('year')?.textContent = new Date().getFullYear();
  loadProducts();
});
