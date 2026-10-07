// /js/public/store.js — Lógica de la tienda pública (carrito, creador de tortas y checkout).
//
// Se carga en las páginas públicas (no en el panel). Partes:
//   1. Configuración del creador (tamaños, bizcochuelos y rellenos desde /api/cake-config/public).
//   2. Carrito: se guarda en localStorage "dreamBakeCart" como una lista de ítems
//      { productType, details, price }. Cada unidad es un ítem (dos tortas iguales = dos ítems).
//   3. Interfaz global: botón flotante, mini-carrito, avisos (toast) y addToCart().
//   4. Actualización de la interfaz del carrito (contadores, listas y totales).
//   5. Creador de tortas paso a paso (/builder).
//   6. Cupones (/cart).
//   7. Checkout: validación, guardado en /api/orders y mensaje de WhatsApp (/cart).
//
// Las funciones que se llaman desde el HTML quedan en window (openMiniCart, closeMiniCart,
// updateCartUI, removeCart, addToCart, showToast, showPublicAlert, selectSize,
// selectCoverage, toggleSponge, toggleFilling, goToStep, addCakeToCart).

document.addEventListener('DOMContentLoaded', async () => {

  const currentPath = window.location.pathname;

  // No ejecutar nada de esto en el panel de administración
  if (currentPath.startsWith('/admin')) return;

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  // Foto de producto que no carga (archivo borrado o mal cargado): se reemplaza por el isotipo
  // de la marca, igual que los productos sin foto. Sirve para el catálogo y la vitrina de la home.
  const FALLBACK_MARK = '<svg class="shop-card-fallback showcase-fallback" viewBox="0 0 40 40" aria-hidden="true" focusable="false"><path class="bi-wrapper" d="M10.5 21h19l-2.6 11.3A2.6 2.6 0 0 1 24.4 34.4h-8.8a2.6 2.6 0 0 1-2.5-2.1L10.5 21Z"/><path class="bi-frosting" d="M9.6 22.4c-2 0-3.6-1.6-3.6-3.6 0-1.8 1.3-3.3 3-3.6.4-4 3.8-7.1 7.8-7.1 1.2 0 2.4.3 3.4.8 1.2-.9 2.7-1.4 4.2-1.4 4 0 7.3 3.2 7.4 7.2 1.4.6 2.3 2 2.3 3.6 0 2.3-1.8 4.1-4.1 4.1H9.6Z"/><circle class="bi-cherry" cx="21.5" cy="5.6" r="3"/></svg>';
  function replaceBrokenImage(img) {
    const plate = img.closest('.shop-card-plate, .showcase-plate');
    if (!plate || plate.classList.contains('is-broken')) return;
    plate.classList.add('is-broken', 'is-loaded');
    img.remove();
    plate.insertAdjacentHTML('afterbegin', FALLBACK_MARK);
  }
  document.addEventListener('error', (e) => {
    if (e.target.matches && e.target.matches('.shop-card-plate img, .showcase-plate img')) replaceBrokenImage(e.target);
  }, true);   // "true": los errores de imagen no burbujean, se escuchan en la fase de captura
  document.querySelectorAll('.shop-card-plate img, .showcase-plate img').forEach(img => {
    if (img.complete && img.naturalWidth === 0 && img.getAttribute('src')) replaceBrokenImage(img);
  });

  // Número de WhatsApp del negocio. Se cambia desde Panel → Ajustes y llega en el footer
  // (data-whatsapp); el número de respaldo es el de siempre, por si la página no tiene footer.
  const BUSINESS_PHONE = document.querySelector('[data-whatsapp]')?.dataset.whatsapp || '5493764159312';

  // Utilidades ------------------------------------------------------------
  // Formato de precio argentino: 82000 -> "82.000"
  const money = (n) => Number(n || 0).toLocaleString('es-AR');

  // Escapa texto antes de meterlo en HTML (evita que un nombre con "<" rompa la página)
  function esc(text) {
    return String(text ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  }

  const ICON_BAG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M5 8h14l-1.2 11.1a2 2 0 0 1-2 1.9H8.2a2 2 0 0 1-2-1.9L5 8Z"/><path d="M9 10V7a3 3 0 0 1 6 0v3"/></svg>';
  const ICON_X = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M18 6 6 18M6 6l12 12"/></svg>';
  const ICON_TRASH = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/></svg>';
  const ICON_MINUS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M5 12h14"/></svg>';
  const ICON_PLUS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M12 5v14M5 12h14"/></svg>';
  const ICON_CHECK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m5 12.5 4.5 4.5L19 7.5"/></svg>';


  // ================= 1. CONFIGURACIÓN DEL CREADOR =================
  // Los tamaños y precios vienen SOLO del servidor. Antes había una lista de respaldo escrita
  // acá con precios viejos: si el servidor fallaba, el cliente podía pedir con un precio
  // equivocado. Ahora, si no llegan, el creador muestra un aviso (ver "Tamaños como botones").
  let cakeSizes = [];

  let spongesList = ['Vainilla', 'Chocolate', 'Limón', 'Coco'];

  let fillings = {
    Crema: ['Dulce de leche', 'Chantilly', 'Crema oreo', 'Crema bonobon', 'Crema bariloche', 'Mousse de frutilla', 'Ganache blanco', 'Ganache frutos rojos'],
    Fondant: ['Dulce de leche', 'Crema Bariloche', 'Ganache blanco/negro', 'Ganache frutos rojos', 'Ganache dulce de leche', 'Ganache frutillas', 'Ganache chocooreo', 'Ganache pistacho', 'Ganache menta', 'Ganache maracuyá', 'Ganache banana']
  };

  // Configuración real (solo las opciones habilitadas por el admin)
  try {
    const res = await fetch('/api/cake-config/public');
    if (res.ok) {
      const data = await res.json();
      if (data.sizes && data.sizes.length > 0) {
        cakeSizes = data.sizes.map(s => ({
          id: String(s.id),
          name: s.name,
          price: s.price,
          maxRellenos: s.maxRellenos,
          mixSponge: s.mixSponge
        }));
      }
      if (data.options && data.options.length > 0) {
        const dbSponges = data.options.filter(o => o.category === 'Sponge').map(o => o.name);
        if (dbSponges.length > 0) spongesList = dbSponges;

        fillings.Crema = data.options.filter(o => o.category === 'Crema').map(o => o.name);
        fillings.Fondant = data.options.filter(o => o.category === 'Fondant').map(o => o.name);
      }
    }
  } catch (err) {
    console.warn('No se pudo cargar la configuración del creador de tortas.');
  }

  // Deja los tamaños disponibles para otras partes del sitio (el "Desde $…" de la home)
  window.dreamBakeCakeSizes = cakeSizes;
  document.dispatchEvent(new CustomEvent('dreambake:cake-config', { detail: { sizes: cakeSizes } }));


  // ================= 2. CARRITO =================
  let cart = [];
  try {
    cart = JSON.parse(localStorage.getItem('dreamBakeCart')) || [];
  } catch (e) { cart = []; }

  let appliedCoupon = null;

  // Agrupa ítems iguales (mismo producto, detalle y precio) para mostrarlos con cantidad.
  // El carrito guardado no cambia: sigue teniendo un ítem por unidad.
  function groupCart() {
    const groups = [];
    const byKey = {};
    cart.forEach((item, index) => {
      const key = `${item.productType}||${item.details}||${item.price}`;
      if (!byKey[key]) {
        byKey[key] = { key, item, indexes: [] };
        groups.push(byKey[key]);
      }
      byKey[key].indexes.push(index);
    });
    return groups;
  }

  // Calcula subtotal, descuento del cupón y total
  function getTotals() {
    const subtotal = cart.reduce((acc, item) => acc + item.price, 0);
    let discount = 0;
    if (appliedCoupon && appliedCoupon.type === 'DESCUENTO' && appliedCoupon.discountPercentage) {
      discount = subtotal * (appliedCoupon.discountPercentage / 100);
    }
    return { subtotal, discount, total: subtotal - discount };
  }

  // Convierte el detalle de una torta ("10 porciones | Cubierta: Crema | …") en una lista legible
  function detailsToHtml(details) {
    if (!details) return '';
    const parts = String(details).split(' | ');
    if (parts.length === 1) return `<p class="line-desc">${esc(details)}</p>`;
    return `<ul class="line-specs">${parts.map(part => {
      const i = part.indexOf(': ');
      if (i === -1) return `<li>${esc(part)}</li>`;
      return `<li><span>${esc(part.slice(0, i))}:</span> ${esc(part.slice(i + 2))}</li>`;
    }).join('')}</ul>`;
  }


  // ================= 3. INTERFAZ GLOBAL: BOTÓN FLOTANTE, MINI-CARRITO Y AVISOS =================
  if (currentPath !== '/cart') {
    if (!document.getElementById('floating-cart-btn')) {
      document.body.insertAdjacentHTML('beforeend', `
        <button id="floating-cart-btn" class="floating-cart" type="button" style="display: none;">
          <span class="floating-cart-inner">${ICON_BAG} Ver pedido <span class="floating-cart-count" id="cart-count-float">0</span></span>
        </button>
      `);
    }

    // Mini-carrito: panel lateral en escritorio, hoja inferior en celular
    if (!document.getElementById('modal-checkout-preview')) {
      document.body.insertAdjacentHTML('beforeend', `
        <div id="modal-checkout-preview" class="public-modal-overlay mini-cart" hidden>
          <div class="public-modal-card mini-cart-panel" role="dialog" aria-modal="true" aria-labelledby="mini-cart-title">
            <div class="public-modal-header">
              <h2 id="mini-cart-title" class="mini-cart-title">Tu pedido</h2>
              <button type="button" class="public-close-modal" data-close-mini-cart aria-label="Cerrar">${ICON_X}</button>
            </div>
            <div class="public-modal-body">
              <div id="cart-items-preview"></div>
            </div>
            <div class="mini-cart-footer">
              <p class="mini-cart-total"><span>Total</span><strong>$<span id="cart-total-preview">0</span></strong></p>
              <a href="/cart" class="button button--ink button--lg button--block" data-mini-cart-cta>Ir a confirmar el pedido</a>
            </div>
          </div>
        </div>
      `);
    }
  }

  // Los botones se conectan acá (antes usaban onclick="…" dentro del HTML)
  document.getElementById('floating-cart-btn')?.addEventListener('click', () => openMiniCart());
  document.querySelector('[data-close-mini-cart]')?.addEventListener('click', () => closeMiniCart());

  let lastFocusBeforeMiniCart = null;

  window.openMiniCart = function () {
    const modal = document.getElementById('modal-checkout-preview');
    if (!modal) return;
    lastFocusBeforeMiniCart = document.activeElement;
    updateCartUI();
    modal.hidden = false;
    void modal.offsetWidth;           // fuerza el dibujo para que la transición arranque
    modal.classList.add('is-open');
    document.documentElement.classList.add('is-scroll-locked');
    const closeBtn = modal.querySelector('.public-close-modal');
    if (closeBtn) closeBtn.focus({ preventScroll: true });
  };

  window.closeMiniCart = function () {
    const modal = document.getElementById('modal-checkout-preview');
    if (!modal || modal.hidden) return;
    modal.classList.remove('is-open');
    document.documentElement.classList.remove('is-scroll-locked');
    const finish = () => { if (!modal.classList.contains('is-open')) modal.hidden = true; };
    // Se espera a que termine la salida (deslizamiento, o fundido con movimiento reducido)
    setTimeout(finish, 220);
    if (lastFocusBeforeMiniCart && lastFocusBeforeMiniCart.focus) lastFocusBeforeMiniCart.focus({ preventScroll: true });
  };

  // Cerrar el mini-carrito tocando el fondo o con Esc
  document.addEventListener('click', (e) => {
    const modal = document.getElementById('modal-checkout-preview');
    if (modal && !modal.hidden && e.target === modal) closeMiniCart();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    const modal = document.getElementById('modal-checkout-preview');
    if (modal && !modal.hidden) closeMiniCart();
    const alertModal = document.getElementById('modal-alert-v1');
    if (alertModal && !alertModal.hidden) alertModal.hidden = true;
  });

  // El carrito del header abre el mini-carrito (en /cart navega normal)
  const navbarCartBtn = document.querySelector('.btn-cart');
  if (navbarCartBtn && currentPath !== '/cart') {
    navbarCartBtn.addEventListener('click', (e) => {
      e.preventDefault();
      openMiniCart();
    });
  }

  // Aviso abajo de la pantalla que se va solo a los 3 segundos.
  // action (opcional): { label, href, onClick } para un botón dentro del aviso.
  window.showToast = function (message, action) {
    let region = document.getElementById('toast-region');
    if (!region) {
      document.body.insertAdjacentHTML('beforeend', '<div id="toast-region" class="toast-region" role="status" aria-live="polite"></div>');
      region = document.getElementById('toast-region');
    }

    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `${ICON_CHECK}<span class="toast-message"></span>`;
    toast.querySelector('.toast-message').textContent = message;

    if (action) {
      const link = document.createElement('a');
      link.className = 'toast-action';
      link.href = action.href || '#';
      link.textContent = action.label;
      if (action.onClick) link.addEventListener('click', (ev) => { ev.preventDefault(); action.onClick(); });
      toast.appendChild(link);
    }

    region.replaceChildren(toast); // un aviso por vez
    requestAnimationFrame(() => toast.classList.add('is-visible'));

    clearTimeout(window.__dreamBakeToastTimer);
    window.__dreamBakeToastTimer = setTimeout(() => {
      toast.classList.remove('is-visible');
      // Se borra recién cuando terminó de salir (la transición más larga dura 320 ms)
      setTimeout(() => toast.remove(), 360);
    }, 3200);
  };

  // Suma un producto al carrito sin recargar la página y avisa con un toast.
  // item = { productId, productType, details, price } (mismo formato que guarda el carrito).
  // El id permite que el servidor busque el precio real del producto al recibir el pedido.
  window.addToCart = function (item) {
    cart.push({
      productId: item.productId ? Number(item.productId) : null,
      productType: item.productType,
      details: item.details,
      price: Number(item.price) || 0
    });
    updateCartUI();
    window.showToast(`Agregaste "${item.productType}" a tu pedido`, {
      label: 'Ver pedido',
      href: '/cart',
      onClick: currentPath !== '/cart' ? () => openMiniCart() : null
    });
  };

  // Botones "Agregar" de la vitrina (home) y del catálogo (/products).
  // Usan data-prod-name, data-prod-desc y data-prod-price; se escuchan en todo el documento.
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-add-to-cart]');
    if (!btn) return;
    window.addToCart({
      productId: btn.getAttribute('data-prod-id'),
      productType: btn.getAttribute('data-prod-name'),
      details: btn.getAttribute('data-prod-desc') || 'Producto del catálogo',
      price: parseFloat(btn.getAttribute('data-prod-price')) || 0
    });
    // Feedback en el propio botón: "Agregado" con un tilde durante 1,6 s
    const label = btn.querySelector('[data-add-label]');
    btn.classList.add('is-added');
    if (label) label.textContent = 'Agregado';
    clearTimeout(btn._addedTimer);
    btn._addedTimer = setTimeout(() => {
      btn.classList.remove('is-added');
      if (label) label.textContent = 'Agregar';
    }, 1600);
  });

  // Quita un ítem por su posición (se mantiene por compatibilidad)
  window.removeCart = function (index) {
    cart.splice(index, 1);
    updateCartUI();
  };

  // Acciones de cantidad sobre un grupo de ítems iguales (botones con data-cart-action)
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-cart-action]');
    if (!btn) return;
    const group = groupCart().find(g => g.key === btn.getAttribute('data-key'));
    if (!group) return;
    const action = btn.getAttribute('data-cart-action');

    const apply = () => {
      if (action === 'inc') {
        cart.push({ ...group.item });
      } else if (action === 'dec') {
        cart.splice(group.indexes[group.indexes.length - 1], 1);
      } else if (action === 'remove') {
        group.indexes.slice().reverse().forEach(i => cart.splice(i, 1));
      }
      updateCartUI();
    };

    // Si la fila desaparece, primero se desvanece y después se actualiza la lista
    const row = btn.closest('.cart-item-row');
    const disappears = action === 'remove' || (action === 'dec' && group.indexes.length === 1);
    // (con movimiento reducido la fila solo se desvanece, sin desplazarse: lo resuelve el CSS)
    if (row && disappears) {
      row.classList.add('is-removing');
      setTimeout(apply, 180);
    } else {
      apply();
    }
  });


  // ================= 4. ACTUALIZACIÓN DE LA INTERFAZ DEL CARRITO =================
  let lastTotal = null;
  let lastCount = null; // cantidad anterior, para saber si se agregó algo

  window.updateCartUI = function () {
    localStorage.setItem('dreamBakeCart', JSON.stringify(cart));

    document.querySelectorAll('.cart-count, #cart-count, .header-cart-count, #cart-count-float').forEach(el => {
      el.innerText = cart.length;
    });

    const floatBtn = document.getElementById('floating-cart-btn');
    if (floatBtn) {
      floatBtn.style.display = cart.length > 0 ? 'block' : 'none';
      // El contador del botón flotante "late" cuando se suma un producto
      if (lastCount !== null && cart.length > lastCount && !reduceMotion.matches) {
        floatBtn.classList.remove('is-bumped');
        void floatBtn.offsetWidth; // reinicia la animación
        floatBtn.classList.add('is-bumped');
      }
    }
    lastCount = cart.length;

    const { subtotal, discount, total } = getTotals();
    const groups = groupCart();

    // Mini-carrito
    const preview = document.getElementById('cart-items-preview');
    if (preview) {
      if (cart.length === 0) {
        preview.innerHTML = `<div class="cart-empty cart-empty--mini"><p>Tu pedido está vacío.</p><a class="button button--primary" href="/builder">Armá tu torta</a></div>`;
      } else {
        preview.innerHTML = groups.map(g => `
          <div class="cart-item-row cart-item-row--mini">
            <div class="line-main">
              <p class="cart-item-title">${esc(g.item.productType)}${g.indexes.length > 1 ? ` <span class="line-qty-inline">× ${g.indexes.length}</span>` : ''}</p>
            </div>
            <p class="line-price">$${money(g.item.price * g.indexes.length)}</p>
            <button type="button" class="line-remove" data-cart-action="remove" data-key="${esc(g.key)}" aria-label="Quitar ${esc(g.item.productType)}">${ICON_TRASH}</button>
          </div>`).join('');
      }
      const cta = document.querySelector('[data-mini-cart-cta]');
      if (cta) cta.hidden = cart.length === 0;
    }
    document.querySelectorAll('#cart-total-preview').forEach(el => { el.innerText = money(total); });

    // Página /cart: lista completa con cantidades
    const fullContainer = document.getElementById('full-cart-items');
    if (fullContainer) {
      const layout = document.querySelector('[data-cart-layout]');
      const emptyState = document.getElementById('cart-empty');
      if (layout) layout.hidden = cart.length === 0;
      if (emptyState) emptyState.hidden = cart.length > 0;
      // Ya se sabe qué mostrar: el contenido de /cart aparece (fundido en cart.css)
      document.querySelector('.cart-page')?.classList.add('is-ready');

      fullContainer.innerHTML = groups.map(g => {
        const qty = g.indexes.length;
        const name = esc(g.item.productType);
        return `
          <li class="cart-item-row">
            <div class="line-main">
              <h3 class="cart-item-title">${name}</h3>
              ${detailsToHtml(g.item.details)}
              <p class="line-unit">$${money(g.item.price)} c/u</p>
            </div>
            <div class="line-side">
              <p class="line-price">$${money(g.item.price * qty)}</p>
              <div class="qty" role="group" aria-label="Cantidad de ${name}">
                <button type="button" class="qty-btn" data-cart-action="dec" data-key="${esc(g.key)}" aria-label="${qty === 1 ? 'Quitar' : 'Restar uno de'} ${name}">${qty === 1 ? ICON_TRASH : ICON_MINUS}</button>
                <span class="qty-value" aria-live="polite">${qty}</span>
                <button type="button" class="qty-btn" data-cart-action="inc" data-key="${esc(g.key)}" aria-label="Sumar uno de ${name}">${ICON_PLUS}</button>
              </div>
            </div>
          </li>`;
      }).join('');

      // El botón de confirmar está en el resumen (fuera del <form>, unido con form="final-checkout-form")
      const submitBtn = document.querySelector('button[type="submit"][form="final-checkout-form"]') || document.getElementById('final-checkout-form')?.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.disabled = cart.length === 0;

      const setText = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
      setText('cart-subtotal', money(subtotal));
      setText('cart-items-count', `${cart.length} ${cart.length === 1 ? 'producto' : 'productos'}`);

      const discountRow = document.getElementById('cart-discount-row');
      if (discountRow) {
        discountRow.hidden = discount === 0;
        setText('cart-discount', money(discount));
        setText('cart-discount-label', appliedCoupon ? `Cupón ${appliedCoupon.code} (${appliedCoupon.discountPercentage}%)` : 'Descuento');
      }
      const benefitRow = document.getElementById('cart-benefit-row');
      if (benefitRow) {
        const hasBenefit = appliedCoupon && appliedCoupon.type !== 'DESCUENTO';
        benefitRow.hidden = !hasBenefit;
        if (hasBenefit) setText('cart-benefit', appliedCoupon.type === 'ENVIO_GRATIS' ? 'Envío gratis' : (appliedCoupon.customText || 'Regalo'));
      }

      document.querySelectorAll('#full-cart-total, [data-cart-total]').forEach(el => { el.textContent = money(total); });

      // El total "late" apenas cuando cambia
      if (lastTotal !== null && lastTotal !== total && !reduceMotion.matches) {
        document.querySelectorAll('.summary-total').forEach(el => {
          el.classList.remove('is-bumped'); void el.offsetWidth; el.classList.add('is-bumped');
        });
      }
    }
    lastTotal = total;

    // Avisa al header (header.js) que cambió el carrito
    document.dispatchEvent(new CustomEvent('dreambake:cart-updated', { detail: { count: cart.length } }));
  };

  updateCartUI();


  // ================= 5. CREADOR DE TORTAS (/builder) =================
  // Pasos: 1 Tamaño y cobertura · 2 Bizcochuelo · 3 Rellenos · 4 Detalles · 5 Resumen
  const TOTAL_STEPS = 5;
  let cakeData = { size: null, coverage: null, sponges: [], fillings: [] };
  let currentStep = 1;
  const gridSizes = document.getElementById('grid-sizes');

  if (gridSizes && cakeSizes.length === 0) {
    // No llegaron los tamaños (servidor caído o el admin los deshabilitó todos): aviso en vez de precios inventados
    gridSizes.innerHTML = `
      <div class="builder-unavailable" role="status">
        <p><strong>No pudimos cargar los tamaños de torta.</strong> Probá de nuevo en un rato o escribinos y te pasamos los precios.</p>
        <a class="button button--primary" href="https://wa.me/${esc(BUSINESS_PHONE)}" target="_blank" rel="noopener">Escribinos por WhatsApp</a>
      </div>`;
  } else if (gridSizes) {
    // Tamaños como botones (se pueden usar con el teclado); aria-pressed indica cuál está elegido
    gridSizes.innerHTML = cakeSizes.map(size => {
      const match = String(size.name).match(/^([\d\s\-–]+)\s*porciones\s*(.*)$/i);
      const portions = match ? match[1].trim() : '';
      const rest = match ? match[2].replace(/^\(|\)$/g, '').trim() : size.name;
      return `
        <button type="button" class="select-card size-card" data-id="${esc(size.id)}" aria-pressed="false">
          <span class="option-check" aria-hidden="true">${ICON_CHECK}</span>
          ${portions ? `<span class="size-portions">${esc(portions)}</span><span class="size-unit">porciones</span>` : ''}
          <span class="size-name">${esc(portions ? rest : size.name)}</span>
          <span class="price">$${money(size.price)}</span>
        </button>`;
    }).join('');
  }

  // Clicks del creador (tamaño, cobertura y "Agregar al pedido"), en un solo lugar
  // (antes eran onclick="…" escritos en el HTML)
  document.addEventListener('click', (e) => {
    const sizeCard = e.target.closest('.size-card[data-id]');
    if (sizeCard) { window.selectSize(sizeCard.dataset.id); return; }
    const coverageCard = e.target.closest('#grid-coverages .select-card[data-val]');
    if (coverageCard) { window.selectCoverage(coverageCard.dataset.val); return; }
    if (e.target.closest('#btn-add-cake')) window.addCakeToCart();
  });

  // Marca como elegidas (o no) las tarjetas de un grupo
  function syncPressed(selector, isSelected) {
    document.querySelectorAll(selector).forEach(card => {
      const on = isSelected(card);
      card.classList.toggle('selected', on);
      card.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
  }

  // Mensaje de ayuda/error de un paso
  function setStepMessage(step, text) {
    const el = document.getElementById(`step-msg-${step}`);
    if (!el) return;
    el.textContent = text || '';
    el.hidden = !text;
  }

  // Aviso de límite: si el mensaje quedó fuera de la pantalla (lista larga en celular), se repite como toast
  function showLimitMessage(step, text) {
    setStepMessage(step, text);
    const el = document.getElementById(`step-msg-${step}`);
    const r = el ? el.getBoundingClientRect() : null;
    if (r && (r.bottom < 70 || r.top > window.innerHeight)) window.showToast(text);
  }

  window.selectSize = function (id) {
    const newSize = cakeSizes.find(s => String(s.id) === String(id));
    if (!newSize) return;
    // Mismas reglas de siempre: si baja el máximo de rellenos se vacían; si el tamaño no permite mezclar bizcochuelos, se vacían
    if (cakeData.size && newSize.maxRellenos < cakeData.size.maxRellenos) cakeData.fillings = [];
    if (cakeData.size && newSize.mixSponge === false) cakeData.sponges = [];
    cakeData.size = newSize;
    syncPressed('.size-card', c => c.getAttribute('data-id') === String(id));
    setStepMessage(1, '');
    renderSummary();
  };

  window.selectCoverage = function (cov) {
    if (cakeData.coverage !== cov) cakeData.fillings = [];
    cakeData.coverage = cov;
    syncPressed('#grid-coverages .select-card', c => c.getAttribute('data-val') === cov);
    setStepMessage(1, '');
    renderSummary();
  };

  // Bizcochuelos y rellenos: se arman al entrar al paso, con las opciones habilitadas
  function buildOptions() {
    if (!cakeData.size) return;
    const spongeLimit = cakeData.size.mixSponge ? 2 : 1;
    const setText = (id, text) => { const el = document.getElementById(id); if (el) el.innerText = text; };
    setText('sponge-info', cakeData.size.mixSponge ? 'Elegí hasta 2 sabores' : 'Elegí 1 sabor');
    setText('fillings-info', `Elegí hasta ${cakeData.size.maxRellenos}`);
    setText('lbl-cov', cakeData.coverage || '');

    const gridS = document.getElementById('grid-sponges');
    if (gridS) {
      gridS.innerHTML = spongesList.map(s => `
        <button type="button" class="select-card chip-option sponge-card" data-val="${esc(s)}" aria-pressed="false">
          <span class="option-check" aria-hidden="true">${ICON_CHECK}</span>${esc(s)}
        </button>`).join('');
    }

    const gridF = document.getElementById('grid-fillings');
    if (gridF) {
      gridF.innerHTML = (fillings[cakeData.coverage] || []).map(f => `
        <button type="button" class="select-card chip-option fill-card" data-val="${esc(f)}" aria-pressed="false">
          <span class="option-check" aria-hidden="true">${ICON_CHECK}</span>${esc(f)}
        </button>`).join('');
    }
    refreshOptionStates(spongeLimit);
  }

  // Marca elegidas/deshabilitadas según el límite de cada grupo
  function refreshOptionStates() {
    if (!cakeData.size) return;
    const spongeLimit = cakeData.size.mixSponge ? 2 : 1;
    const fillLimit = cakeData.size.maxRellenos;

    document.querySelectorAll('.sponge-card').forEach(card => {
      const on = cakeData.sponges.includes(card.getAttribute('data-val'));
      card.classList.toggle('selected', on);
      card.setAttribute('aria-pressed', on ? 'true' : 'false');
      // Con 1 solo sabor permitido se puede cambiar directo; con 2, los demás se atenúan al llegar al límite
      card.classList.toggle('is-limited', !on && spongeLimit > 1 && cakeData.sponges.length >= spongeLimit);
    });
    document.querySelectorAll('.fill-card').forEach(card => {
      const on = cakeData.fillings.includes(card.getAttribute('data-val'));
      card.classList.toggle('selected', on);
      card.setAttribute('aria-pressed', on ? 'true' : 'false');
      card.classList.toggle('is-limited', !on && cakeData.fillings.length >= fillLimit);
    });

    const counter = document.getElementById('fillings-count');
    if (counter) counter.textContent = `${cakeData.fillings.length} de ${fillLimit}`;
    const spongeCounter = document.getElementById('sponges-count');
    if (spongeCounter) spongeCounter.textContent = `${cakeData.sponges.length} de ${spongeLimit}`;
  }

  // Clicks en bizcochuelos y rellenos (los botones se crean dinámicamente)
  document.addEventListener('click', (e) => {
    const sponge = e.target.closest('.sponge-card');
    if (sponge) { toggleSponge(sponge.getAttribute('data-val')); return; }
    const fill = e.target.closest('.fill-card');
    if (fill) toggleFilling(fill.getAttribute('data-val'));
  });

  window.toggleSponge = function (sp) {
    if (!cakeData.size) return;
    const limit = cakeData.size.mixSponge ? 2 : 1;
    const idx = cakeData.sponges.indexOf(sp);
    if (idx > -1) {
      cakeData.sponges.splice(idx, 1);
    } else if (limit === 1) {
      cakeData.sponges = [sp];             // con un solo sabor, elegir otro lo reemplaza
    } else if (cakeData.sponges.length < limit) {
      cakeData.sponges.push(sp);
    } else {
      showLimitMessage(2, `Este tamaño permite hasta ${limit} sabores. Quitá uno para elegir otro.`);
      return;
    }
    setStepMessage(2, '');
    refreshOptionStates();
    renderSummary();
  };

  window.toggleFilling = function (f) {
    if (!cakeData.size) return;
    const limit = cakeData.size.maxRellenos;
    const idx = cakeData.fillings.indexOf(f);
    if (idx > -1) {
      cakeData.fillings.splice(idx, 1);
    } else if (cakeData.fillings.length < limit) {
      cakeData.fillings.push(f);
    } else {
      showLimitMessage(3, `Este tamaño lleva hasta ${limit} rellenos. Quitá uno para cambiarlo.`);
      return;
    }
    setStepMessage(3, '');
    refreshOptionStates();
    renderSummary();
  };

  // ¿Se puede pasar del paso indicado? Devuelve el mensaje de error o '' si está todo bien
  function stepError(step) {
    if (step === 1) {
      if (!cakeData.size && !cakeData.coverage) return 'Elegí un tamaño y una cobertura para seguir.';
      if (!cakeData.size) return 'Elegí un tamaño para seguir.';
      if (!cakeData.coverage) return 'Elegí una cobertura para seguir.';
    }
    if (step === 2 && cakeData.sponges.length === 0) return 'Elegí al menos un sabor de bizcochuelo.';
    if (step === 3 && cakeData.fillings.length === 0) return 'Elegí al menos un relleno.';
    return '';
  }

  window.goToStep = function (step) {
    step = Math.max(1, Math.min(TOTAL_STEPS, Number(step)));

    // Para avanzar hay que completar los pasos anteriores
    for (let s = 1; s < step; s++) {
      const err = stepError(s);
      if (err) {
        if (s !== currentStep) showStep(s);
        setStepMessage(s, err);
        const msg = document.getElementById(`step-msg-${s}`);
        if (msg) {
          msg.focus({ preventScroll: true });
          msg.scrollIntoView({ behavior: reduceMotion.matches ? 'auto' : 'smooth', block: 'center' });
        }
        return;
      }
    }
    if (step === 2 || step === 3) buildOptions();
    showStep(step);
  };

  function showStep(step) {
    // Solo entra "desde la izquierda" si se vuelve a un paso anterior
    // (al cargar la página el paso es el mismo y no debe reiniciar su animación)
    const backward = step < currentStep;
    currentStep = step;

    document.querySelectorAll('.wizard-step').forEach(s => {
      const isActive = s.id === `wizard-step-${step}`;
      s.classList.toggle('active', isActive);
      s.classList.toggle('from-left', isActive && backward);
    });

    // Indicador de pasos: completados, actual y pendientes
    document.querySelectorAll('.step').forEach((s, idx) => {
      const n = idx + 1;
      s.classList.toggle('active', n <= step);
      s.classList.toggle('is-current', n === step);
      s.classList.toggle('is-done', n < step);
      if (n === step) s.setAttribute('aria-current', 'step'); else s.removeAttribute('aria-current');
    });

    const prev = document.getElementById('btn-prev');
    const next = document.getElementById('btn-next');
    const add = document.getElementById('btn-add-cake');
    if (prev) prev.hidden = step === 1;
    if (next) next.hidden = step === TOTAL_STEPS;
    if (add) add.hidden = step !== TOTAL_STEPS;

    const label = document.getElementById('step-counter');
    if (label) label.textContent = `Paso ${step} de ${TOTAL_STEPS}`;

    renderSummary();

    const builderSection = document.getElementById('cake-builder');
    if (builderSection && builderSection.getBoundingClientRect().top < 0) {
      builderSection.scrollIntoView({ behavior: reduceMotion.matches ? 'auto' : 'smooth', block: 'start' });
    }
    const heading = document.querySelector(`#wizard-step-${step} .step-heading`);
    if (heading) heading.focus({ preventScroll: true });
  }

  // Resumen en vivo (columna lateral, barra inferior y paso final)
  function renderSummary() {
    if (!gridSizes) return;
    const d = cakeData;
    const set = (selector, text) => document.querySelectorAll(selector).forEach(el => { el.textContent = text; });
    const details = (document.getElementById('cake-details')?.value || '').trim();

    set('[data-sum="size"]', d.size ? d.size.name : 'Sin elegir');
    set('[data-sum="coverage"]', d.coverage || 'Sin elegir');
    set('[data-sum="sponges"]', d.sponges.length ? d.sponges.join(', ') : 'Sin elegir');
    set('[data-sum="fillings"]', d.fillings.length ? d.fillings.join(', ') : 'Sin elegir');
    set('[data-sum="details"]', details || 'Sin detalles');

    const priceText = d.size ? `$${money(d.size.price)}` : '—';
    document.querySelectorAll('[data-sum="price"]').forEach(el => {
      if (el.textContent !== priceText && d.size && !reduceMotion.matches) {
        el.classList.remove('is-bumped'); void el.offsetWidth; el.classList.add('is-bumped');
      }
      el.textContent = priceText;
      el.classList.toggle('is-empty', !d.size); // sin tamaño elegido, el guion se ve suave (builder.css)
    });

    document.querySelectorAll('[data-sum-row]').forEach(row => {
      const key = row.getAttribute('data-sum-row');
      const empty = key === 'size' ? !d.size : key === 'coverage' ? !d.coverage : key === 'sponges' ? !d.sponges.length : key === 'fillings' ? !d.fillings.length : !details;
      row.classList.toggle('is-empty', empty);
    });
  }

  const cakeDetailsInput = document.getElementById('cake-details');
  if (cakeDetailsInput) cakeDetailsInput.addEventListener('input', renderSummary);

  const btnPrev = document.getElementById('btn-prev');
  const btnNext = document.getElementById('btn-next');
  if (btnPrev) btnPrev.addEventListener('click', () => goToStep(currentStep - 1));
  if (btnNext) btnNext.addEventListener('click', () => goToStep(currentStep + 1));

  // El indicador de pasos permite volver a un paso ya hecho
  document.querySelectorAll('.step').forEach((s, idx) => {
    s.addEventListener('click', () => { if (idx + 1 < currentStep) goToStep(idx + 1); });
  });

  window.addCakeToCart = function () {
    for (let s = 1; s <= 3; s++) {
      if (stepError(s)) { goToStep(s + 1); return; }
    }
    const details = document.getElementById('cake-details').value || 'Ninguno';
    // Mismo formato de texto que siempre (lo leen el panel y el mensaje de WhatsApp)
    const detailsText = `${cakeData.size.name} | Cubierta: ${cakeData.coverage} | Bizcochuelo: ${cakeData.sponges.join(', ')} | Rellenos: ${cakeData.fillings.join(', ')} | Detalles: ${details}`;

    cart.push({
      productType: 'Torta Personalizada',
      cakeSizeId: Number(cakeData.size.id),   // el servidor busca el precio real de este tamaño
      details: detailsText,
      price: cakeData.size.price
    });
    updateCartUI();

    // Vuelve a empezar para poder armar otra
    cakeData = { size: null, coverage: null, sponges: [], fillings: [] };
    document.querySelectorAll('.select-card').forEach(c => { c.classList.remove('selected'); c.setAttribute('aria-pressed', 'false'); });
    document.getElementById('cake-details').value = '';
    showStep(1);

    openMiniCart();
  };

  if (gridSizes) showStep(1);


  // ================= 6. CUPONES (/cart) =================
  const btnApplyCoupon = document.getElementById('btn-apply-coupon');
  const couponInput = document.getElementById('cart-coupon');
  const couponMsg = document.getElementById('coupon-message');

  function setCouponMessage(type, text) {
    if (!couponMsg) return;
    couponMsg.className = `coupon-message coupon-message--${type}`;
    couponMsg.innerHTML = `${type === 'success' ? ICON_CHECK : ICON_X}<span></span>`;
    couponMsg.querySelector('span').textContent = text;
    couponMsg.hidden = false;
    if (couponInput) couponInput.setAttribute('aria-invalid', type === 'error' ? 'true' : 'false');
  }

  async function applyCoupon() {
    const code = couponInput.value.trim();
    if (!code) {
      setCouponMessage('error', 'Escribí el código del cupón.');
      couponInput.focus();
      return;
    }
    btnApplyCoupon.setAttribute('aria-busy', 'true');
    try {
      const res = await fetch(`/api/coupons/validate/${encodeURIComponent(code)}`);
      if (res.ok) {
        appliedCoupon = await res.json();
        setCouponMessage('success', 'Cupón aplicado.');
      } else {
        appliedCoupon = null;
        setCouponMessage('error', 'Ese cupón no es válido, ya se usó o está inactivo.');
      }
    } catch (e) {
      appliedCoupon = null;
      setCouponMessage('error', 'No pudimos validar el cupón. Revisá tu conexión e intentá de nuevo.');
    } finally {
      btnApplyCoupon.removeAttribute('aria-busy');
      updateCartUI();
    }
  }

  if (btnApplyCoupon && couponInput) {
    btnApplyCoupon.addEventListener('click', applyCoupon);
    couponInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); applyCoupon(); }
    });
  }


  // ================= 7. CHECKOUT (/cart) =================
  // Fecha mínima: 3 días de anticipación
  const dateInput = document.getElementById('chk-date');
  let minDate = '';
  if (dateInput) {
    const today = new Date();
    today.setDate(today.getDate() + 3);
    minDate = today.toISOString().split('T')[0];
    dateInput.setAttribute('min', minDate);
    const hint = document.getElementById('chk-date-hint');
    if (hint) hint.textContent = `Pedidos con 3 días de anticipación: elegí una fecha desde el ${minDate.split('-').reverse().join('/')}.`;
  }

  document.querySelector('[data-close-alert]')?.addEventListener('click', () => {
    document.getElementById('modal-alert-v1').hidden = true;
  });

  // Avisos en ventana (errores de guardado o de conexión)
  window.showPublicAlert = function (msg) {
    const alertMsg = document.getElementById('alert-v1-msg');
    const alertModal = document.getElementById('modal-alert-v1');
    if (alertMsg && alertModal) {
      alertMsg.innerText = msg;
      alertModal.hidden = false;
      alertModal.querySelector('button')?.focus();
    } else {
      window.showToast(msg);
    }
  };

  // Validación de cada campo: devuelve el mensaje de error o ''
  const validators = {
    'chk-name': (v) => v.trim().length >= 3 ? '' : 'Escribí tu nombre y apellido.',
    'chk-phone': (v) => /^[+\d][\d\s\-()]{7,19}$/.test(v.trim()) && (v.match(/\d/g) || []).length >= 8 ? '' : 'Escribí un celular válido, con código de área (por ejemplo 3764 123456).',
    'chk-email': (v) => !v.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()) ? '' : 'Revisá el email (por ejemplo nombre@gmail.com) o dejalo vacío.',
    'chk-date': (v) => !v ? 'Elegí la fecha de entrega.' : (minDate && v < minDate ? `La fecha tiene que ser desde el ${minDate.split('-').reverse().join('/')} (3 días de anticipación).` : '')
  };

  function showFieldError(id, message) {
    const input = document.getElementById(id);
    const error = document.getElementById(`${id}-error`);
    const field = input?.closest('.field');
    if (!input) return;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (field) field.classList.toggle('field--error', Boolean(message));
    if (error) {
      error.hidden = !message;
      const span = error.querySelector('span');
      if (span) span.textContent = message; else error.textContent = message;
    }
  }

  function validateField(id) {
    const input = document.getElementById(id);
    if (!input || !validators[id]) return true;
    const message = validators[id](input.value);
    showFieldError(id, message);
    return !message;
  }

  function validateChoice(name, errorId, message) {
    const checked = document.querySelector(`input[name="${name}"]:checked`);
    const error = document.getElementById(errorId);
    if (error) error.hidden = Boolean(checked);
    const group = error?.closest('.field');
    if (group) group.classList.toggle('field--error', !checked);
    if (error && !checked) error.querySelector('span').textContent = message;
    return Boolean(checked);
  }

  // Validar al salir de cada campo (y volver a validar mientras se corrige)
  Object.keys(validators).forEach(id => {
    const input = document.getElementById(id);
    if (!input) return;
    input.addEventListener('blur', () => { if (input.value || input.getAttribute('aria-invalid') === 'true') validateField(id); });
    input.addEventListener('input', () => { if (input.getAttribute('aria-invalid') === 'true') validateField(id); });
  });
  document.querySelectorAll('input[name="shift"]').forEach(r => r.addEventListener('change', () => validateChoice('shift', 'chk-shift-error', '')));

  // Retiro o envío: la dirección se pide solo si es envío
  const addressField = document.getElementById('chk-address-field');
  const isDelivery = () => (document.querySelector('input[name="delivery"]:checked') || {}).value === 'Envío';
  validators['chk-address'] = (v) => !isDelivery() || v.trim().length >= 5 ? '' : 'Escribí la dirección: calle, número y barrio.';
  document.getElementById('chk-address')?.addEventListener('input', (e) => {
    if (e.target.getAttribute('aria-invalid') === 'true') validateField('chk-address');
  });
  document.querySelectorAll('input[name="delivery"]').forEach(r => r.addEventListener('change', () => {
    if (!addressField) return;
    addressField.hidden = !isDelivery();
    if (isDelivery()) document.getElementById('chk-address').focus();
    else showFieldError('chk-address', '');
  }));

  const finalForm = document.getElementById('final-checkout-form');
  if (finalForm) {
    finalForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (cart.length === 0) return;

      // 1. Validar todo y llevar el foco al primer error
      const okFields = Object.keys(validators).map(validateField);
      const okShift = validateChoice('shift', 'chk-shift-error', 'Elegí el turno de entrega.');
      if (okFields.includes(false) || !okShift) {
        const firstInvalid = finalForm.querySelector('[aria-invalid="true"], .field--error input');
        if (firstInvalid) firstInvalid.focus();
        return;
      }

      // El botón está fuera del <form> (en el resumen), por eso se busca por su atributo form
      const submitBtn = document.querySelector('button[type="submit"][form="final-checkout-form"]') || finalForm.querySelector('button[type="submit"]');
      submitBtn.setAttribute('aria-busy', 'true');
      submitBtn.disabled = true;

      const cName = document.getElementById('chk-name').value.trim();
      const cPhone = document.getElementById('chk-phone').value.trim();
      const cEmail = document.getElementById('chk-email') ? document.getElementById('chk-email').value.trim() : '';
      const cDetails = document.getElementById('chk-general-details') ? document.getElementById('chk-general-details').value : '';
      const cDate = document.getElementById('chk-date').value;
      const cShift = document.querySelector('input[name="shift"]:checked').value;
      const cPay = (document.querySelector('input[name="payment"]:checked') || {}).value || 'Transferencia';
      const cDelivery = isDelivery() ? 'Envío' : 'Retiro';
      const cAddress = isDelivery() ? document.getElementById('chk-address').value.trim() : '';

      // Texto de los ítems para el mensaje de WhatsApp
      const itemsText = cart.map(item => `> *${item.productType}*\n${item.details}`).join('\n\n');

      // El navegador dice QUÉ se compra (id del producto o del tamaño de torta) y el cupón.
      // El servidor busca los precios reales, aplica el cupón y calcula el total (OrderPricingService).
      const orderData = {
        customerName: cName,
        customerPhone: cPhone,
        customerEmail: cEmail,
        deliveryDate: cDate,
        deliveryShift: cShift,
        deliveryMethod: cDelivery,
        deliveryAddress: cAddress,
        couponCode: appliedCoupon ? appliedCoupon.code : null,
        items: cart.map((item, index) => {
          const extra = (index === 0 && cDetails) ? `\n\n> *Aclaraciones:* ${cDetails}` : '';
          const isCake = item.productType === 'Torta Personalizada';
          return {
            productType: item.productType,
            details: item.details + extra,
            isCustomCake: isCake,
            cakeSizeId: isCake ? (item.cakeSizeId || null) : null,
            product: !isCake && item.productId ? { id: item.productId } : null
          };
        })
      };

      try {
        // 2. Guardar el pedido en la base
        const res = await fetch('/api/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(orderData)
        });

        if (!res.ok) {
          // El servidor explica qué pasó (producto que ya no está, cupón vencido, falta la dirección…)
          let message = 'No pudimos guardar tu pedido. Probá de nuevo en unos minutos o escribinos por WhatsApp.';
          const raw = await res.text().catch(() => '');
          try {
            const data = JSON.parse(raw);
            if (data && data.message) message = data.message;
          } catch (err) {
            // Respuesta sin JSON: el límite de pedidos por minuto (RateLimitFilter) manda un texto simple
            if (res.status === 429 && raw) message = raw;
          }
          showPublicAlert(message);
          return;
        }

        const savedOrder = await res.json();
        const total = savedOrder.totalPrice;   // el total que calculó el servidor

        // 3. Mensaje de WhatsApp con el número de pedido
        let waMsg = `Hola! 👋 Confirmo mi pedido 🩷\n\n> N° Referencia: #${savedOrder.id}\n\n`;
        waMsg += itemsText + '\n\n';
        if (appliedCoupon) {
          if (appliedCoupon.type === 'DESCUENTO') {
            waMsg += `> 🎟️ Cupón aplicado: ${appliedCoupon.code} - Descuento del ${appliedCoupon.discountPercentage}%\n`;
          } else if (appliedCoupon.type === 'ENVIO_GRATIS') {
            waMsg += `> 🎟️ Cupón aplicado: ${appliedCoupon.code} - ¡Envío Gratis!\n`;
          } else {
            waMsg += `> 🎟️ Cupón aplicado: ${appliedCoupon.code} - Beneficio: ${appliedCoupon.customText}\n`;
          }
        }
        waMsg += `> *Para el:* ${cDate.split('-').reverse().join('/')}\n`;
        waMsg += `> *Turno:* ${cShift}\n`;
        waMsg += cDelivery === 'Envío' ? `> *Envío a:* ${cAddress}\n` : `> *Retira en el local*\n`;
        waMsg += `> *Pago:* ${cPay}\n`;
        if (cDetails.trim() !== '') waMsg += `> *Aclaraciones:* ${cDetails}\n\n`;
        waMsg += `El total es de $${total.toLocaleString('es-AR')}.`;

        const waUrl = `https://wa.me/${BUSINESS_PHONE}?text=${encodeURIComponent(waMsg)}`;

        // 4. El pedido ya quedó guardado: se vacía el carrito para no repetirlo
        cart = [];
        appliedCoupon = null;
        updateCartUI();

        // 5. Pantalla de éxito con el botón de WhatsApp (por si el navegador bloquea la ventana automática)
        const success = document.getElementById('checkout-success');
        if (success) {
          document.getElementById('success-order-id').textContent = `#${savedOrder.id}`;
          const waLink = document.getElementById('success-whatsapp');
          waLink.href = waUrl;
          document.querySelector('[data-cart-layout]').hidden = true;
          document.getElementById('cart-empty').hidden = true;
          success.hidden = false;
          success.querySelector('h2')?.focus();
          window.scrollTo({ top: 0, behavior: reduceMotion.matches ? 'auto' : 'smooth' });
        }
        window.open(waUrl, '_blank');

      } catch (error) {
        showPublicAlert('Error de conexión al enviar el pedido. Revisá tu internet e intentá de nuevo.');
      } finally {
        submitBtn.removeAttribute('aria-busy');
        submitBtn.disabled = cart.length === 0;
      }
    });
  }
});
