$content = Get-Content 'src\main\resources\templates\baker.html' -Raw -Encoding UTF8

$content = $content -replace '<title>Administración — Dulce Sueños</title>', '<title>Pastelero — Dulce Sueños</title>'

$tabsToRemove = @('resumen', 'productos', 'clientes', 'galeria', 'armador', 'equipo')
foreach ($tab in $tabsToRemove) {
    $content = $content -replace "<button class=`"tab-btn(?: active)?`" data-tab=`"$tab`">.*?</button>", ""
}

$content = $content -replace '<button class="tab-btn" data-tab="pedidos">', '<button class="tab-btn active" data-tab="pedidos">'

foreach ($tab in $tabsToRemove) {
    $content = $content -replace "(?s)<section id=`"$tab`".*?</section>", ""
}

$content = $content -replace '<section id="pedidos" class="tab-content">', '<section id="pedidos" class="tab-content active">'

$scriptsToRemove = @('clients.js', 'gallery.js', 'products.js', 'cake-config.js', 'dashboard.js', 'team.js')
foreach ($script in $scriptsToRemove) {
    $scriptRegex = $script -replace "\.", "\."
    $content = $content -replace "(?s)<script th:src=`"@\{/js/admin/$scriptRegex\}`" defer></script>\s*", ""
}
$content = $content -replace "(?s)<script th:src=`"@\{/js/admin/notifications-simple\.js\}`" defer></script>\s*", ""

$content = $content -replace '(?s)<button class="btn btn-grey btn-uniform" id="btn-test-order">.*?</button>', ''
$content = $content -replace '(?s)<button class="btn btn-pink btn-uniform" onclick="openManualOrderModal\(\)">.*?</button>', ''
$content = $content -replace '(?s)<button type="button" id="btn-test-deduct".*?</button>', ''
$content = $content -replace '(?s)<button type="button" id="btn-generate-order".*?</button>', ''
$content = $content -replace '(?s)<button type="button" id="btn-add-inventory".*?</button>', ''
$content = $content -replace '(?s)<button type="button" id="btn-add-recipe".*?</button>', ''

$styleBlock = @"
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
</head>
"@

$content = $content -replace '</head>', $styleBlock

Set-Content 'src\main\resources\templates\baker.html' $content -Encoding UTF8
