// ===== USERS SECTION =====
let allUsers = [];
let assignUserId = null;
let credentialUpgId = null;

function usersFlash(type, msg) {
    if (type === 'error') notifyError(msg);
    else notifySuccess(msg);
}

async function loadUsers() {
    const body = document.getElementById('usersBody');
    body.innerHTML = '<tr><td colspan="5" class="admin-loading">Loading...</td></tr>';
    try {
        const [uRes, gRes, aRes] = await Promise.all([
            fetch('/api/v1/admin/users', { headers: authHeaders() }),
            fetch('/api/v1/admin/gateways', { headers: authHeaders() }),
            fetch('/api/v1/admin/user-gateways', { headers: authHeaders() }),
        ]);
        const u = await uRes.json();
        const g = await gRes.json();
        const a = await aRes.json();

        allUsers = u.success && u.data ? u.data : [];
        allGateways = g.success && g.data ? g.data : [];
        allUserGateways = a.success && a.data ? a.data : [];

        if (allUsers.length === 0) {
            body.innerHTML = '<tr><td colspan="5" class="admin-empty">No users found.</td></tr>';
            return;
        }

        renderUsers();
    } catch (e) {
        body.innerHTML = '<tr><td colspan="5" class="admin-empty">Failed to load: ' + esc(e.message) + '</td></tr>';
    }
}

// Client-side search over the already-loaded list, so typing never hits the API.
function renderUsers() {
    const body = document.getElementById('usersBody');
    if (!body) return;
    const q = ((document.getElementById('usersSearch') || {}).value || '').trim().toLowerCase();
    const rows = q
        ? allUsers.filter(u => (u.email || '').toLowerCase().includes(q)
            || (u.name || '').toLowerCase().includes(q)
            || String(u.id) === q)
        : allUsers;
    const countEl = document.getElementById('usersCount');
    if (countEl) {
        countEl.textContent = q
            ? rows.length + ' of ' + allUsers.length + ' users'
            : allUsers.length + (allUsers.length === 1 ? ' user' : ' users');
    }
    if (rows.length === 0) {
        body.innerHTML = '<tr><td colspan="5" class="admin-empty">'
            + (allUsers.length === 0 ? 'No users yet.' : 'No users match &ldquo;' + esc(q) + '&rdquo;.')
            + '</td></tr>';
        return;
    }
    body.innerHTML = rows.map(user => {
            const isSuper = String(user.isSuperAdmin) === '1';
            const isActive = user.isActive === true || user.isActive === 1 || user.isActive === '1';
            const assigned = allUserGateways.filter(x => x.userId === user.id);
            const me = getUser();

            // Gateways: just badges now. Clicking one opens its credentials, which removes the
            // separate "Credentials" button that used to sit next to every badge.
            const gwCell = assigned.length === 0
                ? '<span class="gw-none">None</span>'
                : '<div class="gw-badges">' + assigned.map(a2 => `
                    <span class="gw-chip ${a2.enabled === '1' ? 'is-on' : 'is-off'}"
                          title="${a2.enabled === '1' ? 'Enabled' : 'Disabled'} — click to edit credentials"
                          onclick="openCredentials(${a2.id})">${esc(a2.gatewayTitle)}</span>
                    <label class="toggle-switch" title="${a2.enabled === '1' ? 'Disable gateway' : 'Enable gateway'}">
                        <input type="checkbox" ${a2.enabled === '1' ? 'checked' : ''} onchange="toggleGateway(${a2.id})" />
                        <span class="toggle-slider"></span>
                    </label>
                `).join('') + '</div>';

            const statusCell = isSuper
                ? '<span class="status-badge initiated">Admin</span>'
                : (isActive
                    ? '<span class="status-badge success">Active</span>'
                    : '<span class="status-badge error">Blocked</span>');

            // One compact button group instead of loose links. Block/Unblock is disabled on your
            // own row and on admin accounts, because neither can sensibly be blocked.
            const canBlock = !isSuper && String(me && me.userId) !== String(user.id);
            const actions = `
                <div class="action-group">
                    ${!isSuper ? `<button class="act" onclick="openAssign(${user.id})">Assign</button>` : ''}
                    <button class="act" onclick="viewUserPaymentLogs(${user.id}, '${esc(user.email)}')">Logs</button>
                    <button class="act" onclick="viewUserRefundLogs(${user.id}, '${esc(user.email)}')">Refunds</button>
                    ${isSuper ? '' : `<button class="act" onclick="openLoginAs(${user.id}, '${esc(user.email)}')">Sign in</button>`}
                    ${isSuper ? '' : `<button class="act ${isActive ? 'act-danger' : 'act-ok'}" ${canBlock ? '' : 'disabled'}
                        onclick="toggleUserBlock(${user.id}, ${isActive}, '${esc(user.email)}')">${isActive ? 'Block' : 'Unblock'}</button>`}
                </div>`;

            // A plain row number for reading. The database id is an internal key with gaps (an
            // account that was deleted leaves a hole), so it is not shown. It is still what every
            // action below passes to the API. Numbered by position in the full list, so searching
            // does not renumber everybody.
            const displayNo = allUsers.findIndex(u => u.id === user.id) + 1;

            return `
                <tr class="${isActive ? '' : 'row-blocked'}">
                    <td>${displayNo}</td>
                    <td class="user-cell">
                        <span class="user-name">${esc(user.name) || '-'}</span>
                        <span class="user-email">${esc(user.email)}</span>
                    </td>
                    <td>${statusCell}</td>
                    <td>${gwCell}</td>
                    <td class="admin-actions">${actions}</td>
                </tr>`;
        }).join('');
}

// ===== LOGIN AS USER =====
// The password is required on purpose: it is the confirmation that the account really is yours to
// open, and the server checks it before it replaces the session.
let loginAsUserId = null;

function openLoginAs(userId, email) {
    loginAsUserId = userId;
    const emailEl = document.getElementById('loginAsEmail');
    const pwEl = document.getElementById('loginAsPassword');
    if (emailEl) emailEl.value = email;
    if (pwEl) pwEl.value = '';
    const modal = document.getElementById('loginAsModal');
    if (modal) modal.classList.add('show');
    if (pwEl) pwEl.focus();
}

function closeLoginAsModal() {
    const modal = document.getElementById('loginAsModal');
    if (modal) modal.classList.remove('show');
    loginAsUserId = null;
}

async function submitLoginAs() {
    if (loginAsUserId == null) return;
    const pwEl = document.getElementById('loginAsPassword');
    const btn = document.getElementById('loginAsSubmit');
    const password = pwEl ? pwEl.value : '';
    if (!password) { notifyError('Enter this account\u2019s password to continue'); return; }
    if (btn) { btn.disabled = true; btn.textContent = 'Signing in\u2026'; }

    // Open the tab NOW, while we are still inside the click. After an await the browser no longer
    // counts this as a user gesture, so window.open would be blocked as a popup and we would end up
    // navigating this tab instead.
    const opened = window.open('', '_blank');
    if (opened) {
        opened.document.open();
        opened.document.write('<!doctype html><title>Signing in\u2026</title>'
            + '<body style="font:14px -apple-system,system-ui;padding:32px;color:#52525b">'
            + 'Signing in\u2026</body>');
        opened.document.close();
    }

    try {
        const res = await fetch('/api/v1/admin/users/' + loginAsUserId + '/login-as', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ password: password })
        });
        const data = await res.json();
        if (!data.success) {
            if (opened) opened.close();          // nothing to show, so close the blank tab again
            notifyError(data.message || 'Could not sign in as that user');
            return;
        }
        const landing = '/stripe/payment.html';
        if (opened) {
            opened.location.href = landing;
        } else {
            window.location.href = landing;   // popup blocked outright: last resort
        }
    } catch (e) {
        if (opened) opened.close();
        notifyError('Could not sign in as that user: ' + e.message);
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Sign in as this user'; }
    }
}

// ===== BLOCK / UNBLOCK =====
async function toggleUserBlock(userId, isActive, email) {
    const verb = isActive ? 'Block' : 'Unblock';
    if (!confirm(`${verb} ${email}?` + (isActive ? '\n\nThey will not be able to sign in again.' : ''))) return;
    try {
        const res = await fetch('/api/v1/admin/users/' + userId + '/status', {
            method: 'PUT', headers: authHeaders()
        });
        const data = await res.json();
        if (!data.success) { usersFlash('error', data.message || 'Failed to update user.'); return; }
        usersFlash('success', data.message);
        loadUsers();
    } catch (e) {
        usersFlash('error', 'Failed to update user: ' + e.message);
    }
}

// ===== ASSIGN =====
function openAssign(userId) {
    assignUserId = userId;
    const sel = document.getElementById('assignGatewaySelect');
    sel.innerHTML = '<option value="">Select gateway</option>' + allGateways.map(g => {
        const already = allUserGateways.some(a => a.userId === userId && a.paymentGatewayId === g.id);
        return `<option value="${g.id}" ${already ? 'disabled' : ''}>${esc(g.title) || 'Gateway'}${already ? ' (already assigned)' : ''}</option>`;
    }).join('');
    document.getElementById('assignModal').classList.add('show');
}

function closeAssignModal() {
    document.getElementById('assignModal').classList.remove('show');
    assignUserId = null;
}

async function submitAssign() {
    const gatewayId = document.getElementById('assignGatewaySelect').value;
    if (!gatewayId) { usersFlash('error', 'Select a gateway.'); return; }
    try {
        const res = await fetch('/api/v1/admin/assign-gateway', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ userId: assignUserId, gatewayId: parseInt(gatewayId) }),
        });
        const data = await res.json();
        if (!data.success) { usersFlash('error', data.message || 'Failed'); return; }
        closeAssignModal();
        usersFlash('success', 'Gateway assigned.');
        await loadUsers();
        // open credentials for the newly assigned gateway
        const newUpg = allUserGateways.find(a => a.userId === assignUserId && a.paymentGatewayId === parseInt(gatewayId));
        if (newUpg) openCredentials(newUpg.id);
    } catch (e) { usersFlash('error', 'Server error: ' + e.message); }
}

// ===== CREDENTIALS =====
async function openCredentials(upgId) {
    credentialUpgId = upgId;
    document.getElementById('credPublicKey').value = '';
    document.getElementById('credSecretKey').value = '';
    document.getElementById('credWebhookSecret').value = '';
    // load existing
    try {
        const res = await fetch('/api/v1/admin/user-gateways', { headers: authHeaders() });
        const data = await res.json();
        if (data.success && data.data) {
            const a = data.data.find(x => x.id === upgId);
            if (a && a.credentials && a.credentials.length) {
                const c = a.credentials[0];
                document.getElementById('credPublicKey').value = c.publicKey || '';
                document.getElementById('credSecretKey').value = c.secretKey || '';
                document.getElementById('credWebhookSecret').value = c.webhookSecret || '';
            }
        }
    } catch (e) { /* ignore */ }
    document.getElementById('credentialModal').classList.add('show');
}

function closeCredentialModal() {
    document.getElementById('credentialModal').classList.remove('show');
    credentialUpgId = null;
}

async function saveCredentials() {
    try {
        const res = await fetch(`/api/v1/admin/user-gateways/${credentialUpgId}/credentials`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({
                publicKey: document.getElementById('credPublicKey').value,
                secretKey: document.getElementById('credSecretKey').value,
                webhookSecret: document.getElementById('credWebhookSecret').value,
            }),
        });
        const data = await res.json();
        if (!data.success) { usersFlash('error', data.message || 'Failed'); return; }
        closeCredentialModal();
        usersFlash('success', 'Credentials saved.');
    } catch (e) { usersFlash('error', 'Server error: ' + e.message); }
}

// ===== TOGGLE =====
async function toggleGateway(upgId) {
    try {
        const res = await fetch(`/api/v1/admin/user-gateways/${upgId}/toggle`, {
            method: 'PUT',
            headers: authHeaders(),
        });
        const data = await res.json();
        if (!data.success) { usersFlash('error', data.message || 'Failed'); return; }
        await loadUsers();
    } catch (e) { usersFlash('error', 'Server error: ' + e.message); }
}
