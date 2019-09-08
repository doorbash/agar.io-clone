package ir.doorbash.agar.io;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import io.colyseus.Client;
import io.colyseus.Client.MatchMakeException;
import io.colyseus.Room;
import ir.doorbash.agar.io.classes.Fruit;
import ir.doorbash.agar.io.classes.GameState;
import ir.doorbash.agar.io.classes.Player;

public class Game extends ApplicationAdapter {

    /* *************************************** CONSTANTS *****************************************/

    private static final String ENDPOINT = "ws://127.0.0.1:2560";
    private static final String PATH_FONT_NOTO = "fonts/NotoSans-Regular.ttf";
    private static final int SEND_DIRECTION_INTERVAL = 200;
    private static final int PING_INTERVAL = 5000;
    private static final int CHECK_CONNECTION_INTERVAL = 3000;
    private static final int GRID_SIZE = 60;
    private static final int FRUIT_RADIUS = 10;
    private static final int LATENCY_MIN = 100; // ms
    private static final int LATENCY_MAX = 500; // ms
    private static final int CONNECTION_STATE_DISCONNECTED = 0;
    private static final int CONNECTION_STATE_CONNECTING = 1;
    private static final int CONNECTION_STATE_CONNECTED = 2;
    private static final float LERP_MIN = 0.1f;
    private static final float LERP_MAX = 0.5f;
    private static final float OTHER_PLAYERS_LERP = 0.5f;

    /* **************************************** FIELDS *******************************************/

    private int connectionState = CONNECTION_STATE_DISCONNECTED;
    private int lastAngle = -1000;
    private int mapWidth = 1200;
    private int mapHeight = 1200;
    private long lastSendDirectionTime;
    private long lastPingSentTime;
    private long lastPingReplyTime;
    private long currentPing = -1;
    private long lastConnectionCheckTime;
    private float lerp = LERP_MAX;

    private String sessionId;
    private String roomId;

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private OrthographicCamera guiCamera;
    private FreeTypeFontGenerator freetypeGeneratorNoto;
    private BitmapFont logFont;

    private Room<GameState> room;
    private LinkedHashMap<String, Object> message;
    private final HashMap<String, Fruit> fruits = new HashMap<>();
    private final HashMap<String, Player> players = new HashMap<>();

    /* *************************************** OVERRIDE *****************************************/

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.update();
        guiCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        guiCamera.update();
        message = new LinkedHashMap<>();
        message.put("op", "angle");

        freetypeGeneratorNoto = new FreeTypeFontGenerator(Gdx.files.internal(PATH_FONT_NOTO));
        FreeTypeFontGenerator.FreeTypeFontParameter logFontParams = new FreeTypeFontGenerator.FreeTypeFontParameter();
        logFontParams.size = 14;
        logFontParams.color = Color.BLACK;
        logFontParams.flip = false;
        logFontParams.incremental = true;
        logFont = freetypeGeneratorNoto.generateFont(logFontParams);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.98f, 0.99f, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        updatePositions();
        adjustCamera();

        if (connectionState == CONNECTION_STATE_CONNECTED) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            drawGrid();
            drawFruits();
            drawPlayers();
            shapeRenderer.end();

            batch.setProjectionMatrix(guiCamera.combined);
            batch.begin();
            drawPing();
            batch.end();
        }

        long now = System.currentTimeMillis();
        if (now - lastSendDirectionTime >= SEND_DIRECTION_INTERVAL) {
            lastSendDirectionTime = now;
            sendDirection();
        }
        if (now - lastPingSentTime >= PING_INTERVAL) {
            lastPingSentTime = now;
            sendPing();
        }
        if (now - lastConnectionCheckTime >= CHECK_CONNECTION_INTERVAL) {
            lastConnectionCheckTime = now;
            checkConnection();
        }
    }

    @Override
    public void dispose() {
        if (logFont != null) logFont.dispose();
        if (batch != null) batch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (freetypeGeneratorNoto != null) freetypeGeneratorNoto.dispose();
        if (room != null) room.leave();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        guiCamera.viewportWidth = width;
        guiCamera.viewportHeight = height;
        guiCamera.update();
    }

    /* ***************************************** DRAW *******************************************/

    private void drawGrid() {
        Player player;
        int leftBottomX;
        int leftBottomY;
        int rightTopX;
        int rightTopY;

        float width = camera.zoom * camera.viewportWidth;
        float height = camera.zoom * camera.viewportHeight;

        if (room != null && (player = room.state.players.get(room.getSessionId())) != null) {
            leftBottomX = (int) (player.position.x - width / 2f);
            leftBottomY = (int) (player.position.y - height / 2f);
        } else {
            leftBottomX = (int) (-width / 2f);
            leftBottomY = (int) (-height / 2f);
        }

        leftBottomX -= leftBottomX % GRID_SIZE + GRID_SIZE;
        leftBottomY -= leftBottomY % GRID_SIZE + GRID_SIZE;
        rightTopX = (int) (leftBottomX + width + 2 * GRID_SIZE);
        rightTopY = (int) (leftBottomY + height + 2 * GRID_SIZE);

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
        Player thisPlayer = room.state.players.get(room.getSessionId());
        if (thisPlayer == null) return;
        synchronized (fruits) {
            for (Fruit fr : fruits.values()) {
                if (fr == null || fr._color == null) continue;
                if (!objectIsInViewport(thisPlayer, fr.position.x, fr.position.y, FRUIT_RADIUS))
                    continue;
                shapeRenderer.setColor(fr._color);
                shapeRenderer.circle(fr.position.x, fr.position.y, FRUIT_RADIUS);
            }
        }
    }

    private void drawPlayers() {
        if (room == null) return;
        Player thisPlayer = room.state.players.get(room.getSessionId());
        if (thisPlayer == null) return;
        synchronized (players) {
            for (Entry<String, Player> keyValue : players.entrySet()) {
                String clientId = keyValue.getKey();
                Player player = keyValue.getValue();
                if (player == null) continue;
                if (clientId.equals(room.getSessionId()) || objectIsInViewport(thisPlayer, player.position.x, player.position.y, player.radius)) {
                    shapeRenderer.setColor(player._strokeColor);
                    shapeRenderer.circle(player.position.x, player.position.y, player.radius);
                    shapeRenderer.setColor(player._color);
                    shapeRenderer.circle(player.position.x, player.position.y, player.radius - 3);
                }
            }
        }
    }

    private void drawPing() {
        String logText = "fps: " + Gdx.graphics.getFramesPerSecond();
        if (currentPing >= 0) {
            logText += " - ping: " + currentPing;
        }
        logFont.draw(batch, logText, -guiCamera.viewportWidth / 2f + 8, -guiCamera.viewportHeight / 2f + 2 + logFont.getLineHeight());
    }

    /* ***************************************** LOGIC *******************************************/

    private void updatePositions() {
        if (room == null) return;
        synchronized (players) {
            for (Entry<String, Player> keyValue : players.entrySet()) {
                String clientId = keyValue.getKey();
                Player player = keyValue.getValue();
                if (player == null) continue;
                if (clientId.equals(room.getSessionId()))
                    player.position.set(MathUtils.lerp(player.position.x, player.x, lerp), MathUtils.lerp(player.position.y, player.y, lerp));
                else
                    player.position.set(MathUtils.lerp(player.position.x, player.x, OTHER_PLAYERS_LERP), MathUtils.lerp(player.position.y, player.y, OTHER_PLAYERS_LERP));
            }
        }
    }

    private boolean objectIsInViewport(Player player, float x, float y, float radius) {
        if (x + radius < player.position.x - camera.zoom * camera.viewportWidth / 2) return false;
        if (x - radius > player.position.x + camera.zoom * camera.viewportWidth / 2) return false;
        if (y + radius < player.position.y - camera.zoom * camera.viewportHeight / 2) return false;
        if (y - radius > player.position.y + camera.zoom * camera.viewportHeight / 2) return false;
        return true;
    }

    private void adjustCamera() {
        if (room == null) return;
        Player player = room.state.players.get(room.getSessionId());
        if (player == null) return;
        camera.position.x = player.position.x;
        camera.position.y = player.position.y;
        camera.zoom = player.radius / 60f;
        camera.update();
    }

    /* **************************************** NETWORK ******************************************/

    private void checkConnection() {
        if (connectionState == CONNECTION_STATE_CONNECTED && lastPingReplyTime > 0 && System.currentTimeMillis() - lastPingReplyTime > 15000) {
            connectionState = CONNECTION_STATE_DISCONNECTED;
        }

        if (connectionState == CONNECTION_STATE_DISCONNECTED) {
            connectToServer();
        }
    }

    private void connectToServer() {
        System.out.println("connectToServer()");
        if (connectionState != CONNECTION_STATE_DISCONNECTED) return;
        connectionState = CONNECTION_STATE_CONNECTING;
        Client client = new Client(ENDPOINT);

        if (sessionId == null) {
            client.joinOrCreate("ffa", GameState.class, this::updateRoom, e -> {
                e.printStackTrace();
                connectionState = CONNECTION_STATE_DISCONNECTED;
            });
        } else {
            client.reconnect(roomId, sessionId, GameState.class, this::updateRoom, e -> {
                if (e instanceof MatchMakeException) {
                    this.roomId = null;
                    this.sessionId = null;
                }
                e.printStackTrace();
                connectionState = CONNECTION_STATE_DISCONNECTED;
            });
        }
    }

    private void updateRoom(Room<GameState> room) {
        this.room = room;
        this.roomId = room.getId();
        this.sessionId = room.getSessionId();
        System.out.println("joined chat");

        synchronized (players) {
            players.clear();
        }
        synchronized (fruits) {
            fruits.clear();
        }

        connectionState = CONNECTION_STATE_CONNECTED;
        lastPingReplyTime = 0;

        room.setListener(new Room.Listener() {

            @Override
            protected void onLeave(int code) {
                System.out.println("left public, code = " + code);
                if (code > 1000) {
                    // abnormal disconnection!
                    connectionState = CONNECTION_STATE_DISCONNECTED;
                } else {
                    // the client has initiated the disconnection
                }
            }

            @Override
            protected void onError(Exception e) {
                connectionState = CONNECTION_STATE_DISCONNECTED;
                System.out.println("onError()");
                e.printStackTrace();
            }

            @Override
            protected void onMessage(Object message) {
                if (message.equals("pong")) {
                    lastPingReplyTime = System.currentTimeMillis();
                    currentPing = lastPingReplyTime - lastPingSentTime;
                    calculateLerp(currentPing);
                }
            }
        });
        room.state.players.onAdd = (player, key) -> {
            if (connectionState != CONNECTION_STATE_CONNECTED) return;
            synchronized (players) {
                players.put(key, player);
            }
//            System.out.println("new player added >> clientId: " + key);
            player.position.x = player.x;
            player.position.y = player.y;
            player._color = new Color(player.color);
            player._strokeColor = new Color(player.color);
            player._strokeColor.mul(0.9f);
        };

        room.state.players.onRemove = (player, key) -> {
            if (connectionState != CONNECTION_STATE_CONNECTED) return;
            synchronized (players) {
                players.remove(key);
            }
        };

        room.state.fruits.onAdd = (fruit, key) -> {
            if (connectionState != CONNECTION_STATE_CONNECTED) return;
            synchronized (fruits) {
                fruits.put(key, fruit);
            }
//            System.out.println("new fruit added >> key: " + key);
            fruit.position.x = fruit.x;
            fruit.position.y = fruit.y;
            fruit._color = new Color(fruit.color);
        };

        room.state.fruits.onRemove = (fruit, key) -> {
            if (connectionState != CONNECTION_STATE_CONNECTED) return;
            synchronized (fruits) {
                fruits.remove(key);
            }
        };
    }

    private void sendDirection() {
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

    private void sendPing() {
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