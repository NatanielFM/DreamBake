// ui.js — Ayudas comunes de los dos paneles (paso 6.8). Se carga antes que los demás scripts del panel.
//
//   DreamUI.busy(boton, async () => { ... })
//       Deshabilita el botón mientras se envía (con la ruedita de "cargando") y lo vuelve a habilitar al
//       terminar, salga bien o mal. Si ya estaba enviando, no hace nada: así un doble clic no duplica
//       un pedido, un insumo o un cliente.
//   DreamUI.tableLoading(tbody, columnas, texto?)   → fila "Cargando…" mientras llegan los datos
//   DreamUI.tableMessage(tbody, columnas, titulo, detalle?, esError?) → fila de "todavía no hay…" o de error
//   DreamUI.boxLoading(contenedor, texto?) / DreamUI.boxMessage(contenedor, titulo, detalle?, esError?)
//       lo mismo para las grillas de tarjetas (productos, recetas, galería).
// Todo el texto se pone con textContent.

(function () {
  async function busy(button, action) {
    if (!button) return action();
    if (button.dataset.busy === '1') return undefined;   // ya se está enviando: se ignora el segundo clic
    button.dataset.busy = '1';
    const wasDisabled = button.disabled;
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.classList.add('is-busy');
    try {
      return await action();
    } finally {
      delete button.dataset.busy;
      button.disabled = wasDisabled;
      button.removeAttribute('aria-busy');
      button.classList.remove('is-busy');
    }
  }

  function stateNode(tag, title, detail, kind) {
    const el = document.createElement(tag);
    el.className = 'state-msg' + (kind ? ' state-msg--' + kind : '');
    if (kind === 'loading') {
      const spin = document.createElement('span');
      spin.className = 'state-spinner';
      spin.setAttribute('aria-hidden', 'true');
      el.append(spin);
    }
    const strong = document.createElement('strong');
    strong.textContent = title;
    el.append(strong);
    if (detail) {
      const p = document.createElement('span');
      p.textContent = detail;
      el.append(p);
    }
    if (kind === 'loading') el.setAttribute('role', 'status');
    if (kind === 'error') el.setAttribute('role', 'alert');
    return el;
  }

  function tableRow(tbody, cols, node) {
    if (!tbody) return;
    const tr = document.createElement('tr');
    tr.className = 'state-row';
    const td = document.createElement('td');
    td.colSpan = cols || 1;
    td.append(node);
    tr.append(td);
    tbody.replaceChildren(tr);
  }

  const tableLoading = (tbody, cols, text = 'Cargando…') => tableRow(tbody, cols, stateNode('div', text, '', 'loading'));
  const tableMessage = (tbody, cols, title, detail = '', isError = false) =>
    tableRow(tbody, cols, stateNode('div', title, detail, isError ? 'error' : 'empty'));
  const boxLoading = (box, text = 'Cargando…') => box && box.replaceChildren(stateNode('div', text, '', 'loading'));
  const boxMessage = (box, title, detail = '', isError = false) =>
    box && box.replaceChildren(stateNode('div', title, detail, isError ? 'error' : 'empty'));

  window.DreamUI = { busy, tableLoading, tableMessage, boxLoading, boxMessage };
})();
