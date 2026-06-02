// scripts/header.js
// Inserta el header completo y mantiene la lógica del menú móvil.
// Además consulta /api/auth/me para mostrar el primer nombre del usuario logueado.

document.addEventListener('DOMContentLoaded', async () => {
  const headerContainer = document.getElementById('header');
  if (!headerContainer) return;

  // Insertar el HTML del header
  headerContainer.innerHTML = `
<header class="site-header" role="banner">
  <div class="container header-inner">

    <!-- Izquierda: logo -->
    <a class="brand" href="index.html" aria-label="Dulce Sueños Posadas">
      <svg class="brand-icon" width="36" height="36" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <circle cx="12" cy="12" r="10" fill="#e0487f"/>
        <path d="M7 12c1.5-2 4-2 5 0 1-2 3.5-2 5 0" stroke="#fff" stroke-width="1.2" fill="none" stroke-linecap="round"/>
      </svg>
      <span class="brand-text">Dulce Sueños</span>
    </a>

    <!-- Centro: navegación -->
    <nav class="main-nav" role="navigation" aria-label="Navegación principal">
      <ul class="nav-list">
        <li><a href="index.html">Inicio</a></li>
        <li><a href="products.html">Productos</a></li>
        <li><a href="about.html">Nosotros</a></li>
        <li><a href="contact.html">Contacto</a></li>
      </ul>
    </nav>

    <!-- Derecha: acciones sociales y botón Menú (móvil) -->
    <div class="header-actions">
      <a class="btn-social btn-whatsapp" href="https://wa.me/549XXXXXXXXXX" target="_blank" rel="noopener" aria-label="Contactar por WhatsApp">
        <img src="media/icons/whatsapp.png" alt="WhatsApp" width="20" height="20" loading="lazy">
      </a>
      <a class="btn-social btn-ig" href="https://www.instagram.com/dulcessuenos_posadas/" target="_blank" rel="noopener" aria-label="Instagram">
        <img src="media/icons/instagram.png" alt="Instagram" width="20" height="20" loading="lazy">
      </a>
      <button class="btn-social btn-cart" id="cart-button" aria-label="Ver carrito. 0 artículos" aria-haspopup="true" aria-expanded="false">
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 2a1 1 0 0 1 .993 .883l.007 .117v1.068l13.071 .935a1 1 0 0 1 .929 1.024l-.01 .114l-1 7a1 1 0 0 1 -.877 .853l-.113 .006h-12v2h10a3 3 0 1 1 -2.995 3.176l-.005 -.176l.005 -.176c.017 -.288 .074 -.564 .166 -.824h-5.342a3 3 0 1 1 -5.824 1.176l-.005 -.176l.005 -.176a3.002 3.002 0 0 1 1.995 -2.654v-12.17h-1a1 1 0 0 1 -.993 -.883l-.007 -.117a1 1 0 0 1 .883 -.993l.117 -.007h2zm0 16a1 1 0 1 0 0 2a1 1 0 0 0 0 -2zm11 0a1 1 0 1 0 0 2a1 1 0 0 0 0 -2z" />
        </svg>
        <span class="cart-count" id="cart-count" aria-hidden="true">0</span>
      </button>
      <a class="btn-social btn-login" id="login-button" href="./login.html" role="button" aria-controls="login-modal" aria-haspopup="dialog">
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.418 0-8 2.239-8 5v1h16v-1c0-2.761-3.582-5-8-5z"></path></svg>
      </a>
      <button id="menuButton" class="menu-button" aria-expanded="false" aria-controls="mobileMenu" aria-label="Abrir menú">Menú</button>
    </div>
  </div>

  <!-- Menú móvil -->
  <div id="mobileMenu" class="mobile-menu" hidden>
    <ul>
      <li><a href="index.html">Inicio</a></li>
      <li><a href="products.html">Productos</a></li>
      <li><a href="about.html">Nosotros</a></li>
      <li><a href="contact.html">Contacto</a></li>
    </ul>
  </div>
</header>
  `;

  // --- Lógica menú móvil (igual que antes) ---
  const menuButton = document.getElementById('menuButton');
  const mobileMenu = document.getElementById('mobileMenu');
  if (menuButton && mobileMenu) {
    if (menuButton.tagName === 'BUTTON') menuButton.type = menuButton.type || 'button';
    menuButton.setAttribute('aria-expanded', 'false');
    mobileMenu.setAttribute('hidden', '');

    function openMenu() {
      mobileMenu.removeAttribute('hidden');
      menuButton.setAttribute('aria-expanded', 'true');
      document.documentElement.classList.add('no-scroll');
    }
    function closeMenu() {
      mobileMenu.setAttribute('hidden', '');
      menuButton.setAttribute('aria-expanded', 'false');
      document.documentElement.classList.remove('no-scroll');
    }
    function toggleMenu() {
      if (mobileMenu.hasAttribute('hidden')) openMenu();
      else closeMenu();
    }

    menuButton.addEventListener('click', (e) => { e.preventDefault(); toggleMenu(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !mobileMenu.hasAttribute('hidden')) closeMenu(); });
    document.addEventListener('click', (e) => {
      if (!mobileMenu.hasAttribute('hidden') && !mobileMenu.contains(e.target) && !menuButton.contains(e.target)) closeMenu();
    });
    mobileMenu.addEventListener('click', (e) => { if (e.target.closest('a')) closeMenu(); });
  }

  // --- Mostrar nombre del usuario logueado ---
  async function fetchProfile() {
    try {
      const res = await fetch('http://localhost:8081/api/auth/me', {
        method: 'GET',
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) return null;
      return await res.json();
    } catch { return null; }
  }

  const user = await fetchProfile();
  console.log('Perfil recibido:', user);
  if (user) {
    const loginBtn = document.getElementById('login-button');
    if (loginBtn) loginBtn.style.display = 'none';

    const span = document.createElement('span');
    span.className = 'user-name';
    span.textContent = `Hola, ${user.name.split(" ")[0]}`;
    document.querySelector('.header-actions').appendChild(span);
  }
});
