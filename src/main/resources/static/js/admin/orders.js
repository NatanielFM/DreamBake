// /js/admin/orders.js - Módulo de Pedidos, Edición y Calendario
document.addEventListener('DOMContentLoaded', () => {

  let dbOrders = [];
  let currentTab = 'nuevos'; 
  
  let currentDate = new Date();
  let currentMonth = currentDate.getMonth();
  let currentYear = currentDate.getFullYear();

  // SVGs
  const iconCheck = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
  const iconEye = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
  const iconX = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>`;
  const iconTrash = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;
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
    } catch (e) { console.error(e); }
  }

  function updateBadges() {
    const newOrdersCount = dbOrders.filter(o => o.status === 'PENDING').length;
    const badge = document.getElementById('badge-new-orders');
    if (badge) {
      badge.innerText = newOrdersCount;
      badge.style.display = newOrdersCount > 0 ? 'inline-block' : 'none';
    }
  }

  function getStatusBadge(status) {
    switch(status) {
      case 'PENDING': return `<span class="status-badge status-pending">Pendiente</span>`;
      case 'CONFIRMED': return `<span class="status-badge status-agendado">Agendado</span>`;
      case 'IN_PROGRESS': return `<span class="status-badge status-agendado">En Preparación</span>`; 
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
      const dayOrders = dbOrders.filter(o => o.deliveryDate === dateStr && (o.status === 'CONFIRMED' || o.status === 'COMPLETED'));

      let isTodayClass = (i === today.getDate() && currentMonth === today.getMonth() && currentYear === today.getFullYear()) ? 'today' : '';
      let badgesHtml = dayOrders.length > 0 ? `<div class="order-badge" style="background:var(--accent);">${dayOrders.length} pedido(s)</div>` : '';

      const dayCell = document.createElement('div');
      dayCell.className = `calendar-day ${isTodayClass}`;
      dayCell.innerHTML = `<div class="day-num">${i}</div><div style="flex:1; display:flex; flex-direction:column; justify-content:center;">${badgesHtml}</div>`;
      
      dayCell.addEventListener('click', () => openDayOrders(dateStr, dayOrders));
      grid.appendChild(dayCell);
    }
  }

  document.getElementById('cal-prev')?.addEventListener('click', () => { currentMonth--; if (currentMonth < 0) { currentMonth = 11; currentYear--; } renderCalendar(); });
  document.getElementById('cal-next')?.addEventListener('click', () => { currentMonth++; if (currentMonth > 11) { currentMonth = 0; currentYear++; } renderCalendar(); });

  // Función para mover el calendario automáticamente al mes de la fecha modificada
  function jumpCalendarToDate(dateString) {
    if(!dateString) return;
    const parts = dateString.split('-');
    currentYear = parseInt(parts[0]);
    currentMonth = parseInt(parts[1]) - 1;
    loadOrders();
  }

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
      } else {
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
        <td><div class="action-buttons-container">${actionsHtml}</div></td>
      `;
      tbody.appendChild(tr);
    });
  }

  // ================= 4. CONFIRMACIÓN Y FECHAS ANTIGUAS =================
  let orderToConfirm = null;
  let onDateConfirmedCallback = null;

  function isPastDate(dateStr) {
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;
    return dateStr < todayStr;
  }

  function handleDateWarning(dateStr, callback) {
    if (isPastDate(dateStr)) {
      onDateConfirmedCallback = callback;
      document.getElementById('modal-past-date').hidden = false;
    } else {
      callback();
    }
  }

  document.getElementById('btn-confirm-past-date')?.addEventListener('click', () => {
    document.getElementById('modal-past-date').hidden = true;
    if(onDateConfirmedCallback) onDateConfirmedCallback();
  });

  window.openConfirmModal = function(id) {
    orderToConfirm = dbOrders.find(o => o.id === id);
    if(!orderToConfirm) return;

    document.getElementById('co-id-label').innerText = orderToConfirm.id;
    document.getElementById('co-date').value = orderToConfirm.deliveryDate;
    document.getElementById('co-hidden-total').value = orderToConfirm.totalPrice;
    document.getElementById('co-payment-type').value = 'total';
    document.getElementById('co-deposit-group').style.display = 'none';
    document.getElementById('co-deposit').value = '';

    document.getElementById('modal-confirm-order').hidden = false;
  };

  window.toggleDepositInput = function() {
    const type = document.getElementById('co-payment-type').value;
    document.getElementById('co-deposit-group').style.display = (type === 'sena') ? 'block' : 'none';
  };

  document.getElementById('btn-submit-confirm')?.addEventListener('click', () => {
    if(!orderToConfirm) return;

    const newDate = document.getElementById('co-date').value;
    const isSena = document.getElementById('co-payment-type').value === 'sena';
    const total = parseFloat(document.getElementById('co-hidden-total').value);
    let depositValue = isSena ? parseFloat(document.getElementById('co-deposit').value) : total;

    if (isSena && (isNaN(depositValue) || depositValue <= 0)) {
      window.showErrorAlert("Por favor, ingresa un monto válido para la seña.");
      return;
    }
    if (depositValue > total) {
      window.showErrorAlert(`La seña ($${depositValue}) no puede ser mayor al total del pedido ($${total}).`);
      return;
    }

    handleDateWarning(newDate, async () => {
      try {
        const res = await fetch(`/api/orders/${orderToConfirm.id}/confirm`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ deposit: depositValue, deliveryDate: newDate })
        });
        if (res.ok) {
          document.getElementById('modal-confirm-order').hidden = true;
          jumpCalendarToDate(newDate); // Auto actualiza
        }
      } catch(e) { console.error(e); }
    });
  });

  // ================= 5. RECHAZAR Y ELIMINAR =================
  let orderToRejectId = null;

  window.actionReject = function(id) {
    orderToRejectId = id;
    document.getElementById('modal-reject-order').hidden = false;
  };

  document.getElementById('btn-confirm-reject')?.addEventListener('click', async () => {
    if(!orderToRejectId) return;
    try {
      const res = await fetch(`/api/orders/${orderToRejectId}/status`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'CANCELLED' })
      });
      if (res.ok) {
        document.getElementById('modal-reject-order').hidden = true;
        loadOrders();
      }
    } catch(e) {}
  });

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

  // ================= 6. VER Y EDITAR PEDIDO =================
  let viewingOrder = null;

  // Función auxiliar que SOLO llena los datos (Separa los datos de la interfaz)
  function fillViewModal(order) {
    document.getElementById('view-order-title').innerText = `Pedido - N° Ref #${order.id}`;
    document.getElementById('vo-name').value = order.customerName || '';
    document.getElementById('vo-phone').value = order.customerPhone || '';
    document.getElementById('vo-date-input').value = order.deliveryDate;
    document.getElementById('vo-total').value = order.totalPrice;
    document.getElementById('vo-deposit').value = order.deposit;

    // Aplanar items a texto
    let detailsText = "";
    order.items.forEach(i => {
      detailsText += `[${i.productType}] - ${i.details}\n\n`;
    });
    document.getElementById('vo-items-details').value = detailsText.trim();
  }

  // Función auxiliar que SOLO maneja lo visual (bloquear/desbloquear cajones)
  function toggleReadOnlyUI(isReadOnly) {
    document.querySelectorAll('#modal-view-order .form-input').forEach(input => {
      if(isReadOnly) {
        input.classList.add('readonly-mode');
        input.setAttribute('readonly', 'true');
      } else {
        input.classList.remove('readonly-mode');
        input.removeAttribute('readonly');
      }
    });
    document.getElementById('btn-enable-edit').hidden = !isReadOnly;
    document.getElementById('vo-save-container').hidden = isReadOnly;
  }

  window.actionView = function(id) {
    viewingOrder = dbOrders.find(o => o.id === id);
    if(!viewingOrder) return;

    toggleReadOnlyUI(true); // 1. Bloqueamos la interfaz visualmente
    fillViewModal(viewingOrder); // 2. Llenamos los datos sin bucles infinitos
    
    document.getElementById('modal-view-order').hidden = false;
  };

  // Botón "Editar Pedido"
  window.enableEditMode = function() {
    toggleReadOnlyUI(false); 
  };

  // Botón "Cancelar" dentro de la edición
  window.disableEditMode = function() {
    toggleReadOnlyUI(true); 
    if(viewingOrder) {
      fillViewModal(viewingOrder); // Restaura los datos originales si escribió algo y canceló
    }
  };

  window.saveEditedOrder = async function() {
    if(!viewingOrder) return;

    const newDate = document.getElementById('vo-date-input').value;
    const newTotal = parseFloat(document.getElementById('vo-total').value);
    const newDeposit = parseFloat(document.getElementById('vo-deposit').value);

    if (newDeposit > newTotal) {
      window.showPublicAlert("La seña no puede ser mayor al total del pedido.");
      return;
    }

    const updatedOrder = {
      customerName: document.getElementById('vo-name').value,
      customerPhone: document.getElementById('vo-phone').value,
      deliveryDate: newDate,
      totalPrice: newTotal,
      deposit: newDeposit,
      items: [{ productType: "Detalles del Pedido", details: document.getElementById('vo-items-details').value, price: newTotal }]
    };

    handleDateWarning(newDate, async () => {
      try {
        const res = await fetch(`/api/orders/${viewingOrder.id}`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(updatedOrder)
        });
        if (res.ok) {
          document.getElementById('modal-view-order').hidden = true;
          jumpCalendarToDate(newDate); // Actualiza y salta al calendario correcto
        }
      } catch(e) { console.error(e); }
    });
  };

  // ================= 7. CREAR PEDIDO MANUAL =================
  window.openManualOrderModal = function() {
    document.getElementById('form-manual-order').reset();
    document.getElementById('modal-manual-order').hidden = false;
  };

  document.getElementById('form-manual-order')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const newDate = document.getElementById('mo-date').value;
    const total = parseFloat(document.getElementById('mo-total').value);
    const deposit = parseFloat(document.getElementById('mo-deposit').value);

    if(deposit > total) {
      window.showErrorAlert("La seña no puede ser mayor al total.");
      return;
    }

    const orderData = {
      customerName: document.getElementById('mo-name').value,
      customerPhone: document.getElementById('mo-phone').value,
      deliveryDate: newDate,
      totalPrice: total,
      deposit: deposit,
      status: document.getElementById('mo-status').value,
      items: [{ productType: "Pedido Manual", details: document.getElementById('mo-details').value, price: total }]
    };

    handleDateWarning(newDate, async () => {
      try {
        const res = await fetch('/api/orders', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(orderData)
        });
        if (res.ok) {
          document.getElementById('modal-manual-order').hidden = true;
          jumpCalendarToDate(newDate);
        }
      } catch(err) { console.error(err); }
    });
  });

  // ================= 8. MODAL DEL CALENDARIO =================
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
          <button class="btn btn-sm" style="background:#25D366; color:#fff;" title="Marcar Completado" onclick="setOrderStatus(${o.id}, 'COMPLETED')">${iconCheck}</button>
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
          <td><div class="action-buttons-container">${actionsHtml}</div></td>
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