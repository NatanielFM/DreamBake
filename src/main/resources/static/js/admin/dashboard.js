document.addEventListener('DOMContentLoaded', () => {
  let chartRevenue = null;
  let chartStatus = null;
  let chartProducts = null;
  let chartDays = null;

  let dashState = {
    orders: [],
    inventory: [],
    clients: [],
    products: [],
    selectedYear: new Date().getFullYear()
  };

  const yearSelect = document.getElementById('dash-year-filter');
  const btnRefresh = document.getElementById('btn-refresh-dashboard');
  const btnPdf = document.getElementById('btn-export-dashboard-pdf');

  async function loadDashboardData() {
    try {
      const [ordRes, invRes, cliRes, prodRes, monthlyRes, topProdRes] = await Promise.all([
        fetch('/api/orders').catch(() => ({ ok: false })),
        fetch('/api/inventory').catch(() => ({ ok: false })),
        fetch('/api/customers').catch(() => ({ ok: false })),
        fetch('/api/products').catch(() => ({ ok: false })),
        fetch('/api/orders/stats/monthly').catch(() => ({ ok: false })),
        fetch('/api/orders/stats/top-products').catch(() => ({ ok: false }))
      ]);

      dashState.orders = ordRes.ok ? await ordRes.json() : [];
      dashState.inventory = invRes.ok ? await invRes.json() : [];
      dashState.clients = cliRes.ok ? await cliRes.json() : [];
      dashState.products = prodRes.ok ? await prodRes.json() : [];
      dashState.monthlyStats = monthlyRes.ok ? await monthlyRes.json() : null;
      dashState.topProductsStats = topProdRes.ok ? await topProdRes.json() : [];

      populateYearSelector();
      computeAndRender();
    } catch (err) {
      console.error('Error cargando dashboard:', err);
    }
  }

  function populateYearSelector() {
    if (!yearSelect) return;
    const currentYear = new Date().getFullYear();
    const years = new Set([currentYear]);

    dashState.orders.forEach(o => {
      if (o.deliveryDate) {
        const y = parseInt(String(o.deliveryDate).split('-')[0], 10);
        if (!isNaN(y)) years.add(y);
      }
    });

    const sortedYears = Array.from(years).sort((a, b) => b - a);
    yearSelect.innerHTML = sortedYears
      .map(y => `<option value="${y}" ${y === dashState.selectedYear ? 'selected' : ''}>Año ${y}</option>`)
      .join('');
  }

  function parseOrderItems(o) {
    let itemsCount = 0;
    let cakesCount = 0;
    const itemsMap = {};

    if (Array.isArray(o.items) && o.items.length > 0) {
      o.items.forEach(it => {
        const name = it.isCustomCake ? 'Torta Personalizada' : (it.product?.name || it.productType || it.name || 'Desconocido');
        itemsCount++;
        if (it.isCustomCake) cakesCount++; // Use architectural flag, not string category
        itemsMap[name] = (itemsMap[name] || 0) + 1;
      });
    } else if (typeof o.itemsDetails === 'string' && o.itemsDetails.trim() !== '') {
      const lines = o.itemsDetails.split('\n').filter(l => l.trim() !== '');
      itemsCount += lines.length || 1;
      if (o.itemsDetails.toLowerCase().includes('torta')) cakesCount += 1;
      itemsMap['Pedido Personalizado'] = (itemsMap['Pedido Personalizado'] || 0) + 1;
    } else {
      itemsCount = 1;
      cakesCount = 1;
      itemsMap['Torta Personalizada'] = (itemsMap['Torta Personalizada'] || 0) + 1;
    }

    return { itemsCount, cakesCount, itemsMap };
  }

  function computeAndRender() {
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const currentMonth = now.getMonth();
    const selectedYear = parseInt(yearSelect?.value || now.getFullYear(), 10);

    let totalRevenue = 0;
    let monthRevenue = 0;
    let pendingBalance = 0;
    let collectedDeposits = 0;
    let ordersMonthCount = 0;
    let ordersYearCount = 0;
    let cakesSoldCount = 0;

    const monthlyRev = new Array(12).fill(0);
    const monthlyOrd = new Array(12).fill(0);
    const statusCounts = { PENDING: 0, CONFIRMED: 0, COMPLETED: 0, CANCELLED: 0 };
    const weekdayCounts = new Array(7).fill(0); // Lun a Dom
    const productRanking = {};
    const clientMap = {};
    const upcomingDeliveries = [];

    dashState.orders.forEach(o => {
      const status = (o.status || 'PENDING').toUpperCase();
      if (statusCounts[status] !== undefined) statusCounts[status]++;

      const price = Number(o.totalPrice || o.total || 0);
      const deposit = Number(o.deposit || 0);
      const dateStr = o.deliveryDate ? String(o.deliveryDate).split('T')[0] : null;

      let orderYear = null;
      let orderMonth = null;
      let dateObj = null;

      if (dateStr) {
        const parts = dateStr.split('-');
        if (parts.length === 3) {
          orderYear = parseInt(parts[0], 10);
          orderMonth = parseInt(parts[1], 10) - 1;
          dateObj = new Date(orderYear, orderMonth, parseInt(parts[2], 10));
        }
      }

      const isValidSale = status === 'CONFIRMED' || status === 'COMPLETED';

      if (isValidSale) {
        if (!orderYear || orderYear === selectedYear) {
          totalRevenue += price;
          ordersYearCount++;

          if (orderMonth !== null && orderMonth >= 0 && orderMonth < 12) {
            monthlyRev[orderMonth] += price;
            monthlyOrd[orderMonth] += 1;
          }
        }

        if (status === 'CONFIRMED') {
          const paid = deposit > 0 ? Math.min(deposit, price) : 0;
          collectedDeposits += paid;
          pendingBalance += Math.max(0, price - paid);
        } else if (status === 'COMPLETED') {
          collectedDeposits += price;
        }

        const parsed = parseOrderItems(o);
        cakesSoldCount += parsed.cakesCount;

        if (dateObj && !isNaN(dateObj)) {
          const dayIdx = (dateObj.getDay() + 6) % 7; // 0=Lun ... 6=Dom
          weekdayCounts[dayIdx]++;
        }

        const cKey = (o.customerPhone || o.customerName || 'Cliente').trim();
        if (!clientMap[cKey]) {
          clientMap[cKey] = {
            name: o.customerName || 'Cliente sin nombre',
            phone: o.customerPhone || '-',
            orders: 0,
            spent: 0
          };
        }
        clientMap[cKey].orders += 1;
        clientMap[cKey].spent += price;
      }

      if ((status === 'CONFIRMED' || status === 'PENDING') && dateStr && dateStr >= todayStr) {
        upcomingDeliveries.push({
          id: o.id,
          customerName: o.customerName || 'Cliente',
          customerPhone: o.customerPhone || '-',
          deliveryDate: dateStr,
          totalPrice: price,
          deposit: deposit,
          status: status
        });
      }
    });

    upcomingDeliveries.sort((a, b) => a.deliveryDate.localeCompare(b.deliveryDate));

    // Update Pedidos del Mes strictly based on the currently selected calendar year
    monthRevenue = monthlyRev[currentMonth] || 0;
    ordersMonthCount = monthlyOrd[currentMonth] || 0;

    if (dashState.topProductsStats) {
      dashState.topProductsStats.forEach(p => {
        productRanking[p.name] = p.count;
      });
    }

    const avgTicket = ordersYearCount > 0 ? Math.round(totalRevenue / ordersYearCount) : 0;

    // Alertas de Inventario
    const criticalInventory = [];
    dashState.inventory.forEach(item => {
      const current = Number(item.currentStock ?? item.stock ?? 0);
      const min = Number(item.minimumStock ?? 0);
      const expStr = item.expirationDate ? String(item.expirationDate).split('T')[0] : null;

      let isLow = current <= min;
      let isExpiring = false;
      let daysToExp = null;

      if (expStr) {
        const expDate = new Date(expStr + 'T00:00:00');
        const diffDays = Math.ceil((expDate - new Date(todayStr + 'T00:00:00')) / (1000 * 60 * 60 * 24));
        daysToExp = diffDays;
        if (diffDays <= 7) isExpiring = true;
      }

      if (isLow || isExpiring) {
        criticalInventory.push({
          name: item.name || 'Insumo',
          current,
          min,
          unit: item.unit || 'u.',
          expStr,
          daysToExp,
          isLow,
          isExpiring
        });
      }
    });

    // Renderizar KPIs
    setText('kpi-total-revenue', '$' + totalRevenue.toLocaleString('es-AR'));
    setText('kpi-month-revenue', 'Este mes: $' + monthRevenue.toLocaleString('es-AR'));
    setText('kpi-pending-balance', '$' + pendingBalance.toLocaleString('es-AR'));
    setText('kpi-collected-deposits', 'Cobrado (señas): $' + collectedDeposits.toLocaleString('es-AR'));
    setText('kpi-orders-month', ordersMonthCount);
    setText('kpi-orders-year', `Total en ${selectedYear}: ${ordersYearCount} pedidos`);
    setText('kpi-cakes-sold', cakesSoldCount);
    setText('kpi-avg-ticket', 'Ticket promedio: $' + avgTicket.toLocaleString('es-AR'));

    if (upcomingDeliveries.length > 0) {
      const next = upcomingDeliveries[0];
      setText('kpi-next-date', formatDateAR(next.deliveryDate));
      setText('kpi-next-client', `${next.customerName} (#${next.id})`);
    } else {
      setText('kpi-next-date', 'Sin entregas');
      setText('kpi-next-client', 'No hay pedidos próximos');
    }

    setText('kpi-low-stock', criticalInventory.length);
    setText('kpi-pending-inbox', `${statusCounts.PENDING} pedidos nuevos sin confirmar`);

    renderTables(upcomingDeliveries, clientMap, criticalInventory);
    renderCharts(monthlyRev, monthlyOrd, statusCounts, productRanking, weekdayCounts);
    renderMargins(selectedYear);

    // Guardar resumen para exportación PDF
    dashState.summary = {
      selectedYear,
      totalRevenue,
      monthRevenue,
      pendingBalance,
      collectedDeposits,
      ordersMonthCount,
      ordersYearCount,
      cakesSoldCount,
      avgTicket,
      statusCounts,
      upcomingDeliveries,
      topClients: Object.values(clientMap).sort((a, b) => b.spent - a.spent).slice(0, 8),
      criticalInventory
    };
  }

  function renderTables(upcomingDeliveries, clientMap, criticalInventory) {
    // 1. Próximas Entregas
    const upTbody = document.getElementById('dash-upcoming-tbody');
    if (upTbody) {
      if (upcomingDeliveries.length === 0) {
        upTbody.innerHTML = `<tr><td colspan="4" class="cell-empty">No hay entregas agendadas por ahora.</td></tr>`;
      } else {
        upTbody.innerHTML = upcomingDeliveries.slice(0, 6).map(o => {
          // Mismos colores que en Pedidos: Agendado rosa, Pendiente ámbar (con texto e ícono)
          const badge = o.status === 'CONFIRMED'
            ? `<span class="status-badge status-agendado">Agendado</span>`
            : `<span class="status-badge status-pending">Pendiente</span>`;
          // data-label: en el celular cada fila se ve como tarjeta con "Etiqueta: valor"
          return `
            <tr>
              <td class="cell-title"><span class="cell-id">#${o.id}</span> <span class="cell-strong">${o.customerName}</span></td>
              <td data-label="Entrega">${formatDateAR(o.deliveryDate)}</td>
              <td data-label="Total" class="cell-money">$${o.totalPrice.toLocaleString('es-AR')}</td>
              <td data-label="Estado">${badge}</td>
            </tr>
          `;
        }).join('');
      }
    }

    // 2. Mejores Clientes
    const cliTbody = document.getElementById('dash-top-clients-tbody');
    const topClients = Object.values(clientMap).sort((a, b) => b.spent - a.spent).slice(0, 6);
    if (cliTbody) {
      if (topClients.length === 0) {
        cliTbody.innerHTML = `<tr><td colspan="4" class="cell-empty">Todavía no hay clientes con compras confirmadas.</td></tr>`;
      } else {
        cliTbody.innerHTML = topClients.map((c, idx) => `
          <tr>
            <td class="cell-title"><span class="dash-rank-num">${idx + 1}</span> <span class="cell-strong">${c.name}</span></td>
            <td data-label="Contacto">${c.phone}</td>
            <td data-label="Compras">${c.orders}</td>
            <td data-label="Invertido" class="cell-money">$${c.spent.toLocaleString('es-AR')}</td>
          </tr>
        `).join('');
      }
    }

    // 3. Stock Crítico y Vencimientos
    const stockTbody = document.getElementById('dash-low-stock-tbody');
    if (stockTbody) {
      if (criticalInventory.length === 0) {
        stockTbody.innerHTML = `<tr><td colspan="4" class="cell-empty"><span class="status-badge status-completado">Todo el inventario está bien</span></td></tr>`;
      } else {
        stockTbody.innerHTML = criticalInventory.slice(0, 8).map(it => {
          let alertBadge = '';
          if (it.isExpiring && it.daysToExp < 0) {
            alertBadge = `<span class="dash-badge dash-badge-danger">Vencido (${formatDateAR(it.expStr)})</span>`;
          } else if (it.isExpiring) {
            alertBadge = `<span class="dash-badge dash-badge-warn">Vence en ${it.daysToExp}d</span>`;
          } else {
            alertBadge = `<span class="dash-badge dash-badge-danger">Stock bajo</span>`;
          }
          return `
            <tr>
              <td class="cell-title cell-strong">${it.name}</td>
              <td data-label="Stock actual" class="text-danger">${it.current} ${it.unit}</td>
              <td data-label="Stock mínimo">${it.min} ${it.unit}</td>
              <td data-label="Condición">${alertBadge}</td>
            </tr>
          `;
        }).join('');
      }
    }
  }

  // ================= GANANCIA ESTIMADA (costos y márgenes) =================
  // Costo de un producto = costo de su receta (lo calcula el servidor con el costo por unidad
  // de cada insumo). Ganancia de un pedido = total cobrado − costo de sus productos.
  // Solo se puede calcular en los pedidos donde TODOS los productos tienen receta con costo:
  // las tortas del creador todavía no tienen receta, así que esos pedidos se cuentan aparte.
  function renderMargins(selectedYear) {
    const tbody = document.getElementById('dash-margin-tbody');
    const summary = document.getElementById('dash-margin-summary');
    if (!tbody || !summary) return;

    const fmt = (n) => '$' + Math.round(n).toLocaleString('es-AR');
    const products = dashState.products || [];
    const byId = new Map(products.map(p => [p.id, p]));
    const byName = new Map(products.map(p => [String(p.name).toLowerCase(), p]));
    // Solo cuenta si TODOS los insumos de la receta tienen costo (un costo parcial inflaría la ganancia)
    const costOf = (p) => (p && p.recipe && p.recipe.costComplete) ? p.recipe.estimatedCost : null;

    const soldUnits = {};          // id del producto → unidades vendidas en el año
    let ordersWithCost = 0, ordersTotal = 0, revenueWithCost = 0, costTotal = 0;

    dashState.orders.forEach(o => {
      const status = (o.status || '').toUpperCase();
      if (status !== 'CONFIRMED' && status !== 'COMPLETED') return;
      const year = o.deliveryDate ? parseInt(String(o.deliveryDate).slice(0, 4), 10) : null;
      if (year && year !== selectedYear) return;
      ordersTotal++;

      let orderCost = 0;
      let complete = (o.items || []).length > 0;
      (o.items || []).forEach(it => {
        const product = it.isCustomCake ? null
          : (it.product && byId.get(it.product.id)) || byName.get(String(it.productType || '').toLowerCase());
        if (product) soldUnits[product.id] = (soldUnits[product.id] || 0) + 1;
        const cost = costOf(product);
        if (cost === null) complete = false; else orderCost += cost;
      });
      if (complete) {
        ordersWithCost++;
        revenueWithCost += Number(o.totalPrice || 0);
        costTotal += orderCost;
      }
    });

    // Resumen en palabras
    if (!products.some(p => costOf(p) !== null)) {
      summary.textContent = 'Para ver la ganancia: cargá el costo por unidad de cada insumo (Inventario) y elegí la receta de cada producto (Productos).';
    } else if (ordersWithCost === 0) {
      summary.textContent = `Todavía no hay pedidos en ${selectedYear} con todos sus productos con costo. Abajo, la ganancia por unidad de cada producto.`;
    } else {
      const profit = revenueWithCost - costTotal;
      const pct = revenueWithCost > 0 ? Math.round(profit / revenueWithCost * 100) : 0;
      const others = ordersTotal - ordersWithCost;
      summary.textContent = `Ganancia estimada en ${selectedYear}: ${fmt(profit)} (${pct}% de ${fmt(revenueWithCost)} cobrados).`
        + (others > 0 ? ` Calculada con ${ordersWithCost} de ${ordersTotal} pedidos: los otros ${others} tienen tortas personalizadas o productos sin costo cargado.` : '');
    }

    // Tabla por producto: los más vendidos primero
    const rows = products.slice().sort((a, b) => (soldUnits[b.id] || 0) - (soldUnits[a.id] || 0));
    if (rows.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="cell-empty">Todavía no hay productos cargados.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(p => {
      const cost = costOf(p);
      const price = Number(p.price || 0);
      let costCell, profitCell;
      if (!p.recipe) {
        costCell = '<span class="cell-soft">Sin receta</span>';
        profitCell = '<span class="cell-soft">—</span>';
      } else if (cost === null && p.recipe.estimatedCost > 0) {
        // Algunos insumos tienen costo y otros no: se muestra lo que hay, sin calcular la ganancia
        costCell = `${fmt(p.recipe.estimatedCost)} <span class="cell-soft">(parcial)</span>`;
        profitCell = '<span class="cell-soft">Faltan costos de insumos</span>';
      } else if (cost === null) {
        costCell = '<span class="cell-soft">Insumos sin costo</span>';
        profitCell = '<span class="cell-soft">—</span>';
      } else {
        const profit = price - cost;
        const pct = price > 0 ? Math.round(profit / price * 100) : 0;
        costCell = fmt(cost);
        profitCell = `<span class="${profit < 0 ? 'text-danger' : ''}">${fmt(profit)} (${pct}%)</span>`;
      }
      return `
        <tr>
          <td class="cell-title cell-strong">${p.name}</td>
          <td data-label="Precio" class="cell-money">${fmt(price)}</td>
          <td data-label="Costo de insumos" class="cell-money">${costCell}</td>
          <td data-label="Ganancia por unidad" class="cell-money">${profitCell}</td>
          <td data-label="Vendidos en el año">${soldUnits[p.id] || 0}</td>
        </tr>`;
    }).join('');
  }

  // Colores de los gráficos: salen de las variables de admin.css, así respetan
  // la paleta y el modo oscuro (se leen cada vez que se dibuja)
  function chartTheme() {
    const css = getComputedStyle(document.documentElement);
    const v = (name) => css.getPropertyValue(name).trim();
    return {
      text: v('--admin-text-soft'), line: v('--admin-line'), surface: v('--admin-surface'),
      rose: '#e0487f', roseSoft: 'rgba(224, 72, 127, 0.78)', ink: v('--admin-heading'),
      amber: '#f59e0b', green: '#3f9e6b', cherry: '#cf1810'
    };
  }

  // Muestra "Todavía no hay datos" en lugar de un gráfico vacío
  function toggleEmpty(canvasId, isEmpty) {
    const canvas = document.getElementById(canvasId);
    const msg = document.querySelector(`[data-empty-for="${canvasId}"]`);
    if (canvas) canvas.hidden = isEmpty;
    if (msg) msg.hidden = !isEmpty;
  }

  function renderCharts(monthlyRev, monthlyOrd, statusCounts, productRanking, weekdayCounts) {
    if (typeof Chart === 'undefined') return;

    const th = chartTheme();
    Chart.defaults.font.family = "'Poppins', system-ui, sans-serif";
    Chart.defaults.color = th.text;
    Chart.defaults.borderColor = th.line;

    const monthsLabels = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

    // 1. Gráfico Mixto: Facturación + Cantidad de Pedidos por Mes
    const ctxRev = document.getElementById('chart-revenue-monthly')?.getContext('2d');
    if (ctxRev) {
      if (chartRevenue) chartRevenue.destroy();
      chartRevenue = new Chart(ctxRev, {
        type: 'bar',
        data: {
          labels: monthsLabels,
          datasets: [
            {
              type: 'bar',
              label: 'Facturación ($)',
              data: monthlyRev,
              backgroundColor: th.roseSoft,
              borderColor: th.rose,
              borderWidth: 1,
              borderRadius: 6,
              yAxisID: 'y'
            },
            {
              type: 'line',
              label: 'Pedidos del mes',
              data: monthlyOrd,
              borderColor: th.ink,
              backgroundColor: th.ink,
              borderWidth: 2.5,
              tension: 0.35,
              pointRadius: 4,
              yAxisID: 'y1'
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          scales: {
            y: {
              type: 'linear',
              position: 'left',
              ticks: { callback: v => '$' + Number(v).toLocaleString('es-AR') }
            },
            y1: {
              type: 'linear',
              position: 'right',
              grid: { drawOnChartArea: false },
              ticks: { stepSize: 1, precision: 0 }
            }
          }
        }
      });
    }

    // 2. Gráfico Dona: Distribución de Estados de Pedidos
    const ctxStatus = document.getElementById('chart-orders-status')?.getContext('2d');
    const statusTotal = statusCounts.PENDING + statusCounts.CONFIRMED + statusCounts.COMPLETED + statusCounts.CANCELLED;
    toggleEmpty('chart-orders-status', statusTotal === 0);
    if (ctxStatus) {
      if (chartStatus) chartStatus.destroy();
      chartStatus = new Chart(ctxStatus, {
        type: 'doughnut',
        data: {
          labels: ['Pendientes', 'Agendados', 'Completados', 'Cancelados'],
          datasets: [{
            data: [statusCounts.PENDING, statusCounts.CONFIRMED, statusCounts.COMPLETED, statusCounts.CANCELLED],
            // Mismos colores que las etiquetas de estado de Pedidos
            backgroundColor: [th.amber, th.rose, th.green, th.cherry],
            borderWidth: 2,
            borderColor: th.surface
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '62%',
          plugins: {
            legend: { position: 'bottom' }
          }
        }
      });
    }

    // 3. Gráfico Barras Horizontales: Productos / Tortas más vendidos
    const ctxProd = document.getElementById('chart-top-products')?.getContext('2d');
    if (ctxProd) {
      if (chartProducts) chartProducts.destroy();
      const sortedProds = Object.entries(productRanking).sort((a, b) => b[1] - a[1]).slice(0, 6);
      toggleEmpty('chart-top-products', sortedProds.length === 0);
      const prodLabels = sortedProds.length ? sortedProds.map(p => p[0]) : ['Sin datos aún'];
      const prodValues = sortedProds.length ? sortedProds.map(p => p[1]) : [0];

      chartProducts = new Chart(ctxProd, {
        type: 'bar',
        data: {
          labels: prodLabels,
          datasets: [{
            label: 'Unidades vendidas',
            data: prodValues,
            backgroundColor: th.roseSoft,
            borderRadius: 6
          }]
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { ticks: { stepSize: 1, precision: 0 } }
          }
        }
      });
    }

    // 4. Barras: cuántas entregas caen en cada día de la semana
    //    (antes era un gráfico polar; las barras se leen de un vistazo)
    const ctxDays = document.getElementById('chart-delivery-days')?.getContext('2d');
    toggleEmpty('chart-delivery-days', weekdayCounts.every(n => n === 0));
    if (ctxDays) {
      if (chartDays) chartDays.destroy();
      chartDays = new Chart(ctxDays, {
        type: 'bar',
        data: {
          labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
          datasets: [{
            label: 'Entregas',
            data: weekdayCounts,
            backgroundColor: th.roseSoft,
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            y: { ticks: { stepSize: 1, precision: 0 }, beginAtZero: true }
          }
        }
      });
    }
  }

  // Exportar Reporte Ejecutivo a PDF usando #print-area
  function exportDashboardPDF() {
    const printArea = document.getElementById('print-area');
    if (!printArea || !dashState.summary) return;

    const s = dashState.summary;
    const nowStr = new Date().toLocaleDateString('es-AR', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });

    const revImg = document.getElementById('chart-revenue-monthly')?.toDataURL('image/png') || '';
    const statusImg = document.getElementById('chart-orders-status')?.toDataURL('image/png') || '';

    const topClientsRows = s.topClients.length
      ? s.topClients.map((c, i) => `
          <tr>
            <td style="padding:8px; border-bottom:1px solid #eee;">#${i + 1} - <strong>${c.name}</strong></td>
            <td style="padding:8px; border-bottom:1px solid #eee;">${c.phone}</td>
            <td style="padding:8px; border-bottom:1px solid #eee; text-align:center;">${c.orders}</td>
            <td style="padding:8px; border-bottom:1px solid #eee; text-align:right; font-weight:700; color:#e0487f;">$${c.spent.toLocaleString('es-AR')}</td>
          </tr>`).join('')
      : `<tr><td colspan="4" style="padding:10px; text-align:center;">Sin registros</td></tr>`;

    const stockRows = s.criticalInventory.length
      ? s.criticalInventory.map(it => `
          <tr>
            <td style="padding:8px; border-bottom:1px solid #eee;"><strong>${it.name}</strong></td>
            <td style="padding:8px; border-bottom:1px solid #eee; color:#dc2626; font-weight:700;">${it.current} ${it.unit}</td>
            <td style="padding:8px; border-bottom:1px solid #eee;">${it.min} ${it.unit}</td>
            <td style="padding:8px; border-bottom:1px solid #eee;">${it.expStr ? formatDateAR(it.expStr) : 'No especificado'}</td>
          </tr>`).join('')
      : `<tr><td colspan="4" style="padding:10px; text-align:center; color:#10b981;">Inventario sin alertas críticas</td></tr>`;

    printArea.innerHTML = `
      <div style="font-family:'Poppins', sans-serif; color:#222; padding:28px;">
        <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:3px solid #e0487f; padding-bottom:14px; margin-bottom:20px;">
          <div>
            <h1 style="margin:0; color:#e0487f; font-size:24px;">Dulce Sueños — Reporte Ejecutivo de Gestión</h1>
            <p style="margin:4px 0 0 0; color:#666; font-size:13px;">Balance Analítico Anual (${s.selectedYear}) y Estado Operativo</p>
          </div>
          <div style="text-align:right; font-size:12px; color:#555;">
            <div><strong>Emitido:</strong> ${nowStr}</div>
            <div>Sistema DreamBake</div>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:12px; margin-bottom:22px;">
          <div style="border:1px solid #f3d1dc; background:#fff9fb; padding:12px; border-radius:10px;">
            <div style="font-size:11px; color:#666; text-transform:uppercase;">Total Facturado (${s.selectedYear})</div>
            <div style="font-size:20px; font-weight:800; color:#e0487f;">$${s.totalRevenue.toLocaleString('es-AR')}</div>
            <div style="font-size:11px; color:#555;">Mes actual: $${s.monthRevenue.toLocaleString('es-AR')}</div>
          </div>
          <div style="border:1px solid #e5e7eb; background:#fafafa; padding:12px; border-radius:10px;">
            <div style="font-size:11px; color:#666; text-transform:uppercase;">Saldo Pendiente a Cobrar</div>
            <div style="font-size:20px; font-weight:800; color:#f59e0b;">$${s.pendingBalance.toLocaleString('es-AR')}</div>
            <div style="font-size:11px; color:#555;">Cobrado: $${s.collectedDeposits.toLocaleString('es-AR')}</div>
          </div>
          <div style="border:1px solid #e5e7eb; background:#fafafa; padding:12px; border-radius:10px;">
            <div style="font-size:11px; color:#666; text-transform:uppercase;">Pedidos y Ticket Promedio</div>
            <div style="font-size:20px; font-weight:800; color:#222;">${s.ordersYearCount} pedidos</div>
            <div style="font-size:11px; color:#555;">Ticket prom: $${s.avgTicket.toLocaleString('es-AR')}</div>
          </div>
          <div style="border:1px solid #e5e7eb; background:#fafafa; padding:12px; border-radius:10px;">
            <div style="font-size:11px; color:#666; text-transform:uppercase;">Tortas Elaboradas</div>
            <div style="font-size:20px; font-weight:800; color:#10b981;">${s.cakesSoldCount} u.</div>
            <div style="font-size:11px; color:#555;">Alertas Stock: ${s.criticalInventory.length}</div>
          </div>
        </div>

        <div style="display:grid; grid-template-columns: 1.6fr 1fr; gap:18px; margin-bottom:22px; align-items:center;">
          <div style="border:1px solid #eee; border-radius:10px; padding:12px;">
            <h3 style="margin:0 0 8px 0; font-size:14px; color:#e0487f;">Evolución Mensual de Ingresos y Pedidos</h3>
            ${revImg ? `<img src="${revImg}" style="width:100%; max-height:200px; object-fit:contain;">` : ''}
          </div>
          <div style="border:1px solid #eee; border-radius:10px; padding:12px;">
            <h3 style="margin:0 0 8px 0; font-size:14px; color:#e0487f;">Distribución de Pedidos</h3>
            ${statusImg ? `<img src="${statusImg}" style="width:100%; max-height:200px; object-fit:contain;">` : ''}
          </div>
        </div>

        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:18px;">
          <div>
            <h3 style="font-size:14px; color:#e0487f; border-bottom:1px solid #eee; padding-bottom:6px;">Ranking de Mejores Clientes</h3>
            <table style="width:100%; border-collapse:collapse; font-size:12px;">
              <thead>
                <tr style="background:#fdf2f6; text-align:left;">
                  <th style="padding:8px;">Cliente</th>
                  <th style="padding:8px;">Contacto</th>
                  <th style="padding:8px; text-align:center;">Pedidos</th>
                  <th style="padding:8px; text-align:right;">Total</th>
                </tr>
              </thead>
              <tbody>${topClientsRows}</tbody>
            </table>
          </div>

          <div>
            <h3 style="font-size:14px; color:#e0487f; border-bottom:1px solid #eee; padding-bottom:6px;">Insumos en Estado Crítico / Por Vencer</h3>
            <table style="width:100%; border-collapse:collapse; font-size:12px;">
              <thead>
                <tr style="background:#fdf2f6; text-align:left;">
                  <th style="padding:8px;">Insumo</th>
                  <th style="padding:8px;">Actual</th>
                  <th style="padding:8px;">Mínimo</th>
                  <th style="padding:8px;">Vencimiento</th>
                </tr>
              </thead>
              <tbody>${stockRows}</tbody>
            </table>
          </div>
        </div>
      </div>
    `;

    const oldTitle = document.title;
    document.title = `Reporte_Ejecutivo_DreamBake_${s.selectedYear}`;
    window.print();
    setTimeout(() => {
      document.title = oldTitle;
      printArea.innerHTML = '';
    }, 600);
  }

  function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.innerText = val;
  }

  function formatDateAR(dateStr) {
    if (!dateStr) return '-';
    const parts = String(dateStr).split('T')[0].split('-');
    if (parts.length !== 3) return dateStr;
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }

  yearSelect?.addEventListener('change', () => {
    dashState.selectedYear = parseInt(yearSelect.value, 10);
    computeAndRender();
  });

  btnRefresh?.addEventListener('click', loadDashboardData);
  // Al cambiar entre modo claro y oscuro, los gráficos se vuelven a dibujar con los colores nuevos
  document.addEventListener('themechange', () => { if (dashState.summary) computeAndRender(); });
  btnPdf?.addEventListener('click', exportDashboardPDF);

  // Recargar en tiempo real cada vez que se hace clic en la pestaña "Resumen"
  document.querySelectorAll('.tab-btn[data-tab="resumen"]').forEach(btn => {
    btn.addEventListener('click', loadDashboardData);
  });

  loadDashboardData();
});