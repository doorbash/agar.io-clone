package ir.doorbash.agar_io_clone;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Game extends ApplicationAdapter {

    class Player {
        public float x;
        public float y;
        public Color color;
        public Color strokeColor;
        public float radius;
    }

    class Fruit {
        public float x;
        public float y;
        public Color color;
    }

    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private int width = 600;
    private int height = 600;
    Client client;
    Room room;
    int lastAngle = -1000;
    HashMap<String, Player> players = new HashMap<>();
    final LinkedHashMap<String, Fruit> fruits = new LinkedHashMap<>();
    int mapWidth;
    int mapHeight;

    @Override
    public void create() {
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(width, height);
        connectToServer();
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.98f, 0.99f, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        adjustCamera();
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawGrid(-width/2, -height/2, mapWidth + width / 2, mapHeight + height / 2, 60);
        drawFruits();
        drawPlayers();
        shapeRenderer.end();

        networkUpdate();
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

    private void drawGrid(int startX, int startY, int width, int height, int size) {
        shapeRenderer.setColor(Color.BLACK);
        for (int i = startX; i < width; i += size) {
            shapeRenderer.line(i, startY, i, height);
        }

        for (int i = startY; i < height; i += size) {
            shapeRenderer.line(startX, i, width, i);
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(0, 0, mapWidth, mapHeight);
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void drawFruits() {
        synchronized (fruits) {
            for (String fruitId : fruits.keySet()) {
                Fruit fr = fruits.get(fruitId);
                if (fr == null) continue;
                shapeRenderer.setColor(fr.color);
                shapeRenderer.circle(fr.x, fr.y, 10);
            }
        }
    }

    private void drawPlayers() {

        if (client == null) return;

        // draw other players
        for (String id : players.keySet()) {
            if (id.equals(client.getId())) continue;
            Player player = players.get(id);
            shapeRenderer.setColor(player.strokeColor);
            shapeRenderer.circle(player.x, player.y, player.radius);
            shapeRenderer.setColor(player.color);
            shapeRenderer.circle(player.x, player.y, player.radius - 3);
        }
        // draw me
        Player player = players.get(client.getId());
        if (player == null) return;
        shapeRenderer.setColor(player.strokeColor);
        shapeRenderer.circle(player.x, player.y, player.radius);
        shapeRenderer.setColor(player.color);
        shapeRenderer.circle(player.x, player.y, player.radius - 3);
    }

    private void adjustCamera() {
        if (client == null) return;
        Player player = players.get(client.getId());
        if (player == null) return;
        camera.position.set(new Vector3(player.x, player.y, 0));
//        camera.zoom = 10;
        camera.update();
    }

    private void connectToServer() {
        client = new Client("ws://localhost:3300", new Client.Listener() {
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

                            if (data.get("x") instanceof Float) player.x = (Float) data.get("x");
                            else if (data.get("x") instanceof Double) player.x = ((Double) data.get("x")).floatValue();
                            else if (data.get("x") instanceof Integer)
                                player.x = ((Integer) data.get("x")).floatValue();

                            if (data.get("y") instanceof Float) player.y = (Float) data.get("y");
                            else if (data.get("y") instanceof Double) player.y = ((Double) data.get("y")).floatValue();
                            else if (data.get("y") instanceof Integer)
                                player.y = ((Integer) data.get("y")).floatValue();

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
                        if (attribute.equals("x")) {
                            if (change.value instanceof Double) {
                                player.x = ((Double) change.value).floatValue();
                            } else if (change.value instanceof Integer) {
                                player.x = ((Integer) change.value).floatValue();
                            } else if (change.value instanceof Float) {
                                player.x = ((Float) change.value).floatValue();
                            }
                        } else if (attribute.equals("y")) {
                            if (change.value instanceof Double) {
                                player.y = ((Double) change.value).floatValue();
                            } else if (change.value instanceof Integer) {
                                player.y = ((Integer) change.value).floatValue();
                            } else if (change.value instanceof Float) {
                                player.y = ((Float) change.value).floatValue();
                            }
                        } else if (attribute.equals("radius")) {
                            if (change.value instanceof Float) player.radius = (Float) change.value;
                            else if (change.value instanceof Double)
                                player.radius = ((Double) change.value).floatValue();
                            else if (change.value instanceof Integer)
                                player.radius = ((Integer) change.value).floatValue();
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

                            if (data.get("x") instanceof Float) fruit.x = (Float) data.get("x");
                            else if (data.get("x") instanceof Double) fruit.x = ((Double) data.get("x")).floatValue();
                            else if (data.get("x") instanceof Integer)
                                fruit.x = ((Integer) data.get("x")).floatValue();

                            if (data.get("y") instanceof Float) fruit.y = (Float) data.get("y");
                            else if (data.get("y") instanceof Double) fruit.y = ((Double) data.get("y")).floatValue();
                            else if (data.get("y") instanceof Integer)
                                fruit.y = ((Integer) data.get("y")).floatValue();

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
            }

            @Override
            public void onMessage(Object o) {
                System.out.println(o);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                System.out.println("close");
            }

            @Override
            public void onError(Exception e) {
                System.out.println(e);
            }
        });
    }

    private void networkUpdate() {
        if (room == null) return;
        float dx = Gdx.input.getX() - width / 2;
        float dy = height / 2 - Gdx.input.getY();
        int angle = (int) Math.toDegrees(Math.atan2(dy, dx));
        if (lastAngle != angle) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("op", "angle");
            data.put("angle", angle);
            room.send(data);
            lastAngle = angle;
        }
    }
}
