package ir.doorbash.agar.io;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.colyseus.Client;
import io.colyseus.Room;
import io.colyseus.serializer.schema.DataChange;
import ir.doorbash.agar.io.classes.Fruit;
import ir.doorbash.agar.io.classes.GameState;
import ir.doorbash.agar.io.classes.Player;

public class Game extends ApplicationAdapter {

    /* *************************************** CONSTANTS *****************************************/

    private static final String ENDPOINT = "ws://192.168.1.134:2560";
    private static final int NETWORK_UPDATE_INTERVAL = 200;
    private static final int CHECK_LATENCY_INTERVAL = 10000;
    private static final int GRID_SIZE = 60;
    private static final int FRUIT_RADIUS = 10;
    private static final int LATENCY_MIN = 100; // ms
    private static final int LATENCY_MAX = 500; // ms
    private static final float LERP_MIN = 0.1f;
    private static final float LERP_MAX = 0.5f;
    private static final float OTHER_PLAYERS_LERP = 0.5f;

    /* **************************************** FIELDS *******************************************/

    private int lastAngle = -1000;
    private int mapWidth = 1200;
    private int mapHeight = 1200;
    private long lastNetworkUpdateTime;
    private long lastLatencyCheckTime;
    private float lerp = LERP_MAX;

    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;

    private Client client;
    private Room<GameState> room;
    private LinkedHashMap<String, Object> message;
    private FPSLogger fpsLogger;
    private final HashMap<String, Fruit> fruits = new HashMap<>();
    private final HashMap<String, Player> players = new HashMap<>();

    /* *************************************** OVERRIDE *****************************************/

    @Override
    public void create() {
        fpsLogger = new FPSLogger();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.update();
        message = new LinkedHashMap<>();
        message.put("op", "angle");
        connectToServer();
    }

    @Override
    public void render() {
//        fpsLogger.log();
        updatePositions();
        Gdx.gl.glClearColor(0.98f, 0.99f, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        adjustCamera();
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawGrid();
        drawFruits();
        drawPlayers();
        shapeRenderer.end();

        long now = System.currentTimeMillis();
        if (now - lastNetworkUpdateTime >= NETWORK_UPDATE_INTERVAL) {
            lastNetworkUpdateTime = now;
            networkUpdate();
        }
        if (now - lastLatencyCheckTime >= CHECK_LATENCY_INTERVAL) {
            lastLatencyCheckTime = now;
            checkLatency();
        }
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        if (room != null) room.leave();
        if (client != null) client.close();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /* ***************************************** DRAW *******************************************/

    private void drawGrid() {
        Player player;
        int leftBottomX;
        int leftBottomY;
        int rightTopX;
        int rightTopY;

        if (room != null && (player = room.state.players.get(client.getId())) != null) {
            leftBottomX = (int) (player.position.x - camera.viewportWidth / 2);
            leftBottomY = (int) (player.position.y - camera.viewportHeight / 2);
        } else {
            leftBottomX = (int) (-camera.viewportWidth / 2);
            leftBottomY = (int) (-camera.viewportHeight / 2);
        }

        leftBottomX -= leftBottomX % GRID_SIZE + GRID_SIZE;
        leftBottomY -= leftBottomY % GRID_SIZE + GRID_SIZE;
        rightTopX = (int) (leftBottomX + camera.viewportWidth + 2 * GRID_SIZE);
        rightTopY = (int) (leftBottomY + camera.viewportHeight + 2 * GRID_SIZE);

        shapeRenderer.setColor(Color.BLACK);
        for (int i = leftBottomX; i < rightTopX; i += GRID_SIZE) {
            shapeRenderer.line(i, leftBottomY, i, rightTopY);
        }

        for (int i = leftBottomY; i < rightTopY; i += GRID_SIZE) {
            shapeRenderer.line(leftBottomX, i, rightTopX, i);
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(0, 0, mapWidth, mapHeight);
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void drawFruits() {
        if (room == null) return;
        Player thisPlayer = room.state.players.get(client.getId());
        if (thisPlayer == null) return;
        synchronized (fruits) {
            for (Fruit fr : fruits.values()) {
                if (fr == null) continue;
                if (!objectIsInViewport(thisPlayer, fr.position.x, fr.position.y, FRUIT_RADIUS))
                    continue;
                shapeRenderer.setColor(fr._color);
                shapeRenderer.circle(fr.position.x, fr.position.y, FRUIT_RADIUS);
            }
        }
    }

    private void drawPlayers() {
        if (room == null) return;
        Player thisPlayer = room.state.players.get(client.getId());
        if (thisPlayer == null) return;
        synchronized (players) {
            for (Map.Entry<String, Player> keyValue : players.entrySet()) {
                String clientId = keyValue.getKey();
                Player player = keyValue.getValue();
                if (player == null) continue;
                if (clientId.equals(client.getId()) || objectIsInViewport(thisPlayer, player.position.x, player.position.y, player.radius)) {
                    shapeRenderer.setColor(player._strokeColor);
                    shapeRenderer.circle(player.position.x, player.position.y, player.radius);
                    shapeRenderer.setColor(player._color);
                    shapeRenderer.circle(player.position.x, player.position.y, player.radius - 3);
                }
            }
        }
    }

    /* ***************************************** LOGIC *******************************************/

    private void updatePositions() {
        if (room == null) return;
        synchronized (players) {
            for (Map.Entry<String, Player> keyValue : players.entrySet()) {
                String clientId = keyValue.getKey();
                Player player = keyValue.getValue();
                if (player == null) continue;
                if (clientId.equals(client.getId()))
                    player.position.lerp(player.newPosition, lerp);
                else
                    player.position.lerp(player.newPosition, OTHER_PLAYERS_LERP);
            }
        }
    }

    private boolean objectIsInViewport(Player player, float x, float y, float radius) {
        if (x + radius < player.position.x - camera.viewportWidth / 2) return false;
        if (x - radius > player.position.x + camera.viewportWidth / 2) return false;
        if (y + radius < player.position.y - camera.viewportHeight / 2) return false;
        if (y - radius > player.position.y + camera.viewportHeight / 2) return false;
        return true;
    }

    private void adjustCamera() {
        if (room == null) return;
        Player player = room.state.players.get(client.getId());
        if (player == null) return;
        camera.position.set(new Vector3(player.position.x, player.position.y, 0));
        camera.zoom = 1;
        camera.update();
    }

    /* **************************************** NETWORK ******************************************/

    private void connectToServer() {
        System.out.println("connectToServer()");
        client = new Client(ENDPOINT, new Client.Listener() {
            @Override
            public void onOpen(String id) {
                room = client.join("public", GameState.class);
                room.addListener(new Room.Listener() {

                    @Override
                    protected void onMessage(Object message) {
                        if (message.equals("pong")) {
                            calculateLerp(System.currentTimeMillis() - lastLatencyCheckTime);
                        }
                    }

                    @Override
                    protected void onJoin() {
                        System.out.println("joined to room");
                        room.state.players.onAdd = (player, key) -> {
                            synchronized (players) {
                                players.put(key, player);
                            }
                            System.out.println("new player added >> clientId: " + key);
                            player.position.x = player.x;
                            player.position.y = player.y;
                            player._color = new Color(player.color);
                            player._strokeColor = new Color(player.color);
                            player._strokeColor.mul(0.9f);
                            player.onChange = changes -> {
                                for (DataChange change : changes) {
                                    switch (change.field) {
                                        case "x":
                                            player.newPosition.x = (float) change.value;
                                            break;
                                        case "y":
                                            player.newPosition.y = (float) change.value;
                                            break;
                                    }
                                }
                            };
                        };

                        room.state.players.onRemove = (player, key) -> {
                            synchronized (players) {
                                players.remove(key);
                            }
                        };

                        room.state.fruits.onAdd = (fruit, key) -> {
                            synchronized (fruits) {
                                fruits.put(key, fruit);
                            }
                            System.out.println("new fruit added >> key: " + key);
                            fruit.position.x = fruit.x;
                            fruit.position.y = fruit.y;
                            fruit._color = new Color(fruit.color);
                        };

                        room.state.fruits.onRemove = (fruit, key) -> {
                            synchronized (fruits) {
                                fruits.remove(key);
                            }
                        };
                    }
                });
            }

            @Override
            public void onMessage(Object o) {
                System.out.println(o);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                System.out.println("Client.onClose()");
                try {
                    Thread.sleep(2000);
                    connectToServer();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void networkUpdate() {
        if (room == null) return;
        float dx = Gdx.input.getX() - camera.viewportWidth / 2;
        float dy = Gdx.input.getY() - camera.viewportHeight / 2;
        int angle = (int) Math.toDegrees(Math.atan2(-dy, dx));
        if (lastAngle != angle) {
            message.put("angle", angle);
            room.send(message);
            lastAngle = angle;
        }
    }

    private void checkLatency() {
        if (room == null) return;
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("op", "ping");
        room.send(data);
    }

    private void calculateLerp(float currentLatency) {
        float latency;
        if (currentLatency < LATENCY_MIN) latency = LATENCY_MIN;
        else if (currentLatency > LATENCY_MAX) latency = LATENCY_MAX;
        else latency = currentLatency;
        lerp = LERP_MAX + ((latency - LATENCY_MIN) / (LATENCY_MAX - LATENCY_MIN)) * (LERP_MIN - LERP_MAX);
        System.out.println("current latency: " + currentLatency + " ms");
        System.out.println("lerp : " + lerp);
    }
}