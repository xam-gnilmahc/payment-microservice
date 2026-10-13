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
    body.innerHTML = '<tr><td colspan="6" class="admin-loading">Loading...</td></tr>';
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
            body.innerHTML = '<tr><td colspan="6" class="admin-empty">No users found.</td></tr>';
            return;
        }

        renderUsers();
    } catch (e) {
        body.innerHTML = '<tr><td colspan="6" class="admin-empty">Failed to load: ' + esc(e.message) + '</td></tr>';
    }
}

// Client-side search over the already-loaded list, so typing never hits the API.
// Gateways: a badge per assigned gateway with its enable switch. Clicking a badge opens that
// gateway's credentials, which is why there is no separate Credentials button anywhere.
function gatewayCellHtml(user) {
    const assigned = allUserGateways.filter(x => x.userId === user.id);
    return assigned.length === 0
        ? '<span class="gw-none">None</span>'
        : '<div class="gw-badges">' + assigned.map(a2 => `
            <span class="gw-chip ${a2.enabled === '1' ? 'is-on' : 'is-off'}"
                  title="${a2.enabled === '1' ? 'Enabled' : 'Disabled'} — click to edit credentials"
                  onclick="openCredentials(${a2.id})">${esc(a2.gatewayTitle)}</span>
            <label class="toggle-switch" title="${a2.enabled === '1' ? 'Disable gateway' : 'Enable gateway'}">
                <input type="checkbox" ${a2.enabled === '1' ? 'checked' : ''}
                       onchange="gatewayToggleChanged(this, ${a2.id}, '${esc(user.email)}', '${esc(a2.gatewayTitle)}')" />
                <span class="toggle-slider"></span>
            </label>
        `).join('') + '</div>';
}

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
        body.innerHTML = '<tr><td colspan="6" class="admin-empty">'
            + (allUsers.length === 0 ? 'No users yet.' : 'No users match &ldquo;' + esc(q) + '&rdquo;.')
            + '</td></tr>';
        return;
    }
    body.innerHTML = rows.map(user => {
            const isSuper = String(user.isSuperAdmin) === '1';
            const isActive = user.isActive === true || user.isActive === 1 || user.isActive === '1';
            const gwCell = gatewayCellHtml(user);

            // Role is a column of its own, so Status can be what it always meant: is this account
            // able to sign in. Everyone who can be blocked gets a capsule switch there; it asks
            // for confirmation before it flips, because blocking cuts somebody off from paying.
            const roleCell = isSuper
                ? '<span class="status-badge initiated">Superadmin</span>'
                : '<span class="status-badge pending">Member</span>';

            const statusCell = isSuper
                ? '<span class="status-text is-on" title="Admin accounts cannot be blocked">Active</span>'
                : `<div class="status-cell">
                        <label class="toggle-switch" title="${isActive ? 'Block this user' : 'Unblock this user'}">
                            <input type="checkbox" ${isActive ? 'checked' : ''}
                                   onchange="statusToggleChanged(this, ${user.id}, '${esc(user.email)}')" />
                            <span class="toggle-slider"></span>
                        </label>
                        <span class="status-text ${isActive ? 'is-on' : 'is-off'}">${isActive ? 'Active' : 'Inactive'}</span>
                    </div>`;

            // The three everyday account actions sit as plain buttons. Logs, refunds and the
            // rest live on the detail page, which the User cell opens.
            const actions = `
                <div class="action-group">
                    ${isSuper ? '' : `<button class="act" onclick="openLoginAs(${user.id}, '${esc(user.email)}')">Sign in</button>`}
                    ${isSuper ? '' : `<button class="act" onclick="openAssign(${user.id})">Assign</button>`}
                    <button class="act" onclick="openSetPassword(${user.id}, '${esc(user.email)}')">Set password</button>
                </div>`;

            // A plain row number for reading. The database id is an internal key with gaps (an
            // account that was deleted leaves a hole), so it is not shown. It is still what every
            // action below passes to the API. Numbered by position in the full list, so searching
            // does not renumber everybody.
            const displayNo = allUsers.findIndex(u => u.id === user.id) + 1;

            return `
                <tr class="${isActive ? '' : 'row-blocked'}">
                    <td>${displayNo}</td>
                    <td class="user-cell user-cell-link" onclick="openUserDetail(${user.id})"
                        title="Open details for ${esc(user.email)}">
                        <span class="user-name">${esc(user.name) || '-'}</span>
                        <span class="user-email">${esc(user.email)}</span>
                    </td>
                    <td>${roleCell}</td>
                    <td>${statusCell}</td>
                    <td>${gwCell}</td>
                    <td class="admin-actions">${actions}</td>
                </tr>`;
        }).join('');
}

// ===== USER DETAIL =====
// Clicking the name/email in the users table opens this page: the account itself, its
// gateways, and the two log streams that belong to it. The list stays a list — everything
// about one customer lives here.
let detailUserId = null;

function openUserDetail(userId) {
    const user = allUsers.find(u => u.id === userId);
    if (!user) return;
    detailUserId = userId;
    const isSuper = String(user.isSuperAdmin) === '1';
    const isActive = user.isActive === true || user.isActive === 1 || user.isActive === '1';

    document.getElementById('detailName').textContent = user.name || user.email;
    document.getElementById('detailEmail').textContent = user.email;
    document.getElementById('detailCard').innerHTML = `
        <div class="detail-facts">
            <div class="detail-fact">
                <span class="detail-label">User ID</span>
                <span>${user.id}</span>
            </div>
            <div class="detail-fact">
                <span class="detail-label">Role</span>
                ${isSuper ? '<span class="status-badge initiated">Superadmin</span>' : '<span class="status-badge pending">Member</span>'}
            </div>
            <div class="detail-fact">
                <span class="detail-label">Status</span>
                <span class="status-text ${isActive ? 'is-on' : 'is-off'}">${isActive ? 'Active' : 'Inactive'}</span>
            </div>
            ${user.createdAt ? `<div class="detail-fact">
                <span class="detail-label">Joined</span>
                <span>${fmtDate(user.createdAt)}</span>
            </div>` : ''}
            <div class="detail-fact detail-fact-wide">
                <span class="detail-label">Gateways</span>
                ${gatewayCellHtml(user)}
            </div>
        </div>`;

    showSection('user-detail');
    loadUserDetailLogs();
}

function backToUsers() {
    showSection('users', document.querySelector('[data-section="users"]'));
}

// The two log streams for this one account, newest first. Same endpoints the filtered logs
// sections use, so a detail page never shows anything the logs pages would not.
async function loadUserDetailLogs() {
    const userId = detailUserId;
    const pBody = document.getElementById('detailLogsBody');
    const rBody = document.getElementById('detailRefundBody');
    if (!pBody || !rBody) return;
    pBody.innerHTML = '<tr><td colspan="7" class="admin-loading">Loading...</td></tr>';
    rBody.innerHTML = '<tr><td colspan="5" class="admin-loading">Loading...</td></tr>';
    try {
        const [pRes, rRes] = await Promise.all([
            fetch(`/api/v1/admin/user/${userId}/payment-logs?page=0&size=50`, { headers: authHeaders() }),
            fetch(`/api/v1/admin/user/${userId}/refund-logs?page=0&size=50`, { headers: authHeaders() }),
        ]);
        const pData = await pRes.json();
        const rData = await rRes.json();

        const d = pData.success && pData.data ? pData.data : {};
        const pRaw = d.logs || d.content || (Array.isArray(d) ? d : []);
        const pItems = Array.isArray(pRaw) ? pRaw : [];
        pBody.innerHTML = pItems.length === 0
            ? '<tr><td colspan="7" class="admin-empty">No payment logs yet.</td></tr>'
            : pItems.map((l, i) => `
                <tr>
                    <td>${i + 1}</td>
                    <td>${esc(l.paymentMethod) || '-'}</td>
                    <td class="num cell-amount">${fmtAmountCell(l.currency, l.amount)}</td>
                    <td>${paymentBadge(l.status)}</td>
                    <td class="col-secondary-lg cell-msg"><span class="clamp2" title="${esc(l.message) || ''}">${esc(l.message) || '-'}</span></td>
                    <td class="cell-fail${l.failureCode ? ' is-error' : ''}">${esc(l.failureCode) || '-'}</td>
                    <td class="ta-r cell-date">${fmtDate(l.createdAt)}</td>
                </tr>`).join('');

        const rRaw = Array.isArray(rData.data) ? rData.data : (rData.data?.logs || rData.data?.content || []);
        const rItems = Array.isArray(rRaw) ? rRaw : [];
        rBody.innerHTML = rItems.length === 0
            ? '<tr><td colspan="5" class="admin-empty">No refund logs yet.</td></tr>'
            : rItems.map(r => `
                <tr>
                    <td class="cell-mono" title="${esc(r.refundId) || ''}">${esc(r.refundId) || '-'}</td>
                    <td class="num cell-amount">${fmtAmountCell(r.currency, r.amount)}</td>
                    <td>${refundBadge(r.status)}</td>
                    <td class="col-secondary cell-msg"><span class="clamp2" title="${esc(r.message) || ''}">${esc(r.message) || '-'}</span></td>
                    <td class="ta-r cell-date">${fmtDate(r.createdAt)}</td>
                </tr>`).join('');
    } catch (e) {
        notifyError('Failed to load logs: ' + e.message);
        pBody.innerHTML = '';
        rBody.innerHTML = '';
    }
}

// ===== SET PASSWORD =====
// The admin resets any account's password from the Users section. No current password is needed:
// this is the path for somebody who has forgotten theirs.
let setPwUserId = null;
const PW_EYE = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
const PW_EYE_OFF = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><path d="M14.12 14.12A3 3 0 1 1 9.88 9.88"/><path d="M1 1l22 22"/></svg>';

function togglePasswordEye(btn) {
    const input = document.getElementById(btn.dataset.target);
    const hidden = input.type === 'password';
    input.type = hidden ? 'text' : 'password';
    btn.innerHTML = hidden ? PW_EYE_OFF : PW_EYE;
    const label = hidden ? 'Hide password' : 'Show password';
    btn.title = label;
    btn.setAttribute('aria-label', label);
}

function openSetPassword(userId, email) {
    setPwUserId = userId;
    document.getElementById('setPwEmail').value = email;
    ['setPwNew', 'setPwConfirm'].forEach(id => { document.getElementById(id).value = ''; document.getElementById(id).type = 'password'; });
    document.querySelectorAll('#setPasswordModal .cp-eye').forEach(btn => {
        btn.innerHTML = PW_EYE;
        btn.title = 'Show password';
        btn.setAttribute('aria-label', 'Show password');
    });
    document.getElementById('setPasswordModal').classList.add('show');
    document.getElementById('setPwNew').focus();
}

function closeSetPasswordModal() {
    document.getElementById('setPasswordModal').classList.remove('show');
    setPwUserId = null;
}

async function submitSetPassword() {
    if (setPwUserId == null) return;
    const next = document.getElementById('setPwNew').value;
    const confirm = document.getElementById('setPwConfirm').value;

    if (!next || !confirm) { notifyError('Please fill in both fields'); return; }
    if (next !== confirm) {
        notifyError('New password and confirm password do not match');
        document.getElementById('setPwConfirm').focus();
        return;
    }
    if (next.length < 6) { notifyError('Password must be at least 6 characters'); document.getElementById('setPwNew').focus(); return; }

    const btn = document.getElementById('setPwSubmit');
    const label = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Saving...';
    try {
        const res = await fetch('/api/v1/admin/users/' + setPwUserId + '/password', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ newPassword: next })
        });
        const data = await res.json();
        if (!data.success) { notifyError(data.message || 'Could not update the password'); return; }
        closeSetPasswordModal();
        notifySuccess(data.message || 'Password updated.');
    } catch (e) {
        notifyError('Server error: ' + e.message);
    } finally {
        btn.disabled = false;
        btn.textContent = label;
    }
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

// ===== CONFIRMATION DIALOG =====
// One dialog for every action that should not happen by accident: blocking or unblocking an
// account, and enabling or disabling a gateway for one. Each caller supplies the wording and
// the work to run when Confirm is pressed.
let confirmAction = null;

function openConfirmDialog(title, note, confirmLabel, action) {
    confirmAction = action;
    const titleEl = document.getElementById('confirmModalTitle');
    const noteEl = document.getElementById('confirmModalNote');
    const btn = document.getElementById('confirmModalSubmit');
    if (titleEl) titleEl.textContent = title;
    if (noteEl) noteEl.textContent = note;
    if (btn) { btn.textContent = confirmLabel; btn.disabled = false; }
    const modal = document.getElementById('confirmModal');
    if (modal) modal.classList.add('show');
    if (btn) btn.focus();
}

function closeConfirmDialog() {
    const modal = document.getElementById('confirmModal');
    if (modal) modal.classList.remove('show');
    confirmAction = null;
}

async function submitConfirmDialog() {
    if (!confirmAction) return;
    const action = confirmAction;
    const btn = document.getElementById('confirmModalSubmit');
    const label = btn ? btn.textContent : 'Confirm';
    if (btn) { btn.disabled = true; btn.textContent = label + '\u2026'; }
    try {
        await action();
    } catch (e) {
        usersFlash('error', 'Failed: ' + e.message);
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = label; }
        closeConfirmDialog();
    }
}

// ===== USER STATUS (block / unblock) =====
// The switch in the Status column, and the Block/Unblock items in the Actions menu, both come
// through here. The browser flips the switch before onchange fires, so it is put straight back
// and is only allowed to move again once the server has agreed.
function statusToggleChanged(input, userId, email) {
    const nextActive = input.checked;
    input.checked = !nextActive;
    confirmToggleUserBlock(userId, nextActive, email);
}

function confirmToggleUserBlock(userId, nextActive, email) {
    openConfirmDialog(
        nextActive ? 'Unblock user' : 'Block user',
        nextActive
            ? email + ' will be able to sign in and pay again.'
            : email + ' will be signed out of every device and cannot sign in or pay again until you unblock them.',
        nextActive ? 'Unblock' : 'Block',
        () => setUserStatus(userId, nextActive)
    );
}

async function setUserStatus(userId, nextActive) {
    const res = await fetch('/api/v1/admin/users/' + userId + '/status', {
        method: 'PUT', headers: authHeaders()
    });
    const data = await res.json();
    if (!data.success) { usersFlash('error', data.message || 'Failed to update user.'); return; }
    usersFlash('success', data.message);
    loadUsers();
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

// ===== GATEWAY ENABLE / DISABLE =====
// Same treatment as the Status switch: the gateway switch asks first, and snaps straight back
// until the server has actually changed it. Losing a gateway stops that one payment route for
// the customer, so it should not happen from a stray click.
function gatewayToggleChanged(input, upgId, email, gatewayTitle) {
    const nextEnabled = input.checked;
    input.checked = !nextEnabled;
    openConfirmDialog(
        nextEnabled ? 'Enable gateway' : 'Disable gateway',
        nextEnabled
            ? email + ' will be able to pay through ' + gatewayTitle + ' again.'
            : 'Payments through ' + gatewayTitle + ' will stop for ' + email + ' until you enable it again.',
        nextEnabled ? 'Enable' : 'Disable',
        () => setGatewayEnabled(upgId, nextEnabled)
    );
}

async function setGatewayEnabled(upgId, nextEnabled) {
    const res = await fetch(`/api/v1/admin/user-gateways/${upgId}/toggle`, {
        method: 'PUT',
        headers: authHeaders(),
    });
    const data = await res.json();
    if (!data.success) { usersFlash('error', data.message || 'Failed'); return; }
    await loadUsers();
}
