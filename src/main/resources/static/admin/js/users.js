// ===== USERS SECTION =====
let allUsers = [];
let assignUserId = null;
let credentialUpgId = null;

function usersFlash(type, msg) {
    const el = document.getElementById(type === 'error' ? 'users-error' : 'users-success');
    showMsg(el, msg);
    setTimeout(() => el.classList.remove('show'), 4000);
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

        body.innerHTML = allUsers.map(user => {
            const isSuper = String(user.isSuperAdmin) === '1';
            const assigned = allUserGateways.filter(x => x.userId === user.id);

            const gwCell = assigned.length === 0
                ? '<span style="color:#8b8fa3;">None</span>'
                : '<div class="gw-badges">' + assigned.map(a2 => `
                    <span class="gw-badge ${a2.enabled === '1' ? 'badge-green' : 'badge-yellow'}">${esc(a2.gatewayTitle)}</span>
                    <button class="btn-link" style="font-size:11px;" onclick="openCredentials(${a2.id})">Credentials</button>
                    <label class="toggle-switch" title="${a2.enabled === '1' ? 'Enabled' : 'Disabled'}">
                        <input type="checkbox" ${a2.enabled === '1' ? 'checked' : ''} onchange="toggleGateway(${a2.id})" />
                        <span class="toggle-slider"></span>
                    </label>
                `).join('') + '</div>';

            const actions = `
                ${!isSuper ? `<button class="btn-link" onclick="openAssign(${user.id})">Assign</button>` : ''}
                <button class="btn-link" onclick="viewUserPaymentLogs(${user.id}, '${esc(user.email)}')">Payment Logs</button>
                <button class="btn-link" onclick="viewUserRefundLogs(${user.id}, '${esc(user.email)}')">Refund Logs</button>
            `;

            return `
                <tr>
                    <td>${user.id}</td>
                    <td style="font-weight:600;">${esc(user.name) || '-'}</td>
                    <td>${esc(user.email)}</td>
                    <td>${gwCell}</td>
                    <td class="admin-actions">${actions}</td>
                </tr>`;
        }).join('');
    } catch (e) {
        body.innerHTML = '<tr><td colspan="5" class="admin-empty">Failed to load: ' + esc(e.message) + '</td></tr>';
    }
}

// ===== ASSIGN =====
function openAssign(userId) {
    assignUserId = userId;
    const sel = document.getElementById('assignGatewaySelect');
    sel.innerHTML = '<option value="">Select gateway</option>' + allGateways.map(g => {
        const already = allUserGateways.some(a => a.userId === userId && a.paymentGatewayId === g.id);
        return `<option value="${g.id}" ${already ? 'disabled' : ''}>${esc(g.title) || 'Gateway ' + g.id}${already ? ' (already assigned)' : ''}</option>`;
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
