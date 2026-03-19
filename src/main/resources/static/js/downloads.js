(function() {
    var polling = null;
    var indicator = document.getElementById('dl-indicator');
    var countEl = document.getElementById('dl-count');
    var listEl = document.getElementById('dl-list');
    var panel = document.getElementById('dl-panel');
    var lastRunningIds = new Set();

    function fireToast(message, type) {
        document.dispatchEvent(new CustomEvent('showToast', {detail: {message: message, type: type || 'info'}}));
    }

    function pollTasks() {
        fetch('/api/tasks')
            .then(function(r) { return r.ok ? r.json() : []; })
            .then(function(tasks) {
                var running = tasks.filter(function(t) { return t.status === 'RUNNING'; });
                var runningIds = new Set(running.map(function(t) { return t.id; }));

                // Detect tasks that were running but are now complete/failed
                lastRunningIds.forEach(function(id) {
                    if (!runningIds.has(id)) {
                        var task = tasks.find(function(t) { return t.id === id; });
                        if (task) {
                            var suffix = task.status === 'DONE' ? ' \u2713' : ' \u2717';
                            var toastType = task.status === 'DONE' ? 'success' : 'error';
                            fireToast(task.label + suffix, toastType);
                        }
                    }
                });
                lastRunningIds = runningIds;

                var recent = tasks.filter(function(t) { return t.status !== 'RUNNING'; }).slice(0, 5);
                var all = running.concat(recent);

                if (all.length === 0) {
                    indicator.style.display = 'none';
                    if (polling) { clearInterval(polling); polling = null; }
                    return;
                }

                indicator.style.display = 'block';
                countEl.textContent = running.length;
                countEl.style.display = running.length > 0 ? 'flex' : 'none';

                listEl.innerHTML = all.map(function(t) {
                    var icon = t.status === 'RUNNING' ? 'ri-loader-4-line' :
                                 t.status === 'DONE' ? 'ri-check-line' : 'ri-error-warning-line';
                    var color = t.status === 'RUNNING' ? 'var(--bt-accent)' :
                                  t.status === 'DONE' ? 'var(--bt-text)' : 'var(--bt-danger)';
                    return '<div style="display:flex;align-items:center;gap:0.5rem;padding:0.375rem 0;font-size:0.75rem;color:' + color + ';">' +
                        '<i class="' + icon + '" style="flex-shrink:0;' + (t.status === 'RUNNING' ? 'animation:bt-spin 1s linear infinite;' : '') + '"></i>' +
                        '<span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + t.label + '</span></div>';
                }).join('');

                var progressBar = document.getElementById('global-progress-bar');
                if (progressBar) {
                    if (running.length > 0) {
                        var pct = 10 + (Date.now() / 30 % 80);
                        progressBar.style.width = pct + '%';
                        progressBar.style.opacity = '1';
                    } else {
                        progressBar.style.width = '100%';
                        setTimeout(function() {
                            progressBar.style.width = '0%';
                            progressBar.style.opacity = '0';
                        }, 500);
                    }
                }

                if (running.length > 0 && !polling) {
                    polling = setInterval(pollTasks, 3000);
                } else if (running.length === 0 && polling) {
                    clearInterval(polling);
                    polling = null;
                }
            })
            .catch(function() {});
    }

    window.toggleDlPanel = function() {
        panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
    };

    document.addEventListener('click', function(e) {
        var ind = document.getElementById('dl-indicator');
        if (ind && !ind.contains(e.target)) {
            panel.style.display = 'none';
        }
    });

    // Initial check, then poll if active
    pollTasks();
    // Also check periodically in case new tasks start
    setInterval(pollTasks, 10000);
})();
