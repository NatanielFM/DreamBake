document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const remember = document.getElementById('remember');
  const status = document.getElementById('login-status');
  const submitBtn = document.getElementById('login-submit');
  const spinner = document.getElementById('login-spinner');

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
      remember: remember ? remember.checked : false
    };

    try {
      const res = await fetch('http://localhost:8081/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      // Si la respuesta no es 2xx, mostramos siempre el texto deseado
      if (!res.ok) {
        setStatus('Datos incorrectos', 'error');
        setSubmitting(false);
        return;
      }

      // Si es OK, intentamos parsear el body (puede ser {} o el user)
      let data = null;
      try {
        data = await res.json();
      } catch (e) {
        data = null;
      }

      // Aquí decidís qué mostrar al usuario en caso de éxito.
      // Según tu pedido, quitamos mensajes del backend y mostramos uno local.
      setStatus('Sesión iniciada correctamente', 'info');

      // Si querés usar el user devuelto, está en `data` (puede ser objeto user o {})
      // Ejemplo: guardar user en localStorage si existe
      if (data && typeof data === 'object' && Object.keys(data).length > 0) {
        // Si el backend devolvió directamente el user (UserController devuelve user en 200),
        // guardamos en localStorage bajo "user" (opcional)
        localStorage.setItem('user', JSON.stringify(data));
      }

      // Redirigir a index.html
      setTimeout(() => { window.location.href = 'index.html'; }, 600);

    } catch (networkErr) {
      console.error('Network error:', networkErr);
      setStatus('Error de red. Intentá de nuevo.', 'error');
      setSubmitting(false);
    } finally {
      setSubmitting(false);
    }
  }

  if (form) form.addEventListener('submit', submitForm);
});
