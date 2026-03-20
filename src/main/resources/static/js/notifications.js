(function () {
    var POLL_INTERVAL = 30000;
    var knownCount = 0;
    var pollTimer = null;
    var sse = null;

    // Read i18n strings from the script tag's data attributes
    var scriptEl = document.querySelector('script[data-i18n-no-notifications]');
    var i18nNoNotifications = scriptEl ? scriptEl.getAttribute('data-i18n-no-notifications') : 'No notifications';
    var i18nNewNotification = scriptEl ? scriptEl.getAttribute('data-i18n-new-notification') : 'new notification';
    var i18nNewNotifications = scriptEl ? scriptEl.getAttribute('data-i18n-new-notifications') : 'new notifications';

    // ── Badge ────────────────────────────────────────────────────────────
    function setBadge(count) {
        var badge = document.getElementById('notif-badge');
        if (!badge) return;
        knownCount = count;
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.style.display = '';
        } else {
            badge.style.display = 'none';
        }
    }

    // ── Panel ────────────────────────────────────────────────────────────
    window.toggleNotifPanel = function () {
        var panel = document.getElementById('notif-panel');
        if (!panel) return;
        if (panel.style.display === 'none') {
            loadNotifications();
            panel.style.display = '';
        } else {
            panel.style.display = 'none';
        }
    };
    document.addEventListener('click', function (e) {
        var panel = document.getElementById('notif-panel');
        var btn   = document.getElementById('notif-btn');
        if (panel && btn && !panel.contains(e.target) && !btn.contains(e.target)) {
            panel.style.display = 'none';
        }
    });

    function loadNotifications() {
        fetch('/api/notifications?unread=true', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (items) {
                renderList(items);
                setBadge(items.length);
            })
            .catch(function () {});
    }

    function renderList(items) {
        var list = document.getElementById('notif-list');
        if (!list) return;
        list.textContent = '';
        if (items.length === 0) {
            var empty = document.createElement('p');
            empty.style.cssText = 'font-size:0.78rem;color:var(--bt-muted);text-align:center;padding:1rem;';
            empty.textContent = i18nNoNotifications;
            list.appendChild(empty);
            return;
        }
        items.forEach(function (n) {
            var div = document.createElement('div');
            div.style.cssText = 'padding:0.5rem 0.625rem;border-radius:0.4rem;cursor:pointer;transition:background 0.12s;';
            div.addEventListener('mouseover', function() { this.style.background = 'var(--bt-bg)'; });
            div.addEventListener('mouseout', function() { this.style.background = ''; });
            div.addEventListener('click', function() { dismissNotif(n.id, this); });
            var title = document.createElement('span');
            title.style.cssText = 'font-size:0.78rem;color:var(--bt-text);font-weight:500;';
            title.textContent = n.title || '';
            div.appendChild(title);
            if (n.body) {
                var body = document.createElement('span');
                body.style.cssText = 'display:block;font-size:0.72rem;color:var(--bt-muted);margin-top:0.15rem;';
                body.textContent = n.body;
                div.appendChild(body);
            }
            list.appendChild(div);
        });
    }

    window.dismissNotif = function (id, el) {
        fetch('/api/notifications/' + id + '/read', { method: 'POST', credentials: 'same-origin' })
            .then(function () { if (el) el.remove(); setBadge(Math.max(0, knownCount - 1)); })
            .catch(function () {});
    };

    window.markAllRead = function () {
        fetch('/api/notifications/read-all', { method: 'POST', credentials: 'same-origin' })
            .then(function () {
                setBadge(0);
                var list = document.getElementById('notif-list');
                if (list) { list.textContent = ''; var p = document.createElement('p'); p.style.cssText = 'font-size:0.78rem;color:var(--bt-muted);text-align:center;padding:1rem;'; p.textContent = i18nNoNotifications; list.appendChild(p); }
            })
            .catch(function () {});
    };

    // ── SSE + polling fallback ───────────────────────────────────────────
    function onNotification(data) {
        try {
            var n = JSON.parse(data);
            setBadge(knownCount + 1);
            window.showToast(n.title || ('1 ' + i18nNewNotification), 'info');
        } catch (e) {}
    }

    function startPolling() {
        if (pollTimer) return;
        pollTimer = setInterval(function () {
            fetch('/api/notifications/count', { credentials: 'same-origin' })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (d) {
                    if (!d) return;
                    if (d.count > knownCount) {
                        var delta = d.count - knownCount;
                        setBadge(d.count);
                        window.showToast(delta === 1 ? '1 ' + i18nNewNotification : delta + ' ' + i18nNewNotifications, 'info');
                    } else {
                        setBadge(d.count);
                    }
                })
                .catch(function () {});
        }, POLL_INTERVAL);
    }

    function startSSE() {
        if (!window.EventSource) { startPolling(); return; }
        sse = new EventSource('/api/notifications/stream');
        sse.addEventListener('notification', function (e) { onNotification(e.data); });
        sse.addEventListener('heartbeat', function () {});
        sse.onerror = function () {
            sse.close();
            sse = null;
            // EventSource auto-reconnects but also start polling as backup
            startPolling();
            // Try re-opening SSE after POLL_INTERVAL
            setTimeout(function () {
                if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
                startSSE();
            }, POLL_INTERVAL);
        };
    }

    // Initial count fetch + start real-time transport
    fetch('/api/notifications/count', { credentials: 'same-origin' })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (d) { if (d) setBadge(d.count); })
        .catch(function () {});

    startSSE();
})();
