// scripts/login.js
// Requiere: formulario con ids: login-form, email, password, remember, login-status, login-submit, login-spinner
// Recomendado: backend en http://localhost:8081/api/auth/login y /api/auth/me y /api/auth/logout
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const remember = document.getElementById('remember');
  const status = document.getElementById('login-status');
  const submitBtn = document.getElementById('login-submit');
  const spinner = document.getElementById('login-spinner');

  /* ---------- Helpers UI ---------- */
  function setStatus(message = '', type = 'info') {
    if (!status) return;
    status.textContent = message;
    status.style.color = type === 'error' ? '#cf1810' : '';
  }

  function setSubmitting(isSubmitting) {
    if (!submitBtn) return;
    submitBtn.disabled = isSubmitting;
    if (spinner) spinner.classList.toggle('visually-hidden', !isSubmitting);
  }

  /* ---------- Local user state (frontend) ---------- */
  const STORAGE_KEY = 'ds_user'; // guarda solo el objeto user para UI, no el token

  function saveUserForUI(user) {
    try {
      if (!user) { localStorage.removeItem(STORAGE_KEY); return; }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    } catch (e) { console.warn('No se pudo guardar user en localStorage', e); }
  }

  function getUserFromStorage() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }

  function clearUserForUI() {
    try { localStorage.removeItem(STORAGE_KEY); } catch (e) {}
  }

  /* ---------- Validation ---------- */
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

  /* ---------- Login submit ---------- */
  async function submitForm(ev) {
    ev.preventDefault();
    const err = validate();
    if (err) {
      setStatus(err, 'error');
      return;
    }

    setSubmitting(true);
    setStatus('Iniciando sesión...', 'info');

    const payload = {
      email: email.value.trim(),
      password: password.value,
      remember: remember ? Boolean(remember.checked) : false
    };

    try {
      // IMPORTANTE: credentials: 'include' para recibir cookie HttpOnly del backend
      const res = await fetch('http://localhost:8081/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        // El frontend decide el texto visible al usuario
        setStatus('Datos incorrectos', 'error');
        setSubmitting(false);
        return;
      }

      // Intentamos leer user devuelto (si el backend lo envía)
      let data = null;
      try { data = await res.json(); } catch (e) { data = null; }

      // Guardamos el user para uso UI (no contiene token)
      if (data && typeof data === 'object' && Object.keys(data).length > 0) {
        saveUserForUI(data);
      } else {
        // Si backend no devuelve user, intentamos pedir /me para obtener perfil
        const me = await fetchProfile();
        if (me) saveUserForUI(me);
      }

      setStatus('Sesión iniciada correctamente', 'info');

      // Redirigir a index o a la página que quiera
      setTimeout(() => { window.location.href = 'index.html'; }, 600);
      

    } catch (networkErr) {
      console.error('Network error:', networkErr);
      setStatus('Error de red. Intentá de nuevo.', 'error');
      setSubmitting(false);
    } finally {
      setSubmitting(false);
    }
  }

  /* ---------- Profile check (al cargar páginas) ---------- */
  // Llamar a este endpoint para saber si hay sesión activa.
  async function fetchProfile() {
    try {
      const res = await fetch('http://localhost:8081/api/auth/me', {
        method: 'GET',
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) return null;
      const user = await res.json();
      return user;
    } catch (e) {
      console.warn('fetchProfile error', e);
      return null;
    }
  }

  // Función pública para inicializar estado en cualquier página
  async function initAuth() {
    // Primero, si ya tenemos user en localStorage, devolvemos eso (rápido)
    const cached = getUserFromStorage();
    if (cached) return cached;

    // Si no, pedimos al backend (cookie HttpOnly debe enviarse automáticamente)
    const user = await fetchProfile();
    if (user) saveUserForUI(user);
    return user;
  }

  /* ---------- Logout ---------- */
  async function logout() {
    try {
      // Pedimos al backend que borre la cookie (backend debe implementar /api/auth/logout)
      await fetch('http://localhost:8081/api/auth/logout', {
        method: 'POST',
        credentials: 'include'
      });
    } catch (e) {
      console.warn('Logout request failed', e);
    } finally {
      clearUserForUI();
      // Actualizar UI localmente: redirigir o mostrar login
      window.location.href = 'index.html';
    }
  }

  /* ---------- Exponer utilidades globales (opcional) ---------- */
  // Si querés usar initAuth() desde otras páginas, exponelo en window
  window.auth = {
    init: initAuth,
    logout,
    getUser: getUserFromStorage
  };

  /* ---------- Attach submit ---------- */
  if (form) form.addEventListener('submit', submitForm);

  /* ---------- Auto-init (opcional) ---------- */
  // Si querés que al cargar la página ya intente recuperar perfil, descomentá:
  // initAuth().then(user => { if (user) console.log('Usuario activo:', user); });

});
