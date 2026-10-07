// /js/admin/clients.js - Módulo de Clientes (Búsqueda Avanzada Multicampo)
document.addEventListener('DOMContentLoaded', () => {

  let dbClients = [];
  let dbTags = [];
  
  const clientsTbody = document.getElementById('clients-tbody');
  const searchClient = document.getElementById('search-client');
  const clientSelectTag = document.getElementById('client-tag');

  // Estado de Ordenamiento y Filtro
  let sortCol = ''; 
  let sortAsc = true;
  let currentClientFilter = 'all';

  const iconEdit = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>`;
  const iconDelete = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>`;
  const iconUp = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"></path></svg>`;
  const iconDown = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12l7 7 7-7"></path></svg>`;

  function getSortIcon(col) {
    let colorUp = '#ccc'; 
    let colorDown = '#ccc';
    if (sortCol === col) {
      if (sortAsc) { colorUp = 'var(--accent)'; colorDown = '#eee'; } 
      else { colorUp = '#eee'; colorDown = 'var(--accent)'; }
    }
    return `<svg class="sort-svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="7 9 12 4 17 9" stroke="${colorUp}"></polyline>
              <polyline points="7 15 12 20 17 15" stroke="${colorDown}"></polyline>
            </svg>`;
  }

  function formatDateToLatam(dateStr) {
    if (!dateStr) return 'N/A';
    const [year, month, day] = dateStr.split('-');
    return `${day}/${month}/${year}`;
  }

  // ================= 1. CARGA DE DATOS =================
  async function loadTagsFromServer() {
    try {
      const res = await fetch('/api/tags');
      if (res.ok) {
        dbTags = await res.json();
        updateTagUI();
      }
    } catch (e) {}
  }

  async function loadClientsFromServer() {
    try {
      const res = await fetch('/api/customers');
      if (res.ok) {
        dbClients = await res.json();
        renderClients();
      }
    } catch (e) {}
  }

  // ================= 2. GESTIÓN DE CLIENTES =================
  const thName = document.getElementById('th-sort-client-name');
  const thDate = document.getElementById('th-sort-client-date');

  if (thName) thName.addEventListener('click', () => setSort('name'));
  if (thDate) thDate.addEventListener('click', () => setSort('dateAdded'));

  function setSort(col) {
    if (sortCol === col) sortAsc = !sortAsc;
    else { sortCol = col; sortAsc = true; }
    renderClients();
  }

  function renderClients() {
    if (!clientsTbody) return;
    clientsTbody.innerHTML = '';
    
    if (thName) thName.innerHTML = `Cliente <span class="sort-icon">${getSortIcon('name')}</span>`;
    if (thDate) thDate.innerHTML = `Registro <span class="sort-icon">${getSortIcon('dateAdded')}</span>`;

    const query = searchClient ? searchClient.value.toLowerCase() : '';

    let filtered = dbClients.filter(c => {
      
      // FILTRO MULTICAMPO INTELIGENTE
      if (query) {
        // Se asegura de que el campo exista antes de pasarlo a minúsculas
        const matchName = c.name && c.name.toLowerCase().includes(query);
        const matchPhone = c.phone && c.phone.toLowerCase().includes(query);
        const matchEmail = c.email && c.email.toLowerCase().includes(query);
        const matchAddress = c.address && c.address.toLowerCase().includes(query);

        // Si NO coincide con NINGUNO de los campos, lo descartamos
        if (!matchName && !matchPhone && !matchEmail && !matchAddress) {
          return false;
        }
      }

      // Filtro de Etiquetas (Chips)
      if (currentClientFilter !== 'all') {
        if (!c.tag || c.tag.name !== currentClientFilter) return false;
      }
      return true;
    });

    if (sortCol) {
      filtered.sort((a, b) => {
        let valA = a[sortCol]; let valB = b[sortCol];
        if (sortCol === 'name') { valA = valA.toLowerCase(); valB = valB.toLowerCase(); } 
        else if (sortCol === 'dateAdded') { valA = valA ? new Date(valA).getTime() : 0; valB = valB ? new Date(valB).getTime() : 0; }
        if (valA < valB) return sortAsc ? -1 : 1;
        if (valA > valB) return sortAsc ? 1 : -1;
        return 0;
      });
    }

    if (filtered.length === 0) {
      const hasQuery = searchClient && searchClient.value.trim() !== '';
      const emptyText = (hasQuery || currentClientFilter !== 'all') ? 'Ningún cliente coincide con la búsqueda.'
        : 'Todavía no hay clientes. Se agregan solos al confirmar un pedido, o con "Nuevo cliente".';
      clientsTbody.innerHTML = `<tr><td colspan="7" class="cell-empty">${emptyText}</td></tr>`;
      return;
    }

    filtered.forEach(c => {
      const tr = document.createElement('tr');
      
      // Contacto en renglones (sin emojis; cada dato con su nombre para lectores de pantalla)
      const phone = c.phone ? `<span class="contact-line"><span class="visually-hidden">Teléfono: </span>${c.phone}</span>` : '';
      const email = c.email ? `<span class="contact-line"><span class="visually-hidden">Email: </span>${c.email}</span>` : '';
      const address = c.address ? `<span class="contact-line"><span class="visually-hidden">Dirección: </span>${c.address}</span>` : '';
      const contactInfo = (phone || email || address) ? (phone + email + address) : '<span class="cell-soft">Sin datos</span>';
      
      const tagBadge = c.tag ? `<span class="tag-badge">${c.tag.name}</span>` : '';
      const birthStr = c.birthDate ? formatDateToLatam(c.birthDate) : '<span class="cell-soft">Sin fecha</span>';
      // Pedidos confirmados o entregados del cliente y lo que gastó (lo calcula el servidor)
      const totalP = c.ordersCount || 0;
      const spent = c.totalSpent ? ` · $${Math.round(c.totalSpent).toLocaleString('es-AR')}` : '';

      tr.innerHTML = `
        <td class="cell-title cell-strong">${c.name}</td>
        <td data-label="Contacto"><span class="contact-lines">${contactInfo}</span></td>
        <td data-label="Etiqueta">${tagBadge || '<span class="cell-soft">—</span>'}</td>
        <td data-label="Cumpleaños">${birthStr}</td>
        <td data-label="Registro" class="cell-soft">${formatDateToLatam(c.dateAdded)}</td>
        <td data-label="Pedidos" class="cell-strong">${totalP} ${totalP === 1 ? 'pedido' : 'pedidos'}<span class="cell-soft">${spent}</span></td>
        <td class="cell-actions"><div class="action-buttons-container">
          <button type="button" class="btn btn-grey btn-sm btn-client-orders">Ver pedidos</button>
          <button type="button" class="btn btn-grey btn-sm btn-edit">${iconEdit} Editar</button>
          <button type="button" class="btn btn-red btn-sm btn-delete">${iconDelete} Eliminar</button>
        </div>
        </td>
      `;

      tr.querySelector('.btn-client-orders').addEventListener('click', () => openClientOrders(c));
      tr.querySelector('.btn-edit').addEventListener('click', () => openClientForm(c));
      tr.querySelector('.btn-delete').addEventListener('click', () => deleteClient(c));

      clientsTbody.appendChild(tr);
    });
  }

  if(searchClient) searchClient.addEventListener('input', renderClients);

  // ================= HISTORIAL DE PEDIDOS DEL CLIENTE =================
  // Relación Pedido–Cliente: el servidor devuelve los pedidos unidos al cliente
  // y también los viejos que tienen su mismo teléfono.
  const ORDER_STATUS = {
    PENDING: ['Nuevo', 'status-pending'], CONFIRMED: ['Agendado', 'status-agendado'],
    IN_PROGRESS: ['En preparación', 'status-progress'], COMPLETED: ['Entregado', 'status-completado'],
    CANCELLED: ['Cancelado', 'status-cancelled'], REJECTED: ['Rechazado', 'status-cancelled']
  };
  const escHtml = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

  async function openClientOrders(c) {
    const tbody = document.getElementById('client-orders-tbody');
    const summary = document.getElementById('client-orders-summary');
    document.getElementById('client-orders-title').textContent = `Pedidos de ${c.name}`;
    tbody.innerHTML = '<tr><td colspan="5" class="cell-empty">Cargando…</td></tr>';
    summary.textContent = '';
    document.getElementById('modal-client-orders').hidden = false;

    try {
      const res = await fetch(`/api/customers/${c.id}/orders`);
      if (!res.ok) throw new Error();
      const orders = await res.json();
      const totalP = c.ordersCount || 0;
      summary.textContent = orders.length === 0
        ? 'Este cliente todavía no tiene pedidos.'
        : `${totalP} ${totalP === 1 ? 'pedido confirmado o entregado' : 'pedidos confirmados o entregados'} · Total: $${Math.round(c.totalSpent || 0).toLocaleString('es-AR')}`;
      tbody.innerHTML = orders.length === 0 ? '<tr><td colspan="5" class="cell-empty">Sin pedidos.</td></tr>' : orders.map(o => {
        const [label, cls] = ORDER_STATUS[o.status] || [o.status, 'status-pending'];
        const items = (o.items || []).map(i => escHtml(i.productType)).join(', ') || '—';
        return `<tr>
          <td class="cell-id" data-label="Pedido">#${o.id}</td>
          <td data-label="Entrega">${o.deliveryDate ? formatDateToLatam(o.deliveryDate) : 'Sin fecha'}</td>
          <td data-label="Productos">${items}</td>
          <td class="cell-money" data-label="Total">$${Number(o.totalPrice || 0).toLocaleString('es-AR')}</td>
          <td data-label="Estado"><span class="status-badge is-small ${cls}">${label}</span></td>
        </tr>`;
      }).join('');
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="5" class="cell-empty">No se pudieron cargar los pedidos.</td></tr>';
    }
  }

  // Formulario de Cliente
  const clientForm = document.getElementById('client-form');
  const btnAddClient = document.getElementById('btn-add-client');
  
  if(btnAddClient) {
    btnAddClient.addEventListener('click', () => {
      clientForm.reset();
      document.getElementById('client-id').value = '';
      document.getElementById('client-modal-title').innerText = "Nuevo cliente";
      document.getElementById('modal-client-form').hidden = false;
    });
  }

  function openClientForm(c) {
    clientForm.reset();
    document.getElementById('client-id').value = c.id;
    document.getElementById('client-name').value = c.name;
    document.getElementById('client-phone').value = c.phone || '';
    document.getElementById('client-email').value = c.email || '';
    document.getElementById('client-address').value = c.address || '';
    if (c.birthDate) document.getElementById('client-birthdate').value = c.birthDate;
    if(c.tag) document.getElementById('client-tag').value = c.tag.name;
    
    document.getElementById('client-modal-title').innerText = "Editar cliente";
    document.getElementById('modal-client-form').hidden = false;
  }

  if(clientForm) {
    clientForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('client-id').value;
      const data = {
        name: document.getElementById('client-name').value,
        phone: document.getElementById('client-phone').value,
        email: document.getElementById('client-email').value,
        address: document.getElementById('client-address').value,
        birthDate: document.getElementById('client-birthdate').value || null,
        tagName: document.getElementById('client-tag').value
      };

      const url = id ? `/api/customers/${id}` : '/api/customers';
      const method = id ? 'PUT' : 'POST';

      try {
        const res = await fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
        if (res.ok) {
          document.getElementById('modal-client-form').hidden = true;
          loadClientsFromServer();
        } else if (res.status === 400) {
          window.showErrorAlert(await res.json());
        }
      } catch(e) { window.showErrorAlert("Error de conexión"); }
    });
  }

  function deleteClient(c) {
    window.showConfirmModal('Eliminar Cliente', `¿Seguro que deseas eliminar a "${c.name}"?`, 'Eliminar', 'btn-red', async () => {
      await fetch(`/api/customers/${c.id}`, { method: 'DELETE' });
      loadClientsFromServer();
    });
  }

  // ================= 3. GESTIÓN DE ETIQUETAS (TAGS Y CHIPS) =================
  function updateTagUI() {
    if(clientSelectTag) {
      clientSelectTag.innerHTML = '';
      dbTags.forEach(t => clientSelectTag.innerHTML += `<option value="${t.name}">${t.name}</option>`);
    }

    const filterContainer = document.getElementById('client-filters');
    if (filterContainer) {
      filterContainer.innerHTML = `<button type="button" class="chip active" data-filter="all">Todos</button>`;
      dbTags.forEach(t => {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'chip';
        chip.dataset.filter = t.name;
        chip.innerText = t.name;
        if(currentClientFilter === t.name) chip.classList.add('active');
        filterContainer.appendChild(chip);
      });
      assignClientChipEvents();
    }

    renderTagsListModal(); 
  }

  function assignClientChipEvents() {
    document.querySelectorAll('#client-filters .chip').forEach(chip => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('#client-filters .chip').forEach(c => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
        currentClientFilter = e.currentTarget.dataset.filter;
        renderClients();
      });
    });
  }

  function renderTagsListModal() {
    const list = document.getElementById('tags-list');
    if(!list) return;
    list.innerHTML = '';

    dbTags.forEach((t, index) => {
      const li = document.createElement('li');
      li.innerHTML = `
        <span class="cat-name">${t.name}</span>
        <div class="cat-actions">
            <button type="button" class="cat-action-btn btn-edit" title="Modificar" aria-label="Cambiar el nombre de ${t.name}">${iconEdit}</button>
            <button type="button" class="cat-action-btn btn-up" title="Subir" ${index === 0 ? 'disabled' : ''}>${iconUp}</button>
            <button type="button" class="cat-action-btn btn-down" title="Bajar" ${index === dbTags.length - 1 ? 'disabled' : ''}>${iconDown}</button>
            ${t.deletable ? `<button type="button" class="cat-action-btn btn-delete" title="Eliminar" aria-label="Eliminar ${t.name}">${iconDelete}</button>` : ''}
        </div>
      `;

      li.querySelector('.btn-edit').addEventListener('click', async () => {
        const newName = await window.showCustomPrompt("Escribí el nuevo nombre de la etiqueta:", t.name);
        if (newName && newName.trim() !== "" && newName !== t.name) {
          await fetch(`/api/tags/${t.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: newName.trim() }) });
          loadTagsFromServer();
          loadClientsFromServer(); 
        }
      });

      li.querySelector('.btn-up').addEventListener('click', () => { if (index > 0) reorderTag(index, index - 1); });
      li.querySelector('.btn-down').addEventListener('click', () => { if (index < dbTags.length - 1) reorderTag(index, index + 1); });

      if (t.deletable) {
        li.querySelector('.btn-delete').addEventListener('click', () => {
          window.showConfirmModal('Eliminar Etiqueta', `¿Seguro que deseas eliminar la etiqueta "${t.name}"? Los clientes volverán a "Sin etiqueta".`, 'Eliminar', 'btn-red', async () => {
            await fetch(`/api/tags/${t.id}`, { method: 'DELETE' });
            loadTagsFromServer();
            loadClientsFromServer(); 
          });
        });
      }
      list.appendChild(li);
    });
  }

  async function reorderTag(oldIndex, newIndex) {
    const temp = dbTags[oldIndex];
    dbTags[oldIndex] = dbTags[newIndex];
    dbTags[newIndex] = temp;
    renderTagsListModal();

    const orderedIds = dbTags.map(t => t.id);
    try {
      await fetch('/api/tags/reorder', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(orderedIds) });
      loadTagsFromServer(); 
    } catch (e) { window.showErrorAlert("No se pudo guardar el nuevo orden."); }
  }

  const btnOpenTags = document.getElementById('btn-open-tags-modal');
  if(btnOpenTags) {
    btnOpenTags.addEventListener('click', () => {
      document.getElementById('modal-tags').hidden = false;
      document.getElementById('add-tag-container').style.display = 'none';
      document.getElementById('btn-show-add-tag').style.display = 'block';
    });
  }

  const btnShowAddTag = document.getElementById('btn-show-add-tag');
  if(btnShowAddTag) {
    btnShowAddTag.addEventListener('click', (e) => {
      e.target.style.display = 'none';
      document.getElementById('add-tag-container').style.display = 'flex';
      document.getElementById('new-tag-name').focus();
    });
  }

  const btnAddTag = document.getElementById('btn-add-tag');
  if(btnAddTag) {
    btnAddTag.addEventListener('click', async () => {
      const input = document.getElementById('new-tag-name');
      if (!input.value.trim()) return;
      try {
        const res = await fetch('/api/tags', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: input.value.trim() }) });
        if (res.ok) {
          input.value = '';
          document.getElementById('add-tag-container').style.display = 'none';
          document.getElementById('btn-show-add-tag').style.display = 'block';
          loadTagsFromServer(); 
        } else {
          window.showErrorAlert(await res.text());
        }
      } catch(e) {}
    });
  }

  // ================= 4. GESTIÓN DE CUPONES =================
  let dbCoupons = [];
  const iconEyeOn = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
  const iconEyeOff = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;

  async function loadCouponsFromServer() {
    try {
      const res = await fetch('/api/coupons');
      if (res.ok) {
        dbCoupons = await res.json();
        renderCouponsListModal();
      }
    } catch (e) {}
  }

  // Nombre de cada tipo de cupón para mostrar (en la base se guarda en mayúsculas)
  const COUPON_TYPES = { DESCUENTO: 'Descuento', ENVIO_GRATIS: 'Envío gratis', REGALO: 'Regalo', OTROS: 'Otros' };

  function renderCouponsListModal() {
    const list = document.getElementById('coupons-list');
    if(!list) return;
    list.innerHTML = '';

    dbCoupons.forEach(c => {
      const li = document.createElement('li');
      if (!c.active) li.classList.add('is-inactive');   // cupón apagado: se ve atenuado y lo dice
      
      const eyeIcon = c.active ? iconEyeOn : iconEyeOff;
      

      li.innerHTML = `
        <div class="coupon-info">
          <span class="cat-name">${c.code}</span>
          <span class="tag-badge">${COUPON_TYPES[c.type] || c.type}</span>
          ${c.active ? '' : '<span class="status-badge is-small status-cancelled">Pausado</span>'}
        </div>
        <div class="cat-actions">
            <button type="button" class="cat-action-btn btn-edit-coupon" title="Editar" aria-label="Editar el cupón ${c.code}">${iconEdit}</button>
            <button type="button" class="cat-action-btn btn-toggle" title="${c.active ? 'Pausar' : 'Activar'}" aria-label="${c.active ? 'Pausar' : 'Activar'} el cupón ${c.code}">${eyeIcon}</button>
            <button type="button" class="cat-action-btn btn-delete" title="Eliminar" aria-label="Eliminar el cupón ${c.code}">${iconDelete}</button>
        </div>
      `;

      li.querySelector('.btn-edit-coupon').addEventListener('click', () => {
        document.getElementById('coupon-id').value = c.id;
        document.getElementById('new-coupon-code').value = c.code;
        document.getElementById('new-coupon-type').value = c.type;
        document.getElementById('new-coupon-desc').value = c.description || '';
        document.getElementById('new-coupon-discount').value = c.discountPercentage || '';
        document.getElementById('new-coupon-custom').value = c.customText || '';
        document.getElementById('new-coupon-limit').value = c.usageLimit || '';
        document.getElementById('new-coupon-unlimited').checked = !!c.unlimited;
        
        document.getElementById('new-coupon-type').dispatchEvent(new Event('change'));
        document.getElementById('new-coupon-unlimited').dispatchEvent(new Event('change'));
        
        document.getElementById('btn-show-add-coupon').style.display = 'none';
        document.getElementById('add-coupon-container').style.display = 'flex';
        document.getElementById('new-coupon-code').focus();
      });

      li.querySelector('.btn-toggle').addEventListener('click', async () => {
        try {
          const updatedCoupon = { ...c, active: !c.active };
          const res = await fetch(`/api/coupons/${c.id}`, { 
            method: 'PUT', 
            headers: { 'Content-Type': 'application/json' }, 
            body: JSON.stringify(updatedCoupon) 
          });
          if (res.ok) loadCouponsFromServer();
        } catch (e) {}
      });

      li.querySelector('.btn-delete').addEventListener('click', () => {
        window.showConfirmModal('Eliminar Cupón', `¿Seguro que deseas eliminar el cupón "${c.code}"?`, 'Eliminar', 'btn-red', async () => {
          await fetch(`/api/coupons/${c.id}`, { method: 'DELETE' });
          loadCouponsFromServer();
        });
      });

      list.appendChild(li);
    });
  }

  const btnOpenCoupons = document.getElementById('btn-open-coupons-modal');
  if (btnOpenCoupons) {
    btnOpenCoupons.addEventListener('click', () => {
      document.getElementById('modal-coupons').hidden = false;
      document.getElementById('add-coupon-container').style.display = 'none';
      document.getElementById('btn-show-add-coupon').style.display = 'block';
      loadCouponsFromServer();
    });
  }

  const btnShowAddCoupon = document.getElementById('btn-show-add-coupon');
  if (btnShowAddCoupon) {
    btnShowAddCoupon.addEventListener('click', (e) => {
      e.target.style.display = 'none';
      document.getElementById('coupon-id').value = '';
      document.getElementById('new-coupon-code').value = '';
      document.getElementById('new-coupon-desc').value = '';
      document.getElementById('new-coupon-discount').value = '';
      document.getElementById('new-coupon-custom').value = '';
      document.getElementById('new-coupon-limit').value = '';
      document.getElementById('new-coupon-unlimited').checked = false;
      document.getElementById('new-coupon-limit').disabled = false;
      document.getElementById('add-coupon-container').style.display = 'flex';
      document.getElementById('new-coupon-code').focus();
    });
  }

  const newCouponType = document.getElementById('new-coupon-type');
  const newCouponDesc = document.getElementById('new-coupon-desc');
  const newCouponDiscount = document.getElementById('new-coupon-discount');
  const newCouponCustom = document.getElementById('new-coupon-custom');
  const newCouponLimit = document.getElementById('new-coupon-limit');
  const newCouponUnlimited = document.getElementById('new-coupon-unlimited');

  if (newCouponType) {
    newCouponType.addEventListener('change', (e) => {
      const val = e.target.value;
      if (val === 'DESCUENTO') {
        newCouponDiscount.style.display = 'block';
        newCouponCustom.style.display = 'none';
      } else if (val === 'ENVIO_GRATIS') {
        newCouponDiscount.style.display = 'none';
        newCouponCustom.style.display = 'none';
      } else {
        newCouponDiscount.style.display = 'none';
        newCouponCustom.style.display = 'block';
      }
    });
  }

  if (newCouponUnlimited) {
    newCouponUnlimited.addEventListener('change', (e) => {
      newCouponLimit.disabled = e.target.checked;
      if (e.target.checked) newCouponLimit.value = '';
    });
  }

  const btnAddCoupon = document.getElementById('btn-add-coupon');
  if (btnAddCoupon) {
    btnAddCoupon.addEventListener('click', async () => {
      const codeInput = document.getElementById('new-coupon-code');
      if (!codeInput.value.trim()) return;
      
      const id = document.getElementById('coupon-id').value;
      const url = id ? `/api/coupons/${id}` : '/api/coupons';
      const method = id ? 'PUT' : 'POST';

      const payload = {
         code: codeInput.value.trim().toUpperCase(),
         type: newCouponType.value,
         description: newCouponDesc.value.trim(),
         discountPercentage: newCouponDiscount.value ? parseInt(newCouponDiscount.value) : null,
         customText: newCouponCustom.value.trim(),
         usageLimit: newCouponLimit.value ? parseInt(newCouponLimit.value) : null,
         unlimited: newCouponUnlimited.checked,
         active: true
      };

      try {
        const res = await fetch(url, { 
          method: method, 
          headers: { 'Content-Type': 'application/json' }, 
          body: JSON.stringify(payload) 
        });
        if (res.ok) {
          document.getElementById('coupon-id').value = '';
          codeInput.value = '';
          newCouponDesc.value = '';
          newCouponDiscount.value = '';
          newCouponCustom.value = '';
          newCouponLimit.value = '';
          newCouponUnlimited.checked = false;
          newCouponLimit.disabled = false;
          
          document.getElementById('add-coupon-container').style.display = 'none';
          document.getElementById('btn-show-add-coupon').style.display = 'block';
          loadCouponsFromServer(); 
        } else {
          window.showErrorAlert(await res.text());
        }
      } catch(e) {}
    });
  }

  // ================= 5. EVENTOS GLOBALES =================
  document.addEventListener('tabChanged', e => {
    if (e.detail === 'clientes') {
      loadTagsFromServer();
      loadClientsFromServer();
      loadCouponsFromServer();
    }
  });

  // Carga inicial silente
  loadTagsFromServer();
  loadClientsFromServer();
  loadCouponsFromServer();
});