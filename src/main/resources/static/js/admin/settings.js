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

  // ================= CONFIGURACIÓN DEL NEGOCIO (paso 6.4) =================
  // Nombre, WhatsApp, email, dirección, redes, horario, anticipación, seña, mensaje de la tienda y pausa.
  // El servidor (BusinessProfileService) valida todo de nuevo, guarda y audita el antes y el después.
  const form = document.getElementById('settings-form');
  if (!form) return;

  const FIELDS = ['name', 'whatsapp', 'email', 'address', 'instagram', 'facebook', 'openingHours',
                  'minLeadDays', 'depositPercent', 'storeBanner'];
  const $ = (field) => document.getElementById(`biz-${field}`);
  const paused = $('ordersPaused');
  const status = document.getElementById('settings-status');
  const preview = document.getElementById('biz-whatsapp-preview');
  const testLink = document.getElementById('biz-whatsapp-test');

  // ---- WhatsApp: la dueña escribe el número como lo usa siempre y acá se arma 549 + número ----
  // Acepta "0376 15 4159312", "376 4159312" o pegado completo "+54 9 376 415-9312".
  function localNumber() {
    let digits = $('whatsapp').value.replace(/\D/g, '');
    if (digits.startsWith('549') && digits.length === 13) digits = digits.slice(3);
    else if (digits.startsWith('54') && digits.length === 12) digits = digits.slice(2);
    if (digits.startsWith('0')) digits = digits.slice(1);
    if (digits.length === 12) {   // con el 15 adelante del número
      const pos = digits.indexOf('15', 2);
      if (pos >= 2 && pos <= 4) digits = digits.slice(0, pos) + digits.slice(pos + 2);
    }
    return digits;
  }
  const whatsappError = (local) => !local ? 'Escribí el número de WhatsApp.'
    : (local.length !== 10 ? 'Tiene que tener 10 dígitos: código de área + número, sin el 0 ni el 15 (ej: 3764 159312).' : '');

  function refreshWhatsapp() {
    const local = localNumber();
    const ok = !whatsappError(local);
    preview.textContent = ok ? `Así queda: +54 9 ${local}` : '';
    testLink.href = ok ? `https://wa.me/549${local}` : '#';
    testLink.setAttribute('aria-disabled', ok ? 'false' : 'true');
  }

  // ---- Errores por campo (los del navegador y los que devuelve el servidor) ----
  function setError(field, message) {
    const input = $(field);
    const box = document.getElementById(`biz-${field}-error`);
    if (input) input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (box) { box.textContent = message || ''; box.hidden = !message; }
  }
  const clearErrors = () => FIELDS.forEach(f => setError(f, ''));

  function validate() {
    const errors = {};
    if ($('name').value.trim().length < 2) errors.name = 'Escribí el nombre del negocio.';
    const w = whatsappError(localNumber());
    if (w) errors.whatsapp = w;
    const email = $('email').value.trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = 'Revisá el email (por ejemplo dulcesuenos@gmail.com).';
    const lead = Number($('minLeadDays').value);
    if ($('minLeadDays').value === '' || !Number.isInteger(lead) || lead < 0 || lead > 60) errors.minLeadDays = 'Un número entero de 0 a 60.';
    const dep = Number($('depositPercent').value);
    if ($('depositPercent').value === '' || !Number.isInteger(dep) || dep < 0 || dep > 100) errors.depositPercent = 'Un número entero de 0 a 100.';
    return errors;
  }

  function fill(data) {
    $('name').value = data.name || '';
    $('whatsapp').value = (data.whatsapp || '').replace(/^549/, '');
    ['email', 'address', 'instagram', 'facebook', 'openingHours', 'storeBanner'].forEach(f => { $(f).value = data[f] || ''; });
    $('minLeadDays').value = data.minLeadDays ?? 3;
    $('depositPercent').value = data.depositPercent ?? 50;
    paused.checked = Boolean(data.ordersPaused);
    refreshWhatsapp();
  }

  async function loadSettings() {
    try {
      const res = await fetch('/api/settings');
      if (!res.ok) return;
      fill(await res.json());
    } catch (e) { console.error('No se pudieron cargar los datos del negocio', e); }
  }

  $('whatsapp').addEventListener('input', () => {
    refreshWhatsapp();
    if ($('whatsapp').getAttribute('aria-invalid') === 'true') setError('whatsapp', whatsappError(localNumber()));
  });
  form.addEventListener('input', () => { status.hidden = true; });

  async function save() {
    clearErrors();
    status.hidden = true;
    const errors = validate();
    if (Object.keys(errors).length) {
      Object.entries(errors).forEach(([f, m]) => setError(f, m));
      $(Object.keys(errors)[0]).focus();
      return;
    }
    const body = {
      name: $('name').value, whatsapp: '549' + localNumber(), email: $('email').value,
      address: $('address').value, instagram: $('instagram').value, facebook: $('facebook').value,
      openingHours: $('openingHours').value, minLeadDays: Number($('minLeadDays').value),
      depositPercent: Number($('depositPercent').value), storeBanner: $('storeBanner').value,
      ordersPaused: paused.checked
    };
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    try {
      const res = await fetch('/api/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        fill(data);
        status.textContent = data.ordersPaused
          ? 'Guardado. Los pedidos online quedaron PAUSADOS: la tienda ya lo avisa.'
          : 'Guardado. La tienda ya muestra estos datos.';
        status.hidden = false;
      } else if (data.errors) {
        Object.entries(data.errors).forEach(([f, m]) => setError(f, m));
        $(Object.keys(data.errors)[0])?.focus();
      } else {
        window.showErrorAlert(data.message || 'No se pudieron guardar los datos.');
      }
    } catch (err) {
      window.showErrorAlert('No se pudo conectar con el servidor.');
    } finally {
      submitBtn.disabled = false;
    }
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); save(); });

  // Pausar o reanudar es una decisión importante: se pide confirmación y se guarda en el momento
  paused.addEventListener('change', () => {
    const pausing = paused.checked;
    paused.checked = !pausing;   // hasta que confirme, queda como estaba
    window.showConfirmModal(
      pausing ? 'Pausar pedidos online' : 'Reanudar pedidos online',
      pausing ? 'La tienda va a avisar que no toma pedidos y no va a dejar enviarlos. Podés seguir cargando pedidos desde el panel.'
              : 'La tienda vuelve a aceptar pedidos.',
      pausing ? 'Pausar' : 'Reanudar',
      'btn-pink',
      () => { paused.checked = pausing; save(); }
    );
  });

  // Se carga al abrir la pestaña (y una vez al entrar, por si se abre directamente)
  document.addEventListener('tabChanged', (e) => { if (e.detail === 'ajustes') loadSettings(); });
  loadSettings();
});
