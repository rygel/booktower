/* Runary — Global keyboard shortcuts
 *
 * Escape  → close any open modal/panel
 * /       → focus search input
 * g h     → go to home (dashboard)
 * g l     → go to libraries
 * g s     → go to search
 * g p     → go to profile
 *
 * All shortcuts are ignored when focus is inside an input/textarea/select.
 */
(function () {
  var pendingG = false;
  var gTimer = null;

  document.addEventListener('keydown', function (e) {
    var tag = (e.target.tagName || '').toUpperCase();
    var isInput = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.target.isContentEditable;

    // Escape always works — close modals and panels
    if (e.key === 'Escape') {
      // Close any visible modal
      document.querySelectorAll('[id$="-modal"]').forEach(function (m) {
        if (m.style.display !== 'none' && m.style.display !== '') {
          m.style.display = 'none';
        }
      });
      // Close notification panel
      var notif = document.getElementById('notif-panel');
      if (notif) notif.style.display = 'none';
      // Close download panel
      var dl = document.getElementById('dl-panel');
      if (dl) dl.style.display = 'none';
      // Blur focused input
      if (isInput) e.target.blur();
      return;
    }

    // All other shortcuts are suppressed when typing
    if (isInput) return;

    // / → focus search
    if (e.key === '/' && !e.ctrlKey && !e.metaKey) {
      e.preventDefault();
      var search = document.querySelector('input[type="search"], input[name="q"]');
      if (search) search.focus();
      return;
    }

    // g + key → navigation (vim-style "go to")
    if (e.key === 'g' && !e.ctrlKey && !e.metaKey) {
      if (pendingG) return; // already waiting
      pendingG = true;
      gTimer = setTimeout(function () { pendingG = false; }, 500);
      return;
    }

    if (pendingG) {
      pendingG = false;
      clearTimeout(gTimer);
      switch (e.key) {
        case 'h': window.location.href = '/'; break;
        case 'l': window.location.href = '/libraries'; break;
        case 's': window.location.href = '/search'; break;
        case 'p': window.location.href = '/profile'; break;
        case 'a': window.location.href = '/analytics'; break;
      }
    }
  });
})();
