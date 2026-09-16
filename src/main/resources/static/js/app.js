// ===== SHARED STATE =====
let token = localStorage.getItem('jwtToken');
let paymentIntentData = null;
let billingAddresses = [];
let selectedAddressId = null;
let showAllAddresses = false;
let selectedGatewayId = null;
let selectedGatewayTitle = null;

// ===== TOKEN =====
function decodeToken(t) {
    try {
        const payload = JSON.parse(atob(t.split('.')[1]));
        return { userId: payload.userId, email: payload.sub, name: payload.name };
    } catch (e) { return null; }
}

function getUser() { return token ? decodeToken(token) : null; }
function authHeaders() { return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }; }

// ===== STRIPE =====
const stripe = Stripe('pk_test_51Tbaep9TqKKXsHJPLgXdpxDYWBZiqOUx8MDEVj3Un1s2sx0Xr0hj8ARbttuztRc5KPl1ITneavqQBt4SRnagPSyJ00tP8VTxQh');
const elements = stripe.elements();
const cardElement = elements.create('card');
cardElement.on('change', (e) => { document.getElementById('card-errors').textContent = e.error ? e.error.message : ''; });

// ===== HELPERS =====
function hideAll() { ['step-login', 'step-gateway', 'step-checkout', 'step-card', 'step-address', 'step-logs'].forEach(id => document.getElementById(id).classList.add('hidden')); }
function goToLogin() { localStorage.clear(); token = paymentIntentData = null; selectedGatewayId = null; selectedGatewayTitle = null; hideAll(); document.getElementById('step-login').classList.remove('hidden'); document.getElementById('loginForm').classList.remove('hidden'); document.getElementById('registerForm').classList.add('hidden'); }
function showMsg(el, msg) { el.textContent = msg; el.classList.add('show'); el.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }

// ===== INIT =====
if (token) {
    const user = decodeToken(token);
    if (user) {
        hideAll();
        document.getElementById('step-gateway').classList.remove('hidden');
    } else {
        goToLogin();
    }
} else {
    goToLogin();
}
