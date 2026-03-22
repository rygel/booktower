document.addEventListener('DOMContentLoaded', function() {
    initializeHTMX();
    initializeForms();
    initializeModals();
    initializeTheme();
    initializeNotifications();
});

function initializeHTMX() {
    document.body.addEventListener('htmx:beforeRequest', function(evt) {
        const target = evt.target;
        if (target.classList.contains('btn')) {
            target.classList.add('loading');
        }
    });

    document.body.addEventListener('htmx:afterRequest', function(evt) {
        const target = evt.target;
        if (target.classList.contains('btn')) {
            target.classList.remove('loading');
        }

        if (evt.detail.xhr.status >= 400) {
            const error = evt.detail.xhr.responseText;
            try {
                const json = JSON.parse(error);
                showNotification(json.message || 'An error occurred', 'error');
            } catch (e) {
                showNotification('An error occurred', 'error');
            }
        }
    });

    document.body.addEventListener('htmx:afterSwap', function(evt) {
        if (evt.detail.target.id === 'books-container') {
            initializeBookCards();
        }
    });
}

function initializeForms() {
    document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', function(e) {
            if (!form.checkValidity()) {
                e.preventDefault();
                e.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });
}

function initializeModals() {
    document.querySelectorAll('[data-modal]').forEach(button => {
        button.addEventListener('click', function() {
            const modalId = this.getAttribute('data-modal');
            const modal = document.getElementById(modalId);
            if (modal) {
                modal.showModal();
            }
        });
    });

    document.querySelectorAll('dialog').forEach(modal => {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                modal.close();
            }
        });
    });
}

function initializeTheme() {
    const themeSelect = document.querySelector('[data-theme-select]');
    if (themeSelect) {
        const savedTheme = getCookie('app_theme') || 'dark';
        themeSelect.value = savedTheme;
        applyTheme(savedTheme);
    }
}

function applyTheme(theme) {
    document.cookie = `app_theme=${theme};path=/;max-age=31536000`;
    if (window.applyThemeCSS) {
        window.applyThemeCSS(theme);
    }
}

function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

function showNotification(message, type = 'success') {
    const notification = document.createElement('div');
    notification.className = `toast toast-${type}`;
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 3000);
}

function initializeNotifications() {
    const urlParams = new URLSearchParams(window.location.search);
    const success = urlParams.get('success');
    const error = urlParams.get('error');

    if (success) {
        // Sanitize URL param to prevent DOM XSS — strip HTML tags
        showNotification(decodeURIComponent(success).replace(/<[^>]*>/g, ''), 'success');
        cleanUrl();
    } else if (error) {
        showNotification(decodeURIComponent(error).replace(/<[^>]*>/g, ''), 'error');
        cleanUrl();
    }
}

function cleanUrl() {
    const url = new URL(window.location);
    url.searchParams.delete('success');
    url.searchParams.delete('error');
    window.history.replaceState({}, '', url);
}

function initializeBookCards() {
    document.querySelectorAll('.book-card').forEach(card => {
        card.addEventListener('click', function(e) {
            if (!e.target.closest('button')) {
                const bookId = this.getAttribute('data-book-id');
                if (bookId) {
                    window.location.href = `/books/${bookId}`;
                }
            }
        });
    });
}

function confirmDelete(message, callback) {
    if (confirm(message)) {
        callback();
    }
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

const searchDebounced = debounce((query) => {
    const searchInput = document.querySelector('[data-search]');
    if (searchInput) {
        htmx.ajax('GET', `/api/books?q=${encodeURIComponent(query)}`, {
            target: '#books-container'
        });
    }
}, 300);
