// scripts/login.js
// Requiere en el HTML: ids -> login-form, email, password, remember, login-status, login-submit, login-spinner
// Backend esperado: http://localhost:8080/api/auth/login y /api/auth/me y /api/auth/logout

document.addEventListener('DOMContentLoaded', () => {
  const API_BASE = 'http://localhost:8080/api/auth';
  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const remember = document.getElementById('remember');
  const status = document.getElementById('login-status');
  const submitBtn = document.getElementById('login-submit');
  const spinner = document.getElementById('login-spinner');

  const STORAGE_KEY = 'ds_user';

  /* ---------- UI helpers ---------- */
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

  /* ---------- local storage helpers ---------- */
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

  /* ---------- validation ---------- */
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

  /* ---------- network helpers ---------- */
  async function fetchProfile() {
    try {
      const res = await fetch(`${API_BASE.replace('/auth','/auth')}/me`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) return null;
      const body = await res.json();
      // backend puede devolver { user: {...} } o directamente user
      return body && body.user ? body.user : body;
    } catch (e) {
      console.warn('fetchProfile error', e);
      return null;
    }
  }

  /* ---------- submit ---------- */
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
      const res = await fetch(`${API_BASE}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include', // importante para recibir cookie HttpOnly
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        if (res.status === 401) setStatus('Credenciales incorrectas.', 'error');
        else setStatus('Error al iniciar sesión.', 'error');
        setSubmitting(false);
        return;
      }

      // Intentamos leer user devuelto
      let data = null;
      try { data = await res.json(); } catch (e) { data = null; }

      const user = data && data.user ? data.user : data;
      if (user && typeof user === 'object' && Object.keys(user).length > 0) {
        saveUserForUI(user);
      } else {
        // Si backend no devuelve user, pedimos /me
        const me = await fetchProfile();
        if (me) saveUserForUI(me);
      }

      setStatus('Sesión iniciada correctamente', 'info');
      //setTimeout(() => { window.location.href = 'index.html'; }, 600);

    } catch (networkErr) {
      console.error('Network error:', networkErr);
      setStatus('Error de red. Intentá de nuevo.', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  /* ---------- logout ---------- */
  async function logout() {
    try {
      await fetch(`${API_BASE}/logout`, {
        method: 'POST',
        credentials: 'include'
      });
    } catch (e) {
      console.warn('Logout request failed', e);
    } finally {
      clearUserForUI();
      window.location.href = 'index.html';
    }
  }

  /* ---------- initAuth (público) ---------- */
  async function initAuth() {
    const cached = getUserFromStorage();
    if (cached) return cached;
    const user = await fetchProfile();
    if (user) saveUserForUI(user);
    return user;
  }

  /* ---------- expose ---------- */
  window.auth = Object.assign(window.auth || {}, {
    init: initAuth,
    logout,
    getUser: getUserFromStorage
  });

  if (form) form.addEventListener('submit', submitForm);
});
