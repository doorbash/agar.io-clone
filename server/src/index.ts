import * as express from 'express';

import { Server } from "colyseus";
import { createServer } from "http";
import { PublicRoom } from './rooms/public';
const port = 2560

const app = express();
app.use(express.json());

const gameServer = new Server({
  server: createServer(app),
  express: app,
});

gameServer.define("public", PublicRoom);

gameServer.listen(port);