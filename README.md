// ===== GO TO CARD PAGE =====
function goToCard(amount) {
    const user = getUser();
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    hideAll();
    document.getElementById('step-card').classList.remove('hidden');
    document.getElementById('payAmount').textContent = '$' + parseFloat(amount).toFixed(2);
    document.getElementById('detailPiId').textContent = paymentIntentData.paymentIntentId || '-';
    document.getElementById('detailCustomer').textContent = (addr ? addr.name : user.name);
    document.getElementById('detailChargeId').textContent = 'Waiting...';
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge pending">Pending</span>';
    document.getElementById('resultPiId').textContent = paymentIntentData.paymentIntentId || '-';
    document.getElementById('card-success').classList.remove('show');
    document.getElementById('card-error').classList.remove('show');
    document.getElementById('payBtn').disabled = false;
    document.getElementById('payBtn').textContent = 'Pay Now';
    
    cardElement.unmount();
    cardElement.mount('#card-element');
    
    // Initialize Stripe Payment Request Button
    setupPaymentRequest(amount);
}

// ===== FIRE-AND-FORGET CONFIRM =====
async function confirmOnBackend(paymentIntentId, paymentMethod) {
    const user = getUser();
    try {
        const res = await fetch('/api/v1/payments/confirm', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ paymentIntentId, customerId: parseInt(user.userId), paymentMethod })
        });
        return await res.json();
    } catch (e) {
        return null;
    }
}

// ===== SHOW SUCCESS =====
function showPaymentSuccess(paymentIntent) {
    document.getElementById('detailChargeId').textContent = 'N/A';
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge success">Succeeded</span>';
    document.getElementById('resultPiId').textContent = paymentIntent.id;
    showMsg(document.getElementById('card-success'), 'Payment successful!');
    disableAllPaymentButtons();
}

// ===== DISABLE ALL PAYMENT BUTTONS =====
function disableAllPaymentButtons() {
    document.getElementById('payBtn').disabled = true;
    document.getElementById('payBtn').textContent = 'Paid';
    const walletContainer = document.getElementById('pr-button-container');
    if (walletContainer) {
        walletContainer.style.pointerEvents = 'none';
        walletContainer.style.opacity = '0.5';
    }
}

// ===== SHOW FAILURE =====
function showPaymentError(message) {
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge error">Failed</span>';
    showMsg(document.getElementById('card-error'), message);
}

// ===== STRIPE NATIVE PAYMENT REQUEST BUTTON =====
function setupPaymentRequest(amount) {
    const container = document.getElementById('pr-button-container');
    const separator = document.getElementById('payOrSeparator');
    
    // Clear existing container content
    container.innerHTML = '';

    const totalInCents = Math.round(parseFloat(amount) * 100);

    const paymentRequest = stripe.paymentRequest({
        country: 'US',
        currency: 'usd',
        total: { label: 'Payment', amount: totalInCents },
        requestPayerName: true,
        requestPayerEmail: true,
    });

    let walletType = 'wallet';

    // Check availability of Payment Request API
    paymentRequest.canMakePayment().then(result => {
        console.log(result);
        if (result) {
            console.log('log', result);
            // Track detected wallet type for backend logging
            walletType = result.applePay ? 'apple_pay' : (result.googlePay ? 'google_pay' : 'link');

            // Create Stripe's native Payment Request Button Element
            const elements = stripe.elements();
            const prButton = elements.create('paymentRequestButton', {
                paymentRequest: paymentRequest,
                style: {
                    paymentRequestButton: {
                        type: 'default', // 'default' | 'book' | 'buy' | 'donate'
                        theme: 'dark',    // 'dark' | 'light' | 'light-outline'
                        height: '48px',   // Matches standard button height
                    },
                },
            });

            // Mount the button inside your container
            prButton.mount('#pr-button-container');
            container.style.display = 'block';
            if (separator) separator.style.display = 'flex';
        } else {
            container.style.display = 'none';
            if (separator) separator.style.display = 'none';
        }
    });

    // Process payment when user authorizes via native sheet
    paymentRequest.on('paymentmethod', async (e) => {
        try {
            const { error: confirmError, paymentIntent } = await stripe.confirmCardPayment(
                paymentIntentData.clientSecret,
                { payment_method: e.paymentMethod.id },
                { handleActions: false }
            );

            if (confirmError) {
                e.complete('fail');
                showPaymentError(confirmError.message);
                return;
            }

            e.complete('success');

            if (paymentIntent.status === 'requires_action') {
                const { error: err2 } = await stripe.confirmCardPayment(paymentIntentData.clientSecret);
                if (err2) {
                    showPaymentError(err2.message);
                    return;
                }
            }

            showPaymentSuccess(paymentIntent);

            confirmOnBackend(paymentIntent.id, walletType).then(data => {
                if (data && data.success && data.data.chargeId) {
                    document.getElementById('detailChargeId').textContent = data.data.chargeId;
                }
            });
        } catch (err) {
            e.complete('fail');
            showPaymentError('Server error: ' + err.message);
        }
    });
}

// ===== PAY WITH CARD =====
document.getElementById('payBtn').addEventListener('click', async () => {
    const user = getUser();
    const err = document.getElementById('card-error');
    const ok = document.getElementById('card-success');
    err.classList.remove('show'); ok.classList.remove('show');
    if (!paymentIntentData || !paymentIntentData.clientSecret) { showMsg(err, 'No payment intent.'); return; }
    const btn = document.getElementById('payBtn');
    btn.disabled = true; btn.textContent = 'Processing...';
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    const billingDetails = addr ? {
        name: addr.name,
        email: addr.email,
        address: {
            line1: addr.addressLine1,
            line2: addr.addressLine2 || '',
            city: addr.city,
            state: addr.state,
            postal_code: addr.zipCode,
            country: addr.country
        }
    } : {};
    const { error, paymentIntent } = await stripe.confirmCardPayment(paymentIntentData.clientSecret, {
        payment_method: { card: cardElement, billing_details: billingDetails }
    });

    if (error) { showMsg(err, error.message); btn.disabled = false; btn.textContent = 'Pay Now'; return; }
    showPaymentSuccess(paymentIntent);
    confirmOnBackend(paymentIntent.id, 'card').then(data => {
        if (data && data.success && data.data.chargeId) {
            document.getElementById('detailChargeId').textContent = data.data.chargeId;
        }
    });
});