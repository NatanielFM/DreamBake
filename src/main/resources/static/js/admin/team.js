/**
 * js/admin/team.js
 * Handles fetching, rendering, creating, updating and deleting staff members with the PASTELERO role.
 */

document.addEventListener('DOMContentLoaded', () => {
  const teamTbody = document.getElementById('team-tbody');
  // Paso 6.8: nombres escapados
  const esc = (t) => String(t ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const btnAddTeamMember = document.getElementById('btn-add-team-member');
  const modalTeam = document.getElementById('modal-team');
  const formTeam = document.getElementById('form-team');
  const teamIdInput = document.getElementById('team-id');

  // ¿Ya se mostró la lista alguna vez? (va antes de la primera carga: con let, usarla antes de declararla da error)
  let teamLoaded = false;

  // Load pasteleros on init
  loadTeam();

  // Show modal to create a new pastelero
  if (btnAddTeamMember) {
    btnAddTeamMember.addEventListener('click', () => {
      formTeam.reset();
      teamIdInput.value = '';
      document.getElementById('modal-team-title').textContent = 'Agregar pastelero';
      document.getElementById('team-password').setAttribute('required', 'true');
      modalTeam.hidden = false;
    });
  }

  // Handle form submit for create/update
  if (formTeam) {
    formTeam.addEventListener('submit', async (e) => {
      e.preventDefault();

      const id = teamIdInput.value;
      const name = document.getElementById('team-name').value;
      const email = document.getElementById('team-email').value;
      const phone = document.getElementById('team-phone').value;
      const password = document.getElementById('team-password').value;

      const payload = {
        name,
        email,
        phone,
        password,
        enabled: true
      };

      if (id) {
        payload.id = id;
      }

      // Botón bloqueado mientras se guarda (un doble clic creaba dos cuentas o daba error de email repetido)
      await window.DreamUI.busy(e.submitter || formTeam.querySelector('button[type="submit"]'), async () => {
        try {
          const response = await fetch('/api/users/pasteleros', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          });
          // Avisos con las ventanas del panel (no con alert() del navegador)
          if (response.ok) {
            modalTeam.hidden = true;
            loadTeam();
          } else {
            const text = await response.text();
            let message = text;
            try { const data = JSON.parse(text); message = data.message || Object.values(data).join(' '); } catch (err) { /* texto simple */ }
            window.showErrorAlert(message || 'No se pudo guardar al pastelero.');
          }
        } catch (error) {
          window.DreamUI.offline();
        }
      });
    });
  }

  // Load and render pasteleros table
  async function loadTeam() {
    if (!teamLoaded) window.DreamUI?.tableLoading(teamTbody, 4, 'Cargando el equipo…');
    try {
      const response = await fetch('/api/users/pasteleros');
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const team = await response.json();
      teamLoaded = true;
      renderTeamTable(team);
    } catch (error) {
      if (!teamLoaded) window.DreamUI?.tableMessage(teamTbody, 4, 'No se pudo cargar el equipo.', 'Revisá la conexión y recargá la página.', true);
    }
  }

  // Render rows
  function renderTeamTable(team) {
    if (!teamTbody) return;
    teamTbody.innerHTML = '';

    if (team.length === 0) {
      teamTbody.innerHTML = '<tr><td colspan="4" class="cell-empty">Todavía no hay pasteleros. Agregá uno para que pueda entrar al panel de la cocina.</td></tr>';
      return;
    }

    team.forEach(member => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="cell-title cell-strong">${esc(member.name)}</td>
        <td data-label="Email">${esc(member.email)}</td>
        <td data-label="Teléfono">${esc(member.phone || '—')}</td>
        <td class="cell-actions"><div class="action-buttons-container">
          <button type="button" class="btn btn-grey btn-sm btn-edit" data-id="${member.id}">Editar</button>
          <button type="button" class="btn btn-red btn-sm btn-delete" data-id="${member.id}">Eliminar</button>
        </div></td>
      `;
      teamTbody.appendChild(tr);
    });

    // Attach events for edit/delete buttons
    // Solo los botones de la tabla de Equipo (antes usaba '.btn-edit' de todo el panel)
    teamTbody.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        const member = team.find(m => m.id == id);
        if (member) {
          teamIdInput.value = member.id;
          document.getElementById('team-name').value = member.name;
          document.getElementById('team-email').value = member.email;
          document.getElementById('team-phone').value = member.phone || '';
          document.getElementById('team-password').value = ''; // Leave empty
          document.getElementById('team-password').removeAttribute('required'); // Not required on edit
          document.getElementById('modal-team-title').textContent = 'Editar pastelero';
          modalTeam.hidden = false;
        }
      });
    });

    teamTbody.querySelectorAll('.btn-delete').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        const member = team.find(m => m.id == id);
        // Confirmación con la ventana del panel (no la del navegador)
        window.showConfirmModal('Eliminar pastelero', `¿Eliminar a ${member ? member.name : 'este pastelero'}? Ya no va a poder entrar al panel.`, 'Eliminar', 'btn-red', async () => {
          try {
            const response = await fetch('/api/users/' + id, { method: 'DELETE' });
            if (response.ok) {
              loadTeam();
            } else {
              await window.DreamUI.fail(response, 'No se pudo eliminar al pastelero.');
            }
          } catch (error) {
            window.DreamUI.offline();
          }
        });
      });
    });
  }
});
