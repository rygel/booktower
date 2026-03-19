(function () {
    function showToast(message, type) {
        var container = document.getElementById('toast-container');
        if (!container) return;
        var toast = document.createElement('div');
        toast.className = 'bt-toast ' + (type || 'info');
        var icon = type === 'success' ? 'ri-checkbox-circle-line'
                 : type === 'error'   ? 'ri-error-warning-line'
                 :                      'ri-information-line';
        var safe = message.replace(/&/g, '&amp;').replace(/</g, '&lt;');
        toast.innerHTML = '<i class="' + icon + '" style="font-size:1rem;"></i><span>' + safe + '</span>';
        container.appendChild(toast);
        setTimeout(function () { toast.remove(); }, 3500);
    }

    // Expose globally for HTMX event listeners and other scripts
    window.showToast = showToast;

    // HTMX: HX-Trigger fires a custom DOM event named by the key
    document.addEventListener('showToast', function (e) {
        var d = e.detail;
        if (typeof d === 'string') showToast(d, 'info');
        else showToast(d.message || '', d.type || 'info');
    });

    // Flash cookie: read toast message set before a server-side redirect
    function getCookie(name) {
        var prefix = name + '=';
        var parts = document.cookie.split('; ');
        for (var i = 0; i < parts.length; i++) {
            if (parts[i].indexOf(prefix) === 0)
                return decodeURIComponent(parts[i].slice(prefix.length));
        }
        return null;
    }
    function clearCookie(name) {
        document.cookie = name + '=; path=/; max-age=0';
    }
    var flashMsg  = getCookie('flash_msg');
    var flashType = getCookie('flash_type');
    if (flashMsg) {
        showToast(flashMsg, flashType || 'info');
        clearCookie('flash_msg');
        clearCookie('flash_type');
    }
})();
