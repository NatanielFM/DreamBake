// /js/public/store.js - Lógica del Creador de Tortas y Checkout
document.addEventListener('DOMContentLoaded', () => {
  console.log("El script store.js cargó correctamente.");

  // --- REGLAS DE NEGOCIO ---
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
  let cart = [];

  // ================= 1. GENERAR TAMAÑOS (PASO 1) =================
  const gridSizes = document.getElementById('grid-sizes');
  if(gridSizes) {
    let sizesHtml = '';
    cakeSizes.forEach(size => {
      sizesHtml += `<div class="select-card size-card" data-id="${size.id}" onclick="selectSize('${size.id}')">
        ${size.name} <span class="price">$${size.price.toLocaleString('es-AR')}</span>
      </div>`;
    });
    gridSizes.innerHTML = sizesHtml;
  } else {
    console.error("No se encontró el contenedor 'grid-sizes' en el HTML.");
  }

  window.selectSize = function(id) {
    document.querySelectorAll('.size-card').forEach(c => c.classList.remove('selected'));
    document.querySelector(`.size-card[data-id="${id}"]`).classList.add('selected');
    cakeData.size = cakeSizes.find(s => s.id === id);
    validateStep1();
  }

  window.selectCoverage = function(cov) {
    document.querySelectorAll('#grid-coverages .select-card').forEach(c => c.classList.remove('selected'));
    document.querySelector(`#grid-coverages .select-card[data-val="${cov}"]`).classList.add('selected');
    cakeData.coverage = cov;
    validateStep1();
  }

  function validateStep1() {
    document.getElementById('btn-next-1').disabled = !(cakeData.size && cakeData.coverage);
  }

  // ================= 2. NAVEGACIÓN Y SABORES (PASO 2) =================
  window.goToStep = function(step) {
    if (step === 2) buildStep2();

    document.querySelectorAll('.wizard-step').forEach(s => s.classList.remove('active'));
    document.getElementById(`wizard-step-${step}`).classList.add('active');

    document.querySelectorAll('.step').forEach((s, idx) => {
      s.classList.toggle('active', idx < step);
    });
  }

  function buildStep2() {
    cakeData.sponges = [];
    cakeData.fillings = [];
    document.querySelectorAll('.sponge-card').forEach(c => c.classList.remove('selected'));
    
    document.getElementById('sponge-info').innerText = cakeData.size.mixSponge ? 'Hasta 2 sabores' : '1 sabor';
    document.getElementById('fillings-info').innerText = `Elige hasta ${cakeData.size.maxRellenos}`;
    document.getElementById('lbl-cov').innerText = cakeData.coverage;

    const gridF = document.getElementById('grid-fillings');
    let fillsHtml = '';
    fillings[cakeData.coverage].forEach(f => {
      fillsHtml += `<div class="select-card fill-card" data-val="${f}" onclick="toggleFilling('${f}')">${f}</div>`;
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
    document.getElementById('btn-next-2').disabled = !(spongeOk && fillsOk);
  }

  // ================= 3. CARRITO Y PERSONALIZACIÓN (PASO 3) =================
  window.addCakeToCart = function() {
    const theme = document.getElementById('cake-theme').value || 'Sin definir';
    const name = document.getElementById('cake-name').value || '-';
    const age = document.getElementById('cake-age').value || '-';
    const phrase = document.getElementById('cake-phrase').value || '-';

    const detailsText = `${cakeData.size.name} | Cubierta: ${cakeData.coverage} | Bizcochuelo: ${cakeData.sponges.join(', ')} | Rellenos: ${cakeData.fillings.join(', ')} | Temática: ${theme} | Nombre: ${name} | Edad: ${age} | Frase: ${phrase}`;

    cart.push({
      productType: "Torta Personalizada",
      details: detailsText,
      price: cakeData.size.price
    });

    updateCartUI();
    resetBuilder();
    
    const btnCart = document.getElementById('floating-cart-btn');
    btnCart.style.display = 'block';
    btnCart.classList.add('pulse-animation');
    setTimeout(() => btnCart.classList.remove('pulse-animation'), 500);
  }

  function resetBuilder() {
    goToStep(1);
    cakeData = { size: null, coverage: null, sponges: [], fillings: [] };
    document.querySelectorAll('.select-card').forEach(c => c.classList.remove('selected'));
    document.getElementById('btn-next-1').disabled = true;
    
    document.getElementById('cake-theme').value = '';
    document.getElementById('cake-name').value = '';
    document.getElementById('cake-age').value = '';
    document.getElementById('cake-phrase').value = '';
  }

  function updateCartUI() {
    document.getElementById('cart-count').innerText = cart.length;
    
    const container = document.getElementById('cart-items');
    container.innerHTML = '';
    let total = 0;

    if(cart.length === 0) {
      container.innerHTML = '<p style="text-align:center; color:#666;">Tu carrito está vacío.</p>';
      document.getElementById('floating-cart-btn').style.display = 'none';
    } else {
      document.getElementById('floating-cart-btn').style.display = 'block';
    }

    cart.forEach((item, index) => {
      total += item.price;
      container.innerHTML += `
        <div class="cart-item-row">
          <div>
            <div class="cart-item-title">${item.productType}</div>
            <div class="cart-item-desc">${item.details}</div>
          </div>
          <div style="font-weight:bold;">
            $${item.price.toLocaleString('es-AR')}
            <button type="button" onclick="removeCart(${index})" style="background:none; border:none; color:red; cursor:pointer; margin-left:10px;" title="Eliminar">✖</button>
          </div>
        </div>
      `;
    });

    document.getElementById('cart-total').innerText = total.toLocaleString('es-AR');
  }

  window.removeCart = function(index) {
    cart.splice(index, 1);
    updateCartUI();
  }

  const floatBtn = document.getElementById('floating-cart-btn');
  if(floatBtn){
    floatBtn.addEventListener('click', () => {
      document.getElementById('modal-checkout').hidden = false;
      updateCartUI();
    });
  }

  // ================= 4. CHECKOUT (API + WHATSAPP) =================
  const chkForm = document.getElementById('checkout-form');
  if(chkForm){
    chkForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (cart.length === 0) { alert("Agrega algo al carrito primero."); return; }

      const cName = document.getElementById('chk-name').value;
      const cPhone = document.getElementById('chk-phone').value;
      const cEmail = document.getElementById('chk-email').value;
      const cDate = document.getElementById('chk-date').value;
      const cShift = document.getElementById('chk-shift').value;
      
      let total = cart.reduce((acc, item) => acc + item.price, 0);

      const orderData = {
        customerName: cName,
        customerPhone: cPhone,
        customerEmail: cEmail,
        deliveryDate: cDate,
        deliveryShift: cShift,
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
          
          let waMsg = `Hola Vero/Maca! Fíjate si está todo ok 🩷\n\nEntonces quedaría en:\n\n`;
          
          cart.forEach(item => {
            waMsg += `> *${item.productType}*\n${item.details}\n\n`;
          });

          waMsg += `> Para el ${cDate.split('-').reverse().join('/')}\n`;
          waMsg += `> Horario de retiro: ${cShift}\n\n`;
          waMsg += `El total sería de $${total.toLocaleString('es-AR')}.\n\n(Mi nombre es ${cName} - Ref: Pedido #${savedOrder.id})`;

          const businessPhone = "5491100000000"; // CAMBIAR AQUÍ
          const waUrl = `https://wa.me/${businessPhone}?text=${encodeURIComponent(waMsg)}`; 
          
          window.open(waUrl, '_blank');
          
          cart = [];
          updateCartUI();
          document.getElementById('modal-checkout').hidden = true;

        } else {
          alert("Ocurrió un error al guardar tu pedido. Por favor intenta de nuevo.");
        }
      } catch (error) {
        alert("Error de conexión al intentar generar el pedido.");
      }
    });
  }
});