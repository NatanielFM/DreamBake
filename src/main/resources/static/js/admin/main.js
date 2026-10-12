// /js/admin/main.js - Controlador Principal (UI, Sidebar y Modales Globales)
document.addEventListener('DOMContentLoaded', () => {

  // ================= 1. FUNCIONES GLOBALES (Alerta v1 y Modales) =================
  window.showConfirmModal = function(title, message, okText, okBtnClass, onConfirm) {
    document.getElementById('confirm-title').innerText = title;
    document.getElementById('confirm-message').innerText = message;
    
    const btnOk = document.getElementById('btn-confirm-ok');
    btnOk.innerText = okText;
    btnOk.className = `btn ${okBtnClass} btn-uniform`;

    document.getElementById('modal-confirm').hidden = false;

    const newBtnOk = btnOk.cloneNode(true);
    btnOk.replaceWith(newBtnOk);
    const btnCancel = document.getElementById('btn-confirm-cancel');
    const newBtnCancel = btnCancel.cloneNode(true);
    btnCancel.replaceWith(newBtnCancel);

    // Paso 6.8: mientras se hace la acción el botón queda bloqueado ("cargando") y recién después se
    // cierra la ventana. Si la acción abrió OTRA confirmación (ej: "falta stock, ¿forzar?"), el botón
    // ya fue reemplazado y la ventana nueva no se cierra.
    newBtnOk.addEventListener('click', () => {
      const run = async () => {
        try { await onConfirm(); }
        finally { if (newBtnOk.isConnected) document.getElementById('modal-confirm').hidden = true; }
      };
      if (window.DreamUI) window.DreamUI.busy(newBtnOk, run); else run();
    });

    newBtnCancel.addEventListener('click', () => {
      document.getElementById('modal-confirm').hidden = true;
    });
  };

  // "title" es opcional: por defecto "Revisá esto". Se puede cambiar para avisos que no son errores (ej: "Listo").
  window.showErrorAlert = function(errors, title) {
    let msg = "";
    if (typeof errors === 'object') {
      for (const key in errors) {
        msg += `${errors[key]}\n`;
      }
    } else {
      msg += errors;
    }
    const alertTitle = document.getElementById('alert-title');
    if (alertTitle) alertTitle.innerText = title || 'Revisá esto';
    document.getElementById('alert-message').innerText = msg.trim();
    document.getElementById('modal-alert').hidden = false;
  };

  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.modal-overlay').hidden = true;
    });
  });

  // Botones "Cancelar" / "Volver" / ✕ que cierran una ventana: data-close-modal="id-de-la-ventana".
  // (Antes cada uno tenía su onclick="…" dentro del HTML; la política de seguridad ya no lo permite.)
  document.addEventListener('click', (e) => {
    const closer = e.target.closest('[data-close-modal]');
    if (!closer) return;
    const modal = document.getElementById(closer.dataset.closeModal);
    if (modal) modal.hidden = true;
  });

  // Al abrir una ventana, el foco del teclado pasa adentro (al primer campo o botón) y,
  // al cerrarla, vuelve al botón que la abrió. Así se puede usar todo con el teclado.
  const focusBeforeModal = new Map();
  const modalObserver = new MutationObserver(records => {
    records.forEach(({ target: modal }) => {
      if (!modal.hidden) {
        if (!focusBeforeModal.has(modal)) focusBeforeModal.set(modal, document.activeElement);
        // Esperamos un cuadro: si la ventana se llena con JS después de mostrarse, el campo ya existe
        requestAnimationFrame(() => {
          if (modal.hidden || modal.contains(document.activeElement)) return;
          // Primero se busca un campo para escribir; si la ventana no tiene, un botón.
          // Se toma el primero que se ve (getClientRects vacío = oculto con display:none).
          const visible = (selector) => [...modal.querySelectorAll(selector)].find(el => el.getClientRects().length > 0);
          const first = visible('input:not([type="hidden"]):not([readonly]):not([disabled]), select:not([disabled]), textarea:not([readonly])')
            || visible('.modal-body button:not([disabled])')
            || visible('.close-modal');
          if (first) first.focus();
        });
      } else {
        const previous = focusBeforeModal.get(modal);
        focusBeforeModal.delete(modal);
        if (previous && document.contains(previous) && !topOpenModal()) previous.focus();
      }
    });
  });
  document.querySelectorAll('.modal-overlay').forEach(modal => {
    modalObserver.observe(modal, { attributes: true, attributeFilter: ['hidden'] });
  });

  // ================= 2. MENÚ LATERAL Y RESPONSIVE =================
  const sidebarToggleBtn = document.getElementById('sidebar-toggle');
  const backdrop = document.getElementById('sidebar-backdrop');
  const body = document.body;
  
  if (sidebarToggleBtn) {
    sidebarToggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      body.classList.toggle('sidebar-toggled');
      syncSidebarExpanded();
      // Emitimos un evento global para que otros módulos (como products) se adapten al nuevo espacio
      document.dispatchEvent(new Event('layoutChanged'));
    });
  }

  if (backdrop) {
    backdrop.addEventListener('click', () => {
      body.classList.remove('sidebar-toggled');
      syncSidebarExpanded();
    });
  }

  // Le avisa a los lectores de pantalla si el menú lateral está abierto
  // (en el celular "sidebar-toggled" = abierto; en escritorio = oculto)
  function syncSidebarExpanded() {
    if (!sidebarToggleBtn) return;
    const mobile = window.innerWidth <= 880;
    const toggled = body.classList.contains('sidebar-toggled');
    sidebarToggleBtn.setAttribute('aria-expanded', String(mobile ? toggled : !toggled));
  }
  syncSidebarExpanded();

  let isMobile = window.innerWidth <= 880;
  window.addEventListener('resize', () => {
    const currentlyMobile = window.innerWidth <= 880;
    if (isMobile !== currentlyMobile) {
      body.classList.remove('sidebar-toggled');
      isMobile = currentlyMobile;
      syncSidebarExpanded();
    }
  });

  // ================= 3. NAVEGACIÓN DE PESTAÑAS (Módulos) =================
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      
      btn.classList.add('active');
      const tabId = btn.dataset.tab;
      document.getElementById(tabId).classList.add('active');
      
      if (window.innerWidth <= 880) { body.classList.remove('sidebar-toggled'); syncSidebarExpanded(); }
      
      // Magia: Emitimos un evento informando qué pestaña se abrió.
      // Los otros scripts escucharán esto y cargarán sus datos solo si es necesario.
      document.dispatchEvent(new CustomEvent('tabChanged', { detail: tabId }));
    });
  });

  // ================= 4. TECLA ESC Y FONDO DE LAS VENTANAS =================
  // Esc cierra la ventana que está más arriba; si no hay ninguna abierta y el
  // menú lateral está abierto en el celular, cierra el menú.
  function topOpenModal() {
    const open = [...document.querySelectorAll('.modal-overlay:not([hidden])')];
    if (open.length === 0) return null;
    const z = (el) => parseInt(getComputedStyle(el).zIndex, 10) || 0;
    return open.sort((a, b) => z(b) - z(a))[0];
  }

  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    const modal = topOpenModal();
    if (modal) {
      modal.hidden = true;
      return;
    }
    if (window.innerWidth <= 880 && body.classList.contains('sidebar-toggled')) {
      body.classList.remove('sidebar-toggled');
      syncSidebarExpanded();
      if (sidebarToggleBtn) sidebarToggleBtn.focus();
    }
  });

  // Tocar el fondo oscuro cierra las ventanas SIN formulario (avisos, detalles).
  // Las que tienen formulario solo se cierran con sus botones, para no perder lo cargado.
  document.querySelectorAll('.modal-overlay').forEach(modal => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal && !modal.querySelector('form')) modal.hidden = true;
    });
  });

  // ================= 5. HEADER (Modo oscuro, Cerrar Sesión, Campana) =================
  // Modo claro / oscuro: se guarda en el navegador (theme-init.js lo aplica al entrar)
  const themeToggleBtn = document.getElementById('btn-theme-toggle');
  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      if (isDark) {
        document.documentElement.removeAttribute('data-theme');
        try { localStorage.setItem('theme', 'light'); } catch (e) { /* sin localStorage */ }
      } else {
        document.documentElement.setAttribute('data-theme', 'dark');
        try { localStorage.setItem('theme', 'dark'); } catch (e) { /* sin localStorage */ }
      }
      document.dispatchEvent(new Event('themechange')); // los gráficos se redibujan
    });
  }

  const logoutHeaderBtn = document.getElementById('btn-logout-header');
  if (logoutHeaderBtn) {
    logoutHeaderBtn.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Estás seguro que deseas salir?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

});

// Registro de auditoría (paso 3.3): avisa al servidor "quién descargó qué". El informe, la lista de
// compras y los recibos se arman acá en el navegador (imprimir / guardar como PDF), así que el
// servidor no se entera solo. type: "informe", "lista-compras" o "recibo" (con orderId).
// Si el aviso falla, la descarga sigue igual (no se muestra ningún error).
window.logDownload = function (type, orderId) {
  try {
    fetch('/api/audit/download', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type, orderId: orderId || null }),
      keepalive: true
    }).catch(() => {});
  } catch (e) { /* nada: la auditoría nunca frena al usuario */ }
};
