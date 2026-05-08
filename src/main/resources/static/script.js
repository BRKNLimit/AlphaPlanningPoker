let socket = null;
let myId = null;
let isHost = false;

const joinScreen = document.getElementById('join-screen');
const gameScreen = document.getElementById('game-screen');
const roomDisplay = document.getElementById('room-display');
const participantsGrid = document.getElementById('participants');
const hostControls = document.getElementById('host-controls');
const cards = document.querySelectorAll('.card');

document.getElementById('join-btn').addEventListener('click', joinRoom);
document.getElementById('reveal-btn').addEventListener('click', () => send({type: 'reveal'}));
document.getElementById('reset-btn').addEventListener('click', () => send({type: 'reset'}));

cards.forEach(card => {
    card.addEventListener('click', () => {
        const value = card.dataset.value;
        cards.forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        send({type: 'vote', vote: value});
    });
});

function joinRoom() {
    const name = document.getElementById('name-input').value || 'Alpha';
    const roomId = document.getElementById('room-input').value || 'ALPHA';

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/poker`);

    socket.onopen = () => {
        send({type: 'join', roomId, name});
        joinScreen.classList.add('hidden');
        gameScreen.classList.remove('hidden');
        roomDisplay.innerText = `ROOM: ${roomId.toUpperCase()}`;
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
    participantsGrid.innerHTML = '';
    
    // Check if I am host
    // (Note: In a real app, the server would tell us our ID, but for this proto 
    // we'll just check if any participant matches the name and has isHost=true)
    // Actually, let's just use the first room update to find ourself if we don't know our ID.
    
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

        // Simple host check based on name (improvement: server should send ID)
        if (p.isHost && p.name === document.getElementById('name-input').value) {
            isHost = true;
            hostControls.classList.remove('hidden');
        }
    });

    if (!room.isRevealed) {
        // Reset local selection if room was reset
        if (Object.values(room.participants).every(p => !p.vote)) {
            cards.forEach(c => c.classList.remove('selected'));
        }
    }
}

// Key shortcuts
window.addEventListener('keydown', (e) => {
    if (gameScreen.classList.contains('hidden')) return;

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
