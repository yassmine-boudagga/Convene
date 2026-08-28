(function () {
  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  const UI = {
    toast(message, type = 'info') {
      const existing = document.querySelector('.toast');
      if (existing) existing.remove();

      const toast = document.createElement('div');
      toast.className = 'toast';
      toast.textContent = message;

      if (type === 'success') {
        toast.style.background = '#4ABE4F';
      } else if (type === 'error') {
        toast.style.background = '#E0533E';
      } else {
        toast.style.background = '#FFAC81';
        toast.style.color = '#111111';
      }

      document.body.appendChild(toast);
      setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
      }, 3000);
    },

    modal({ title, bodyHTML, confirmLabel = 'Confirmer', dangerConfirm = false, hideCancel = false, onConfirm }) {
      const overlay = document.createElement('div');
      overlay.className = 'modal-overlay';

      const dialog = document.createElement('div');
      dialog.className = 'modal-dialog';

      const titleEl = document.createElement('div');
      titleEl.className = 'modal-title';
      titleEl.textContent = title || '';

      const bodyEl = document.createElement('div');
      bodyEl.className = 'modal-body';
      bodyEl.innerHTML = bodyHTML || '';

      const actions = document.createElement('div');
      actions.className = 'modal-actions';

      function close() {
        overlay.remove();
      }

      if (!hideCancel) {
        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'btn btn-secondary';
        cancelBtn.textContent = 'Annuler';
        cancelBtn.addEventListener('click', close);
        actions.appendChild(cancelBtn);
      }

      const confirmBtn = document.createElement('button');
      confirmBtn.className = dangerConfirm ? 'btn btn-danger' : 'btn btn-primary';
      confirmBtn.textContent = confirmLabel;

      actions.appendChild(confirmBtn);

      dialog.appendChild(titleEl);
      dialog.appendChild(bodyEl);
      dialog.appendChild(actions);
      overlay.appendChild(dialog);
      document.body.appendChild(overlay);

      overlay.addEventListener('click', (event) => {
        if (event.target === overlay) close();
      });

      confirmBtn.addEventListener('click', async () => {
        confirmBtn.disabled = true;
        const spinner = document.createElement('span');
        spinner.className = 'spinner';
        confirmBtn.prepend(spinner);
        try {
          if (typeof onConfirm === 'function') {
            await onConfirm();
          }
        } finally {
          close();
        }
      });

      return { close };
    },

    renderTable(containerId, columns, rows, actions = []) {
      const container = document.getElementById(containerId);
      if (!container) return;

      container.innerHTML = '';

      if (!rows || rows.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'empty-state';
        empty.textContent = 'Aucun résultat';
        container.appendChild(empty);
        return;
      }

      const wrapper = document.createElement('div');
      wrapper.className = 'table-wrapper';

      const table = document.createElement('table');
      const thead = document.createElement('thead');
      const headRow = document.createElement('tr');

      columns.forEach(col => {
        const th = document.createElement('th');
        th.textContent = col.label;
        headRow.appendChild(th);
      });

      if (actions.length > 0) {
        const th = document.createElement('th');
        th.textContent = 'Actions';
        headRow.appendChild(th);
      }

      thead.appendChild(headRow);
      table.appendChild(thead);

      const tbody = document.createElement('tbody');
      rows.forEach(row => {
        const tr = document.createElement('tr');
        columns.forEach(col => {
          const td = document.createElement('td');
          const value = row[col.key];
          if (typeof col.render === 'function') {
            td.innerHTML = col.render(value, row);
          } else {
            td.textContent = value ?? '';
          }
          tr.appendChild(td);
        });

        if (actions.length > 0) {
          const actionTd = document.createElement('td');
          actions.forEach(action => {
            const btn = document.createElement('button');
            btn.className = `btn ${action.class || ''}`.trim();
            btn.textContent = action.label;
            btn.addEventListener('click', () => action.onClick(row));
            actionTd.appendChild(btn);
          });
          tr.appendChild(actionTd);
        }

        tbody.appendChild(tr);
      });

      table.appendChild(tbody);
      wrapper.appendChild(table);
      container.appendChild(wrapper);
    },

    renderPagination(containerId, pagination, onPageChange) {
      const container = document.getElementById(containerId);
      if (!container) return;

      container.innerHTML = '';
      if (!pagination || pagination.pages <= 1) return;

      const info = document.createElement('div');
      info.className = 'pagination-info';
      info.textContent = `Page ${pagination.page} sur ${pagination.pages} — ${pagination.total} résultat(s)`;
      container.appendChild(info);

      const wrapper = document.createElement('div');
      wrapper.className = 'pagination';

      const prevBtn = document.createElement('button');
      prevBtn.innerHTML = '&laquo;';
      prevBtn.disabled = pagination.page === 1;
      prevBtn.addEventListener('click', () => onPageChange(pagination.page - 1));
      wrapper.appendChild(prevBtn);

      const pages = pagination.pages;
      const current = pagination.page;

      let start = Math.max(1, current - 2);
      let end = Math.min(pages, current + 2);

      if (start > 1) {
        const firstBtn = document.createElement('button');
        firstBtn.textContent = '1';
        firstBtn.addEventListener('click', () => onPageChange(1));
        wrapper.appendChild(firstBtn);
        if (start > 2) {
          const dots = document.createElement('span');
          dots.textContent = '...';
          wrapper.appendChild(dots);
        }
      }

      for (let i = start; i <= end; i++) {
        const btn = document.createElement('button');
        btn.textContent = String(i);
        if (i === current) btn.className = 'active';
        btn.addEventListener('click', () => onPageChange(i));
        wrapper.appendChild(btn);
      }

      if (end < pages) {
        if (end < pages - 1) {
          const dots = document.createElement('span');
          dots.textContent = '...';
          wrapper.appendChild(dots);
        }
        const lastBtn = document.createElement('button');
        lastBtn.textContent = String(pages);
        lastBtn.addEventListener('click', () => onPageChange(pages));
        wrapper.appendChild(lastBtn);
      }

      const nextBtn = document.createElement('button');
      nextBtn.innerHTML = '&raquo;';
      nextBtn.disabled = pagination.page === pagination.pages;
      nextBtn.addEventListener('click', () => onPageChange(pagination.page + 1));
      wrapper.appendChild(nextBtn);

      container.appendChild(wrapper);
    },

    renderBadge(value, colorMap = {}) {
      const color = colorMap[value] || '#9CA3AF';
      return `<span class="badge" style="background:${color}33;color:${color}">${escapeHtml(value)}</span>`;
    },

    showLoading(containerId) {
      const container = document.getElementById(containerId);
      if (!container) return;
      container.innerHTML = '<div style="display:flex;align-items:center;gap:8px"><span class="spinner"></span>Chargement...</div>';
    },

    hideLoading(containerId) {
      const container = document.getElementById(containerId);
      if (!container) return;
      container.innerHTML = '';
    }
  };

  window.UI = UI;
})();