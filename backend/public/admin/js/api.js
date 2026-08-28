(function () {
  function getToken() {
    return sessionStorage.getItem('convene_admin_token');
  }

  function getRefreshToken() {
    return sessionStorage.getItem('convene_admin_refresh');
  }

  async function handleResponse(response, requestFn, isRetry) {
    if (response.status === 401 && !isRetry) {
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        await API.logout();
        return null;
      }

      const refreshRes = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      });

      if (refreshRes.ok) {
        const refreshData = await refreshRes.json();
        const newToken = refreshData?.data?.token || refreshData?.token || refreshData?.data?.accessToken || refreshData?.accessToken;
        if (newToken) {
          sessionStorage.setItem('convene_admin_token', newToken);
          const retryResponse = await requestFn();
          return handleResponse(retryResponse, requestFn, true);
        }
      }

      await API.logout();
      return null;
    }

    if (response.ok) {
      return await response.json();
    }

    const err = await response.json();
    throw err;
  }

  async function request(method, url, body, isRetry) {
    const requestFn = () => {
      const headers = {};
      const token = getToken();
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }
      if (body) {
        headers['Content-Type'] = 'application/json';
      }

      return fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined
      });
    };

    const response = await requestFn();
    return handleResponse(response, requestFn, isRetry === true);
  }

  const API = {
    async login(email, password) {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      const data = await res.json();
      if (!res.ok || data?.success === false) {
        throw data;
      }

      const token = data?.data?.token || data?.token || data?.data?.accessToken || data?.accessToken;
      const refreshToken = data?.data?.refreshToken || data?.refreshToken;

      if (!token || !refreshToken) {
        throw { success: false, message: 'Token manquant' };
      }

      sessionStorage.setItem('convene_admin_token', token);
      sessionStorage.setItem('convene_admin_refresh', refreshToken);
      
      const user = data?.data?.user || data?.user;
      if (user) {
        sessionStorage.setItem('convene_admin_user', JSON.stringify(user));
      }

      return data;
    },

    async logout() {
      const token = getToken();
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        try {
          await fetch('/api/auth/logout', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              ...(token ? { 'Authorization': `Bearer ${token}` } : {})
            },
            body: JSON.stringify({ refreshToken })
          });
        } catch (_) {
        }
      }
      sessionStorage.removeItem('convene_admin_token');
      sessionStorage.removeItem('convene_admin_refresh');
      sessionStorage.removeItem('convene_admin_user');
      window.location.href = '/admin/index.html';
    },

    isLoggedIn() {
      return !!getToken();
    },

    currentUser() {
      try {
        const stored = sessionStorage.getItem('convene_admin_user');
        if (stored) return JSON.parse(stored);

        const token = getToken();
        if (!token) return null;
        const payload = token.split('.')[1];
        if (!payload) return null;
        const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
        const decoded = JSON.parse(atob(normalized));
        return decoded || null;
      } catch (err) {
        return null;
      }
    },

    get(path, params) {
      const qs = params ? new URLSearchParams(params).toString() : '';
      const url = `/api/admin/${path}${qs ? `?${qs}` : ''}`;
      return request('GET', url, null, false);
    },

    post(path, body) {
      const url = `/api/admin/${path}`;
      return request('POST', url, body, false);
    },

    put(path, body) {
      const url = `/api/admin/${path}`;
      return request('PUT', url, body, false);
    },

    delete(path) {
      const url = `/api/admin/${path}`;
      return request('DELETE', url, null, false);
    }
  };

  window.API = API;
})();
