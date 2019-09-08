import { type, Schema } from "@colyseus/schema";
import Constants from "../util/Constants";

class Player extends Schema {
    @type("float32")
    x: number;

    @type("float32")
    y: number;

    @type("float32")
    radius: number = Constants.PLAYER_INIT_RADIUS;

    @type("int32")
    color: number;

    speed: number = Constants.PLAYER_INIT_SPEED;
    angle = Math.PI * (Math.random() * 2 - 1);
    online: boolean;
}

export default Player;