const fs = require('fs');

let content = fs.readFileSync('src/main/resources/templates/baker.html', 'utf8');

// 1. Remove unwanted sidebar tabs
const tabsToRemove = ['resumen', 'productos', 'clientes', 'galeria', 'armador', 'equipo'];
for (const tab of tabsToRemove) {
    const regex = new RegExp(`<button class="tab-btn(?: active)?" data-tab="${tab}">.*?</button>`, 'g');
    content = content.replace(regex, '');
}

// 2. Make 'pedidos' active tab by default
content = content.replace('<button class="tab-btn" data-tab="pedidos">', '<button class="tab-btn active" data-tab="pedidos">');

// 3. Remove sections entirely from DOM
for (const tab of tabsToRemove) {
    const regex = new RegExp(`<section id="${tab}".*?</section>`, 'gs');
    content = content.replace(regex, '');
}

// Make 'pedidos' section active
content = content.replace('<section id="pedidos" class="tab-content">', '<section id="pedidos" class="tab-content active">');

// 4. Remove scripts entirely
const scriptsToRemove = ['clients.js', 'gallery.js', 'products.js', 'cake-config.js', 'dashboard.js', 'team.js'];
for (const script of scriptsToRemove) {
    const regex = new RegExp(`<script th:src="@\\{/js/admin/${script.replace('.', '\\.')}\\}" defer></script>\\s*`, 'g');
    content = content.replace(regex, '');
}

const notifRegex = new RegExp(`<script th:src="@\\{/js/admin/notifications-simple\\.js\\}" defer></script>\\s*`, 'g');
content = content.replace(notifRegex, '');

// 5. Hide or strip admin UI elements from the 3 allowed tabs (Pedidos, Inventario, Recetas)
content = content.replace(/<button class="btn btn-grey btn-uniform" id="btn-test-order">.*?<\/button>/gs, '');
content = content.replace(/<button class="btn btn-pink btn-uniform" onclick="openManualOrderModal\(\)">.*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-test-deduct".*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-generate-order".*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-add-inventory".*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-add-recipe".*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-new-size".*?<\/button>/gs, '');
content = content.replace(/<button type="button" id="btn-new-option".*?<\/button>/gs, '');

const styleBlock = `
  <style>
    /* Hide admin financial data and actions for bakers */
    #pedidos table.admin-table th:nth-child(5),
    #pedidos table.admin-table td:nth-child(5),
    #btn-enable-edit,
    #btn-cancel-view-order,
    .btn-delete,
    .btn-edit,
    button.btn-red,
    .receipt-financials,
    #mo-total,
    #mo-deposit,
    #co-deposit-group,
    #co-payment-type,
    .order-details-box:nth-child(2) {
      display: none !important;
    }
  </style>
`;
content = content.replace('</head>', `${styleBlock}\n</head>`);

// Replace title
content = content.replace('<title>Administración — Dulce Sueños</title>', '<title>Pastelero — Dulce Sueños</title>');

fs.writeFileSync('src/main/resources/templates/baker.html', content, 'utf8');
