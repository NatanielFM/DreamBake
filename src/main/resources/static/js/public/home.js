// home.js — Comportamiento propio de la página de inicio.
//
// 1. Aparición suave de las secciones al hacer scroll (IntersectionObserver).
// 2. Precio "Desde $…" con el tamaño de torta más barato del creador
//    (los tamaños los carga store.js desde /api/cake-config/public).
// (Los botones "Agregar" de la vitrina los maneja store.js, igual que en /products.)

document.addEventListener('DOMContentLoaded', () => {
  const root = document.documentElement;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------------------------------------------------------
     1. Aparición al hacer scroll
     ---------------------------------------------------------
     Los elementos con data-reveal aparecen con un fundido corto cuando
     entran en pantalla. Si son hermanos, entran escalonados (60 ms entre uno
     y otro). Sin JS o con movimiento reducido, todo se ve desde el principio. */
  const revealItems = document.querySelectorAll('[data-reveal]');

  if (!reduceMotion && 'IntersectionObserver' in window && revealItems.length) {
    revealItems.forEach((el) => {
      const siblings = Array.from(el.parentElement.children).filter(c => c.hasAttribute('data-reveal'));
      const index = Math.min(siblings.indexOf(el), 5);
      el.style.setProperty('--reveal-delay', `${index * 60}ms`);
    });

    root.classList.add('has-reveal');

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target); // aparece una sola vez
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });

    revealItems.forEach(el => observer.observe(el));
  }

  /* ---------------------------------------------------------
     2. Precio mínimo del creador ("Desde $44.000")
     --------------------------------------------------------- */
  function fillMinPrice(sizes) {
    if (!Array.isArray(sizes) || sizes.length === 0) return;
    const prices = sizes.map(s => Number(s.price)).filter(p => p > 0);
    if (prices.length === 0) return;

    const min = window.DreamFormat ? window.DreamFormat.money(Math.min(...prices)).replace(/^\$\u00a0/, '') : Math.min(...prices).toLocaleString('es-AR');   // el "$" está en el HTML
    document.querySelectorAll('[data-min-price]').forEach((el) => {
      const value = el.querySelector('[data-min-price-value]');
      if (value) value.textContent = min;
      el.hidden = false;
    });
  }

  // store.js puede haber terminado antes o avisar después
  if (window.dreamBakeCakeSizes) fillMinPrice(window.dreamBakeCakeSizes);
  document.addEventListener('dreambake:cake-config', (e) => fillMinPrice(e.detail && e.detail.sizes));

  // El botón "Agregar" de la vitrina lo maneja store.js (es el mismo que en /products)
});
