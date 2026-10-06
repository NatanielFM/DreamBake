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

    newBtnOk.addEventListener('click', () => {
      document.getElementById('modal-confirm').hidden = true;
      onConfirm();
    });

    newBtnCancel.addEventListener('click', () => {
      document.getElementById('modal-confirm').hidden = true;
    });
  };

  window.showErrorAlert = function(errors) {
    let msg = "";
    if (typeof errors === 'object') {
      for (const key in errors) {
        msg += `${errors[key]}\n`;
      }
    } else {
      msg += errors;
    }
    document.getElementById('alert-message').innerText = msg.trim();
    document.getElementById('modal-alert').hidden = false;
  };

  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.modal-overlay').hidden = true;
    });
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

  // ================= 5. HEADER (Cerrar Sesión, Campana) =================
  const logoutHeaderBtn = document.getElementById('btn-logout-header');
  if (logoutHeaderBtn) {
    logoutHeaderBtn.addEventListener('click', () => {
      showConfirmModal('Cerrar sesión', '¿Estás seguro que deseas salir?', 'Salir', 'btn-red', () => {
        document.getElementById('logoutForm').submit();
      });
    });
  }

});