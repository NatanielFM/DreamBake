// /js/admin/settings.js - Pestaña "Ajustes": datos del negocio que usa la tienda
document.addEventListener('DOMContentLoaded', () => {
  // ================= EMAIL DE PRUEBA =================
  // Pide al servidor que mande un mail de prueba al administrador logueado y muestra el resultado
  // en el modal de avisos de main.js (showErrorAlert, con título propio cuando salió bien).
  const emailTestBtn = document.getElementById('btn-email-test');
  if (emailTestBtn) {
    emailTestBtn.addEventListener('click', async () => {
      const originalText = emailTestBtn.textContent;
      emailTestBtn.disabled = true;
      emailTestBtn.textContent = 'Enviando…';
      try {
        const res = await fetch('/api/email/test', { method: 'POST' });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
          window.showErrorAlert(data.message || 'Email enviado.', 'Email enviado');
        } else {
          window.showErrorAlert(data.message || 'No se pudo enviar el email de prueba.', 'No se pudo enviar');
        }
      } catch (err) {
        window.showErrorAlert('No se pudo conectar con el servidor.', 'No se pudo enviar');
      } finally {
        emailTestBtn.disabled = false;
        emailTestBtn.textContent = originalText;
      }
    });
  }


  // ================= CORREO DE ENVÍO =================
  // La cuenta desde la que salen los emails. Al guardar, el servidor prueba la conexión con
  // Gmail: si la rechaza, no se guarda y se explica por qué. La contraseña nunca vuelve del servidor.
  const mailForm = document.getElementById('mail-form');
  if (mailForm) {
    const mailStatus = document.getElementById('mail-status');
    const mailError = document.getElementById('mail-error');
    const mailUser = document.getElementById('mail-username');
    const mailPass = document.getElementById('mail-password');
    const mailHost = document.getElementById('mail-host');
    const mailPort = document.getElementById('mail-port');
    const mailTls = document.getElementById('mail-starttls');
    const mailRemove = document.getElementById('btn-mail-remove');
    const mailSave = document.getElementById('btn-mail-save');
    const passHint = document.getElementById('mail-password-hint');
    const PASS_HINT = '16 letras que da Gmail. No es la contraseña normal de la cuenta.';

    const showMailError = (message) => {
      mailError.textContent = message || '';
      mailError.hidden = !message;
    };

    // Muestra el estado actual: qué cuenta se usa y de dónde sale
    function renderMail(info) {
      if (info.problem) {
        mailStatus.textContent = info.problem;
      } else if (info.configured && info.source === 'panel') {
        mailStatus.textContent = `Los emails salen desde ${info.username}.`;
      } else if (info.configured) {
        mailStatus.textContent = 'Los emails salen desde la cuenta de config/application.properties. Si guardás una cuenta acá, se usa esta en su lugar.';
      } else {
        mailStatus.textContent = 'Todavía no hay una cuenta de correo: los emails a los clientes no se envían.';
      }
      mailUser.value = info.username || '';
      mailPass.value = '';
      // Si ya hay una guardada, se puede dejar vacía para no cambiarla
      mailPass.placeholder = info.hasPassword ? '•••••••• (guardada)' : '';
      passHint.textContent = info.hasPassword ? 'Dejala vacía para seguir usando la guardada.' : PASS_HINT;
      mailHost.value = info.host || 'smtp.gmail.com';
      mailPort.value = info.port || 587;
      mailTls.checked = info.starttls !== false;
      mailRemove.hidden = !info.hasPassword;
      // Si se usa otro proveedor, se muestra abierto
      document.getElementById('mail-advanced').open = (info.host && info.host !== 'smtp.gmail.com') || info.port !== 587;
    }

    async function loadMail() {
      try {
        const res = await fetch('/api/settings/mail');
        if (res.ok) renderMail(await res.json());
      } catch (e) { console.error('No se pudo cargar la cuenta de correo', e); }
    }

    mailForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      showMailError('');
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mailUser.value.trim())) {
        showMailError('Escribí el email completo de la cuenta (por ejemplo, dulcesuenos@gmail.com).');
        mailUser.focus();
        return;
      }
      const original = mailSave.textContent;
      mailSave.disabled = true;
      mailSave.textContent = 'Probando conexión…';
      try {
        const res = await fetch('/api/settings/mail', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: mailUser.value.trim(),
            password: mailPass.value,
            host: mailHost.value.trim(),
            port: mailPort.value ? Number(mailPort.value) : null,
            starttls: mailTls.checked
          })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
          renderMail(data);
          window.showErrorAlert(`Listo: el servidor de correo aceptó la cuenta. Los emails van a salir desde ${data.username}.`, 'Cuenta guardada');
        } else {
          showMailError(data.message || 'No se pudo guardar la cuenta.');
        }
      } catch (err) {
        showMailError('No se pudo conectar con el servidor.');
      } finally {
        mailSave.disabled = false;
        mailSave.textContent = original;
      }
    });

    mailRemove.addEventListener('click', () => {
      window.showConfirmModal('Quitar cuenta de correo',
        'El sistema deja de mandar emails desde esta cuenta (cumpleaños y promociones quedan en pausa hasta que cargues otra).',
        'Quitar cuenta', 'btn-red', async () => {
          try {
            const res = await fetch('/api/settings/mail', { method: 'DELETE' });
            if (res.ok) renderMail(await res.json());
            else window.showErrorAlert('No se pudo quitar la cuenta.');
          } catch (err) {
            window.showErrorAlert('No se pudo conectar con el servidor.');
          }
        });
    });

    document.addEventListener('tabChanged', (e) => { if (e.detail === 'ajustes') loadMail(); });
  }

  const form = document.getElementById('settings-form');
  if (!form) return;

  const countrySelect = document.getElementById('setting-country');
  const input = document.getElementById('setting-whatsapp');
  const hint = document.getElementById('setting-whatsapp-hint');
  const error = document.getElementById('setting-whatsapp-error');
  const preview = document.getElementById('setting-whatsapp-preview');
  const status = document.getElementById('settings-status');
  const testLink = document.getElementById('setting-whatsapp-test');

  // ================= PAÍS + NÚMERO LOCAL =================
  // WhatsApp necesita el número completo, solo con dígitos: código de país + número.
  // En Argentina, además, va un 9 después del 54 (celulares): 54 9 3765224107.
  // Por eso la dueña elige el país y escribe el número como lo usa siempre (3765224107);
  // acá se juntan las dos partes. Lo que se guarda en el servidor es "5493765224107".
  const ARGENTINA = '549';
  const isArgentina = () => countrySelect.value === ARGENTINA;
  const digitsOf = (value) => value.replace(/\D/g, '');

  // Limpia lo que escribió y devuelve solo el número local (sin código de país)
  function localNumber() {
    let digits = digitsOf(input.value);
    const code = countrySelect.value;
    // Si pegó el número completo con el código de país (+54 9 376…), se lo sacamos
    if (digits.startsWith(code) && digits.length > code.length + 6) digits = digits.slice(code.length);
    else if (isArgentina() && digits.startsWith('54') && digits.length === 12) digits = digits.slice(2);
    if (isArgentina()) {
      // Forma argentina de siempre: 0 adelante del área y 15 adelante del número (0376 15 5224107)
      if (digits.startsWith('0')) digits = digits.slice(1);
      if (digits.length === 12) {
        const pos = digits.indexOf('15', 2);
        if (pos >= 2 && pos <= 4) digits = digits.slice(0, pos) + digits.slice(pos + 2);
      }
    }
    return digits;
  }

  // Devuelve el mensaje de error, o '' si el número está bien
  function validate(local) {
    if (!local) return 'Escribí el número de WhatsApp.';
    if (isArgentina() && local.length !== 10) {
      return 'En Argentina el número tiene 10 dígitos: código de área + número, sin el 0 ni el 15 (ej: 3765224107).';
    }
    if (!isArgentina() && (local.length < 6 || countrySelect.value.length + local.length > 15)) {
      return 'Revisá el número: escribilo sin el código de país.';
    }
    return '';
  }

  // Texto de ayuda, vista previa ("Así queda: +54 9 3765224107") y link para probarlo
  function refresh() {
    hint.textContent = isArgentina()
      ? 'Código de área y número, sin el 0 ni el 15. Ejemplo: 3765224107.'
      : 'El número sin el código de país.';
    const local = localNumber();
    const ok = local && !validate(local);
    const code = countrySelect.value;
    const shownCode = isArgentina() ? '+54 9' : `+${code}`;
    preview.textContent = ok ? `Así queda: ${shownCode} ${local}` : '';
    testLink.href = ok ? `https://wa.me/${code}${local}` : '#';
    testLink.setAttribute('aria-disabled', ok ? 'false' : 'true');
  }

  // Al cargar: separa el número guardado en país + número local
  function fillFromSaved(full) {
    const codes = [...countrySelect.options].map(o => o.value).sort((a, b) => b.length - a.length);
    const code = codes.find(c => full.startsWith(c) && full.length > c.length);
    if (code) {
      countrySelect.value = code;
      input.value = full.slice(code.length);
    } else {
      // Un país que no está en la lista: se muestra completo con Argentina elegida para que se corrija
      countrySelect.value = ARGENTINA;
      input.value = full;
    }
    refresh();
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
      fillFromSaved(data.whatsapp || '');
    } catch (e) { console.error('No se pudieron cargar los ajustes', e); }
  }

  countrySelect.addEventListener('change', () => { status.hidden = true; showError(''); refresh(); });
  input.addEventListener('input', () => {
    refresh();
    status.hidden = true;
    if (input.getAttribute('aria-invalid') === 'true') showError(validate(localNumber()));
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    status.hidden = true;
    const local = localNumber();
    const message = validate(local);
    if (message) {
      showError(message);
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
        body: JSON.stringify({ whatsapp: countrySelect.value + local })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        fillFromSaved(data.whatsapp);
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
