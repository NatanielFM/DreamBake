// scripts/header.js
// Inserta el header completo y mantiene la lógica del menú móvil.
// Consulta /api/auth/me para mostrar el primer nombre del usuario logueado.
// Comportamiento solicitado:
// - Mantener el botón de login. Si no hay sesión redirige a login.html.
// - Si hay sesión, el mismo botón abre un menú con "Hola, nombre", "Mi cuenta" y "Cerrar sesión" (cada uno con su SVG).
// - El botón "Menú" en mobile es un SVG de 3 líneas y está colocado a la izquierda (en desktop desaparece).
// - Mantener el resto de la estructura y clases tal como estaban.

document.addEventListener('DOMContentLoaded', async () => {
  const headerContainer = document.getElementById('header');
  if (!headerContainer) return;

  // Insertar el HTML del header (menuButton colocado a la izquierda con SVG hamburger)
  headerContainer.innerHTML = `
<header class="site-header" role="banner">
  <div class="container header-inner">

    <!-- Botón Menú (móvil) a la izquierda -->
    <button id="menuButton" class="menu-button" aria-expanded="false" aria-controls="mobileMenu" aria-label="Abrir menú" type="button">
      <svg width="22" height="16" viewBox="0 0 22 16" aria-hidden="true" focusable="false">
        <rect y="0" width="22" height="2" rx="1" fill="currentColor"></rect>
        <rect y="7" width="22" height="2" rx="1" fill="currentColor"></rect>
        <rect y="14" width="22" height="2" rx="1" fill="currentColor"></rect>
      </svg>
    </button>

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

    <!-- Derecha: acciones sociales y botón login -->
    <div class="header-actions">
      <a class="btn-social btn-whatsapp" href="https://wa.me/549XXXXXXXXXX" target="_blank" rel="noopener" aria-label="Contactar por WhatsApp">
        <img src="media/icons/whatsapp.png" alt="WhatsApp" width="20" height="20" loading="lazy">
      </a>
      <a class="btn-social btn-ig" href="https://www.instagram.com/dulcessuenos_posadas/" target="_blank" rel="noopener" aria-label="Instagram">
        <img src="media/icons/instagram.png" alt="Instagram" width="20" height="20" loading="lazy">
      </a>
      <button class="btn-social btn-cart" id="cart-button" aria-label="Ver carrito. 0 artículos" aria-haspopup="true" aria-expanded="false" type="button">
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M6 2a1 1 0 0 1 .993 .883l.007 .117v1.068l13.071 .935a1 1 0 0 1 .929 1.024l-.01 .114l-1 7a1 1 0 0 1 -.877 .853l-.113 .006h-12v2h10a3 3 0 1 1 -2.995 3.176l-.005 -.176l.005 -.176c.017 -.288 .074 -.564 .166 -.824h-5.342a3 3 0 1 1 -5.824 1.176l-.005 -.176l.005 -.176a3.002 3.002 0 0 1 1.995 -2.654v-12.17h-1a1 1 0 0 1 -.993 -.883l-.007 -.117a1 1 0 0 1 .883 -.993l.117 -.007h2zm0 16a1 1 0 1 0 0 2a1 1 0 0 0 0 -2zm11 0a1 1 0 1 0 0 2a1 1 0 0 0 0 -2z" />
        </svg>
        <span class="cart-count" id="cart-count" aria-hidden="true">0</span>
      </button>

      <!-- Botón login (se mantiene) -->
      <a class="btn-social btn-login" id="login-button" href="./login.html" role="button" aria-controls="login-modal" aria-haspopup="dialog">
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.418 0-8 2.239-8 5v1h16v-1c0-2.761-3.582-5-8-5z"></path></svg>
      </a>
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

  // --- Lógica menú móvil ---
  (function setupMobileMenu() {
    const menuButton = document.getElementById('menuButton');
    const mobileMenu = document.getElementById('mobileMenu');
    if (!menuButton || !mobileMenu) return;

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
  })();

  // --- Mostrar nombre del usuario logueado y convertir login button en disparador de menú cuando corresponda ---
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

  // Helper: crear menú de usuario (para abrir desde el mismo botón login)
  function createUserMenu(firstName) {
    const container = document.createElement('div');
    container.className = 'user-menu';
    container.setAttribute('role', 'menu');
    container.hidden = true;

    const title = document.createElement('div');
    title.className = 'user-menu-title';
    title.textContent = `Hola, ${firstName}`;
    container.appendChild(title);

    const account = document.createElement('button');
    account.className = 'user-menu-item';
    account.type = 'button';
    account.innerHTML = `
      <svg class="icon" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.418 0-8 2.239-8 5v1h16v-1c0-2.761-3.582-5-8-5z"></path>
      </svg>
      <span>Mi cuenta</span>
    `;
    account.addEventListener('click', () => { window.location.href = '/profile.html'; });

    const logoutBtn = document.createElement('button');
    logoutBtn.className = 'user-menu-item';
    logoutBtn.type = 'button';
    logoutBtn.innerHTML = `
      <svg class="icon" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M16 13v-2H7V8l-5 4 5 4v-3zM20 3h-8v2h8v14h-8v2h8a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z"></path>
      </svg>
      <span>Cerrar sesión</span>
    `;
    logoutBtn.addEventListener('click', () => openConfirmModal());

    container.appendChild(account);
    container.appendChild(logoutBtn);

    // Close on outside click or Escape
    document.addEventListener('click', (e) => {
      if (!container.contains(e.target) && !document.getElementById('login-button').contains(e.target)) {
        container.hidden = true;
        const lb = document.getElementById('login-button');
        if (lb) lb.setAttribute('aria-expanded', 'false');
      }
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { container.hidden = true; const lb = document.getElementById('login-button'); if (lb) lb.setAttribute('aria-expanded', 'false'); } });

    return container;
  }

  // Modal confirmación para logout
  function openConfirmModal() {
    if (document.getElementById('confirmModal')) return;
    const overlay = document.createElement('div');
    overlay.id = 'confirmModal';
    overlay.className = 'confirm-modal';
    overlay.innerHTML = `
      <div class="confirm-card" role="dialog" aria-modal="true" aria-labelledby="confirmTitle">
        <h3 id="confirmTitle">Estás seguro?</h3>
        <p>¿Querés cerrar la sesión?</p>
        <div class="confirm-actions">
          <button id="confirmYes" class="btn btn-danger">Si</button>
          <button id="confirmNo" class="btn btn-secondary">No</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);

    const yes = document.getElementById('confirmYes');
    const no = document.getElementById('confirmNo');

    function closeModal() {
      const el = document.getElementById('confirmModal');
      if (el) el.remove();
      document.removeEventListener('keydown', onKey);
    }
    function onKey(e) { if (e.key === 'Escape') closeModal(); }

    no.addEventListener('click', closeModal);
    overlay.addEventListener('click', (e) => { if (e.target === overlay) closeModal(); });
    document.addEventListener('keydown', onKey);

    yes.addEventListener('click', async () => {
      try {
        await fetch('http://localhost:8081/api/auth/logout', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (e) {
        console.warn('Logout error', e);
      } finally {
        closeModal();
        localStorage.removeItem('ds_user');
        window.location.reload();
      }
    });
  }

  // If user is logged, replace login link behavior with menu trigger and show user menu
  if (user) {
    const loginBtn = document.getElementById('login-button');
    if (loginBtn) {
      // keep the same element but prevent navigation and use it as menu trigger
      loginBtn.href = '#';
      loginBtn.setAttribute('role', 'button');
      loginBtn.setAttribute('aria-haspopup', 'true');
      loginBtn.setAttribute('aria-expanded', 'false');

      // create user menu and append to header-actions
      const firstName = (user.name || user.email || 'Usuario').split(' ')[0];
      const userMenu = createUserMenu(firstName);
      const headerActions = document.querySelector('.header-actions');
      if (headerActions) headerActions.appendChild(userMenu);

      // toggle menu on click
      loginBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const isHidden = userMenu.hidden;
        // close any other menus
        document.querySelectorAll('.user-menu').forEach(m => { if (m !== userMenu) m.hidden = true; });
        if (isHidden) {
          // align horizontally with button
          const rect = loginBtn.getBoundingClientRect();
          const viewportWidth = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
          const rightOffset = Math.max(12, viewportWidth - (rect.right + 8));
          userMenu.style.right = rightOffset + 'px';
          userMenu.hidden = false;
          loginBtn.setAttribute('aria-expanded', 'true');
        } else {
          userMenu.hidden = true;
          loginBtn.setAttribute('aria-expanded', 'false');
        }
      });
    }
  } else {
    // not logged: ensure login button links to login.html
    const loginBtn = document.getElementById('login-button');
    if (loginBtn) {
      loginBtn.href = './login.html';
      loginBtn.removeAttribute('role');
      loginBtn.removeAttribute('aria-haspopup');
      loginBtn.removeAttribute('aria-controls');
      loginBtn.addEventListener('click', () => {
        // no-op here, navigation will occur
      });
    }
  }
});
