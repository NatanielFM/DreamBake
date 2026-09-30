/**
 * js/admin/team.js
 * Handles fetching, rendering, creating, updating and deleting staff members with the PASTELERO role.
 */

document.addEventListener('DOMContentLoaded', () => {
  const teamTbody = document.getElementById('team-tbody');
  const btnAddTeamMember = document.getElementById('btn-add-team-member');
  const modalTeam = document.getElementById('modal-team');
  const formTeam = document.getElementById('form-team');
  const teamIdInput = document.getElementById('team-id');

  // Load pasteleros on init
  loadTeam();

  // Show modal to create a new pastelero
  if (btnAddTeamMember) {
    btnAddTeamMember.addEventListener('click', () => {
      formTeam.reset();
      teamIdInput.value = '';
      document.getElementById('modal-team-title').textContent = 'Nuevo Pastelero';
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

      try {
        const response = await fetch('/api/users/pasteleros', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        if (response.ok) {
          if (typeof showNotification === 'function') {
            showNotification('Pastelero guardado exitosamente', 'success');
          } else {
            alert('Guardado exitosamente');
          }
          modalTeam.hidden = true;
          loadTeam();
        } else {
          const err = await response.text();
          alert('Error: ' + err);
        }
      } catch (error) {
        console.error(error);
        alert('Error de conexión');
      }
    });
  }

  // Load and render pasteleros table
  async function loadTeam() {
    try {
      const response = await fetch('/api/users/pasteleros');
      if (response.ok) {
        const team = await response.json();
        renderTeamTable(team);
      }
    } catch (error) {
      console.error('Error cargando equipo:', error);
    }
  }

  // Render rows
  function renderTeamTable(team) {
    if (!teamTbody) return;
    teamTbody.innerHTML = '';
    
    if (team.length === 0) {
      teamTbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: #666;">No hay pasteleros registrados.</td></tr>';
      return;
    }

    team.forEach(member => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${member.name}</td>
        <td>${member.email}</td>
        <td>${member.phone || '-'}</td>
        <td style="text-align: center;">
          <button class="btn btn-grey btn-sm btn-edit" data-id="${member.id}" style="margin-right: 5px;">Editar</button>
          <button class="btn btn-red btn-sm btn-delete" data-id="${member.id}">Eliminar</button>
        </td>
      `;
      teamTbody.appendChild(tr);
    });

    // Attach events for edit/delete buttons
    document.querySelectorAll('.btn-edit').forEach(btn => {
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
          document.getElementById('modal-team-title').textContent = 'Editar Pastelero';
          modalTeam.hidden = false;
        }
      });
    });

    document.querySelectorAll('.btn-delete').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-id');
        if (confirm('¿Estás seguro de que deseas eliminar este pastelero?')) {
          try {
            const response = await fetch('/api/users/' + id, { method: 'DELETE' });
            if (response.ok) {
              if (typeof showNotification === 'function') {
                showNotification('Pastelero eliminado', 'success');
              }
              loadTeam();
            } else {
              alert('Error al eliminar');
            }
          } catch (error) {
            console.error(error);
          }
        }
      });
    });
  }
});
