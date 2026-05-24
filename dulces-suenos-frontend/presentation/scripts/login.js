// scripts/login.js
// Lógica para la página de login: validación, envío simulado, toggles y handlers de UI.
// Incluye: toggle contraseña, envío del formulario, handlers para Google/Facebook,
// control del menú móvil y contador del carrito. Adaptá las rutas de OAuth y API según tu backend.

document.addEventListener('DOMContentLoaded', () => {
  /* ---------- Selectores principales ---------- */
  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const remember = document.getElementById('remember');
  const status = document.getElementById('login-status');
  const submitBtn = document.getElementById('login-submit');
  const spinner = document.getElementById('login-spinner');
  const togglePass = document.getElementById('toggle-password');
  const yearEl = document.getElementById('year'); // si existe en footer
  const cartCountEl = document.getElementById('cart-count');
  const cartButton = document.getElementById('cart-button');

  const btnGoogle = document.getElementById('social-google');
  const btnFacebook = document.getElementById('social-facebook');

  const menuButton = document.getElementById('menuButton');
  const mobileMenu = document.getElementById('mobileMenu');

  /* ---------- Utilidades ---------- */
  function setStatus(message = '', type = 'info') {
    if (!status) return;
    status.textContent = message;
    status.style.color = type === 'error' ? '#cf1810' : '';
  }

  function setCartCount(n = 0) {
    if (!cartCountEl) return;
    cartCountEl.textContent = String(n);
    if (cartButton) {
      cartButton.setAttribute('aria-label', `Ver carrito. ${n} artículos`);
    }
  }

  // Mostrar/ocultar spinner y estado de submit
  function setSubmitting(isSubmitting) {
    if (!submitBtn) return;
    submitBtn.disabled = isSubmitting;
    if (spinner) {
      spinner.classList.toggle('visually-hidden', !isSubmitting);
    }
  }

  /* ---------- Toggle contraseña ---------- */
  if (togglePass && password) {
    togglePass.addEventListener('click', () => {
      const isPwd = password.getAttribute('type') === 'password';
      password.setAttribute('type', isPwd ? 'text' : 'password');
      togglePass.setAttribute('aria-pressed', String(isPwd));
      // Cambiar icono textual (puede reemplazarse por SVG)
      togglePass.textContent = isPwd ? '🙈' : '👁️';
      password.focus();
    });
  }

  /* ---------- Validación simple (cliente) ---------- */
  function validate() {
    if (!email || !password) return 'Formulario incompleto.';
    const e = email.value.trim();
    const p = password.value;
    if (!e) return 'Ingresá tu correo electrónico.';
    if (!p) return 'Ingresá tu contraseña.';
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!re.test(e)) return 'Ingresá un correo válido.';
    return '';
  }

  /* ---------- Envío del formulario (placeholder/fetch) ---------- */
  async function submitForm(ev) {
    ev.preventDefault();
    const err = validate();
    if (err) {
      setStatus(err, 'error');
      if (err.toLowerCase().includes('correo')) email.focus();
      else password.focus();
      return;
    }

    setSubmitting(true);
    setStatus('Iniciando sesión...', 'info');

    const payload = {
      email: email.value.trim(),
      password: password.value,
      remember: remember ? remember.checked : false
    };

    try {
      // Reemplazá este bloque por la llamada real a tu backend:
      // const res = await fetch(form.action, {
      //   method: 'POST',
      //   headers: { 'Content-Type': 'application/json' },
      //   credentials: 'include', // si usás cookies
      //   body: JSON.stringify(payload)
      // });
      // const data = await res.json();
      // if (!res.ok) throw new Error(data?.message || 'Error de autenticación');

      // Simulación de respuesta (quitar en producción)
      await new Promise(r => setTimeout(r, 900));
      const fakeSuccess = true;

      if (fakeSuccess) {
        setStatus('¡Bienvenido! Redirigiendo...', 'info');
        // Redirigir al dashboard o home según tu flujo
        setTimeout(() => { window.location.href = '/'; }, 800);
      } else {
        throw new Error('Credenciales inválidas');
      }
    } catch (err) {
      console.error(err);
      setStatus('Usuario o contraseña incorrectos.', 'error');
      setSubmitting(false);
    } finally {
      // Si usás redirección inmediata en éxito, este finally puede no ejecutarse antes de navegar
      // ocultamos spinner si quedó visible
      if (spinner) spinner.classList.add('visually-hidden');
    }
  }

  if (form) form.addEventListener('submit', submitForm);

  /* ---------- Handlers para login social (placeholders) ---------- */
  if (btnGoogle) {
    btnGoogle.addEventListener('click', (e) => {
      e.preventDefault();
      // Reemplazá la ruta por tu endpoint de OAuth
      window.location.href = '/auth/google';
    });
  }

  if (btnFacebook) {
    btnFacebook.addEventListener('click', (e) => {
      e.preventDefault();
      // Reemplazá la ruta por tu endpoint de OAuth
      window.location.href = '/auth/facebook';
    });
  }

  /* ---------- Inicializaciones y accesibilidad ---------- */
  // Año en footer si existe
  if (yearEl) yearEl.textContent = new Date().getFullYear();

  // Inicializar contador del carrito (puede venir del localStorage o API)
  // Ejemplo: leer de localStorage si lo usás
  const savedCount = Number(localStorage.getItem('cartCount') || 0);
  setCartCount(savedCount);

  // Si el botón del header apunta al mismo formulario, hacer scroll y focus
  const headerLogin = document.getElementById('login-button');
  if (headerLogin && form) {
    headerLogin.addEventListener('click', (e) => {
      // Si el header-login es un botón que debería llevar a la página actual, evitamos navegación
      const href = headerLogin.getAttribute('href');
      if (!href || href === '#' || href === '#login-form') {
        e.preventDefault();
        email.focus();
        const top = form.getBoundingClientRect().top + window.scrollY - 80;
        window.scrollTo({ top, behavior: 'smooth' });
      }
    });
  }

  /* ---------- Limpieza: evitar referencias a elementos eliminados ---------- */
  // Este archivo ya no asume existencia de aside/imágenes o botones WA/IG en esta página.
  // Si en algún momento agregás handlers para WA/IG, añadilos explícitamente aquí.

  /* ---------- Fin ---------- */
});
