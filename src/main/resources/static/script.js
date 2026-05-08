let socket = null;
let isHost = false;
let isAdmin = false;

const screens = ['login-screen', 'register-screen', 'join-screen', 'game-screen', 'admin-screen'];

function showScreen(screenId) {
    screens.forEach(id => {
        document.getElementById(id).classList.add('hidden');
    });
    document.getElementById(screenId).classList.remove('hidden');
}

// Auth Handlers
document.getElementById('login-btn').addEventListener('click', async () => {
    const username = document.getElementById('login-user').value;
    const password = document.getElementById('login-pass').value;
    
    const res = await fetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    
    if (data.status === 'OK') {
        isAdmin = data.isAdmin;
        document.getElementById('user-display').innerText = username.toUpperCase();
        if (isAdmin) document.getElementById('admin-panel-btn').classList.remove('hidden');
        showScreen('join-screen');
    } else if (data.status === 'PENDING') {
        alert('WAITING FOR ADMIN APPROVAL');
    } else {
        alert('INVALID CREDENTIALS');
    }
});

document.getElementById('reg-btn').addEventListener('click', async () => {
    const username = document.getElementById('reg-user').value;
    const password = document.getElementById('reg-pass').value;
    
    const res = await fetch('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    
    if (data.status === 'OK') {
        alert('REGISTRATION SUCCESSFUL. WAIT FOR ADMIN APPROVAL.');
        showScreen('login-screen');
    } else {
        alert('REGISTRATION FAILED');
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
            <span>${user.username} (${user.status})</span>
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

// Game Handlers
document.getElementById('enter-btn').addEventListener('click', joinRoom);
document.getElementById('reveal-btn').addEventListener('click', () => send({type: 'reveal'}));
document.getElementById('reset-btn').addEventListener('click', () => send({type: 'reset'}));

document.querySelectorAll('.card').forEach(card => {
    card.addEventListener('click', () => {
        const value = card.dataset.value;
        document.querySelectorAll('.card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        send({type: 'vote', vote: value});
    });
});

function joinRoom() {
    const roomId = document.getElementById('room-input').value || 'ALPHA';

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/poker`);

    socket.onopen = () => {
        send({type: 'join', roomId});
        showScreen('game-screen');
        document.getElementById('room-display').classList.remove('hidden');
        document.getElementById('room-display').innerText = `ROOM: ${roomId.toUpperCase()}`;
    };

    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        updateUI(data);
    };

    socket.onclose = () => {
        alert('Connection lost');
        window.location.reload();
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
        const div = document.createElement('div');
        div.className = `participant-card ${p.vote ? 'voted' : ''}`;
        if (room.isRevealed && p.vote) {
            div.classList.add('reveal-animation');
        }
        
        let voteContent = '';
        if (room.isRevealed) {
            voteContent = `<div class="vote-value">${p.vote || '-'}</div>`;
        } else {
            voteContent = p.vote ? `<div class="vote-hidden"></div>` : `<div class="vote-value">-</div>`;
        }
        
        div.innerHTML = `
            ${voteContent}
            <div class="participant-name">${p.name} ${p.isHost ? '👑' : ''}</div>
        `;
        participantsGrid.appendChild(div);

        if (p.isHost && p.name === document.getElementById('user-display').innerText) {
            isHost = true;
            document.getElementById('host-controls').classList.remove('hidden');
        }
    });

    if (!room.isRevealed) {
        if (Object.values(room.participants).every(p => !p.vote)) {
            document.querySelectorAll('.card').forEach(c => c.classList.remove('selected'));
        }
    }
}

// Shortcuts
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

    if (e.key === ' ' && isHost) {
        send({type: 'reveal'});
        e.preventDefault();
    }

    if (e.key === 'Escape' && isHost) {
        send({type: 'reset'});
    }
});
