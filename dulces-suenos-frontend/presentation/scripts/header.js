// scripts/header.js
// Toggle accesible para menú móvil: abre/cierra, actualiza aria-expanded,
// cierra con Escape y con clic fuera. Requiere: botón con id="menuButton" y contenedor id="mobileMenu".

document.addEventListener('DOMContentLoaded', () => {
  const menuButton = document.getElementById('menuButton');
  const mobileMenu = document.getElementById('mobileMenu');

  if (!menuButton || !mobileMenu) {
    // No hay elementos; nada que hacer
    return;
  }

  // Asegurar que el botón no actúe como submit si está dentro de un form
  if (menuButton.tagName === 'BUTTON') menuButton.type = menuButton.type || 'button';

  // Inicializar estado si no existe
  if (!mobileMenu.hasAttribute('hidden')) {
    // Si el menú no tiene hidden por defecto, lo dejamos oculto para consistencia
    mobileMenu.setAttribute('hidden', '');
    menuButton.setAttribute('aria-expanded', 'false');
  } else {
    menuButton.setAttribute('aria-expanded', 'false');
  }

  function openMenu() {
    mobileMenu.removeAttribute('hidden');
    menuButton.setAttribute('aria-expanded', 'true');
    // opcional: mover foco al primer enlace del menú
    const firstLink = mobileMenu.querySelector('a, button, [tabindex]:not([tabindex="-1"])');
    if (firstLink) firstLink.focus();
    document.documentElement.classList.add('no-scroll'); // si querés evitar scroll del fondo (añadir CSS .no-scroll { overflow: hidden; })
  }

  function closeMenu() {
    mobileMenu.setAttribute('hidden', '');
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.focus();
    document.documentElement.classList.remove('no-scroll');
  }

  function toggleMenu() {
    if (mobileMenu.hasAttribute('hidden')) openMenu();
    else closeMenu();
  }

  // Click en el botón
  menuButton.addEventListener('click', (e) => {
    e.preventDefault();
    toggleMenu();
  });

  // Cerrar con Escape
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !mobileMenu.hasAttribute('hidden')) {
      closeMenu();
    }
  });

  // Cerrar al hacer clic fuera del menú (solo cuando está abierto)
  document.addEventListener('click', (e) => {
    if (mobileMenu.hasAttribute('hidden')) return;
    const target = e.target;
    if (!mobileMenu.contains(target) && !menuButton.contains(target)) {
      closeMenu();
    }
  });

  // Opcional: cerrar al hacer click en un enlace del menú (mejor UX)
  mobileMenu.addEventListener('click', (e) => {
    const el = e.target.closest('a');
    if (el) {
      // si el enlace es interno y querés navegación inmediata, no prevenir
      closeMenu();
    }
  });

  // Evitar que reglas CSS fuera de media query muestren el menú en desktop
  // (esto es solo una comprobación: si detectás que el menú sigue visible en desktop,
  // revisá tu CSS para que .mobile-menu[hidden]{display:none;} esté dentro del media query móvil)
});
