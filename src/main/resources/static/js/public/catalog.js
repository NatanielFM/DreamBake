// Catálogo (/products): búsqueda por nombre + píldoras de categoría.
// Antes estaba escrito dentro de products.html; se pasó a este archivo para que la
// política de seguridad (CSP) pueda prohibir el JavaScript dentro del HTML.
document.addEventListener('DOMContentLoaded', () => {
  const searchInput = document.getElementById('search');
  const pills = document.querySelectorAll('#categoryFilter [data-filter]');
  const items = document.querySelectorAll('.catalog-item');
  const emptyState = document.getElementById('emptyState');
  const counter = document.getElementById('catalogCount');
  const clearBtn = document.getElementById('clearFilters');
  let currentCat = 'all';

  // Esqueleto de carga: el "plato" brilla hasta que la foto termina de cargar
  document.querySelectorAll('.shop-card-plate img').forEach(img => {
    const plate = img.closest('.shop-card-plate');   // se guarda antes: si la foto falla, store.js la reemplaza
    const done = () => { if (plate) plate.classList.add('is-loaded'); };
    if (img.complete) done(); else { img.addEventListener('load', done, { once: true }); img.addEventListener('error', done, { once: true }); }
  });

  function filterProducts() {
    const query = (searchInput?.value || '').toLowerCase().trim();
    let visibleCount = 0;
    items.forEach(card => {
      const name = card.getAttribute('data-name') || '';
      const itemCat = card.getAttribute('data-category') || '';
      const show = name.includes(query) && (currentCat === 'all' || itemCat === currentCat);
      card.hidden = !show;
      if (show) visibleCount++;
    });
    if (emptyState) emptyState.style.display = visibleCount === 0 && items.length > 0 ? 'grid' : 'none';
    if (counter) counter.textContent = `${visibleCount} ${visibleCount === 1 ? 'producto' : 'productos'}`;
  }

  function selectCategory(cat) {
    currentCat = cat;
    pills.forEach(p => {
      const on = p.getAttribute('data-filter') === cat;
      p.classList.toggle('is-selected', on);
      p.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    filterProducts();
  }

  pills.forEach(p => p.addEventListener('click', () => selectCategory(p.getAttribute('data-filter'))));
  searchInput?.addEventListener('input', filterProducts);
  clearBtn?.addEventListener('click', () => { if (searchInput) searchInput.value = ''; selectCategory('all'); searchInput?.focus(); });

  // Si se llega desde la home con ?categoria=Tortas, se preselecciona (si existe)
  const fromUrl = new URLSearchParams(location.search).get('categoria');
  const match = fromUrl && Array.from(pills).find(p => p.getAttribute('data-filter').toLowerCase() === fromUrl.toLowerCase());
  if (match) selectCategory(match.getAttribute('data-filter')); else filterProducts();
});
  
