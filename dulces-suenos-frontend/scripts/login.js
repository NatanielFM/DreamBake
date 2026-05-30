// login.js
// Archivo listo para pegar. Maneja correctamente respuestas 4xx con body JSON,
// muestra mensajes en el UI usando setStatus, y redirige a index.html al loguear.

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

  /* ---------- Envío del formulario (fetch robusto) ---------- */
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
      // Llamada a la API intermedia (AuthController)
      const res = await fetch('http://localhost:8081/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      // Leemos el body de forma segura aunque el status sea 4xx/5xx
      let data = null;
      try {
        // Primero intentamos leer como texto (por si el servidor devuelve texto plano)
        const text = await res.text();
        if (text && text.trim() !== '') {
          try {
            data = JSON.parse(text);
          } catch (e) {
            // Si no es JSON, lo envolvemos en un objeto con message
            data = { message: text };
          }
        }
      } catch (readErr) {
        data = null;
      }

      if (!res.ok) {
        // Mostrar el mensaje que venga del backend o un fallback
        const msg = data?.message || 'Correo o contraseña incorrecta';
        setStatus(msg, 'error');
        setSubmitting(false);
        return;
      }

      // Login correcto
      const successMsg = data?.message || 'Login exitoso';
      setStatus('¡Bienvenido! Redirigiendo...', 'info');

      // Redireccionar explícitamente a index.html
      setTimeout(() => {
        window.location.href = 'index.html';
      }, 700);

    } catch (networkErr) {
      console.error('Network error:', networkErr);
      setStatus('Error de red. Intentá de nuevo.', 'error');
      setSubmitting(false);
    } finally {
      // Aseguramos que el spinner se oculte si quedó visible
      setSubmitting(false);
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
  const savedCount = Number(localStorage.getItem('cartCount') || 0);
  setCartCount(savedCount);

  // Si el botón del header apunta al mismo formulario, hacer scroll y focus
  const headerLogin = document.getElementById('login-button');
  if (headerLogin && form) {
    headerLogin.addEventListener('click', (e) => {
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

  /* ---------- Fin ---------- */
});
