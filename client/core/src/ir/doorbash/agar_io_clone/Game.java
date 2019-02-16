package ir.doorbash.agar_io_clone;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.colyseus.Client;
import io.colyseus.Room;
import io.colyseus.state_listener.DataChange;
import io.colyseus.state_listener.FallbackPatchListenerCallback;
import io.colyseus.state_listener.PatchListenerCallback;
import io.colyseus.state_listener.PatchObject;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Game extends ApplicationAdapter {

    private static final String ENDPOINT = "ws://127.0.0.1:3300";
    private static final int NETWORK_UPDATE_INTERVAL = 200;
    private static final int CHECK_LATENCY_INTERVAL = 10000;
    private static final int GRID_SIZE = 60;
    private static final int FRUIT_RADIUS = 10;
    private static final float LERP_MIN = 0.1f;
    private static final float LERP_MAX = 0.5f;
    private static final int LATENCY_MIN = 100; // ms
    private static final int LATENCY_MAX = 500; // ms
    private static final float OTHER_PLAYERS_LERP = 0.5f;

    class Player {
        Vector2 position = new Vector2();
        Vector2 newPosition = new Vector2();
        Color color;
        Color strokeColor;
        float radius;
    }

    class Fruit {
        Vector2 position = new Vector2();
        Color color;
    }

    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private int width = 600;
    private int height = 600;
    private Client client;
    private Room room;
    private int lastAngle = -1000;
    private HashMap<String, Player> players = new HashMap<>();
    private final LinkedHashMap<String, Fruit> fruits = new LinkedHashMap<>();
    private int mapWidth;
    private int mapHeight;
    private long lastNetworkUpdateTime;
    private long lastLatencyCheckTime;
    private LinkedHashMap<String, Object> message;
    private FPSLogger fpsLogger;
    private float lerp = LERP_MAX;

    @Override
    public void create() {
        fpsLogger = new FPSLogger();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(width, height);
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
    }

    private void updatePositions() {
        if (client == null) return;
        for (String clientId : players.keySet()) {
            Player player = players.get(clientId);
            if (player == null) continue;
            if (clientId.equals(client.getId()))
                player.position.lerp(player.newPosition, lerp);
            else
                player.position.lerp(player.newPosition, OTHER_PLAYERS_LERP);
        }
    }

    private void drawGrid() {
        Player player;
        int leftBottomX;
        int leftBottomY;
        int rightTopX;
        int rightTopY;

        if (client != null && (player = players.get(client.getId())) != null) {
            leftBottomX = (int) (player.position.x - width / 2);
            leftBottomY = (int) (player.position.y - height / 2);
        } else {
            leftBottomX = -width / 2;
            leftBottomY = -height / 2;
        }

        leftBottomX -= leftBottomX % GRID_SIZE + GRID_SIZE;
        leftBottomY -= leftBottomY % GRID_SIZE + GRID_SIZE;
        rightTopX = leftBottomX + width + 2 * GRID_SIZE;
        rightTopY = leftBottomY + height + 2 * GRID_SIZE;

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
        if (client == null) return;
        Player thisPlayer = players.get(client.getId());
        if (thisPlayer == null) return;
        synchronized (fruits) {
            for (String fruitId : fruits.keySet()) {
                Fruit fr = fruits.get(fruitId);
                if (fr == null) continue;
                if (!objectIsInCurrentScreen(thisPlayer, fr.position.x, fr.position.y, FRUIT_RADIUS)) continue;
                shapeRenderer.setColor(fr.color);
                shapeRenderer.circle(fr.position.x, fr.position.y, FRUIT_RADIUS);
            }
        }
    }

    private void drawPlayers() {
        if (client == null) return;
        Player thisPlayer = players.get(client.getId());
        if (thisPlayer == null) return;

        for (String id : players.keySet()) {
            Player player = players.get(id);
            if (id.equals(client.getId()) || objectIsInCurrentScreen(thisPlayer, player.position.x, player.position.y, player.radius)) {
                shapeRenderer.setColor(player.strokeColor);
                shapeRenderer.circle(player.position.x, player.position.y, player.radius);
                shapeRenderer.setColor(player.color);
                shapeRenderer.circle(player.position.x, player.position.y, player.radius - 3);
            }
        }
    }

    private boolean objectIsInCurrentScreen(Player player, float x, float y, float radius) {
        if (x + radius < player.position.x - width / 2) return false;
        if (x - radius > player.position.x + width / 2) return false;
        if (y + radius < player.position.y - height / 2) return false;
        if (y - radius > player.position.y + height / 2) return false;
        return true;
    }

    private void adjustCamera() {
        if (client == null) return;
        Player player = players.get(client.getId());
        if (player == null) return;
        camera.position.set(new Vector3(player.position.x, player.position.y, 0));
        camera.zoom = 1;
        camera.update();
    }

    private void connectToServer() {
        client = new Client(ENDPOINT, new Client.Listener() {
            @Override
            public void onOpen(String id) {
                room = client.join("public");
                room.addPatchListener("players/:id", new PatchListenerCallback() {
                    @Override
                    protected void callback(DataChange change) {
//                        System.out.println(">>> players/:id");
//                        System.out.println(change.path);
//                        System.out.println(change.operation);
//                        System.out.println(change.value);
                        if (change.operation.equals("add")) {
                            Player player = new Player();
                            LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) change.value;

                            if (data.get("x") instanceof Float) player.position.x = (Float) data.get("x");
                            else if (data.get("x") instanceof Double)
                                player.position.x = ((Double) data.get("x")).floatValue();
                            else if (data.get("x") instanceof Integer)
                                player.position.x = ((Integer) data.get("x")).floatValue();

                            if (data.get("y") instanceof Float) player.position.y = (Float) data.get("y");
                            else if (data.get("y") instanceof Double)
                                player.position.y = ((Double) data.get("y")).floatValue();
                            else if (data.get("y") instanceof Integer)
                                player.position.y = ((Integer) data.get("y")).floatValue();

                            if (data.get("radius") instanceof Float) player.radius = (Float) data.get("radius");
                            else if (data.get("radius") instanceof Double)
                                player.radius = ((Double) data.get("radius")).floatValue();
                            else if (data.get("radius") instanceof Integer)
                                player.radius = ((Integer) data.get("radius")).floatValue();

                            int color;
                            if (data.get("color") instanceof Long) color = ((Long) data.get("color")).intValue();
                            else color = (Integer) data.get("color");
                            player.color = new Color(color);
                            player.strokeColor = new Color(player.color);
                            player.strokeColor.mul(0.9f);

                            players.put(change.path.get("id"), player);
                        } else if (change.operation.equals("remove")) {
                            players.remove(change.path.get("id"));
                        }
                    }
                });
                room.addPatchListener("players/:id/:attribute", new PatchListenerCallback() {
                    @Override
                    protected void callback(DataChange change) {
//                        System.out.println(">>> players/:id/:attribute");
//                        System.out.println(change.path);
//                        System.out.println(change.operation);
//                        System.out.println(change.value);

                        Player player = players.get(change.path.get("id"));
                        if (player == null) return;
                        String attribute = change.path.get("attribute");
                        switch (attribute) {
                            case "x":
                                if (change.value instanceof Double) {
                                    player.newPosition.x = ((Double) change.value).floatValue();
                                } else if (change.value instanceof Integer) {
                                    player.newPosition.x = ((Integer) change.value).floatValue();
                                } else if (change.value instanceof Float) {
                                    player.newPosition.x = (Float) change.value;
                                }
                                break;
                            case "y":
                                if (change.value instanceof Double) {
                                    player.newPosition.y = ((Double) change.value).floatValue();
                                } else if (change.value instanceof Integer) {
                                    player.newPosition.y = ((Integer) change.value).floatValue();
                                } else if (change.value instanceof Float) {
                                    player.newPosition.y = (Float) change.value;
                                }
                                break;
                            case "radius":
                                if (change.value instanceof Float) player.radius = (Float) change.value;
                                else if (change.value instanceof Double)
                                    player.radius = ((Double) change.value).floatValue();
                                else if (change.value instanceof Integer)
                                    player.radius = ((Integer) change.value).floatValue();
                                break;
                        }

                    }
                });
                room.addPatchListener("fruits/:id", new PatchListenerCallback() {
                    @Override
                    protected void callback(DataChange change) {
                        System.out.println(change);
                        if (change.operation.equals("add")) {
                            Fruit fruit = new Fruit();

                            LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) change.value;

                            if (data.get("x") instanceof Float) fruit.position.x = (Float) data.get("x");
                            else if (data.get("x") instanceof Double)
                                fruit.position.x = ((Double) data.get("x")).floatValue();
                            else if (data.get("x") instanceof Integer)
                                fruit.position.x = ((Integer) data.get("x")).floatValue();

                            if (data.get("y") instanceof Float) fruit.position.y = (Float) data.get("y");
                            else if (data.get("y") instanceof Double)
                                fruit.position.y = ((Double) data.get("y")).floatValue();
                            else if (data.get("y") instanceof Integer)
                                fruit.position.y = ((Integer) data.get("y")).floatValue();

                            int color;
                            if (data.get("color") instanceof Long) color = ((Long) data.get("color")).intValue();
                            else color = (Integer) data.get("color");
                            fruit.color = new Color(color);

                            synchronized (fruits) {
                                fruits.put(change.path.get("id"), fruit);
                            }
                        } else if (change.operation.equals("remove")) {
                            synchronized (fruits) {
                                fruits.remove(change.path.get("id"));
                            }
                        }
                    }
                });
                room.addPatchListener("mapSize/:id", new PatchListenerCallback() {
                    @Override
                    protected void callback(DataChange change) {
                        if (change.path.get("id").equals("width")) {
                            mapWidth = (int) change.value;
                        } else if (change.path.get("id").equals("height")) {
                            mapHeight = (int) change.value;
                        }
                    }
                });
                room.setDefaultPatchListener(new FallbackPatchListenerCallback() {
                    @Override
                    public void callback(PatchObject patch) {
//                        System.out.println(" >>> default listener");
//                        System.out.println(patch.path);
//                        System.out.println(patch.operation);
//                        System.out.println(patch.value);
                    }
                });
                room.addListener(new Room.Listener() {
                    @Override
                    protected void onMessage(Object message) {
                        if (message.equals("pong")) {
                            calculateLerp(System.currentTimeMillis() - lastLatencyCheckTime);
                        }
                    }
                });
            }

            @Override
            public void onMessage(Object o) {
                System.out.println(o);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                try {
                    Thread.sleep(2000);
                    players.clear();
                    fruits.clear();
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
        float dx = Gdx.input.getX() - width / 2;
        float dy = Gdx.input.getY() - height / 2;
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