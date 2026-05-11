let socket = null;
let isHost = false;
let myUsername = "Player_" + Math.floor(Math.random() * 1000);

const screens = ['start-screen', 'role-screen', 'game-screen'];

function showScreen(screenId) {
    screens.forEach(id => {
        document.getElementById(id).classList.add('hidden');
    });
    document.getElementById(screenId).classList.remove('hidden');
}

// Retro Start Logic
document.getElementById('start-coin').addEventListener('click', function() {
    this.classList.add('fast-spin');
    setTimeout(() => {
        showScreen('role-screen');
    }, 750);
});

document.getElementById('host-opt').addEventListener('click', () => {
    isHost = true;
    joinRoom("NEW_SESSION");
});

document.getElementById('join-btn').addEventListener('click', () => {
    const roomId = document.getElementById('room-input').value.trim();
    if (!roomId) return alert("ENTER ROOM CODE");
    isHost = false;
    joinRoom(roomId);
});

// Reactions
document.querySelectorAll('.reaction-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        send({ type: 'reaction', reaction: btn.dataset.emoji });
    });
});

// Game Handlers
document.getElementById('reveal-btn').addEventListener('click', () => send({type: 'reveal'}));
document.getElementById('reset-btn').addEventListener('click', () => send({type: 'reset'}));

document.querySelectorAll('.card').forEach(card => {
    card.addEventListener('click', () => {
        if (isHost) return; // Host cannot vote
        const value = card.dataset.value;
        
        document.querySelectorAll('.card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        
        send({
            type: 'vote', 
            vote: value
        });
    });
});

function joinRoom(roomId) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    
    if (socket) socket.close();
    socket = new WebSocket(`${protocol}//${window.location.host}/poker`);

    socket.onopen = () => {
        send({type: 'join', roomId, username: myUsername});
        showScreen('game-screen');
        document.getElementById('room-info').classList.remove('hidden');
        document.getElementById('user-display').innerText = myUsername;
    };

    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        if (data.type === 'reaction') return triggerReaction(data.emoji);
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
        
        // The first person in a new room is automatically host
        // We sync our local isHost with the server's participant state
        if (isMe) {
            isHost = p.isHost;
        }

        const div = document.createElement('div');
        div.id = `participant-${p.id}`;
        div.className = `participant-card ${p.vote ? 'voted' : ''} ${p.isFoil ? 'foil-card' : ''}`;
        
        if (room.isRevealed && p.vote) {
            div.classList.add('reveal-animation');
            div.classList.add('revealed');
        }
        
        let voteContent = '';
        if (room.isRevealed) {
            voteContent = `<div class="vote-value">${p.vote || '-'}</div>`;
        } else {
            voteContent = p.vote ? `<div class="vote-hidden"></div>` : `<div class="vote-value">-</div>`;
        }
        
        const nameLabel = p.isHost ? `${p.name} [MASTER] 👑` : p.name;

        div.innerHTML = `
            ${p.isHost ? '<div class="vote-value" style="font-size:1rem; color:#666;">MASTER</div>' : voteContent}
            <div class="participant-name">${nameLabel}</div>
        `;
        participantsGrid.appendChild(div);

        if (isMe) {
            if (isHost) {
                document.getElementById('host-controls').classList.remove('hidden');
                document.querySelector('.cards-container').classList.add('hidden');
            } else {
                document.getElementById('host-controls').classList.add('hidden');
                document.querySelector('.cards-container').classList.remove('hidden');
            }
        }
    });

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

function triggerCleanSweep() {
    const overlay = document.getElementById('sweep-overlay');
    overlay.classList.remove('hidden');
    document.body.classList.add('consensus-pulse');
    setTimeout(() => {
        overlay.classList.add('hidden');
        document.body.classList.remove('consensus-pulse');
    }, 3000);
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
