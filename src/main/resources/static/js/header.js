// header.js — Comportamiento del header compartido del sitio público.
// Funciona en todas las páginas que incluyen fragments/header.html.
//
// Qué hace:
//   1. Abre y cierra el menú móvil con animación, atrapa el foco adentro,
//      se cierra con Esc y bloquea el scroll de la página mientras está abierto.
//   2. Abre y cierra el menú de "Mi cuenta".
//   3. Compacta el header al hacer scroll.
//   4. Marca el link de la página actual.
//   5. Mantiene actualizado el contador del carrito (también en páginas sin store.js).
//
// Contratos: usa los ids #siteHeader, #menuButton, #mobileMenu, #accountBtn y #userMenu.
// Los menús se muestran/ocultan con el atributo "hidden" (igual que antes).

document.addEventListener('DOMContentLoaded', () => {
  const header = document.getElementById('siteHeader');
  const menuBtn = document.getElementById('menuButton');
  const mobileMenu = document.getElementById('mobileMenu');
  const accBtn = document.getElementById('accountBtn');
  const userMenu = document.getElementById('userMenu');
  const cartLink = document.querySelector('.site-header .btn-cart');
  const root = document.documentElement;

  if (!header) return;

  // Preferencias del dispositivo
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const desktopQuery = window.matchMedia('(min-width: 64rem)'); // 1024 px: aparece la navegación de escritorio

  /* ---------------------------------------------------------
     Utilidades para mostrar/ocultar con animación
     ---------------------------------------------------------
     Un elemento con "hidden" no se puede animar (no existe en pantalla).
     Para abrir: se quita hidden, se fuerza al navegador a "dibujarlo"
     y recién ahí se agrega .is-open, que dispara la transición del CSS.
     Para cerrar: se quita .is-open, se espera a que termine la
     transición y se vuelve a poner hidden. */
  function showAnimated(el) {
    el.hidden = false;
    void el.offsetWidth; // fuerza el reflow para que la transición arranque desde el estado cerrado
    el.classList.add('is-open');
  }

  function hideAnimated(el) {
    el.classList.remove('is-open');

    if (reduceMotion.matches) {
      el.hidden = true;
      return;
    }

    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      el.removeEventListener('transitionend', onEnd);
      // Si en el medio se volvió a abrir, no lo ocultamos
      if (!el.classList.contains('is-open')) el.hidden = true;
    };
    const onEnd = (e) => {
      if (e.target === el && e.propertyName === 'opacity') finish();
    };
    el.addEventListener('transitionend', onEnd);
    setTimeout(finish, 400); // respaldo por si el evento no llega
  }

  // Elementos enfocables dentro de un contenedor (para la trampa de foco)
  function getFocusable(container) {
    return Array.from(container.querySelectorAll('a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])'))
      .filter(el => el.offsetParent !== null);
  }

  /* ---------------------------------------------------------
     1. Menú móvil
     --------------------------------------------------------- */
  const isMenuOpen = () => mobileMenu && !mobileMenu.hidden && mobileMenu.classList.contains('is-open');

  function openMobileMenu() {
    closeUserMenu(false);
    showAnimated(mobileMenu);
    menuBtn.setAttribute('aria-expanded', 'true');
    menuBtn.setAttribute('aria-label', 'Cerrar menú');
    header.classList.add('is-menu-open');
    root.classList.add('is-scroll-locked'); // la página de atrás no se desplaza

    // El foco pasa al primer link del menú
    const first = mobileMenu.querySelector('a[href]');
    if (first) first.focus({ preventScroll: true });
  }

  function closeMobileMenu(returnFocus = true) {
    if (!mobileMenu || mobileMenu.hidden) return;
    hideAnimated(mobileMenu);
    menuBtn.setAttribute('aria-expanded', 'false');
    menuBtn.setAttribute('aria-label', 'Abrir menú');
    header.classList.remove('is-menu-open');
    root.classList.remove('is-scroll-locked');
    if (returnFocus) menuBtn.focus();
  }

  if (menuBtn && mobileMenu) {
    menuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (isMenuOpen()) closeMobileMenu();
      else openMobileMenu();
    });

    mobileMenu.addEventListener('click', (e) => {
      // Tocar un link cierra el menú (y la navegación sigue normalmente)
      if (e.target.closest('a[href]')) {
        closeMobileMenu(false);
        return;
      }
      // Tocar el fondo vacío (fuera del panel) también lo cierra
      if (e.target === mobileMenu) closeMobileMenu();
    });

    // Si la pantalla se agranda a escritorio, el menú móvil no tiene sentido
    desktopQuery.addEventListener('change', (e) => {
      if (e.matches) closeMobileMenu(false);
    });
  }

  /* ---------------------------------------------------------
     2. Menú de "Mi cuenta"
     --------------------------------------------------------- */
  function openUserMenu() {
    showAnimated(userMenu);
    accBtn.setAttribute('aria-expanded', 'true');
  }

  function closeUserMenu(returnFocus = false) {
    if (!userMenu || userMenu.hidden) return;
    hideAnimated(userMenu);
    accBtn.setAttribute('aria-expanded', 'false');
    if (returnFocus) accBtn.focus();
  }

  if (accBtn && userMenu) {
    accBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (userMenu.hidden || !userMenu.classList.contains('is-open')) openUserMenu();
      else closeUserMenu();
    });
  }

  // Click en cualquier parte fuera del menú de cuenta lo cierra
  document.addEventListener('click', (e) => {
    if (userMenu && !userMenu.hidden && !accBtn.contains(e.target) && !userMenu.contains(e.target)) {
      closeUserMenu();
    }
  });

  /* ---------------------------------------------------------
     Teclado: Esc cierra y Tab queda atrapado en el menú móvil
     --------------------------------------------------------- */
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (isMenuOpen()) closeMobileMenu(true);
      else if (userMenu && !userMenu.hidden) closeUserMenu(true);
      return;
    }

    if (e.key === 'Tab' && isMenuOpen()) {
      // El ciclo de foco es: botón X → links y botones del menú → vuelve al botón X
      const items = [menuBtn, ...getFocusable(mobileMenu)];
      const first = items[0];
      const last = items[items.length - 1];

      if (!items.includes(document.activeElement)) {
        e.preventDefault();
        first.focus();
      } else if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  });

  /* ---------------------------------------------------------
     3. Header compacto al hacer scroll
     ---------------------------------------------------------
     requestAnimationFrame agrupa los eventos de scroll para no
     recalcular más de una vez por cuadro. */
  let ticking = false;
  function updateScrolled() {
    header.classList.toggle('is-scrolled', window.scrollY > 8);
    ticking = false;
  }
  window.addEventListener('scroll', () => {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(updateScrolled);
    }
  }, { passive: true });
  updateScrolled();

  /* ---------------------------------------------------------
     4. Link de la página actual
     --------------------------------------------------------- */
  const currentPath = window.location.pathname.replace(/\/+$/, '') || '/';
  header.querySelectorAll('[data-nav]').forEach(link => {
    if (link.dataset.nav === currentPath) link.setAttribute('aria-current', 'page');
  });

  /* ---------------------------------------------------------
     5. Contador del carrito
     ---------------------------------------------------------
     - Al cargar, lee el carrito guardado en localStorage (así funciona
       también en login y registro, que no cargan store.js).
     - store.js avisa cada cambio con el evento "dreambake:cart-updated".
     - data-count="0" oculta el contador (ver header.css). */
  let lastCount = null;

  function readCartCount() {
    try {
      const cart = JSON.parse(localStorage.getItem('dreamBakeCart'));
      return Array.isArray(cart) ? cart.length : 0;
    } catch (e) {
      return 0;
    }
  }

  function renderCartCount(count) {
    if (!cartLink) return;

    header.querySelectorAll('.header-cart-count').forEach(el => { el.textContent = count; });
    cartLink.dataset.count = String(count);
    cartLink.setAttribute('aria-label',
      count === 0 ? 'Ver carrito (vacío)' : `Ver carrito, ${count} ${count === 1 ? 'producto' : 'productos'}`);

    // Pequeño "pop" del contador cuando se suma algo
    if (lastCount !== null && count > lastCount && !reduceMotion.matches) {
      cartLink.classList.remove('is-bumped');
      void cartLink.offsetWidth; // reinicia la animación
      cartLink.classList.add('is-bumped');
    }
    lastCount = count;
  }

  renderCartCount(readCartCount());

  document.addEventListener('dreambake:cart-updated', (e) => {
    const count = e.detail && typeof e.detail.count === 'number' ? e.detail.count : readCartCount();
    renderCartCount(count);
  });

  // Si el carrito cambia en otra pestaña, también se actualiza
  window.addEventListener('storage', (e) => {
    if (e.key === 'dreamBakeCart') renderCartCount(readCartCount());
  });
});
