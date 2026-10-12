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

  // Escapa texto antes de meterlo en HTML (dirección, turno, cupón…)
  const money = (n) => window.DreamFormat ? window.DreamFormat.money(n) : '$ ' + Number(n || 0).toLocaleString('es-AR');
  // Los montos solo los ve el admin: al pastelero el servidor no se los manda (llegan sin el campo).
  // moneyText muestra vacío en vez de "$ 0,00" o "$ NaN" cuando el dato no existe.
  const moneyText = (n) => (n == null ? '' : money(n));
  const esc = (text) => String(text ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

  const months = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];

  // ================= 1. CARGA DE DATOS =================
  let loadedOnce = false;
  async function loadOrders() {
    const tbody = document.getElementById('orders-tbody');
    // La primera vez la tabla muestra "Cargando…" (después se actualiza sin parpadear)
    if (!loadedOnce) window.DreamUI?.tableLoading(tbody, 7, 'Cargando pedidos…');
    try {
      const res = await fetch('/api/orders');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      dbOrders = await res.json();
      loadedOnce = true;
      renderCalendar();
      renderTable();
      updateBadges();
    } catch (e) {
      if (!loadedOnce) window.DreamUI?.tableMessage(tbody, 7, 'No se pudieron cargar los pedidos.', 'Revisá la conexión y recargá la página.', true);
    }
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
      case 'PENDING': return `<span class="status-badge status-pending">Nuevo</span>`;
      case 'CONFIRMED': return `<span class="status-badge status-agendado">Agendado</span>`;
      case 'IN_PROGRESS': return `<span class="status-badge status-progress">En preparación</span>`;
      case 'COMPLETED': return `<span class="status-badge status-completado">Entregado</span>`;
      case 'REJECTED': return `<span class="status-badge status-cancelled">Rechazado</span>`;
      default: return `<span class="status-badge status-cancelled">Cancelado</span>`;
    }
  }

  // ================= 2. CALENDARIO INTERACTIVO =================
  function initCalendarDropdowns() {
    const mSelect = document.getElementById('cal-month-select');
    const ySelect = document.getElementById('cal-year-select');
    if(!mSelect || !ySelect) return;
    
    mSelect.innerHTML = months.map((m, i) => `<option value="${i}">${m}</option>`).join('');
    
    const currentY = new Date().getFullYear();
    let yearsHtml = '';
    for(let y = currentY - 5; y <= currentY + 5; y++) {
      yearsHtml += `<option value="${y}">${y}</option>`;
    }
    ySelect.innerHTML = yearsHtml;
    
    mSelect.addEventListener('change', (e) => {
      currentMonth = parseInt(e.target.value);
      renderCalendar();
    });
    
    ySelect.addEventListener('change', (e) => {
      currentYear = parseInt(e.target.value);
      renderCalendar();
    });
  }

  function renderCalendar() {
    const grid = document.getElementById('calendar-grid');
    if (!grid) return;

    const mSelect = document.getElementById('cal-month-select');
    const ySelect = document.getElementById('cal-year-select');
    if(mSelect) mSelect.value = currentMonth;
    if(ySelect) ySelect.value = currentYear;

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
      const confirmedCount = dayOrders.filter(o => o.status === 'CONFIRMED').length;
      const completedCount = dayOrders.filter(o => o.status === 'COMPLETED').length;

      let badgesHtml = '';
      // data-count: en el celular solo se muestra el número (ver admin.css)
      if (confirmedCount > 0) {
        badgesHtml += `<div class="order-badge" data-count="${confirmedCount}">${confirmedCount} ${confirmedCount === 1 ? 'pedido' : 'pedidos'}</div>`;
      }
      if (completedCount > 0) {
        badgesHtml += `<div class="order-badge completed-badge" data-count="${completedCount}">${completedCount} ${completedCount === 1 ? 'entregado' : 'entregados'}</div>`;
      }

      const dayCell = document.createElement('div');
      dayCell.className = `calendar-day ${isTodayClass}`;
      dayCell.innerHTML = `<div class="day-num">${i}</div><div class="day-badges">${badgesHtml}</div>`;
      
      dayCell.addEventListener('click', () => openDayOrders(dateStr, dayOrders));
      grid.appendChild(dayCell);
    }
  }

  document.getElementById('cal-prev')?.addEventListener('click', () => { currentMonth--; if (currentMonth < 0) { currentMonth = 11; currentYear--; } renderCalendar(); });
  document.getElementById('cal-next')?.addEventListener('click', () => { currentMonth++; if (currentMonth > 11) { currentMonth = 0; currentYear++; } renderCalendar(); });

  // Función para mover el calendario automáticamente, evitando romper la vista si es fecha histórica
  function jumpCalendarToDate(dateString) {
    if(!dateString) return;
    const parts = dateString.split('-');
    const targetYear = parseInt(parts[0]);
    const targetMonth = parseInt(parts[1]) - 1;
    
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;
    
    // Si la fecha ingresada es histórica (pasada), no obligamos a saltar el calendario.
    if (dateString < todayStr) {
        loadOrders();
        return;
    }

    currentYear = targetYear;
    currentMonth = targetMonth;
    loadOrders();
  }

  // ================= 3. TABLA DE PEDIDOS =================
  const tabs = document.querySelectorAll('.order-tab-btn');
  tabs.forEach(tab => {
    tab.addEventListener('click', (e) => {
      tabs.forEach(t => t.classList.remove('active'));
      e.currentTarget.classList.add('active');
      currentTab = e.currentTarget.dataset.tab;
      renderTable();
    });
  });

  document.getElementById('search-orders')?.addEventListener('input', renderTable);

  // Turno y forma de entrega debajo de la fecha: "Por la tarde · Envío"
  function deliveryInfo(o) {
    const parts = [o.deliveryShift, o.deliveryMethod].filter(Boolean).map(esc);
    return parts.length ? `<span class="cell-soft cell-sub">${parts.join(' · ')}</span>` : '';
  }

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
      // Mensaje según la lista (y si hay una búsqueda escrita)
      const emptyText = query ? 'Ningún pedido coincide con la búsqueda.'
        : currentTab === 'nuevos' ? 'No hay pedidos nuevos. Cuando alguien compre en la web, aparece acá para confirmarlo.'
        : currentTab === 'agendados' ? 'No hay pedidos agendados.'
        : 'Todavía no hay pedidos completados.';
      tbody.innerHTML = `<tr><td colspan="7" class="cell-empty">${emptyText}</td></tr>`;
      return;
    }

    filtered.forEach(o => {
      const tr = document.createElement('tr');
      // Un pedido sin fecha no debe romper toda la tabla
      const [year, month, day] = o.deliveryDate ? o.deliveryDate.split('-') : ['', '', ''];
      
      let actionsHtml = '';
      if (currentTab === 'nuevos') {
        actionsHtml = `
          <button type="button" class="btn btn-pink btn-sm" data-order-action="confirm" data-id="${o.id}">${iconCheck} Confirmar</button>
          <button type="button" class="btn btn-grey btn-sm" data-order-action="view" data-id="${o.id}">${iconEye} Ver</button>
          <button type="button" class="btn btn-red btn-sm" data-order-action="reject" data-id="${o.id}">${iconX} Rechazar</button>
        `;
      } else if (currentTab === 'agendados') {
        actionsHtml = `
          <button type="button" class="btn btn-pink btn-sm" data-order-action="complete" data-id="${o.id}">${iconCheck} Entregado</button>
          <button type="button" class="btn btn-grey btn-sm" data-order-action="view" data-id="${o.id}">${iconEye} Ver</button>
          <button type="button" class="btn btn-red btn-sm btn-icon" aria-label="Eliminar el pedido #${o.id}" title="Eliminar" data-order-action="delete" data-id="${o.id}">${iconTrash}</button>
        `;
      } else {
        actionsHtml = `
          <button type="button" class="btn btn-grey btn-sm" data-order-action="view" data-id="${o.id}">${iconEye} Ver</button>
          <button type="button" class="btn btn-red btn-sm btn-icon" aria-label="Eliminar el pedido #${o.id}" title="Eliminar" data-order-action="delete" data-id="${o.id}">${iconTrash}</button>
        `;
      }

      let stockBadge = '';
      if (o.status === 'PENDING' && o.stockStatus) {
          // ¿Alcanzan los insumos para hacerlo? (lo calcula el servidor)
          if (o.stockStatus === 'Realizable') stockBadge = '<span class="status-badge is-small status-completado">Hay insumos</span>';
          else if (o.stockStatus === 'Bajo stock') stockBadge = '<span class="status-badge is-small status-pending">Insumos justos</span>';
          else if (o.stockStatus === 'Sin inventario') stockBadge = '<span class="status-badge is-small status-cancelled">Faltan insumos</span>';
      }

      // data-label: en el celular cada fila se ve como tarjeta con "Etiqueta: valor".
      // El orden de las columnas no cambia (baker.html oculta la 5.ª, el total).
      tr.innerHTML = `
        <td class="cell-id" data-label="Pedido">#${o.id}</td>
        <td class="cell-strong" data-label="Cliente">${esc(o.customerName || 'Sin nombre')}</td>
        <td class="cell-soft" data-label="Teléfono">${esc(o.customerPhone || '—')}</td>
        <td data-label="Entrega">${o.deliveryDate ? `${day}/${month}/${year}` : 'Sin fecha'}${deliveryInfo(o)}</td>
        <td class="cell-money" data-label="Total">${moneyText(o.totalPrice)}</td>
        <td data-label="Estado"><div class="status-stack">${getStatusBadge(o.status)}${stockBadge}</div></td>
        <td class="cell-actions"><div class="action-buttons-container">${actionsHtml}</div></td>
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

  // true cuando ya se aceptó el aviso de "faltan insumos": se confirma sin volver a preguntar
  let forceStockConfirm = false;

  window.openConfirmModal = function(id) {
    forceStockConfirm = false;
    orderToConfirm = dbOrders.find(o => o.id === id);
    if(!orderToConfirm) return;

    // Faltan insumos: se avisa y, si acepta, se sigue a la ventana de confirmación igual
    // (antes se confirmaba directo con seña $0, sin poder anotar cuánto pagó el cliente)
    if (orderToConfirm.stockStatus === 'Sin inventario') {
      window.showConfirmModal(
        'Faltan insumos',
        'Este pedido necesita más insumos de los que hay. Si lo confirmás, los insumos que falten quedan en 0. ¿Confirmarlo igual?',
        'Sí, seguir', 'btn-red',
        () => { forceStockConfirm = true; proceedToConfirmModal(); }
      );
      return;
    }

    if (orderToConfirm.stockStatus === 'Bajo stock') {
      let msg = 'Este pedido dejará algunos insumos por debajo del stock mínimo. ¿Aceptar de todos modos?';
      window.showConfirmModal('Alerta de Stock', msg, 'Aceptar', 'btn-red', () => {
        proceedToConfirmModal();
      });
      return;
    }
    
    proceedToConfirmModal();
  };

  function proceedToConfirmModal() {
    document.getElementById('co-id-label').innerText = orderToConfirm.id;
    document.getElementById('co-date').value = orderToConfirm.deliveryDate;
    document.getElementById('co-hidden-total').value = orderToConfirm.totalPrice ?? '';
    document.getElementById('co-payment-type').value = 'total';
    document.getElementById('co-deposit-group').style.display = 'none';
    document.getElementById('co-deposit').value = '';

    document.getElementById('modal-confirm-order').hidden = false;
  }

  window.toggleDepositInput = function() {
    const type = document.getElementById('co-payment-type').value;
    document.getElementById('co-deposit-group').style.display = (type === 'sena') ? 'block' : 'none';
  };

  document.getElementById('btn-submit-confirm')?.addEventListener('click', () => {
    if(!orderToConfirm) return;

    const newDate = document.getElementById('co-date').value;
    const isSena = document.getElementById('co-payment-type').value === 'sena';
    const total = parseFloat(document.getElementById('co-hidden-total').value);
    // Sin total (panel del pastelero: el servidor no manda montos) no se pide ni se manda seña
    const noMoney = Number.isNaN(total);
    let depositValue = noMoney ? null : (isSena ? parseFloat(document.getElementById('co-deposit').value) : total);

    if (!noMoney && isSena && (isNaN(depositValue) || depositValue <= 0)) {
      window.showErrorAlert("Por favor, ingresa un monto válido para la seña.");
      return;
    }
    if (!noMoney && depositValue > total) {
      window.showErrorAlert(`La seña (${money(depositValue)}) no puede ser mayor al total del pedido (${money(total)}).`);
      return;
    }

    const submitBtn = document.getElementById('btn-submit-confirm');
    handleDateWarning(newDate, () => window.DreamUI.busy(submitBtn, async () => {
      try {
        const res = await fetch(`/api/orders/${orderToConfirm.id}/confirm`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ deposit: depositValue, deliveryDate: newDate, force: forceStockConfirm })
        });
        if (res.ok) {
          document.getElementById('modal-confirm-order').hidden = true;
          jumpCalendarToDate(newDate); // Auto actualiza
          return;
        }
        const errData = await res.clone().json().catch(() => ({}));
        if (errData.error === 'INSUFFICIENT_STOCK') {
          window.showConfirmModal('Faltan insumos', errData.message, 'Confirmar igual', 'btn-red', async () => {
            const forceRes = await fetch(`/api/orders/${orderToConfirm.id}/confirm`, {
              method: 'PUT', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ deposit: depositValue, deliveryDate: newDate, force: true })
            });
            if (forceRes.ok) {
              document.getElementById('modal-confirm-order').hidden = true;
              jumpCalendarToDate(newDate);
            } else {
              await window.DreamUI.fail(forceRes, 'No se pudo confirmar el pedido.');
            }
          });
        } else {
          await window.DreamUI.fail(res, 'No se pudo confirmar el pedido.');
        }
      } catch (e) { window.DreamUI.offline(); }
    }));
  });

  // ================= 5. RECHAZAR Y ELIMINAR =================
  let orderToRejectId = null;

  window.actionReject = function(id) {
    orderToRejectId = id;
    document.getElementById('modal-reject-order').hidden = false;
  };

  document.getElementById('btn-confirm-reject')?.addEventListener('click', (e) => {
    if(!orderToRejectId) return;
    window.DreamUI.busy(e.currentTarget, async () => {
      try {
        const res = await fetch(`/api/orders/${orderToRejectId}/status`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'REJECTED' })
        });
        if (res.ok) {
          document.getElementById('modal-reject-order').hidden = true;
          loadOrders();
        } else {
          await window.DreamUI.fail(res, 'No se pudo rechazar el pedido.');
        }
      } catch (err) { window.DreamUI.offline(); }
    });
  });

  window.actionDelete = async function(id) {
    window.showConfirmModal("Eliminar pedido", "¿Seguro que querés eliminar este pedido? No se puede deshacer.", "Eliminar", "btn-red", async () => {
      try {
        const res = await fetch(`/api/orders/${id}`, { method: 'DELETE' });
        if (res.ok) {
          document.getElementById('modal-day-orders').hidden = true;
          loadOrders();
        } else {
          await window.DreamUI.fail(res, 'No se pudo eliminar el pedido.');
        }
      } catch(e) { window.DreamUI.offline(); }
    });
  };

  // ================= 6. VER Y EDITAR PEDIDO =================
  let viewingOrder = null;

  // Función auxiliar que SOLO llena los datos (Separa los datos de la interfaz)
  function fillViewModal(order) {
    document.getElementById('view-order-title').innerText = `Pedido #${order.id}`;
    document.getElementById('vo-name').value = order.customerName || '';
    document.getElementById('vo-phone').value = order.customerPhone || '';
    document.getElementById('vo-date-input').value = order.deliveryDate;
    document.getElementById('vo-total').value = order.totalPrice ?? '';
    document.getElementById('vo-deposit').value = order.deposit ?? '';
    document.getElementById('vo-email').value = order.customerEmail || '';
    document.getElementById('vo-shift').value = order.deliveryShift || '';
    document.getElementById('vo-delivery').value = order.deliveryMethod || '';
    document.getElementById('vo-address').value = order.deliveryAddress || '';
    document.getElementById('vo-coupon').value = order.couponCode || '';
    syncViewAddress();

    // Aplanar items a texto
    let detailsText = "";
    order.items.forEach(i => {
      detailsText += `[${i.productType}] - ${i.details}\n\n`;
    });
    document.getElementById('vo-items-details').value = detailsText.trim();
    originalItemsText = detailsText.trim();
  }

  // La dirección solo se ve si es envío
  let originalItemsText = '';
  function syncViewAddress() {
    document.getElementById('vo-address-group').hidden = document.getElementById('vo-delivery').value !== 'Envío';
  }
  document.getElementById('vo-delivery')?.addEventListener('change', syncViewAddress);

  // Función auxiliar que SOLO maneja lo visual (bloquear/desbloquear cajones)
  function toggleReadOnlyUI(isReadOnly) {
    document.querySelectorAll('#modal-view-order .form-input').forEach(input => {
      // El cupón no se edita: lo aplicó el servidor al recibir el pedido
      const locked = isReadOnly || input.hasAttribute('data-always-readonly');
      input.classList.toggle('readonly-mode', locked);
      // Las listas (select) no tienen "readonly": se desactivan
      if (input.tagName === 'SELECT') input.disabled = locked;
      else if (locked) input.setAttribute('readonly', 'true');
      else input.removeAttribute('readonly');
    });
    document.getElementById('btn-enable-edit').hidden = !isReadOnly;
    document.getElementById('vo-save-container').hidden = isReadOnly;
  }

  window.actionView = function(id) {
    viewingOrder = dbOrders.find(o => o.id === id);
    if(!viewingOrder) return;

    toggleReadOnlyUI(true); // 1. Bloqueamos la interfaz visualmente
    fillViewModal(viewingOrder); // 2. Llenamos los datos sin bucles infinitos
    
    const printBtn = document.getElementById('btn-print-receipt');
    const completeBtn = document.getElementById('btn-complete-view-order');
    const cancelBtn = document.getElementById('btn-cancel-view-order');
    
    if (printBtn) {
      // El recibo lleva montos: sin ellos (pastelero) el botón no se muestra
      if ((viewingOrder.status === 'CONFIRMED' || viewingOrder.status === 'COMPLETED') && viewingOrder.totalPrice != null) {
        printBtn.style.display = 'flex';
      } else {
        printBtn.style.display = 'none';
      }
    }

    if (completeBtn && cancelBtn) {
      if (viewingOrder.status === 'CONFIRMED') {
        completeBtn.style.display = 'flex';
        completeBtn.innerHTML = `${iconCheck} Marcar entregado`;
        cancelBtn.style.display = 'flex';
      } else if (viewingOrder.status === 'COMPLETED') {
        completeBtn.style.display = 'flex';
        completeBtn.innerHTML = 'Volver a agendado';
        cancelBtn.style.display = 'flex';
      } else {
        completeBtn.style.display = 'none';
        cancelBtn.style.display = 'none';
      }
    }

    document.getElementById('modal-view-order').hidden = false;
  };

  window.actionToggleComplete = async function(id, fromList) {
    const order = dbOrders.find(o => o.id === id);
    if (!order) return;
    const newStatus = order.status === 'CONFIRMED' ? 'COMPLETED' : 'CONFIRMED';
    const delivering = newStatus === 'COMPLETED';
    window.showConfirmModal(delivering ? 'Marcar como entregado' : 'Volver a agendado',
      delivering ? `¿El pedido #${order.id} ya se entregó?` : `El pedido #${order.id} vuelve a la lista de agendados.`,
      delivering ? 'Sí, entregado' : 'Volver a agendado', 'btn-pink', async () => {
      try {
        const res = await fetch(`/api/orders/${order.id}/status`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: newStatus })
        });
        if (res.ok) {
          if (!fromList) document.getElementById('modal-view-order').hidden = true;
          loadOrders();
        } else {
          await window.DreamUI.fail(res, 'No se pudo cambiar el estado del pedido.');
        }
      } catch(e) { window.DreamUI.offline(); }
    });
  };

  window.actionToggleCompleteViewingOrder = function() {
    if (viewingOrder) actionToggleComplete(viewingOrder.id, false);
  };

  window.actionCancelViewingOrder = function() {
    if (!viewingOrder) return;
    window.showConfirmModal("Cancelar pedido", `¿Seguro que querés cancelar el pedido #${viewingOrder.id}? Queda como Cancelado.`, "Cancelar pedido", "btn-red", async () => {
      try {
        const res = await fetch(`/api/orders/${viewingOrder.id}/status`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'CANCELLED' })
        });
        if (res.ok) {
          document.getElementById('modal-view-order').hidden = true;
          loadOrders();
        } else {
          await window.DreamUI.fail(res, 'No se pudo cancelar el pedido.');
        }
      } catch(e) { window.DreamUI.offline(); }
    });
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

  // Botón "Imprimir Recibo"
  document.getElementById('btn-print-receipt')?.addEventListener('click', () => {
    if (!viewingOrder || viewingOrder.totalPrice == null) return;   // sin montos no hay recibo
    window.logDownload?.('recibo', viewingOrder.id); // auditoría: quién imprimió el recibo
    
    const printSection = document.getElementById('print-receipt-section');
    if (!printSection) return;

    const today = new Date();
    const dateStr = window.DreamFormat ? window.DreamFormat.date(today) : today.toLocaleDateString('es-AR');
    
    // Calcular saldo pendiente
    const pendingBalance = viewingOrder.totalPrice - viewingOrder.deposit;
    
    // Generar lista de items
    let itemsHtml = '';
    viewingOrder.items.forEach(i => {
      itemsHtml += `
        <div class="receipt-item" style="display: flex; justify-content: space-between; font-weight: bold; margin-top: 10px;">
          <span>${esc(i.productType)}</span>
          <span>${money(i.price)}</span>
        </div>
        <div class="receipt-item-details" style="font-size: 0.9em; color: #444; margin-bottom: 5px; white-space: pre-wrap;">${esc(i.details)}</div>
      `;
    });

    const receiptHtml = `
      <div class="receipt-container">
        <h2 style="text-align: center; margin-bottom: 5px;">Dulce Sueños Posadas · Recibo de pedido</h2>
        <p class="receipt-date" style="text-align: center; font-size: 0.9rem; color: #555;">Fecha: ${dateStr}</p>
        <hr style="border-top: 2px dashed #000; margin: 15px 0;">
        <div class="receipt-info">
          <p><strong>Pedido N°:</strong> ${viewingOrder.id}</p>
          <p><strong>Cliente:</strong> ${viewingOrder.customerName || 'Sin Nombre'}</p>
          <p><strong>Teléfono:</strong> ${viewingOrder.customerPhone || 'N/A'}</p>
          <p><strong>Entrega:</strong> ${viewingOrder.deliveryDate ? viewingOrder.deliveryDate.split('-').reverse().join('/') : 'Sin fecha'}${viewingOrder.deliveryShift ? ` (${esc(viewingOrder.deliveryShift)})` : ''}</p>
          ${viewingOrder.deliveryMethod === 'Envío' ? `<p><strong>Envío a:</strong> ${esc(viewingOrder.deliveryAddress)}</p>` : viewingOrder.deliveryMethod === 'Retiro' ? '<p><strong>Retira en el local</strong></p>' : ''}
        </div>
        <hr style="border-top: 2px dashed #000; margin: 15px 0;">
        <div class="receipt-items">
          <h3 style="margin-bottom: 10px;">Detalle de Artículos</h3>
          ${itemsHtml}
        </div>
        <hr style="border-top: 2px dashed #000; margin: 15px 0;">
        <div class="receipt-financials" style="text-align: right; font-size: 1.1em;">
          <p><strong>Total:</strong> ${money(viewingOrder.totalPrice)}</p>
          ${viewingOrder.couponCode ? `<p><strong>Cupón:</strong> ${esc(viewingOrder.couponCode)}</p>` : ''}
          <p><strong>Seña:</strong> ${money(viewingOrder.deposit)}</p>
          <p class="receipt-balance" style="font-size: 1.2em; margin-top: 10px;"><strong>Saldo pendiente:</strong> ${money(pendingBalance)}</p>
        </div>
        <div style="text-align: center; margin-top: 40px; font-size: 0.9em; color: #666;">
          <p>¡Gracias por elegir Dulce Sueños!</p>
        </div>
      </div>
    `;

    printSection.innerHTML = receiptHtml;

    // Ocultar main UI, mostrar print, imprimir y restaurar
    const mainWrapper = document.querySelector('.admin-layout');
    const header = document.querySelector('.site-header');
    
    if (mainWrapper) mainWrapper.style.display = 'none';
    if (header) header.style.display = 'none';
    document.getElementById('modal-view-order').hidden = true;
    
    printSection.hidden = false;
    
    setTimeout(() => {
      window.print();
      
      // Restaurar
      if (mainWrapper) mainWrapper.style.display = '';
      if (header) header.style.display = '';
      printSection.hidden = true;
      document.getElementById('modal-view-order').hidden = false;
    }, 100);
  });

  window.saveEditedOrder = async function() {
    if(!viewingOrder) return;

    const newDate = document.getElementById('vo-date-input').value;
    const newTotal = parseFloat(document.getElementById('vo-total').value);
    const newDeposit = parseFloat(document.getElementById('vo-deposit').value);
    // Sin montos (panel del pastelero) no se manda total, seña ni precio: el servidor conserva los que ya tiene
    const hasMoney = !Number.isNaN(newTotal);

    if (hasMoney && newDeposit > newTotal) {
      window.showErrorAlert("La seña no puede ser mayor al total del pedido.");
      return;
    }

    const delivery = document.getElementById('vo-delivery').value;
    const address = document.getElementById('vo-address').value.trim();
    if (delivery === 'Envío' && !address) {
      window.showErrorAlert('Escribí la dirección para el envío.');
      return;
    }

    const itemsText = document.getElementById('vo-items-details').value;
    const updatedOrder = {
      customerName: document.getElementById('vo-name').value,
      customerPhone: document.getElementById('vo-phone').value,
      customerEmail: document.getElementById('vo-email').value.trim(),
      deliveryDate: newDate,
      deliveryShift: document.getElementById('vo-shift').value,
      deliveryMethod: delivery,
      deliveryAddress: address,
      ...(hasMoney ? { totalPrice: newTotal, deposit: newDeposit } : {}),
      status: viewingOrder.status,
      // Si no se tocó el texto de los productos, no se mandan: el servidor conserva los
      // productos originales (con su precio y su receta para descontar insumos)
      items: itemsText.trim() === originalItemsText ? null
        : [{ productType: "Detalles del Pedido", details: itemsText, ...(hasMoney ? { price: newTotal } : {}) }]
    };

    handleDateWarning(newDate, () => window.DreamUI.busy(document.getElementById('btn-save-edit'), async () => {
      try {
        const res = await fetch(`/api/orders/${viewingOrder.id}`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(updatedOrder)
        });
        if (res.ok) {
          document.getElementById('modal-view-order').hidden = true;
          jumpCalendarToDate(newDate); // Actualiza y salta al calendario correcto
        } else {
          await window.DreamUI.fail(res, 'No se pudo guardar el pedido.');
        }
      } catch(e) { window.DreamUI.offline(); }
    }));
  };

  // ================= 7. CREAR PEDIDO MANUAL =================
  let manualCatalogProducts = [];

  window.openManualOrderModal = async function() {
    document.getElementById('form-manual-order').reset();
    // Panel del pastelero: los campos de monto están ocultos. Un campo obligatorio y oculto no deja
    // enviar el formulario, así que acá no se exigen (el servidor guarda estos pedidos en $ 0).
    ['mo-total', 'mo-deposit'].forEach(id => {
      const field = document.getElementById(id);
      if (field && getComputedStyle(field).display === 'none') { field.required = false; field.value = '0'; }
    });
    document.getElementById('mo-address-group').hidden = true;
    const container = document.getElementById('mo-items-container');
    if (container) container.innerHTML = '';
    
    if (manualCatalogProducts.length === 0) {
      try {
        const res = await fetch('/api/products');
        if (res.ok) manualCatalogProducts = await res.json();
      } catch (e) { /* sin catálogo: igual se puede cargar una torta personalizada */ }
    }
    
    if (typeof window.addManualOrderItem === 'function') {
        window.addManualOrderItem();
    }
    
    document.getElementById('modal-manual-order').hidden = false;
  };

  window.addManualOrderItem = function() {
    const container = document.getElementById('mo-items-container');
    if (!container) return;
    
    let optionsHtml = '<option value="custom">Torta personalizada u otro</option>';
    manualCatalogProducts.forEach(p => {
        optionsHtml += `<option value="${p.id}" data-name="${esc(p.name)}">Catálogo: ${esc(p.name)}</option>`;
    });

    const itemHtml = `
      <div class="manual-order-item">
        <button type="button" class="manual-item-remove" aria-label="Quitar este producto" title="Quitar">&times;</button>
        <div class="form-grid-2">
          <label class="form-group">
            <span>Producto</span>
            <select class="form-input mo-item-type">
              ${optionsHtml}
            </select>
          </label>
          <label class="form-group">
            <span>Nombre</span>
            <input type="text" class="form-input mo-item-name" placeholder="Ej: Torta personalizada" value="Torta Personalizada">
          </label>
        </div>
        <label class="form-group">
          <span>Detalles</span>
          <textarea class="form-input mo-item-details" rows="2" placeholder="Ej: 20 porciones, relleno de mousse…" required></textarea>
        </label>
      </div>
    `;
    container.insertAdjacentHTML('beforeend', itemHtml);
  };

  // Quitar un producto (siempre queda al menos uno) y cambiar de catálogo a personalizado
  document.getElementById('mo-items-container')?.addEventListener('click', (e) => {
    const remove = e.target.closest('.manual-item-remove');
    if (remove && document.querySelectorAll('.manual-order-item').length > 1) remove.closest('.manual-order-item').remove();
  });
  document.getElementById('mo-items-container')?.addEventListener('change', (e) => {
    if (e.target.matches('.mo-item-type')) window.toggleManualItemType(e.target);
  });
  // La dirección solo se pide si es envío
  document.getElementById('mo-delivery')?.addEventListener('change', (e) => {
    document.getElementById('mo-address-group').hidden = e.target.value !== 'Envío';
  });

  window.toggleManualItemType = function(select) {
    const row = select.closest('.manual-order-item');
    const nameInput = row.querySelector('.mo-item-name');
    if (select.value === 'custom') {
      nameInput.value = 'Torta Personalizada';
      nameInput.readOnly = false;
    } else {
      const option = select.options[select.selectedIndex];
      nameInput.value = option.dataset.name;
      nameInput.readOnly = true;
    }
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

    const itemsContainer = document.getElementById('mo-items-container');
    const itemElements = itemsContainer.querySelectorAll('.manual-order-item');
    const items = [];
    
    itemElements.forEach(el => {
       const select = el.querySelector('.mo-item-type');
       const details = el.querySelector('.mo-item-details').value;
       const name = el.querySelector('.mo-item-name').value;
       
       let itemObj = {
           productType: name,
           details: details,
           isCustomCake: select.value === 'custom',
           price: 0
       };
       if (select.value !== 'custom') {
           itemObj.product = { id: parseInt(select.value) };
       }
       items.push(itemObj);
    });

    if (items.length === 0) {
      window.showErrorAlert("Agregá al menos un producto al pedido.");
      return;
    }

    const moDelivery = document.getElementById('mo-delivery').value;
    const moAddress = document.getElementById('mo-address').value.trim();
    if (moDelivery === 'Envío' && !moAddress) {
      window.showErrorAlert('Escribí la dirección para el envío.');
      return;
    }

    const orderData = {
      customerName: document.getElementById('mo-name').value,
      customerPhone: document.getElementById('mo-phone').value,
      deliveryDate: newDate,
      deliveryShift: document.getElementById('mo-shift').value || null,
      deliveryMethod: moDelivery,
      deliveryAddress: moAddress,
      totalPrice: total,
      deposit: deposit,
      status: document.getElementById('mo-status').value,
      items: items
    };

    const submitBtn = e.submitter || document.querySelector('#form-manual-order button[type="submit"]');
    handleDateWarning(newDate, () => window.DreamUI.busy(submitBtn, async () => {
      try {
        const res = await fetch('/api/orders', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(orderData)
        });
        if (res.ok) {
          document.getElementById('modal-manual-order').hidden = true;
          jumpCalendarToDate(newDate);
        } else {
          await window.DreamUI.fail(res, 'No se pudo guardar el pedido.');
        }
      } catch(err) { window.DreamUI.offline(); }
    }));
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
          <button type="button" class="btn btn-pink btn-sm" data-order-action="deliver" data-id="${o.id}">${iconCheck} Entregado</button>
          <button type="button" class="btn btn-grey btn-sm" data-order-action="view" data-id="${o.id}">${iconEye} Ver</button>
          <button type="button" class="btn btn-red btn-sm btn-icon" aria-label="Eliminar el pedido #${o.id}" title="Eliminar" data-order-action="delete" data-id="${o.id}">${iconTrash}</button>
        `;
      } else if (o.status === 'COMPLETED') {
        actionsHtml = `
          <button type="button" class="btn btn-grey btn-sm" data-order-action="view" data-id="${o.id}">${iconEye} Ver</button>
          <button type="button" class="btn btn-grey btn-sm" data-order-action="revert" data-id="${o.id}">${iconRevert} Volver a agendado</button>
        `;
      }

      tbody.innerHTML += `
        <tr>
          <td class="cell-id" data-label="Pedido">#${o.id}</td>
          <td data-label="Cliente"><span class="cell-strong">${esc(o.customerName)}</span><br><span class="cell-soft">${esc(o.customerPhone)}</span></td>
          <td class="cell-money" data-label="Total">${moneyText(o.totalPrice)}</td>
          <td data-label="Estado">${getStatusBadge(o.status)}</td>
          <td class="cell-actions"><div class="action-buttons-container">${actionsHtml}</div></td>
        </tr>
      `;
    });
  }

  window.setOrderStatus = async function(id, newStatus, force = false) {
    try {
      const res = await fetch(`/api/orders/${id}/status`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: newStatus, force: force })
      });
      if (res.ok) {
        await loadOrders(); 
        const dayOrders = dbOrders.filter(o => o.deliveryDate === currentDayModalDate && (o.status === 'CONFIRMED' || o.status === 'COMPLETED'));
        renderDayModalTable(dayOrders);
      } else {
        const errData = await res.clone().json().catch(() => ({}));
        if (errData.error === 'INSUFFICIENT_STOCK') {
          window.showConfirmModal('Faltan insumos', errData.message, 'Confirmar igual', 'btn-red', () => setOrderStatus(id, newStatus, true));
        } else {
          await window.DreamUI.fail(res, 'No se pudo cambiar el estado del pedido.');
        }
      }
    } catch(e) { window.DreamUI.offline(); }
  };

  // ================= 9. BOTONES (antes eran onclick="…" en el HTML) =================
  // La política de seguridad (CSP) ya no permite JavaScript dentro del HTML, así que los
  // botones se conectan desde acá. Los de las tablas usan data-order-action="…" y data-id.
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-order-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    switch (btn.dataset.orderAction) {
      case 'confirm':  window.openConfirmModal(id); break;
      case 'view':     window.actionView(id); break;
      case 'reject':   window.actionReject(id); break;
      case 'complete': window.actionToggleComplete(id, true); break;
      case 'delete':   window.actionDelete(id); break;
      case 'deliver':  window.DreamUI.busy(btn, () => window.setOrderStatus(id, 'COMPLETED')); break;
      case 'revert':   window.DreamUI.busy(btn, () => window.setOrderStatus(id, 'CONFIRMED')); break;
    }
  });

  document.getElementById('btn-new-order')?.addEventListener('click', () => window.openManualOrderModal());
  document.getElementById('btn-add-manual-item')?.addEventListener('click', () => window.addManualOrderItem());
  document.getElementById('co-payment-type')?.addEventListener('change', () => window.toggleDepositInput());
  // "Avisar por WhatsApp" (solo está en el panel de administración): el servidor arma el mensaje
  // según el estado actual del pedido y devuelve el link wa.me al número del cliente.
  document.getElementById('btn-whatsapp-order')?.addEventListener('click', async (e) => {
    if (!viewingOrder) return;
    const btn = e.currentTarget;
    // La ventana se abre YA (en el clic): si se abriera después de esperar al servidor,
    // el navegador la bloquearía como ventana emergente.
    const win = window.open('', '_blank');
    btn.disabled = true;
    try {
      const res = await fetch(`/api/order-notices/${viewingOrder.id}/whatsapp`);
      const data = await res.json().catch(() => ({}));
      if (res.ok && win) {
        win.opener = null;
        win.location.href = data.url;
      } else {
        if (win) win.close();
        window.showErrorAlert(res.ok
          ? 'El navegador bloqueó la ventana. Permití las ventanas emergentes de este sitio y probá de nuevo.'
          : (data.message || 'No se pudo preparar el mensaje.'));
      }
    } catch (err) {
      if (win) win.close();
      window.showErrorAlert('No se pudo conectar con el servidor.');
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('btn-complete-view-order')?.addEventListener('click', () => window.actionToggleCompleteViewingOrder());
  document.getElementById('btn-enable-edit')?.addEventListener('click', () => window.enableEditMode());
  document.getElementById('btn-cancel-view-order')?.addEventListener('click', () => window.actionCancelViewingOrder());
  document.getElementById('btn-discard-edit')?.addEventListener('click', () => window.disableEditMode());
  document.getElementById('btn-save-edit')?.addEventListener('click', () => window.saveEditedOrder());

  document.addEventListener('tabChanged', e => {
    if (e.detail === 'pedidos') loadOrders();
  });

  initCalendarDropdowns();
  loadOrders();
});