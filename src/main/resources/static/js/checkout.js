// ===== GO TO CHECKOUT =====
async function goToCheckout() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    if (!selectedGatewayId) { goToGateway(); return; }
    hideAll();
    document.getElementById('step-checkout').classList.remove('hidden');
    document.getElementById('selectedGatewayTitle').textContent = selectedGatewayTitle || 'Unknown';
    document.getElementById('userAvatar').textContent = (user.name || 'U').charAt(0).toUpperCase();
    document.getElementById('userName').textContent = user.name || 'Unknown';
    document.getElementById('userEmailDisplay').textContent = user.email || '';
    const btn = document.getElementById('checkoutBtn');
    btn.disabled = false;
    btn.textContent = 'Create Payment';
    document.getElementById('checkout-error').classList.remove('show');
    paymentIntentData = null;
    showAllAddresses = false;
    await loadBillingAddresses();
}

// ===== CREATE PAYMENT =====
document.getElementById('checkoutBtn').addEventListener('click', async () => {
    const user = getUser();
    const amount = document.getElementById('amountInput').value.trim();
    const err = document.getElementById('checkout-error');
    if (!amount || parseFloat(amount) < 0.50) { showMsg(err, 'Minimum amount is $0.50.'); return; }
    if (!selectedAddressId) { showMsg(err, 'Please add a billing address.'); return; }

    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    if (!addr) { showMsg(err, 'Selected address not found.'); return; }

    const btn = document.getElementById('checkoutBtn');
    btn.disabled = true; btn.textContent = 'Creating...';

    const body = {
        customerId: parseInt(user.userId),
        gatewayId: selectedGatewayId,
        amount: parseFloat(amount),
        name: addr.name,
        email: addr.email,
        addressLine1: addr.addressLine1,
        addressLine2: addr.addressLine2 || '',
        city: addr.city,
        state: addr.state,
        zipCode: addr.zipCode,
        country: addr.country
    };

    try {
        const res = await fetch('/api/v1/payments', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Failed.'); btn.disabled = false; btn.textContent = 'Create Payment'; return; }
        paymentIntentData = data.data;
        goToCard(amount);
    } catch (e) { showMsg(err, 'Server error: ' + e.message); btn.disabled = false; btn.textContent = 'Create Payment'; }
});
