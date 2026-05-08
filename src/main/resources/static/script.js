let socket = null;
let isHost = false;
let isAdmin = false;
let myUsername = "";
let chips = parseInt(localStorage.getItem('alpha_chips')) || 10;

const screens = ['login-screen', 'register-screen', 'join-screen', 'game-screen', 'admin-screen'];

function showScreen(screenId) {
    screens.forEach(id => {
        document.getElementById(id).classList.add('hidden');
    });
    document.getElementById(screenId).classList.remove('hidden');
}

// Initial chip sync
updateChipsUI();

function updateChipsUI() {
    localStorage.setItem('alpha_chips', chips);
    const display = document.getElementById('chips-display');
    if (display) display.innerText = `CHIPS: ${chips}`;
    const slider = document.getElementById('wager-slider');
    if (slider) slider.max = chips;
}

// Auth Handlers
document.getElementById('login-btn').addEventListener('click', async () => {
    const username = document.getElementById('login-user').value.trim();
    const password = document.getElementById('login-pass').value.trim();
    if (!username || !password) return alert("ENTER CREDENTIALS");

    try {
        const res = await fetch('/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        
        if (data.status === 'OK') {
            isAdmin = data.isAdmin;
            myUsername = username;
            document.getElementById('user-display').innerText = username.toUpperCase();
            if (isAdmin) document.getElementById('admin-panel-btn').classList.remove('hidden');
            showScreen('join-screen');
        } else {
            alert(data.message || 'LOGIN FAILED');
        }
    } catch (e) {
        alert("SERVER DOWN");
    }
});

document.getElementById('reg-btn').addEventListener('click', async () => {
    const username = document.getElementById('reg-user').value.trim();
    const password = document.getElementById('reg-pass').value.trim();
    if (!username || !password) return alert("ENTER CREDENTIALS");

    try {
        const res = await fetch('/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (data.status === 'OK') {
            alert('REGISTERED. WAIT FOR ADMIN APPROVAL.');
            showScreen('login-screen');
        } else {
            alert(data.message || 'REGISTRATION FAILED');
        }
    } catch (e) {
        alert("SERVER DOWN");
    }
});

// Admin Handlers
document.getElementById('admin-panel-btn').addEventListener('click', loadAdminPanel);

async function loadAdminPanel() {
    showScreen('admin-screen');
    const res = await fetch('/admin/users');
    const users = await res.json();
    
    const list = document.getElementById('user-list');
    list.innerHTML = '';
    users.forEach(user => {
        const item = document.createElement('div');
        item.className = 'admin-user-item';
        item.innerHTML = `
            <span>${user.username} [${user.status}]</span>
            ${user.status === 'PENDING' ? `<button onclick="approveUser('${user.username}')">APPROVE</button>` : ''}
        `;
        list.appendChild(item);
    });
}

async function approveUser(username) {
    await fetch('/admin/approve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUsername: username })
    });
    loadAdminPanel();
}

// Betting UI
const wagerSlider = document.getElementById('wager-slider');
const wagerAmount = document.getElementById('wager-amount');
if (wagerSlider) {
    wagerSlider.addEventListener('input', () => {
        wagerAmount.innerText = wagerSlider.value;
    });
}

const allInBtn = document.getElementById('all-in-btn');
if (allInBtn) {
    allInBtn.addEventListener('click', () => {
        wagerSlider.value = chips;
        wagerAmount.innerText = chips;
    });
}

// Reactions
document.querySelectorAll('.reaction-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        send({ type: 'reaction', reaction: btn.dataset.emoji });
    });
});

// Game Handlers
document.getElementById('enter-btn').addEventListener('click', joinRoom);
document.getElementById('reveal-btn').addEventListener('click', () => send({type: 'reveal'}));
document.getElementById('reset-btn').addEventListener('click', () => send({type: 'reset'}));

document.querySelectorAll('.card').forEach(card => {
    card.addEventListener('click', () => {
        if (isHost) return; // Host cannot vote
        const value = card.dataset.value;
        const wager = parseInt(wagerSlider.value);
        const isAllIn = (wager >= chips && chips > 0);
        
        document.querySelectorAll('.card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        
        send({
            type: 'vote', 
            vote: value, 
            wager: wager,
            isAllIn: isAllIn
        });
    });
});

function joinRoom() {
    const roomId = document.getElementById('room-input').value.trim() || 'ALPHA';
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    
    if (socket) socket.close();
    socket = new WebSocket(`${protocol}//${window.location.host}/poker`);

    socket.onopen = () => {
        send({type: 'join', roomId, chips: chips});
        showScreen('game-screen');
        document.getElementById('room-info').classList.remove('hidden');
    };

    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        if (data.type === 'reaction') return triggerReaction(data.emoji);
        if (data.type === 'allInSlam') return triggerSlam(data.pId);
        if (data.type === 'cleanSweep') return triggerCleanSweep();

        // Room Update
        if (data.id && data.name) {
            document.getElementById('room-name-display').innerText = data.name.toUpperCase();
            document.getElementById('room-id-display').innerText = data.id;
            updateUI(data);
        }
    };

    socket.onclose = (e) => {
        if (!document.getElementById('game-screen').classList.contains('hidden')) {
            alert('CONNECTION LOST');
            window.location.reload();
        }
    };
}

function send(data) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(data));
    }
}

function updateUI(room) {
    const participantsGrid = document.getElementById('participants');
    participantsGrid.innerHTML = '';
    
    Object.values(room.participants).forEach(p => {
        const isMe = p.name === myUsername;
        if (isMe) {
            chips = p.chips;
            isHost = p.isHost;
        }

        const div = document.createElement('div');
        div.id = `participant-${p.id}`;
        div.className = `participant-card ${p.vote ? 'voted' : ''} ${p.isAllIn ? 'all-in' : ''} ${p.isFoil ? 'foil-card' : ''}`;
        
        if (room.isRevealed && p.vote) div.classList.add('reveal-animation');
        
        let voteContent = '';
        if (room.isRevealed) {
            voteContent = `<div class="vote-value">${p.vote || '-'}</div>`;
            if (isMe && p.vote === room.consensusValue && room.consensusValue != null) triggerCoinShower();
        } else {
            voteContent = p.vote ? `<div class="vote-hidden"></div>` : `<div class="vote-value">-</div>`;
        }
        
        // Hosts (Scrum Masters) have a special label and no chips/bet display
        const nameLabel = p.isHost ? `${p.name} [MASTER] 👑` : p.name;
        const betLabel = (!p.isHost && p.currentWager > 0) ? `BET: ${p.currentWager}` : '';

        div.innerHTML = `
            ${p.isHost ? '<div class="vote-value" style="font-size:1rem; color:#666;">MASTER</div>' : voteContent}
            <div class="participant-name">${nameLabel}</div>
            <div style="font-size:0.5rem; color:gold;">${betLabel}</div>
        `;
        participantsGrid.appendChild(div);

        if (isMe) {
            if (isHost) {
                document.getElementById('host-controls').classList.remove('hidden');
                document.querySelector('.cards-container').classList.add('hidden');
                document.querySelector('.betting-ui').classList.add('hidden');
                document.getElementById('chips-display').classList.add('hidden');
            } else {
                document.getElementById('host-controls').classList.add('hidden');
                document.querySelector('.cards-container').classList.remove('hidden');
                document.querySelector('.betting-ui').classList.remove('hidden');
                document.getElementById('chips-display').classList.remove('hidden');
            }
        }
    });

    updateChipsUI();
    if (!room.isRevealed && Object.values(room.participants).every(p => !p.vote)) {
        document.querySelectorAll('.card').forEach(c => c.classList.remove('selected'));
    }
}

function triggerReaction(emoji) {
    const el = document.createElement('div');
    el.className = 'floating-emoji';
    el.innerText = emoji;
    el.style.left = Math.random() * 80 + 10 + '%';
    el.style.bottom = '0';
    document.getElementById('animation-layer').appendChild(el);
    setTimeout(() => el.remove(), 2000);
}

function triggerSlam(pId) {
    const card = document.getElementById(`participant-${pId}`);
    if (card) card.classList.add('slam-animation');
    document.body.classList.add('shake');
    setTimeout(() => {
        document.body.classList.remove('shake');
        if (card) card.classList.remove('slam-animation');
    }, 500);
}

function triggerCleanSweep() {
    const overlay = document.getElementById('sweep-overlay');
    overlay.classList.remove('hidden');
    triggerCoinShower();
    setTimeout(() => overlay.classList.add('hidden'), 3000);
}

function triggerCoinShower() {
    const layer = document.getElementById('animation-layer');
    for (let i = 0; i < 30; i++) {
        const coin = document.createElement('div');
        coin.className = 'coin';
        coin.style.left = Math.random() * 100 + 'vw';
        coin.style.animation = `coin-fall ${Math.random() * 2 + 1}s linear forwards`;
        layer.appendChild(coin);
        setTimeout(() => coin.remove(), 3000);
    }
}

window.addEventListener('keydown', (e) => {
    if (document.getElementById('game-screen').classList.contains('hidden')) return;
    if (e.key >= '1' && e.key <= '9') {
        const values = ['1', '2', '3', '5', '8', '13', '21', '34', '55'];
        const val = values[parseInt(e.key) - 1];
        if (val) {
            const card = document.querySelector(`.card[data-value="${val}"]`);
            if (card) card.click();
        }
    }
    if (e.key === ' ' && isHost) { send({type: 'reveal'}); e.preventDefault(); }
    if (e.key === 'Escape' && isHost) { send({type: 'reset'}); }
});
