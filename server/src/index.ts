import * as express from 'express';
import { createServer } from 'http';
import { Server, RedisPresence } from 'colyseus';
import { PublicRoom } from "./rooms/public";
import * as cors from "cors";


const port = Number(process.env.PORT || 3300);

// if (cluster.isMaster) {
//     const cpus = os.cpus().length;
//     console.log('cpus = ' + cpus);
//     for (let i = 0; i < cpus; ++i) {
//         cluster.fork({PORT: 6000});
//     }
// } else {
    const app = express();

    app.use(cors());

    const gameServer = new Server({
        server: createServer(app),
        // presence: new RedisPresence({
        //     // host: "185.8.175.236" 
        // }),
        verifyClient: function (info, next) {
            var userAgent = info.req.headers['user-agent']
            console.log('new connection: useragent: ' + userAgent);
            next(true);
        }
    });

    gameServer.register("public", PublicRoom);


    gameServer.onShutdown(function () {
        console.log(`game server is going down.`);
    });

    gameServer.listen(port);
    console.log(`Listening on http://localhost:${port}`);
// }