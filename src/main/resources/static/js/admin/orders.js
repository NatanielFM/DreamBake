// /js/admin/orders.js - Módulo de Pedidos y Calendario
document.addEventListener('DOMContentLoaded', () => {

  let dbOrders = [];
  let currentTab = 'nuevos'; // 'nuevos', 'agendados', 'completados'
  
  let currentDate = new Date();
  let currentMonth = currentDate.getMonth();
  let currentYear = currentDate.getFullYear();

  // SVGs para Botones
  const iconCheck = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
  const iconEye = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
  const iconX = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>`;
  const iconTrash = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;
  const iconUndo = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>`; // Usaremos una flecha de volver
  const iconRevert = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>`;

  const months = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];

  // ================= 1. CARGA DE DATOS =================
  async function loadOrders() {
    try {
      const res = await fetch('/api/orders');
      if (res.ok) {
        dbOrders = await res.json();
        renderCalendar();
        renderTable();
        updateBadges();
      }
    } catch (e) { console.error("Error al cargar pedidos:", e); }
  }

  function updateBadges() {
    const newOrdersCount = dbOrders.filter(o => o.status === 'PENDING').length;
    const badge = document.getElementById('badge-new-orders');
    if (badge) {
      badge.innerText = newOrdersCount;
      badge.style.display = newOrdersCount > 0 ? 'inline-block' : 'none';
    }
  }

  // Helper para generar etiquetas estéticas
  function getStatusBadge(status) {
    switch(status) {
      case 'PENDING': return `<span class="status-badge status-pending">Pendiente</span>`;
      case 'CONFIRMED': return `<span class="status-badge status-agendado">Agendado</span>`;
      case 'IN_PROGRESS': return `<span class="status-badge status-agendado">En Preparación</span>`; // Se asimila a Agendado
      case 'COMPLETED': return `<span class="status-badge status-completado">Completado</span>`;
      default: return `<span class="status-badge status-cancelled">Cancelado</span>`;
    }
  }

  // ================= 2. CALENDARIO INTERACTIVO =================
  function renderCalendar() {
    const monthYearLabel = document.getElementById('cal-month-year');
    const grid = document.getElementById('calendar-grid');
    if (!grid) return;

    monthYearLabel.innerText = `${months[currentMonth]} ${currentYear}`;
    grid.innerHTML = '';

    const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
    days.forEach(d => grid.innerHTML += `<div class="calendar-day-label">${d}</div>`);

    const firstDay = new Date(currentYear, currentMonth, 1).getDay();
    const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate();

    for (let i = 0; i < firstDay; i++) {
      grid.innerHTML += `<div class="calendar-day empty"></div>`;
    }

    const today = new Date();
    for (let i = 1; i <= daysInMonth; i++) {
      const dateStr = `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
      
      // Mostrar tanto Agendados (CONFIRMED) como Completados en el calendario
      const dayOrders = dbOrders.filter(o => o.deliveryDate === dateStr && (o.status === 'CONFIRMED' || o.status === 'COMPLETED'));

      let isTodayClass = (i === today.getDate() && currentMonth === today.getMonth() && currentYear === today.getFullYear()) ? 'today' : '';
      
      let badgesHtml = '';
      if (dayOrders.length > 0) {
        badgesHtml = `<div class="order-badge" style="background:var(--accent);">${dayOrders.length} pedido(s)</div>`;
      }

      const dayCell = document.createElement('div');
      dayCell.className = `calendar-day ${isTodayClass}`;
      dayCell.innerHTML = `<div class="day-num">${i}</div><div style="flex:1; display:flex; flex-direction:column; justify-content:center;">${badgesHtml}</div>`;
      
      dayCell.addEventListener('click', () => openDayOrders(dateStr, dayOrders));
      grid.appendChild(dayCell);
    }
  }

  document.getElementById('cal-prev')?.addEventListener('click', () => { currentMonth--; if (currentMonth < 0) { currentMonth = 11; currentYear--; } renderCalendar(); });
  document.getElementById('cal-next')?.addEventListener('click', () => { currentMonth++; if (currentMonth > 11) { currentMonth = 0; currentYear++; } renderCalendar(); });

  // ================= 3. TABLA DE PEDIDOS =================
  const tabs = document.querySelectorAll('.order-tab-btn');
  tabs.forEach(tab => {
    tab.addEventListener('click', (e) => {
      tabs.forEach(t => t.classList.remove('active'));
      e.target.classList.add('active');
      currentTab = e.target.dataset.tab;
      renderTable();
    });
  });

  document.getElementById('search-orders')?.addEventListener('input', renderTable);

  function renderTable() {
    const tbody = document.getElementById('orders-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const query = document.getElementById('search-orders')?.value.toLowerCase() || '';

    let filtered = dbOrders.filter(o => {
      if (query) {
        const refMatch = o.id.toString().includes(query);
        const nameMatch = o.customerName && o.customerName.toLowerCase().includes(query);
        const phoneMatch = o.customerPhone && o.customerPhone.includes(query);
        if (!refMatch && !nameMatch && !phoneMatch) return false;
      }
      if (currentTab === 'nuevos') return o.status === 'PENDING';
      if (currentTab === 'agendados') return o.status === 'CONFIRMED';
      if (currentTab === 'completados') return o.status === 'COMPLETED';
      return true;
    });

    filtered.sort((a, b) => new Date(a.deliveryDate) - new Date(b.deliveryDate));

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:2rem; color:#666;">No hay pedidos para mostrar en esta sección.</td></tr>`;
      return;
    }

    filtered.forEach(o => {
      const tr = document.createElement('tr');
      const [year, month, day] = o.deliveryDate.split('-');
      
      let actionsHtml = '';
      if (currentTab === 'nuevos') {
        actionsHtml = `
          <button class="btn btn-pink btn-sm" title="Confirmar" onclick="openConfirmModal(${o.id})">${iconCheck} Confirmar</button>
          <button class="btn btn-grey btn-sm" title="Ver" onclick="actionView(${o.id})">${iconEye} Ver</button>
          <button class="btn btn-red btn-sm" title="Rechazar" onclick="actionReject(${o.id})">${iconX}</button>
        `;
      } else if (currentTab === 'agendados') {
        actionsHtml = `
          <button class="btn btn-grey btn-sm" title="Ver" onclick="actionView(${o.id})">${iconEye} Ver</button>
          <button class="btn btn-red btn-sm" title="Eliminar" onclick="actionDelete(${o.id})">${iconTrash}</button>
        `;
      } else {
        // En Completados solo ver y eliminar
        actionsHtml = `
          <button class="btn btn-grey btn-sm" title="Ver" onclick="actionView(${o.id})">${iconEye} Ver</button>
          <button class="btn btn-red btn-sm" title="Eliminar" onclick="actionDelete(${o.id})">${iconTrash}</button>
        `;
      }

      tr.innerHTML = `
        <td style="font-weight:bold; color:var(--accent);">#${o.id}</td>
        <td style="font-weight:600;">${o.customerName || 'Sin Nombre'}</td>
        <td style="color:#666;">${o.customerPhone}</td>
        <td>${day}/${month}/${year}</td>
        <td style="font-weight:bold;">$${o.totalPrice.toLocaleString('es-AR')}</td>
        <td>${getStatusBadge(o.status)}</td>
        <td style="text-align:center; display:flex; gap:6px; justify-content:center;">${actionsHtml}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // ================= 4. CONFIRMACIÓN Y ESTADOS =================
  let orderToConfirm = null;

  window.openConfirmModal = function(id) {
    orderToConfirm = dbOrders.find(o => o.id === id);
    if(!orderToConfirm) return;

    document.getElementById('co-id-label').innerText = orderToConfirm.id;
    document.getElementById('co-date').value = orderToConfirm.deliveryDate;
    document.getElementById('co-payment-type').value = 'total';
    document.getElementById('co-deposit-group').style.display = 'none';
    document.getElementById('co-deposit').value = '';

    document.getElementById('modal-confirm-order').hidden = false;
  };

  window.toggleDepositInput = function() {
    const type = document.getElementById('co-payment-type').value;
    document.getElementById('co-deposit-group').style.display = (type === 'sena') ? 'block' : 'none';
  };

  document.getElementById('btn-submit-confirm')?.addEventListener('click', async () => {
    if(!orderToConfirm) return;

    const newDate = document.getElementById('co-date').value;
    const isSena = document.getElementById('co-payment-type').value === 'sena';
    let depositValue = isSena ? parseFloat(document.getElementById('co-deposit').value) : orderToConfirm.totalPrice;

    if (isSena && (isNaN(depositValue) || depositValue <= 0)) {
      window.showErrorAlert("Por favor, ingresa un monto válido para la seña.");
      return;
    }

    try {
      const res = await fetch(`/api/orders/${orderToConfirm.id}/confirm`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deposit: depositValue, deliveryDate: newDate })
      });
      if (res.ok) {
        document.getElementById('modal-confirm-order').hidden = true;
        loadOrders();
      }
    } catch(e) { console.error(e); }
  });

  window.actionReject = async function(id) {
    if(!confirm("¿Seguro que deseas rechazar este pedido?")) return;
    try {
      const res = await fetch(`/api/orders/${id}/status`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'CANCELLED' })
      });
      if (res.ok) loadOrders();
    } catch(e) {}
  };

  window.actionDelete = async function(id) {
    window.showConfirmModal("Eliminar Pedido", "¿Seguro que deseas eliminar este pedido permanentemente?", "Eliminar", "btn-red", async () => {
      try {
        const res = await fetch(`/api/orders/${id}`, { method: 'DELETE' });
        if (res.ok) {
          document.getElementById('modal-day-orders').hidden = true;
          loadOrders();
        }
      } catch(e) {}
    });
  };

  // ================= 5. MODAL VER DETALLES Y EDITAR FECHA =================
  let viewingOrderId = null;

  window.actionView = function(id) {
    const order = dbOrders.find(o => o.id === id);
    if(!order) return;
    viewingOrderId = order.id;

    document.getElementById('view-order-title').innerText = `Pedido #${order.id}`;
    document.getElementById('vo-name').innerText = order.customerName;
    document.getElementById('vo-phone').innerText = order.customerPhone;
    document.getElementById('vo-email').innerText = order.customerEmail || 'No provisto';
    
    document.getElementById('vo-date-input').value = order.deliveryDate;
    document.getElementById('vo-status-badge').innerHTML = getStatusBadge(order.status);
    document.getElementById('vo-deposit').innerText = order.deposit.toLocaleString('es-AR');
    document.getElementById('vo-total').innerText = order.totalPrice.toLocaleString('es-AR');

    const itemsContainer = document.getElementById('vo-items-container');
    itemsContainer.innerHTML = '';
    order.items.forEach(item => {
      itemsContainer.innerHTML += `
        <div style="padding: 10px; border-bottom: 1px dashed #eee;">
          <div style="font-weight:600; color:var(--accent);">${item.productType} <span style="float:right; color:#333;">$${item.price.toLocaleString('es-AR')}</span></div>
          <div style="font-size:0.85rem; color:#666; margin-top:5px;">${item.details}</div>
        </div>
      `;
    });

    document.getElementById('modal-view-order').hidden = false;
  };

  window.updateOrderDate = async function() {
    if(!viewingOrderId) return;
    const newDate = document.getElementById('vo-date-input').value;
    if(!newDate) return;

    try {
      const res = await fetch(`/api/orders/${viewingOrderId}/date`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ deliveryDate: newDate })
      });
      if (res.ok) {
        window.showSuccessAlert("Fecha actualizada correctamente.");
        loadOrders();
      }
    } catch(e) { console.error(e); }
  };

  // ================= 6. MODAL DEL CALENDARIO (DÍA ESPECÍFICO) =================
  let currentDayModalDate = null;

  window.openDayOrders = function(dateStr, ordersArray) {
    if(ordersArray.length === 0) return; 
    currentDayModalDate = dateStr;

    const [y, m, d] = dateStr.split('-');
    document.getElementById('day-orders-title').innerText = `Entregas para el ${d}/${m}/${y}`;
    
    renderDayModalTable(ordersArray);
    document.getElementById('modal-day-orders').hidden = false;
  };

  function renderDayModalTable(ordersArray) {
    const tbody = document.getElementById('day-orders-tbody');
    tbody.innerHTML = '';

    ordersArray.forEach(o => {
      let actionsHtml = '';
      
      if (o.status === 'CONFIRMED') {
        actionsHtml = `
          <button class="btn btn-grey btn-sm" title="Ver" onclick="actionView(${o.id})">${iconEye}</button>
          <button class="btn btn-red btn-sm" title="Eliminar" onclick="actionDelete(${o.id})">${iconTrash}</button>
          <button class="btn btn-sm" style="background:#25D366; color:#fff;" title="Marcar como Completado" onclick="setOrderStatus(${o.id}, 'COMPLETED')">${iconCheck}</button>
        `;
      } else if (o.status === 'COMPLETED') {
        actionsHtml = `
          <button class="btn btn-grey btn-sm" title="Ver" onclick="actionView(${o.id})">${iconEye}</button>
          <button class="btn btn-sm" style="background:#f59e0b; color:#fff;" title="Revertir a Agendado" onclick="setOrderStatus(${o.id}, 'CONFIRMED')">${iconRevert}</button>
        `;
      }

      tbody.innerHTML += `
        <tr>
          <td style="font-weight:bold;">#${o.id}</td>
          <td>${o.customerName} <br><small>${o.customerPhone}</small></td>
          <td style="font-weight:bold;">$${o.totalPrice.toLocaleString('es-AR')}</td>
          <td>${getStatusBadge(o.status)}</td>
          <td style="text-align:center; display:flex; gap:6px; justify-content:center;">${actionsHtml}</td>
        </tr>
      `;
    });
  }

  window.setOrderStatus = async function(id, newStatus) {
    try {
      const res = await fetch(`/api/orders/${id}/status`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: newStatus })
      });
      if (res.ok) {
        await loadOrders(); 
        // Actualizamos el modal del día en caliente para que el usuario vea el cambio instantáneo
        const dayOrders = dbOrders.filter(o => o.deliveryDate === currentDayModalDate && (o.status === 'CONFIRMED' || o.status === 'COMPLETED'));
        renderDayModalTable(dayOrders);
      }
    } catch(e) { console.error(e); }
  };

  document.addEventListener('tabChanged', e => {
    if (e.detail === 'pedidos') loadOrders();
  });

  loadOrders();
});