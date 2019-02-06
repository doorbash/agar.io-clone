import { Room, Presence, nosync } from "colyseus";

const PLAYER_COLORS: number[] = [
    0x4cb050FF,
    0xe6194bFF, 0x3cb44bFF, 0xffe119FF, 0x4363d8FF,
    0xf58231FF, 0x911eb4FF, 0x46f0f0FF, 0xf032e6FF,
    0xbcf60cFF, 0xfabebeFF, 0x008080FF, 0xe6beffFF,
    0x9a6324FF, 0xfffac8FF, 0x800000FF, 0xaaffc3FF,
];

const FRUIT_COLORS: number[] = [
    0xFF0000FF,
    0x00FF00FF,
    0x0000FFFF
];

const WORLD_UPDATE_INTERVAL = 16; // ms
const FRUIT_RADIUS = 10;
const EAT_RADIUS_CHANGE = 1;
const EAT_SPEED_CHANGE = 0.5;
const INIT_FRUITS = 50;
const PLAYER_MIN_SPEED = 40;
const PLAYER_INIT_SPEED = 120;

export class Player {
    x: number = 0;
    y: number = 0;
    radius: number = 40;
    color: number;
    @nosync speed: number = PLAYER_INIT_SPEED;
    @nosync angle = Math.PI * (Math.random() * 2 - 1);
}

export class Fruit {
    x: number;
    y: number;
    color: number;
    eaten: boolean;
}

export class GameState {
    players = {};
    mapSize = {
        width: 1200, height: 1200
    };
    fruits = [];
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

        var state = new GameState();
        state.players = {};
        this.setState(state);

        for (var i = 0; i < INIT_FRUITS; i++)
            this.generateFruit();

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
        this.state.players[client.id].color = PLAYER_COLORS[Math.floor(Math.random() * PLAYER_COLORS.length)];
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
                player.angle = data.angle * 0.0174533;
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
            // update player position
            var player = this.state.players[key];
            var newX = player.x + Math.cos(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            var newY = player.y + Math.sin(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            if ((newX - player.radius) < 0) newX = player.radius; else if ((newX + player.radius) > this.state.mapSize.width) newX = this.state.mapSize.width - player.radius;
            if ((newY - player.radius) < 0) newY = player.radius; else if ((newY + player.radius) > this.state.mapSize.height) newY = this.state.mapSize.height - player.radius;
            player.x = newX;
            player.y = newY;

            // check if player eat something
            this.checkIfPlayerIsEatingFruit(player);
        });
    }

    checkIfPlayerIsEatingFruit(player) {
        this.state.fruits.forEach(fruit => {
            if (fruit.eaten) return;
            if ((fruit.x - FRUIT_RADIUS) > (player.x - player.radius) &&
                (fruit.y - FRUIT_RADIUS) > (player.y - player.radius) &&
                (fruit.x + FRUIT_RADIUS) < (player.x + player.radius) &&
                (fruit.y + FRUIT_RADIUS) < (player.y + player.radius)) {
                this.eat(player, fruit);
            }
        });
    }

    eat(player, fruit) {
        fruit.eaten = true;
        player.radius += EAT_RADIUS_CHANGE;
        if (player.speed > PLAYER_MIN_SPEED) player.speed -= EAT_SPEED_CHANGE;
        console.log('yum yum yummm');
        this.generateFruit();
    }

    generateFruit() {
        var fr: Fruit = new Fruit();
        fr.x = FRUIT_RADIUS + Math.random() * (this.state.mapSize.width - 2 * FRUIT_RADIUS);
        fr.y = FRUIT_RADIUS + Math.random() * (this.state.mapSize.height - 2 * FRUIT_RADIUS);
        fr.color = FRUIT_COLORS[Math.floor(Math.random() * FRUIT_COLORS.length)];
        fr.eaten = false;
        this.state.fruits.push(fr);
    }

}
