(function () {
  const viewMap = {
    '#dashboard': { name: 'dashboard', title: 'Dashboard', render: 'renderDashboard' },
    '#users': { name: 'users', title: 'Utilisateurs', render: 'renderUsers' },
    '#meetings': { name: 'meetings', title: 'Reunions', render: 'renderMeetings' },
    '#recordings': { name: 'recordings', title: 'Enregistrements', render: 'renderRecordings' },
    '#tasks': { name: 'tasks', title: 'Taches', render: 'renderTasks' },
    '#notifications': { name: 'notifications', title: 'Notifications', render: 'renderNotifications' },
    '#ai-results': { name: 'ai-results', title: 'Pipeline IA', render: 'renderAIResults' },
    '#maintenance': { name: 'maintenance', title: 'Maintenance', render: 'renderMaintenance' },
    '#system': { name: 'system', title: 'Logs systeme', render: 'renderSystem' }
  };

  function setActiveNav(viewName) {
    document.querySelectorAll('.nav-item').forEach(item => {
      const itemView = item.getAttribute('data-view');
      if (itemView === viewName) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });
  }

  const Router = {
    init() {
      window.addEventListener('hashchange', Router.navigate);
      Router.navigate();
    },

    navigate() {
      if (!window.API || !API.isLoggedIn()) {
        window.location.href = '/admin/index.html';
        return;
      }

      const hash = window.location.hash || '#dashboard';
      const view = viewMap[hash] || viewMap['#dashboard'];

      setActiveNav(view.name);

      const titleEl = document.getElementById('page-title');
      if (titleEl) {
        titleEl.textContent = view.title;
      }

      const renderFn = window[view.render];
      if (typeof renderFn === 'function') {
        renderFn();
      }
    }
  };

  window.Router = Router;
})();
