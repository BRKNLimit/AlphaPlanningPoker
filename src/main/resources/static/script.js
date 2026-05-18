const fibonacci = ["0", "1", "2", "3", "5", "8", "13", "21", "?", "☕"];
let socket = null;
let myId = null;
let isHost = false;
let myUsername = "";
let isAllInMode = false;
let allInCooldown = 0; // Cooldown in seconds
let lastRoomState = null;

const screens = ['start-screen', 'role-screen', 'game-screen'];

function showScreen(screenId) {
    screens.forEach(id => {
        document.getElementById(id).classList.add('hidden');
    });
    const el = document.getElementById(screenId);
    el.classList.remove('hidden');
    if (screenId === 'game-screen') el.classList.add('flex');
}

// 1. Retro Start Logic
document.getElementById('start-coin').addEventListener('click', function() {
    this.classList.add('fast-spin');
    setTimeout(() => {
        showScreen('role-screen');
    }, 750);
});

// 2. Init Deck
const deckContainer = document.getElementById('my-deck');
fibonacci.forEach(val => {
    const btn = document.createElement('button');
    btn.className = 'poker-card w-12 h-16 sm:w-16 sm:h-24 pixel-font text-lg sm:text-2xl font-bold';
    btn.innerText = val;
    btn.onclick = () => sendVote(val);
    btn.dataset.value = val;
    deckContainer.appendChild(btn);
});

function joinGame(host) {
    const name = document.getElementById('usernameInput').value.trim();
    let room = document.getElementById('roomIdInput').value.trim();

    if (host) {
        myUsername = "Product Owner";
        startSession("");
    } else {
        if (!name) return alert("ENTER YOUR NAME");
        if (!room) return alert("ENTER ROOM CODE");
        if (!room.startsWith('#')) room = '#' + room;
        myUsername = name;
        startSession(room);
    }
}

function startSession(roomId) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/poker`);

    socket.onopen = () => {
        send({
            type: "join",
            username: myUsername,
            roomId: roomId
        });
        showScreen('game-screen');
        document.getElementById('user-display').innerText = myUsername;
    };

    socket.onmessage = (event) => {
        const msg = JSON.parse(event.data);

        if (msg.type === "welcome") { myId = msg.yourId; return; }
        if (msg.type === "allInSlam") { triggerScreenShake(); return; }
        if (msg.type === "reaction") { spawnReaction(msg.emoji); return; }

        // Default Room Update
        if (msg.id) {
            lastRoomState = msg;
            updateUI(msg);
        }
    };

    socket.onclose = () => {
        if (!document.getElementById('game-screen').classList.contains('hidden')) {
            alert("CONNECTION LOST");
            window.location.reload();
        }
    };
}

function updateUI(room) {
    document.getElementById('header-room-id').innerText = room.id;
    
    const table = document.getElementById('table-area');
    table.innerHTML = '';

    const participants = Object.values(room.participants);
    
    // Voting Progress
    const voters = participants.filter(p => !p.isHost);
    const votedCount = voters.filter(p => p.vote).length;
    const totalVoters = voters.length;
    
    let statusText = "WAITING FOR PLAYERS";
    if (totalVoters > 0) {
        statusText = `${votedCount}/${totalVoters} VOTED`;
    }
    document.getElementById('user-display').innerText = `${myUsername} | ${statusText}`;

    // Sync my own isHost status
    const me = room.participants[myId];
    if (me) isHost = me.isHost;

    // Only show non-host participants in the table area
    participants.filter(p => !p.isHost).forEach(p => {
        const playerDiv = document.createElement('div');
        playerDiv.className = 'flex flex-col items-center space-y-3';

        let cardVisual = '';
        if (p.vote) {
            if (room.isRevealed) {
                const foilClass = p.isFoil ? 'card-foil' : 'bg-white text-[#E30613]';
                cardVisual = `<div class="w-20 h-28 border-4 border-[#E30613] rounded-sm flex items-center justify-center pixel-font text-3xl shadow-[0_0_15px_#E30613] ${foilClass} card-reveal">${p.vote}</div>`;
            } else {
                const glowClass = 'shadow-[0_0_30px_#E30613] border-white';
                cardVisual = `
                    <div class="w-20 h-28 bg-[#E30613] border-4 rounded-sm flex items-center justify-center relative overflow-hidden transition-all duration-500 ${glowClass}">
                        <div class="absolute w-full h-full flex items-center justify-center opacity-40">
                            <span class="pixel-font text-white text-4xl">A</span>
                        </div>
                        ${p.isAllIn ? '<span class="text-white font-bold text-[10px] absolute top-2 blink">ALL IN</span>' : ''}
                        <div class="absolute inset-0 bg-white/10 animate-pulse"></div>
                    </div>`;
            }
        } else {
            cardVisual = `<div class="w-20 h-28 border-2 border-dashed border-gray-700 rounded-sm flex items-center justify-center text-gray-700 pixel-font text-[10px]">...</div>`;
        }

        playerDiv.innerHTML = `
            ${cardVisual}
            <div class="pixel-font text-[10px] text-center text-white">
                ${p.name}
            </div>
        `;
        table.appendChild(playerDiv);
    });

    // Host Controls
    if (isHost) {
        document.getElementById('btn-reveal').classList.remove('hidden');
        document.getElementById('btn-reset').classList.remove('hidden');
        document.getElementById('my-deck').classList.add('hidden');
        document.getElementById('btn-allin').parentElement.classList.add('hidden');
        document.getElementById('user-display').innerText = "PRODUCT OWNER";
    } else {
        document.getElementById('btn-reveal').classList.add('hidden');
        document.getElementById('btn-reset').classList.add('hidden');
        document.getElementById('my-deck').classList.remove('hidden');
        document.getElementById('btn-allin').parentElement.classList.remove('hidden');
        document.getElementById('user-display').innerText = myUsername;
    }

    // Selected state for my deck
    if (!room.isRevealed) {
        const myP = room.participants[myId];
        document.querySelectorAll('.poker-card').forEach(btn => {
            if (myP && myP.vote === btn.dataset.value) btn.classList.add('selected');
            else btn.classList.remove('selected');
        });
    } else {
        document.querySelectorAll('.poker-card').forEach(btn => btn.classList.remove('selected'));
    }
}

function send(data) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(data));
    }
}

function sendVote(val) {
    if (isAllInMode && allInCooldown > 0) return alert(`COOLDOWN ACTIVE: ${Math.ceil(allInCooldown / 60)}m`);

    // Only prevent voting if cards are already revealed
    if (lastRoomState && lastRoomState.isRevealed) {
        return; 
    }

    send({ type: "vote", vote: val, isAllIn: isAllInMode });

    if (isAllInMode) {
        startAllInCooldown();
        toggleAllIn(); // Auto-off after use
    }
}

function startAllInCooldown() {
    allInCooldown = 180; // 3 minutes
    const btn = document.getElementById('btn-allin');
    btn.disabled = true;

    const interval = setInterval(() => {
        allInCooldown--;
        const mins = Math.floor(allInCooldown / 60);
        const secs = allInCooldown % 60;
        btn.innerText = `COOLDOWN: ${mins}:${secs.toString().padStart(2, '0')}`;

        if (allInCooldown <= 0) {
            clearInterval(interval);
            btn.disabled = false;
            btn.innerText = 'ALL-IN MODE: OFF';
            btn.className = 'bg-black border-2 border-gray-600 text-gray-400 px-4 py-2 pixel-font text-xs transition';
        }
    }, 1000);
}

function sendReveal() { send({ type: "reveal" }); }
function sendReset() { send({ type: "reset" }); }
function sendReaction(emoji) { send({ type: "reaction", reaction: emoji }); }

function toggleAllIn() {
    isAllInMode = !isAllInMode;
    const btn = document.getElementById('btn-allin');
    if (isAllInMode) {
        btn.className = 'bg-[#E30613] border-2 border-[#E30613] text-white px-4 py-2 pixel-font text-xs font-bold shadow-[0_0_10px_#E30613] transition';
        btn.innerText = 'ALL-IN MODE: ON';
    } else {
        btn.className = 'bg-black border-2 border-gray-600 text-gray-400 px-4 py-2 pixel-font text-xs transition';
        btn.innerText = 'ALL-IN MODE: OFF';
    }
}

function triggerScreenShake() {
    document.body.classList.add('shake');
    setTimeout(() => document.body.classList.remove('shake'), 500);
}

function spawnReaction(emoji) {
    const el = document.createElement('div');
    el.className = 'reaction-emoji';
    el.innerText = emoji;
    el.style.left = Math.random() * 80 + 10 + 'vw';
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3000);
}

window.onload = () => {
    const params = new URLSearchParams(window.location.search);
    const room = params.get('room');
    if (room) {
        // Remove # if present since the UI now has it static
        document.getElementById('roomIdInput').value = room.replace('#', '');
    }
};

function copyInvite() {
    const roomId = document.getElementById('header-room-id').innerText;
    const url = `${window.location.origin}/?room=${roomId.replace('#', '')}`;
    navigator.clipboard.writeText(url).then(() => {
        const btn = document.querySelector('button[onclick="copyInvite()"]');
        const oldText = btn.innerText;
        btn.innerText = "COPIED!";
        setTimeout(() => btn.innerText = oldText, 2000);
    }).catch(err => {
        alert("COULD NOT COPY LINK");
    });
}

window.addEventListener('keydown', (e) => {
    if (document.getElementById('game-screen').classList.contains('hidden')) return;
    if (e.key >= '1' && e.key <= '9') {
        const val = fibonacci[parseInt(e.key)];
        if (val) sendVote(val);
    }
    if (e.key === '0') {
        sendVote(fibonacci[0]);
    }
    if (e.key === ' ' && isHost) { sendReveal(); e.preventDefault(); }
    if (e.key === 'Escape' && isHost) { sendReset(); }
});