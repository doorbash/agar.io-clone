import * as express from 'express';
import { Server } from "colyseus";
import { createServer } from "http";
import FreeForAll from './rooms/FreeForAll';

const port = 2560

const app = express();
app.use(express.json());

const gameServer = new Server({
  server: createServer(app),
  express: app,
});

gameServer.define("ffa", FreeForAll);

gameServer.listen(port);