// ===== STATE =====
let payAmount = 0;
let lastAvailableMethods = [];
let expressCheckoutElements = null;
let expressCheckoutEl = null;

// ===== HELPERS =====
function getWalletContainer() {
    return document.getElementById('pr-button-container');
}

function disableAllPaymentButtons() {
    document.getElementById('payBtn').disabled = true;
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = 'none'; wc.style.opacity = '0.5'; }
}

function enableAllPaymentButtons() {
    document.getElementById('payBtn').disabled = false;
    document.getElementById('payBtn').textContent = 'Pay Now';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = ''; wc.style.opacity = ''; }
}

function setProcessing() {
    disableAllPaymentButtons();
    document.getElementById('payBtn').textContent = 'Processing...';
}

// ===== GO TO CARD PAGE =====
function goToCard(amount) {
    const user = getUser();
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    payAmount = parseFloat(amount);
    paymentIntentData = null;
    hideAll();
    document.getElementById('step-card').classList.remove('hidden');
    document.getElementById('payAmount').textContent = '$' + payAmount.toFixed(2);
    document.getElementById('detailPiId').textContent = 'Click Pay to create...';
    document.getElementById('detailCustomer').textContent = (addr ? addr.name : user.name);
    document.getElementById('detailChargeId').textContent = 'Waiting...';
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge pending">Pending</span>';
    document.getElementById('resultPiId').textContent = '-';
    document.getElementById('card-success').classList.remove('show');
    document.getElementById('card-error').classList.remove('show');
    document.getElementById('payBtn').disabled = false;
    document.getElementById('payBtn').textContent = 'Pay Now';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = ''; wc.style.opacity = ''; }
    cardElement.unmount();
    cardElement.mount('#card-element');
    setupExpressCheckout(amount);
}

// ===== CREATE PAYMENT INTENT ON DEMAND =====
async function createPaymentIntent() {
    const user = getUser();
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    const res = await fetch('/api/v1/payments', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
            customerId: parseInt(user.userId),
            amount: payAmount,
            name: addr ? addr.name : user.name,
            email: addr ? addr.email : user.email
        })
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.message || 'Failed to create payment');
    paymentIntentData = data.data;
    document.getElementById('detailPiId').textContent = paymentIntentData.paymentIntentId || '-';
    return paymentIntentData;
}

// ===== CONFIRM ON BACKEND (fire-and-forget) =====
async function confirmOnBackend(paymentIntentId, paymentMethod) {
    const user = getUser();
    try {
        const res = await fetch('/api/v1/payments/confirm', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ paymentIntentId, customerId: parseInt(user.userId), paymentMethod })
        });
        return await res.json();
    } catch (e) { return null; }
}

// ===== UI STATES =====
function showPaymentSuccess(paymentIntent) {
    document.getElementById('detailChargeId').textContent = 'N/A';
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge success">Succeeded</span>';
    document.getElementById('resultPiId').textContent = paymentIntent.id;
    showMsg(document.getElementById('card-success'), 'Payment successful!');
    document.getElementById('payBtn').disabled = true;
    document.getElementById('payBtn').textContent = 'Paid';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = 'none'; wc.style.opacity = '0.5'; }
}

function showPaymentError(message) {
    document.getElementById('detailStatus').innerHTML = '<span class="status-badge error">Failed</span>';
    showMsg(document.getElementById('card-error'), message);
}

// ===== EXPRESS CHECKOUT ELEMENT (Apple Pay, Google Pay, Link) =====
function setupExpressCheckout(amount) {
    const container = document.getElementById('pr-button-container');
    const separator = document.getElementById('payOrSeparator');

    if (expressCheckoutEl) {
        expressCheckoutEl.unmount();
        expressCheckoutEl = null;
        expressCheckoutElements = null;
    }

    const totalInCents = Math.round(parseFloat(amount) * 100);

    expressCheckoutElements = stripe.elements({
        mode: 'payment',
        amount: totalInCents,
        currency: 'usd',
    });

    expressCheckoutEl = expressCheckoutElements.create('expressCheckout', {
        paymentMethods: {
            googlePay: 'always',
            applePay: 'always'
        },
        buttonType: { googlePay: 'checkout', applePay: 'check-out' },
        buttonHeight: 40,
        layout: { maxColumns: 2, maxRows: 2 }
    });

    // Show wallet buttons when methods are available
    expressCheckoutEl.on('availablepaymentmethodschange', ({ paymentMethods }) => {
        console.log('[ExpressCheckout] available methods:', paymentMethods);
        if (paymentMethods && typeof paymentMethods === 'object' && Object.keys(paymentMethods).length > 0) {
            container.style.display = 'block';
            if (separator) separator.style.display = 'flex';
        }
    });

    // Handle wallet confirm — create PI then confirm
    expressCheckoutEl.on('confirm', async (event) => {
        console.log('[ExpressCheckout] confirm event fired', event);
        const walletType = event.expressPaymentType || 'wallet';
        console.log('[ExpressCheckout] wallet type:', walletType);
        setProcessing();

        try {
            if (!paymentIntentData || !paymentIntentData.clientSecret) {
                await createPaymentIntent();
            }

            const { error: submitError } = await expressCheckoutElements.submit();
            if (submitError) {
                console.error('[ExpressCheckout] submit error:', submitError);
                showPaymentError(submitError.message);
                enableAllPaymentButtons();
                return;
            }

            const { error, paymentIntent } = await stripe.confirmPayment({
                elements: expressCheckoutElements,
                clientSecret: paymentIntentData.clientSecret,
                confirmParams: { return_url: window.location.origin + '/payment.html' },
                redirect: 'if_required'
            });

            console.log('[ExpressCheckout] confirmPayment result:', { error, status: paymentIntent?.status });

            if (error) {
                console.error('[ExpressCheckout] confirmPayment error:', error);
                showPaymentError(error.message);
                enableAllPaymentButtons();
                return;
            }

            if (paymentIntent.status === 'succeeded') {
                showPaymentSuccess(paymentIntent);
                confirmOnBackend(paymentIntent.id, walletType).then(data => {
                    if (data && data.success && data.data.chargeId) {
                        document.getElementById('detailChargeId').textContent = data.data.chargeId;
                    }
                });
            } else if (paymentIntent.status === 'requires_action') {
                console.log('[ExpressCheckout] requires_action — Stripe will handle redirect');
            } else {
                console.log('[ExpressCheckout] unexpected status:', paymentIntent.status);
            }
        } catch (err) {
            console.error('[ExpressCheckout] catch error:', err);
            showPaymentError('Server error: ' + err.message);
            enableAllPaymentButtons();
        }
    });

    // Mount AFTER handlers registered
    container.style.display = 'block';
    if (separator) separator.style.display = 'flex';
    expressCheckoutEl.mount('#pr-button-container');
}

// ===== PAY WITH CARD =====
document.getElementById('payBtn').addEventListener('click', async () => {
    const user = getUser();
    const err = document.getElementById('card-error');
    const ok = document.getElementById('card-success');
    err.classList.remove('show'); ok.classList.remove('show');
    setProcessing();

    try {
        if (!paymentIntentData || !paymentIntentData.clientSecret) {
            await createPaymentIntent();
        }

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

        if (error) {
            showMsg(err, error.message);
            enableAllPaymentButtons();
            return;
        }
        showPaymentSuccess(paymentIntent);
        confirmOnBackend(paymentIntent.id, 'card').then(data => {
            if (data && data.success && data.data.chargeId) {
                document.getElementById('detailChargeId').textContent = data.data.chargeId;
            }
        });
    } catch (e) {
        showMsg(err, e.message);
        enableAllPaymentButtons();
    }
});
