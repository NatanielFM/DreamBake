// /js/public/store.js - Lógica del E-commerce Global
document.addEventListener('DOMContentLoaded', () => {

  const currentPath = window.location.pathname;
  
  // 1. BLOQUEO DEL ADMIN: No ejecutar nada de esto si estamos en el panel
  if (currentPath.startsWith('/admin')) return;

  const cakeSizes = [
    { id: '10p', name: '10 porciones (14x12cm)', price: 44000, maxRellenos: 2, mixSponge: false },
    { id: '15p', name: '15 porciones (14x15cm)', price: 60000, maxRellenos: 2, mixSponge: false },
    { id: '20p', name: '20 porciones (16x15cm)', price: 82000, maxRellenos: 3, mixSponge: false },
    { id: '30p', name: '30 porciones (18x15cm)', price: 125000, maxRellenos: 3, mixSponge: false },
    { id: '30p2', name: '30 porciones (2 Pisos)', price: 135000, maxRellenos: 3, mixSponge: true },
    { id: '50p2', name: '40-50 porciones (2 Pisos)', price: 176000, maxRellenos: 3, mixSponge: true }
  ];

  const fillings = {
    Crema: ['Dulce de leche', 'Chantilly', 'Crema oreo', 'Crema bonobon', 'Crema bariloche', 'Mousse de frutilla', 'Ganache blanco', 'Ganache frutos rojos'],
    Fondant: ['Dulce de leche', 'Crema Bariloche', 'Ganache blanco/negro', 'Ganache frutos rojos', 'Ganache dulce de leche', 'Ganache frutillas', 'Ganache chocooreo', 'Ganache pistacho', 'Ganache menta', 'Ganache maracuyá', 'Ganache banana']
  };

  let cakeData = { size: null, coverage: null, sponges: [], fillings: [] };
  
  // Leemos el carrito guardado
  let cart = [];
  try {
    cart = JSON.parse(localStorage.getItem('dreamBakeCart')) || [];
  } catch(e) { cart = []; }

  // ================= 2. INYECCIÓN GLOBAL (BOTÓN FLOTANTE Y POPUP MINI-CARRITO) =================
  if (currentPath !== '/cart') {
    
    // Inyectar Botón Flotante (Si no existe)
    if (!document.getElementById('floating-cart-btn')) {
      document.body.insertAdjacentHTML('beforeend', `
        <button id="floating-cart-btn" class="floating-cart" style="display: none;" onclick="openMiniCart()">
          🛒 Ver Pedido (<span id="cart-count-float">0</span>)
        </button>
      `);
    }

    // Inyectar Modal del Mini-Carrito (Si no existe)
    if (!document.getElementById('modal-checkout-preview')) {
      document.body.insertAdjacentHTML('beforeend', `
        <div id="modal-checkout-preview" class="public-modal-overlay" hidden>
          <div class="public-modal-card">
            <div class="public-modal-header">
              <h3>Tu Carrito</h3>
              <button class="public-close-modal" onclick="closeMiniCart()">&times;</button>
            </div>
            <div class="public-modal-body">
              <div id="cart-items-preview" style="margin-bottom: 20px;"></div>
              <h3 style="text-align: right; color:var(--accent);">Total: $<span id="cart-total-preview">0</span></h3>
              <a href="/cart" class="btn btn-pink btn-large" style="display:block; text-align:center; text-decoration:none; margin-top:20px;">
                Ir a Confirmar Compra ➡️
              </a>
            </div>
          </div>
        </div>
      `);
    }
  }

  window.openMiniCart = function() {
    const modal = document.getElementById('modal-checkout-preview');
    // Si en builder.html ya tenías un modal llamado 'modal-checkout', usamos ese por compatibilidad
    const modalFallback = document.getElementById('modal-checkout'); 
    
    if(modal) modal.hidden = false;
    else if(modalFallback) modalFallback.hidden = false;
    
    updateCartUI();
  };

  window.closeMiniCart = function() {
    const modal = document.getElementById('modal-checkout-preview');
    const modalFallback = document.getElementById('modal-checkout');
    if(modal) modal.hidden = true;
    if(modalFallback) modalFallback.hidden = true;
  };

  // Interceptar también el botón del Navbar para que abra el Popup en lugar de redirigir
  const navbarCartBtn = document.querySelector('.btn-cart');
  if (navbarCartBtn && currentPath !== '/cart') {
    navbarCartBtn.addEventListener('click', (e) => {
      e.preventDefault(); // Evitamos que nos lleve a /cart de una vez
      openMiniCart();
    });
  }

  // ================= 3. ACTUALIZACIÓN GLOBAL DE UI =================
  window.updateCartUI = function() {
    localStorage.setItem('dreamBakeCart', JSON.stringify(cart));

    // Actualizar contadores del header
    document.querySelectorAll('.cart-count, #cart-count, .header-cart-count, #cart-count-float').forEach(el => {
      if(el) el.innerText = cart.length;
    });
    
    // Mostrar/ocultar botón flotante
    const floatBtn = document.getElementById('floating-cart-btn');
    if(floatBtn) floatBtn.style.display = cart.length > 0 ? 'block' : 'none';

    let total = cart.reduce((acc, item) => acc + item.price, 0);

    // Actualizar Mini-Carrito (Popup)
    const previewContainers = document.querySelectorAll('#cart-items-preview, #cart-items');
    previewContainers.forEach(container => {
      if(container) {
        container.innerHTML = '';
        if(cart.length === 0) {
          container.innerHTML = '<p style="text-align:center; color:#666;">Tu carrito está vacío.</p>';
        } else {
          cart.forEach((item, index) => {
            container.innerHTML += `
              <div class="cart-item-row" style="background:#f9f9f9; border:1px solid #eee; padding:15px; border-radius:8px; margin-bottom:10px; display:flex; justify-content:space-between;">
                <div>
                  <div class="cart-item-title" style="font-weight:600; color:var(--accent);">${item.productType}</div>
                </div>
                <div style="font-weight:bold;">
                  $${item.price.toLocaleString('es-AR')}
                  <button type="button" onclick="removeCart(${index})" style="background:none; border:none; color:red; cursor:pointer; margin-left:10px;" title="Eliminar">✖</button>
                </div>
              </div>
            `;
          });
        }
      }
    });

    document.querySelectorAll('#cart-total-preview, #cart-total').forEach(el => {
      if(el) el.innerText = total.toLocaleString('es-AR');
    });

    // Lógica Exclusiva para la página Completa /cart
    const fullContainer = document.getElementById('full-cart-items');
    if(fullContainer) {
      fullContainer.innerHTML = '';
      const submitBtn = document.getElementById('final-checkout-form')?.querySelector('button[type="submit"]');
      
      if(cart.length === 0) {
        fullContainer.innerHTML = '<p style="text-align:center; color:#666; padding: 2rem;">Tu carrito está vacío. ¡Ve a armar una torta!</p>';
        if(submitBtn) submitBtn.disabled = true;
      } else {
        if(submitBtn) submitBtn.disabled = false;
        cart.forEach((item, index) => {
          fullContainer.innerHTML += `
            <div class="cart-item-row" style="background:#fff; border:1px solid #ddd; padding:15px; border-radius:8px; margin-bottom:10px; display:flex; justify-content:space-between;">
              <div>
                <div class="cart-item-title" style="font-weight:600; color:var(--accent);">${item.productType}</div>
                <div class="cart-item-desc" style="font-size:0.85rem; color:#666;">${item.details}</div>
              </div>
              <div style="font-weight:bold; white-space:nowrap;">
                $${item.price.toLocaleString('es-AR')}
                <button type="button" onclick="removeCart(${index})" style="background:none; border:none; color:red; cursor:pointer; margin-left:10px;" title="Eliminar">✖</button>
              </div>
            </div>
          `;
        });
      }
      const totalSpan = document.getElementById('full-cart-total');
      if(totalSpan) totalSpan.innerText = total.toLocaleString('es-AR');
    }
  }

  window.removeCart = function(index) {
    cart.splice(index, 1);
    updateCartUI();
  }

  updateCartUI();

  // ================= 4. WIZARD (Sólo se ejecuta si existe #grid-sizes) =================
  const gridSizes = document.getElementById('grid-sizes');
  if(gridSizes) {
    let sizesHtml = '';
    cakeSizes.forEach(size => {
      sizesHtml += `<div class="select-card size-card" data-id="${size.id}" onclick="selectSize('${size.id}')">
        ${size.name} <span class="price">$${size.price.toLocaleString('es-AR')}</span>
      </div>`;
    });
    gridSizes.innerHTML = sizesHtml;
  }

  window.selectSize = function(id) {
    document.querySelectorAll('.size-card').forEach(c => c.classList.remove('selected'));
    document.querySelector(`.size-card[data-id="${id}"]`).classList.add('selected');
    const newSize = cakeSizes.find(s => s.id === id);
    if(cakeData.size && newSize.maxRellenos < cakeData.size.maxRellenos) cakeData.fillings = []; 
    if(cakeData.size && newSize.mixSponge === false) cakeData.sponges = []; 
    cakeData.size = newSize;
    validateStep1();
  }

  window.selectCoverage = function(cov) {
    document.querySelectorAll('#grid-coverages .select-card').forEach(c => c.classList.remove('selected'));
    document.querySelector(`#grid-coverages .select-card[data-val="${cov}"]`).classList.add('selected');
    if(cakeData.coverage !== cov) cakeData.fillings = [];
    cakeData.coverage = cov;
    validateStep1();
  }

  function validateStep1() {
    const btnNext = document.getElementById('btn-next-1');
    if(btnNext) btnNext.disabled = !(cakeData.size && cakeData.coverage);
  }

  window.goToStep = function(step) {
    if (step === 2) buildStep2();
    document.querySelectorAll('.wizard-step').forEach(s => s.classList.remove('active'));
    document.getElementById(`wizard-step-${step}`).classList.add('active');
    document.querySelectorAll('.step').forEach((s, idx) => {
      s.classList.toggle('active', idx < step);
    });
    const builderSection = document.getElementById('cake-builder');
    if(builderSection) builderSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function buildStep2() {
    document.getElementById('sponge-info').innerText = cakeData.size.mixSponge ? 'Hasta 2 sabores' : '1 sabor';
    document.getElementById('fillings-info').innerText = `Elige hasta ${cakeData.size.maxRellenos}`;
    document.getElementById('lbl-cov').innerText = cakeData.coverage;

    document.querySelectorAll('.sponge-card').forEach(c => {
      c.classList.remove('selected');
      if(cakeData.sponges.includes(c.dataset.val)) c.classList.add('selected');
    });

    const gridF = document.getElementById('grid-fillings');
    let fillsHtml = '';
    fillings[cakeData.coverage].forEach(f => {
      const isSelected = cakeData.fillings.includes(f) ? 'selected' : '';
      fillsHtml += `<div class="select-card fill-card ${isSelected}" data-val="${f}" onclick="toggleFilling('${f}')">${f}</div>`;
    });
    gridF.innerHTML = fillsHtml;
    
    validateStep2();
  }

  window.toggleSponge = function(sp) {
    const limit = cakeData.size.mixSponge ? 2 : 1;
    handleSelection(sp, cakeData.sponges, limit, '.sponge-card');
    validateStep2();
  }

  window.toggleFilling = function(f) {
    handleSelection(f, cakeData.fillings, cakeData.size.maxRellenos, '.fill-card');
    validateStep2();
  }

  function handleSelection(val, array, limit, cardClass) {
    const idx = array.indexOf(val);
    if (idx > -1) {
      array.splice(idx, 1);
      document.querySelector(`${cardClass}[data-val="${val}"]`).classList.remove('selected');
    } else {
      if (array.length < limit) {
        array.push(val);
        document.querySelector(`${cardClass}[data-val="${val}"]`).classList.add('selected');
      }
    }
  }

  function validateStep2() {
    const spongeOk = cakeData.sponges.length > 0;
    const fillsOk = cakeData.fillings.length > 0 && cakeData.fillings.length <= cakeData.size.maxRellenos;
    const btnNext = document.getElementById('btn-next-2');
    if(btnNext) btnNext.disabled = !(spongeOk && fillsOk);
  }

  window.addCakeToCart = function() {
    const details = document.getElementById('cake-details').value || 'Ninguno';
    const detailsText = `${cakeData.size.name} | Cubierta: ${cakeData.coverage} | Bizcochuelo: ${cakeData.sponges.join(', ')} | Rellenos: ${cakeData.fillings.join(', ')} | Detalles: ${details}`;

    cart.push({
      productType: "Torta Personalizada",
      details: detailsText,
      price: cakeData.size.price
    });

    updateCartUI();
    
    goToStep(1);
    cakeData = { size: null, coverage: null, sponges: [], fillings: [] };
    document.querySelectorAll('.select-card').forEach(c => c.classList.remove('selected'));
    document.getElementById('btn-next-1').disabled = true;
    document.getElementById('cake-details').value = '';
    
    // Al añadir, abrimos automáticamente el popup para feedback visual
    openMiniCart();
  }

  // ================= 5. VALIDACIÓN DE FECHAS (+3 DÍAS) =================
  const dateInput = document.getElementById('chk-date');
  if(dateInput) {
    const today = new Date();
    today.setDate(today.getDate() + 3);
    const minDate = today.toISOString().split('T')[0];
    dateInput.setAttribute('min', minDate);

    dateInput.addEventListener('change', (e) => {
      if(e.target.value < minDate) {
        e.target.value = minDate;
        window.showPublicAlert("Los pedidos requieren un mínimo de 3 días de anticipación para preparación.");
      }
    });
  }

  // ================= 6. ALERTAS V1 =================
  window.showPublicAlert = function(msg) {
    const alertMsg = document.getElementById('alert-v1-msg');
    const alertModal = document.getElementById('modal-alert-v1');
    if(alertMsg && alertModal) {
      alertMsg.innerText = msg;
      alertModal.hidden = false;
    } else {
      alert(msg);
    }
  };

  // ================= 7. CHECKOUT FINAL Y WHATSAPP =================
  const finalForm = document.getElementById('final-checkout-form');
  if(finalForm){
    finalForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (cart.length === 0) return;

      const cName = document.getElementById('chk-name').value;
      const cPhone = document.getElementById('chk-phone').value;
      const cEmail = document.getElementById('chk-email').value;
      const cDetails = document.getElementById('chk-general-details').value || '';
      const cDate = document.getElementById('chk-date').value;
      const cPay = document.getElementById('chk-payment').value;
      
      let total = cart.reduce((acc, item) => acc + item.price, 0);

      const orderData = {
        customerName: cName,
        customerPhone: cPhone,
        customerEmail: cEmail,
        deliveryDate: cDate,
        deliveryShift: "N/A", 
        totalPrice: total,
        items: cart
      };

      try {
        const res = await fetch('/api/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(orderData)
        });

        if (res.ok) {
          const savedOrder = await res.json();
          
          let waMsg = `Hola! 👋 Confirmo mi pedido 🩷\n\n> N° Referencia: #${savedOrder.id}\n\n`;
          
          cart.forEach(item => {
            waMsg += `> *${item.productType}*\n${item.details}\n\n`;
          });

          waMsg += `> *Para el:* ${cDate.split('-').reverse().join('/')}\n`;
          waMsg += `> *Pago:* ${cPay}\n`;
          if (cDetails.trim() !== '') waMsg += `> *Aclaraciones:* ${cDetails}\n\n`;
          
          waMsg += `El total es de $${total.toLocaleString('es-AR')}.`;

          const businessPhone = "5491100000000"; 
          const waUrl = `https://wa.me/${businessPhone}?text=${encodeURIComponent(waMsg)}`; 
          
          window.open(waUrl, '_blank');
          
          const successModal = document.getElementById('modal-success-v1');
          if(successModal) {
            successModal.hidden = false;
            
            document.getElementById('btn-keep-shopping').onclick = () => {
              window.location.href = '/';
            };
            
            document.getElementById('btn-accept-success').onclick = () => {
              cart = [];
              localStorage.removeItem('dreamBakeCart');
              window.location.href = '/';
            };
          }

        } else {
          window.showPublicAlert("Ocurrió un error al guardar tu pedido. Por favor intenta de nuevo.");
        }
      } catch (error) {
        window.showPublicAlert("Error de conexión al intentar generar el pedido.");
      }
    });
  }
});