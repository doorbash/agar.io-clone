import { type, Schema, MapSchema } from "@colyseus/schema";
import Player from "./Player";
import Fruit from "./Fruit";

class GameState extends Schema {
    @type({ map: Player })
    players = new MapSchema<Player>();

    @type({ map: Fruit })
    fruits = new MapSchema<Fruit>();
}

export default GameState;