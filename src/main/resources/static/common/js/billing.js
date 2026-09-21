// ===== LOAD ADDRESSES =====
async function loadBillingAddresses() {
    try {
        const res = await fetch('/api/v1/billing-addresses', { headers: authHeaders() });
        const data = await res.json();
        if (data.success) billingAddresses = data.data;
    } catch (e) { console.error('Failed to load addresses:', e); }
    renderAddressList();
}

// ===== RENDER ADDRESS LIST =====
function renderAddressList() {
    const list = document.getElementById('addressList');
    const empty = document.getElementById('addressEmpty');

    if (billingAddresses.length === 0) {
        list.innerHTML = '';
        empty.classList.remove('hidden');
        selectedAddressId = null;
        return;
    }

    empty.classList.add('hidden');
    if (!selectedAddressId) {
        const def = billingAddresses.find(a => a.isDefault) || billingAddresses[0];
        selectedAddressId = def.id;
    }

    const selected = billingAddresses.find(a => a.id === selectedAddressId);
    const addrs = showAllAddresses ? billingAddresses : (selected ? [selected] : [billingAddresses.find(a => a.isDefault) || billingAddresses[0]]);

    list.innerHTML = addrs.map(a => `
        <div class="address-card ${selectedAddressId === a.id ? 'selected' : ''}" onclick="selectAddress(${a.id})">
            <div class="address-card-icon">&#9878;</div>
            <div class="address-card-info">
                <div class="address-card-name">${a.name}${a.isDefault ? ' <span class="address-card-tag">Default</span>' : ''}</div>
                <div class="address-card-line">${a.addressLine1}${a.addressLine2 ? ', ' + a.addressLine2 : ''}, ${a.city}, ${a.state} ${a.zipCode}</div>
                <div class="address-card-line">${a.country} &middot; ${a.email}</div>
            </div>
        </div>
    `).join('');

    if (billingAddresses.length > 1) {
        const label = showAllAddresses ? 'Show less' : `Show all (${billingAddresses.length})`;
        list.innerHTML += `<div class="address-expand"><a class="text-link" onclick="toggleAddressList()" style="font-size:12px;">${label}</a></div>`;
    }
}

function toggleAddressList() {
    showAllAddresses = !showAllAddresses;
    renderAddressList();
}

function selectAddress(id) {
    selectedAddressId = id;
    renderAddressList();
}

// ===== SHOW/HIDE ADDRESS FORM =====
function showBillingForm() {
    hideAll();
    document.getElementById('step-address').classList.remove('hidden');
    document.getElementById('addr-error').classList.remove('show');
    document.getElementById('addrName').value = getUser()?.name || '';
    document.getElementById('addrEmail').value = getUser()?.email || '';
}

function hideBillingForm() {
    hideAll();
    document.getElementById('step-checkout').classList.remove('hidden');
    document.getElementById('checkout-error').classList.remove('show');
    renderAddressList();
}

// ===== SAVE ADDRESS =====
async function saveBillingAddress() {
    const body = {
        name: document.getElementById('addrName').value.trim(),
        email: document.getElementById('addrEmail').value.trim(),
        addressLine1: document.getElementById('addrLine1').value.trim(),
        addressLine2: document.getElementById('addrLine2').value.trim(),
        city: document.getElementById('addrCity').value.trim(),
        state: document.getElementById('addrState').value.trim(),
        zipCode: document.getElementById('addrZip').value.trim(),
        country: document.getElementById('addrCountry').value.trim(),
        isDefault: billingAddresses.length === 0
    };

    const err = document.getElementById('addr-error');
    if (!body.name || !body.email || !body.addressLine1 || !body.city || !body.state || !body.zipCode || !body.country) {
        showMsg(err, 'Please fill in all required fields.'); return;
    }

    try {
        const res = await fetch('/api/v1/billing-addresses', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Failed.'); return; }
        selectedAddressId = data.data.id;
        ['addrName','addrEmail','addrLine1','addrLine2','addrCity','addrState','addrZip','addrCountry'].forEach(id => document.getElementById(id).value = '');
        showAllAddresses = false;
        await loadBillingAddresses();
        hideBillingForm();
    } catch (e) { showMsg(err, 'Server error: ' + e.message); }
}
