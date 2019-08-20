import { Schema, type, ArraySchema, MapSchema } from "@colyseus/schema"
import { Room, Client } from "colyseus"

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
const INIT_FRUITS = 50;
const FRUIT_RADIUS = 10;
const PLAYER_MIN_SPEED = 40;
const PLAYER_INIT_SPEED = 120;
const PLAYER_INIT_RADIUS = 40;

var fruitId = 0;

class Player extends Schema {
    @type("float32")
    x: number;

    @type("float32")
    y: number;

    @type("float32")
    radius: number = PLAYER_INIT_RADIUS;

    @type("int32")
    color: number;

    speed: number = PLAYER_INIT_SPEED;
    angle = Math.PI * (Math.random() * 2 - 1);
}

class Fruit extends Schema {
    @type("float32")
    x: number;

    @type("float32")
    y: number;

    @type("int32")
    color: number;
}

class GameState extends Schema {
    @type({ map: Player })
    players = new MapSchema<Player>();

    @type({ map: Fruit })
    fruits = new MapSchema<Fruit>();
}

export class PublicRoom extends Room {

    maxClients = 20;
    autoDispose = true;

    onInit(options) {
        this.setState(new GameState());

        for (var i = 0; i < INIT_FRUITS; i++)
            this.generateFruit();

        this.setSimulationInterval(() => {
            this.updateWorld();
        }, WORLD_UPDATE_INTERVAL);
    }

    onJoin?(client: Client, options?: any, auth?: any): void | Promise<any> {
        console.log('onJoin(', client.id, ')', options);
        var player: Player = new Player();
        player.x = Math.floor(Math.random() * 1200);
        player.y = Math.floor(Math.random() * 1200);
        player.color = PLAYER_COLORS[Math.floor(Math.random() * PLAYER_COLORS.length)];
        this.state.players[client.id] = player;
    }

    onLeave(client) {
        console.log("onLeave(" + client.sessionId + ")");
        delete this.state.players[client.id];
    }

    onMessage(client, data) {
        console.log("Room received message from", client.id, ":", data);
        var player = this.state.players[client.id];
        switch (data.op) {
            case 'angle': {
                player.angle = data.angle * 0.0174533;
            } break;
            case 'ping': {
                this.send(client, 'pong');
            } break;
        }
    }

    onDispose() {
        console.log("Dispose Room");
    }

    updateWorld() {
        Object.keys(this.state.players).forEach(key => {
            // update player position
            var player = this.state.players[key];
            var newX = player.x + Math.cos(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            var newY = player.y + Math.sin(player.angle) * player.speed * WORLD_UPDATE_INTERVAL / 1000;
            if ((newX - player.radius) < 0) newX = player.radius; else if ((newX + player.radius) > 1200) newX = 1200 - player.radius;
            if ((newY - player.radius) < 0) newY = player.radius; else if ((newY + player.radius) > 1200) newY = 1200 - player.radius;
            player.x = newX;
            player.y = newY;

            // check if player eat something
            this.checkIfPlayerIsEatingFruit(player);
            this.checkIfPlayerIsEatingAnotherPlayer(key, player);
        });
    }

    checkIfPlayerIsEatingFruit(player) {
        var eatenFruitKeys = [];
        Object.keys(this.state.fruits).forEach(key => {
            var fruit = this.state.fruits[key];
            if ((Math.pow(fruit.x - player.x, 2) + Math.pow(fruit.y - player.y, 2)) < Math.pow(player.radius + FRUIT_RADIUS, 2)) {
                eatenFruitKeys.push(key);
            }
        });
        eatenFruitKeys.forEach(key => {
            this.eat(player, key);
        });
    }

    eat(player, fruitKey) {
        delete this.state.fruits[fruitKey];
        player.radius += FRUIT_RADIUS / 10;
        var newSpeed = player.speed - FRUIT_RADIUS / 30;
        if (newSpeed > PLAYER_MIN_SPEED) player.speed = newSpeed;
        console.log('yum yum yummm');
        this.generateFruit();
    }

    generateFruit() {
        var fr: Fruit = new Fruit();
        fr.x = FRUIT_RADIUS + Math.random() * (1200 - 2 * FRUIT_RADIUS);
        fr.y = FRUIT_RADIUS + Math.random() * (1200 - 2 * FRUIT_RADIUS);
        fr.color = FRUIT_COLORS[Math.floor(Math.random() * FRUIT_COLORS.length)];
        var key = "fr_" + (fruitId++);
        this.state.fruits[key] = fr;
    }

    checkIfPlayerIsEatingAnotherPlayer(clientId, player) {
        Object.keys(this.state.players).forEach(key => {
            if (key == clientId) return;
            var p = this.state.players[key];
            if (p.radius < player.radius && (Math.pow(p.x - player.x, 2) + Math.pow(p.y - player.y, 2)) < Math.pow(player.radius + p.radius, 2)) {
                this.eatPlayer(player, p);
            }
        });
    }

    eatPlayer(player, player2) {
        player.radius += player2.radius / 10;
        var newSpeed = player.speed - player2.radius / 20;
        if (newSpeed > PLAYER_MIN_SPEED) player.speed = newSpeed;
        console.log('oh nooooo');
        player2.x = Math.floor(Math.random() * 1200);
        player2.y = Math.floor(Math.random() * 1200);
        player2.radius = PLAYER_INIT_RADIUS;
    }

}