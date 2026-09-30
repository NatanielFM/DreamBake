import re

with open('src/main/resources/templates/baker.html', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove unwanted sidebar tabs
tabs_to_remove = ['resumen', 'productos', 'clientes', 'galeria', 'armador', 'equipo']
for tab in tabs_to_remove:
    content = re.sub(rf'<button class="tab-btn(?: active)?" data-tab="{tab}">.*?</button>', '', content)

# 2. Make 'pedidos' active tab by default
content = content.replace('<button class="tab-btn" data-tab="pedidos">', '<button class="tab-btn active" data-tab="pedidos">')

# 3. Remove sections entirely from DOM
for tab in tabs_to_remove:
    content = re.sub(rf'<section id="{tab}".*?</section>', '', content, flags=re.DOTALL)

# Make 'pedidos' section active
content = content.replace('<section id="pedidos" class="tab-content">', '<section id="pedidos" class="tab-content active">')

# 4. Remove scripts entirely
scripts_to_remove = ['clients.js', 'gallery.js', 'products.js', 'cake-config.js', 'dashboard.js', 'team.js']
for script in scripts_to_remove:
    content = re.sub(rf'<script th:src="@{{/js/admin/{script}}}" defer></script>\s*', '', content)

# Remove notifications js for bakers if we want, or keep it. Let's remove it if it relies on admin endpoints.
content = re.sub(r'<script th:src="@\{/js/admin/notifications-simple\.js\}" defer></script>\s*', '', content)

# 5. Hide or strip admin UI elements from the 3 allowed tabs (Pedidos, Inventario, Recetas)
content = re.sub(r'<button class="btn btn-grey btn-uniform" id="btn-test-order">.*?</button>', '', content, flags=re.DOTALL)
content = re.sub(r'<button class="btn btn-pink btn-uniform" onclick="openManualOrderModal\(\)">.*?</button>', '', content, flags=re.DOTALL)

content = re.sub(r'<button type="button" id="btn-test-deduct".*?</button>', '', content, flags=re.DOTALL)
content = re.sub(r'<button type="button" id="btn-generate-order".*?</button>', '', content, flags=re.DOTALL)
content = re.sub(r'<button type="button" id="btn-add-inventory".*?</button>', '', content, flags=re.DOTALL)

content = re.sub(r'<button type="button" id="btn-add-recipe".*?</button>', '', content, flags=re.DOTALL)
content = re.sub(r'<button type="button" id="btn-new-size".*?</button>', '', content, flags=re.DOTALL)
content = re.sub(r'<button type="button" id="btn-new-option".*?</button>', '', content, flags=re.DOTALL)

style_block = """
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
"""
content = content.replace('</head>', f'{style_block}\n</head>')

# Replace title
content = content.replace('<title>Administración — Dulce Sueños</title>', '<title>Pastelero — Dulce Sueños</title>')

with open('src/main/resources/templates/baker.html', 'w', encoding='utf-8') as f:
    f.write(content)
