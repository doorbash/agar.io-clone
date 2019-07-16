import * as express from 'express';
import { createServer } from 'http';
import { Server } from 'colyseus';

// Import demo room handlers
import { PublicRoom } from "./rooms/public"

const port = 2560
const app = express()

// Attach WebSocket Server on HTTP Server.
const gameServer = new Server({
  server: createServer(app)
});

gameServer.register("public", PublicRoom);

gameServer.onShutdown(function () {
  console.log(`game server is going down.`);
});

gameServer.listen(port, '0.0.0.0');
console.log(`Listening on http://localhost:${port}`);