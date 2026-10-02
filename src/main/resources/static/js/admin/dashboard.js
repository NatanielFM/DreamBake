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
    setText('kpi-collected-deposits', 'Cobrado/Señas: $' + collectedDeposits.toLocaleString('es-AR'));
    setText('kpi-orders-month', ordersMonthCount);
    setText('kpi-orders-year', `Total en ${selectedYear}: ${ordersYearCount} pedidos`);
    setText('kpi-cakes-sold', cakesSoldCount);
    setText('kpi-avg-ticket', 'Ticket Promedio: $' + avgTicket.toLocaleString('es-AR'));

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
        upTbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:#888; padding:18px;">No hay entregas próximas agendadas.</td></tr>`;
      } else {
        upTbody.innerHTML = upcomingDeliveries.slice(0, 6).map(o => {
          const badge = o.status === 'CONFIRMED'
            ? `<span class="dash-badge dash-badge-ok">Agendado</span>`
            : `<span class="dash-badge dash-badge-warn">Pendiente</span>`;
          return `
            <tr>
              <td><strong>#${o.id}</strong> - ${o.customerName}</td>
              <td>${formatDateAR(o.deliveryDate)}</td>
              <td style="font-weight:700; color:var(--accent);">$${o.totalPrice.toLocaleString('es-AR')}</td>
              <td style="text-align:center;">${badge}</td>
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
        cliTbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:#888; padding:18px;">Aún no hay clientes con compras confirmadas.</td></tr>`;
      } else {
        cliTbody.innerHTML = topClients.map((c, idx) => `
          <tr>
            <td><span class="dash-rank-num">${idx + 1}</span> <strong>${c.name}</strong></td>
            <td>${c.phone}</td>
            <td style="text-align:center;">${c.orders}</td>
            <td style="font-weight:700; color:var(--accent);">$${c.spent.toLocaleString('es-AR')}</td>
          </tr>
        `).join('');
      }
    }

    // 3. Stock Crítico y Vencimientos
    const stockTbody = document.getElementById('dash-low-stock-tbody');
    if (stockTbody) {
      if (criticalInventory.length === 0) {
        stockTbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:#10b981; font-weight:600; padding:18px;">✓ Todo el inventario está en niveles óptimos.</td></tr>`;
      } else {
        stockTbody.innerHTML = criticalInventory.slice(0, 8).map(it => {
          let alertBadge = '';
          if (it.isExpiring && it.daysToExp < 0) {
            alertBadge = `<span class="dash-badge dash-badge-danger">Vencido (${formatDateAR(it.expStr)})</span>`;
          } else if (it.isExpiring) {
            alertBadge = `<span class="dash-badge dash-badge-warn">Vence en ${it.daysToExp}d</span>`;
          } else {
            alertBadge = `<span class="dash-badge dash-badge-danger">Stock Bajo</span>`;
          }
          return `
            <tr>
              <td><strong>${it.name}</strong></td>
              <td style="color:#dc2626; font-weight:700;">${it.current} ${it.unit}</td>
              <td>${it.min} ${it.unit}</td>
              <td style="text-align:center;">${alertBadge}</td>
            </tr>
          `;
        }).join('');
      }
    }
  }

  function renderCharts(monthlyRev, monthlyOrd, statusCounts, productRanking, weekdayCounts) {
    if (typeof Chart === 'undefined') return;

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
              backgroundColor: 'rgba(224, 72, 127, 0.78)',
              borderColor: '#e0487f',
              borderWidth: 1,
              borderRadius: 6,
              yAxisID: 'y'
            },
            {
              type: 'line',
              label: 'Pedidos del Mes',
              data: monthlyOrd,
              borderColor: '#7c3aed',
              backgroundColor: '#7c3aed',
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
    if (ctxStatus) {
      if (chartStatus) chartStatus.destroy();
      chartStatus = new Chart(ctxStatus, {
        type: 'doughnut',
        data: {
          labels: ['Nuevos (Pendientes)', 'Agendados', 'Completados', 'Cancelados'],
          datasets: [{
            data: [statusCounts.PENDING, statusCounts.CONFIRMED, statusCounts.COMPLETED, statusCounts.CANCELLED],
            backgroundColor: ['#f59e0b', '#3b82f6', '#10b981', '#ef4444'],
            borderWidth: 2,
            borderColor: '#ffffff'
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
      const prodLabels = sortedProds.length ? sortedProds.map(p => p[0]) : ['Sin datos aún'];
      const prodValues = sortedProds.length ? sortedProds.map(p => p[1]) : [0];

      chartProducts = new Chart(ctxProd, {
        type: 'bar',
        data: {
          labels: prodLabels,
          datasets: [{
            label: 'Unidades Vendidas',
            data: prodValues,
            backgroundColor: [
              '#e0487f', '#ec6b98', '#f48fb1', '#b388ff', '#81d4fa', '#a5d6a7'
            ],
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

    // 4. Gráfico Polar: Demanda por Día de Entrega
    const ctxDays = document.getElementById('chart-delivery-days')?.getContext('2d');
    if (ctxDays) {
      if (chartDays) chartDays.destroy();
      chartDays = new Chart(ctxDays, {
        type: 'polarArea',
        data: {
          labels: ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'],
          datasets: [{
            data: weekdayCounts,
            backgroundColor: [
              'rgba(224, 72, 127, 0.75)',
              'rgba(245, 158, 11, 0.75)',
              'rgba(16, 185, 129, 0.75)',
              'rgba(59, 130, 246, 0.75)',
              'rgba(139, 92, 246, 0.75)',
              'rgba(236, 72, 153, 0.85)',
              'rgba(244, 63, 94, 0.85)'
            ]
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { position: 'right' }
          },
          scales: {
            r: { ticks: { stepSize: 1, precision: 0 } }
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
  btnPdf?.addEventListener('click', exportDashboardPDF);

  // Recargar en tiempo real cada vez que se hace clic en la pestaña "Resumen"
  document.querySelectorAll('.tab-btn[data-tab="resumen"]').forEach(btn => {
    btn.addEventListener('click', loadDashboardData);
  });

  loadDashboardData();
});