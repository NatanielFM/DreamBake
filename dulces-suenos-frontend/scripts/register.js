// scripts/register.js
// Simplificado: el frontend es el único que muestra mensajes legibles.
// Basado en tu register.html (ids: fullName, email, phone, password, confirmPassword, toggle-password, register-submit, register-spinner, register-status)

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('register-form');
  const fullName = document.getElementById('fullName');
  const email = document.getElementById('email');
  const phone = document.getElementById('phone');
  const password = document.getElementById('password');
  const confirmPassword = document.getElementById('confirmPassword');
  const togglePass = document.getElementById('toggle-password');
  const statusEl = document.getElementById('register-status');
  const submitBtn = document.getElementById('register-submit');
  const spinner = document.getElementById('register-spinner');

  function setStatus(msg = '', type = 'info') {
    if (!statusEl) return;
    statusEl.textContent = msg;
    statusEl.style.color = type === 'error' ? '#cf1810' : '';
  }

  function setSubmitting(isSubmitting) {
    if (!submitBtn) return;
    submitBtn.disabled = isSubmitting;
    if (spinner) spinner.classList.toggle('visually-hidden', !isSubmitting);
  }

  if (togglePass && password) {
    togglePass.addEventListener('click', () => {
      const isPwd = password.getAttribute('type') === 'password';
      password.setAttribute('type', isPwd ? 'text' : 'password');
      togglePass.textContent = isPwd ? '🙈' : '👁️';
      password.focus();
    });
  }

  function validate() {
    if (!fullName || !email || !password || !confirmPassword) return 'Formulario incompleto.';
    const name = fullName.value.trim();
    const e = email.value.trim();
    const p = password.value;
    const cp = confirmPassword.value;
    if (!name) return 'Ingresá tu nombre completo.';
    if (!e) return 'Ingresá tu correo electrónico.';
    if (!p) return 'Ingresá tu contraseña.';
    if (p.length < 6) return 'La contraseña debe tener al menos 6 caracteres.';
    if (p !== cp) return 'Las contraseñas no coinciden.';
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
    setStatus('Registrando...', 'info');

    const payload = {
      name: fullName.value.trim(),
      email: email.value.trim(),
      phone: phone.value.trim(),
      password: password.value
    };

    try {
      const res = await fetch('http://localhost:8081/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      // Política solicitada: el frontend decide los textos.
      if (!res.ok) {
        if (res.status === 409) {
          setStatus('El correo ya está registrado.', 'error');
        } else if (res.status === 400) {
          setStatus('Datos inválidos. Verificá el formulario.', 'error');
        } else {
          setStatus('Datos incorrectos', 'error'); // mensaje genérico pedido
        }
        setSubmitting(false);
        return;
      }

      // Éxito: backend devuelve el user (sin password) o {}
      setStatus('Registro exitoso. Redirigiendo...', 'info');
      setTimeout(() => { window.location.href = 'login.html'; }, 900);

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
