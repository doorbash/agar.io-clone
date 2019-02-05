import { Room, Presence, nosync } from "colyseus";

const COLORS: number[] = [
    0x4cb050,
    0xe6194b, 0x3cb44b, 0xffe119, 0x4363d8,
    0xf58231, 0x911eb4, 0x46f0f0, 0xf032e6,
    0xbcf60c, 0xfabebe, 0x008080, 0xe6beff,
    0x9a6324, 0xfffac8, 0x800000, 0xaaffc3,
];

const WORLD_UPDATE_INTERVAL = 16; // ms

export class Player {
    x: number = 0;
    y: number = 0;
    radius: number = 40;
    color: number;
    @nosync speed: number = 100;
    @nosync angle = Math.PI * (Math.random() * 2 - 1);
}

export class GameState {
    players = {};
    mapSize = {
        width: 300, height: 300
    };
}

export class PublicRoom extends Room<GameState> {

    maxClients = 20;
    autoDispose = false;

    constructor(presence?: Presence) {
        super(presence);
    }

    requestJoin(options, isNew) {
        return true;
    }

    onInit(options) {
        console.log(this.getLogTag(), "Game Room created!", options);

        var gameState = new GameState();
        gameState.players = {};
        this.setState(gameState);

        this.setSimulationInterval(() => {
            // update world here
            this.updateWorld();
        }, 16);
    }

    onJoin(client, options?, auth?) {
        console.log(this.getLogTag(), 'Client joined: ' + client.id);
        this.state.players[client.id] = new Player();
        this.state.players[client.id].x = Math.floor(Math.random() * this.state.mapSize.width);
        this.state.players[client.id].y = Math.floor(Math.random() * this.state.mapSize.height);
        this.state.players[client.id].color = COLORS[Math.floor(Math.random() * COLORS.length)];
    }

    onLeave(client, consented?) {
        console.log(this.getLogTag(), 'Client left: ' + client.id);
        delete this.state.players[client.id];
    }

    onMessage(client, data) {
        console.log(this.getLogTag(), "Received message from " + client.id + " :", data);
        var player = this.state.players[client.id];
        switch (data.op) {
            case 'angle': {
                player.angle = data.angle;
            } break;
        }
    }

    onDispose() {
        console.log(this.getLogTag(), "Room disposed");
    }

    getLogTag() {
        return '[' + (new Date()).toLocaleString() + '] ' + this.roomName + '(' + this.roomId + '):';
    }

    updateWorld() {
        Object.keys(this.state.players).forEach(key => {
            var player = this.state.players[key];
            var newX = player.x + Math.cos(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            var newY = player.y + Math.sin(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            if(newX < 0) newX = 0; else if(newX > this.state.mapSize.width) newX = this.state.mapSize.width;
            if(newY < 0) newY = 0; else if(newY > this.state.mapSize.height) newY = this.state.mapSize.height;
            player.x = newX;
            player.y = newY;
        });
    }

}
