import { Room, Client } from "colyseus"
import { IncomingMessage } from "http"
import GameState from "../entities/GameState";
import Player from "../entities/Player";
import Fruit from "../entities/Fruit";
import Constants from "../util/Constants";

class FreeForAll extends Room {

    maxClients = 20;
    autoDispose = true;
    fruitId = 0;

    onCreate?(options: any): void {
        this.setState(new GameState());

        for (var i = 0; i < Constants.INIT_FRUITS; i++)
            this.generateFruit();

        this.setSimulationInterval(() => {
            this.updateWorld();
        }, Constants.WORLD_UPDATE_INTERVAL);
    }

    onAuth(client: Client, options: any, request?: IncomingMessage): any | Promise<any> {
        console.log("onAuth(" + client.id + ")");
        return true;
    }

    onJoin?(client: Client, options?: any, auth?: any): void | Promise<any> {
        console.log('onJoin(', client.id, ')', options);
        if (!this.state.players[client.id]) {
            var player: Player = new Player();
            player.x = Math.floor(Math.random() * 1200);
            player.y = Math.floor(Math.random() * 1200);
            player.color = Constants.PLAYER_COLORS[Math.floor(Math.random() * Constants.PLAYER_COLORS.length)];
            player.online = true;
            this.state.players[client.id] = player;
            console.log("new player added " + client.id);
        }
    }

    onMessage(client: Client, data: any): void {
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

    async onLeave?(client: Client, consented?: boolean): Promise<any> {
        console.log("onLeave(" + client.id + ")");
        let player = this.state.players[client.id];
        if (!player) return;
        player.online = false;
        try {
            if (consented) {
                throw new Error("consented leave");
            }
            console.log("await this.allowReconnection(client, 30);")
            await this.allowReconnection(client, 30);
            player.online = true;
            console.log("player " + client.id + " is back! player.online = true;")
        } catch (e) {
            if (player && !player.online) {
                delete this.state.players[client.id];
            }
        }
    }

    onDispose?(): void | Promise<any> {
        console.log("Dispose Room");
    }

    updateWorld() {
        Object.keys(this.state.players).forEach(key => {
            // update player position
            var player = this.state.players[key];
            var newX = player.x + Math.cos(player.angle) * player.speed * Constants.WORLD_UPDATE_INTERVAL / 1000;
            var newY = player.y + Math.sin(player.angle) * player.speed * Constants.WORLD_UPDATE_INTERVAL / 1000;
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
            if ((Math.pow(fruit.x - player.x, 2) + Math.pow(fruit.y - player.y, 2)) < Math.pow(player.radius + Constants.FRUIT_RADIUS, 2)) {
                eatenFruitKeys.push(key);
            }
        });
        eatenFruitKeys.forEach(key => {
            this.eat(player, key);
        });
    }

    eat(player, fruitKey) {
        delete this.state.fruits[fruitKey];
        player.radius += Constants.FRUIT_RADIUS / 10;
        var newSpeed = player.speed - Constants.FRUIT_RADIUS / 30;
        if (newSpeed > Constants.PLAYER_MIN_SPEED) player.speed = newSpeed;
        console.log('yum yum yummm');
        this.generateFruit();
    }

    generateFruit() {
        var fr: Fruit = new Fruit();
        fr.x = Constants.FRUIT_RADIUS + Math.random() * (1200 - 2 * Constants.FRUIT_RADIUS);
        fr.y = Constants.FRUIT_RADIUS + Math.random() * (1200 - 2 * Constants.FRUIT_RADIUS);
        fr.color = Constants.FRUIT_COLORS[Math.floor(Math.random() * Constants.FRUIT_COLORS.length)];
        var key = "fr_" + (this.fruitId++);
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
        if (newSpeed > Constants.PLAYER_MIN_SPEED) player.speed = newSpeed;
        console.log('oh nooooo');
        player2.x = Math.floor(Math.random() * 1200);
        player2.y = Math.floor(Math.random() * 1200);
        player2.radius = Constants.PLAYER_INIT_RADIUS;
    }

}

export default FreeForAll;