// /js/admin/settings.js - Pestaña "Ajustes": datos del negocio que usa la tienda
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('settings-form');
  if (!form) return;

  const input = document.getElementById('setting-whatsapp');
  const error = document.getElementById('setting-whatsapp-error');
  const status = document.getElementById('settings-status');
  const testLink = document.getElementById('setting-whatsapp-test');

  // Solo los dígitos (la gente escribe "+54 9 3764-15…", con espacios y guiones)
  const digitsOf = (value) => value.replace(/\D/g, '');

  // "Probar el número" abre WhatsApp con el número escrito, para ver que sea el correcto
  function updateTestLink() {
    const digits = digitsOf(input.value);
    testLink.href = digits ? `https://wa.me/${digits}` : '#';
    testLink.setAttribute('aria-disabled', digits ? 'false' : 'true');
  }

  function showError(message) {
    error.textContent = message;
    error.hidden = !message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
  }

  async function loadSettings() {
    try {
      const res = await fetch('/api/settings');
      if (!res.ok) return;
      const data = await res.json();
      input.value = data.whatsapp || '';
      updateTestLink();
    } catch (e) { console.error('No se pudieron cargar los ajustes', e); }
  }

  input.addEventListener('input', () => {
    updateTestLink();
    status.hidden = true;
    if (input.getAttribute('aria-invalid') === 'true') showError('');
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    status.hidden = true;
    const digits = digitsOf(input.value);
    // Misma regla que el servidor (BusinessSettingService): de 10 a 15 números
    if (digits.length < 10 || digits.length > 15) {
      showError('Escribí el número completo: código de país, área y número (ej: 54 9 3764 159312).');
      input.focus();
      return;
    }
    showError('');

    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    try {
      const res = await fetch('/api/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ whatsapp: digits })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        input.value = data.whatsapp;
        updateTestLink();
        status.textContent = 'Guardado. La tienda ya usa este número.';
        status.hidden = false;
      } else {
        showError(data.message || 'No se pudo guardar el número.');
      }
    } catch (err) {
      showError('No se pudo conectar con el servidor.');
    } finally {
      submitBtn.disabled = false;
    }
  });

  // Se carga al abrir la pestaña (y una vez al entrar, por si se abre directamente)
  document.addEventListener('tabChanged', (e) => { if (e.detail === 'ajustes') loadSettings(); });
  loadSettings();
});
