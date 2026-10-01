// ===== SHARED STATE =====
let paymentIntentData = null;
let billingAddresses = [];
let selectedAddressId = null;
let showAllAddresses = false;
let selectedGatewayId = null;
let selectedGatewayTitle = null;
let stripe = null;
let elements = null;
let cardNumberEl = null;
let cardExpiryEl = null;
let cardCvcEl = null;

// ===== HELPERS =====
// single message area on the pay page (below the card fields)
function setCardMsg(type, text) {
    const el = document.getElementById('card-msg');
    if (!el) return;
    el.textContent = text || '';
    el.className = type || '';
    if (text) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function goToLogin() { localStorage.clear(); token = paymentIntentData = null; selectedGatewayId = null; selectedGatewayTitle = null; stripe = null; elements = null; cardNumberEl = null; cardExpiryEl = null; cardCvcEl = null; window.location.href = '/index.html'; }
function goToNoGateway() { hideAll(); document.getElementById('step-no-gateway').classList.remove('hidden'); }

// ===== INIT STRIPE WITH PUBLIC KEY =====
function initStripe(publicKey) {
    stripe = Stripe(publicKey);
    elements = stripe.elements();
    const onCardChange = (e) => { setCardMsg(e.error ? 'error' : '', e.error ? e.error.message : ''); };
    cardNumberEl = elements.create('cardNumber');
    cardExpiryEl = elements.create('cardExpiry');
    cardCvcEl = elements.create('cardCvc');
    cardNumberEl.on('change', onCardChange);
    cardExpiryEl.on('change', onCardChange);
    cardCvcEl.on('change', onCardChange);
    cardNumberEl.on('ready', () => {
        if (typeof hideCardSkeleton === 'function') hideCardSkeleton();
    });
}

// ===== LOAD USER GATEWAY & GO TO CHECKOUT =====
async function loadUserGatewayAndCheckout() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    try {
        const res = await fetch('/api/v1/payment-gateways/user/' + user.userId + '/enabled', { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { goToLogin(); return; }
        const data = await res.json();
        if (!data.success || !data.data) { goToNoGateway(); return; }
        selectedGatewayId = data.data.gatewayId;
        selectedGatewayTitle = data.data.title;
        document.getElementById('headerGatewayBadge').textContent = selectedGatewayTitle;
        const publicKey = data.data.publicKey;
        if (!publicKey) { goToNoGateway(); return; }
        initStripe(publicKey);
        goToCheckout();
    } catch (e) {
        goToLogin();
    }
}

// ===== HANDLE WALLET RETURN URL =====
const urlParams = new URLSearchParams(window.location.search);
const returnedPiId = urlParams.get('payment_intent');
const returnedPiSecret = urlParams.get('payment_intent_client_secret');
const returnedStatus = urlParams.get('redirect_status');

if (returnedPiId && returnedPiSecret) {
    window.history.replaceState({}, '', window.location.pathname);

    const checkStripe = setInterval(() => {
        if (typeof stripe !== 'undefined' && stripe !== null) {
            clearInterval(checkStripe);
            stripe.retrievePaymentIntent(returnedPiSecret).then(({ error, paymentIntent }) => {
                if (error) {
                    console.error('[Return] Failed to retrieve PI:', error);
                    return;
                }
                console.log('[Return] PaymentIntent status:', paymentIntent.status);

                hideAll();
                document.getElementById('step-card').classList.remove('hidden');

                if (paymentIntent.status === 'succeeded') {
                    setCardMsg('success', 'Payment successful!');
                    confirmOnBackend(paymentIntent.id, 'wallet');
                } else {
                    setCardMsg('error', 'Payment status: ' + paymentIntent.status);
                }
            });
        }
    }, 100);
}

// ===== INIT =====
if (!returnedPiId && !returnedPiSecret) {
    if (token) {
        const user = decodeToken(token);
        if (user) loadUserGatewayAndCheckout();
        else goToLogin();
    } else {
        goToLogin();
    }
}
