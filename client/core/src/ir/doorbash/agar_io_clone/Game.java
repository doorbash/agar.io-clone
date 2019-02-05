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

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Game extends ApplicationAdapter {


    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private float width = 600;
    private float height = 600;
    Vector2 gridSize = new Vector2(2000, 2000);
    Client client;
    Room room;
    double lastAngle = -1000;

    class Player {
        public float x;
        public float y;
        public Color color;
        public float radius;
    }

    HashMap<String, Player> players = new HashMap<>();


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
        shapeRenderer.setColor(new Color(0, 0, 0, 0.1f));
        drawGrid(-300, -300, (int) gridSize.x, (int) gridSize.y, 60);
        drawPlayers();
        shapeRenderer.end();

        networkUpdate();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }

    @Override
    public void resize(int width, int height) {
    }

    private void drawGrid(int startX, int startY, int width, int height, int size) {
        for (int i = startX; i < width; i += size) {
            shapeRenderer.line(i, startY, i, height);
        }

        for (int i = startY; i < height; i += size) {
            shapeRenderer.line(startX, i, width, i);
        }
    }

    private void drawPlayers() {

        if(client == null) return;

        // draw other players
        for (String id : players.keySet()) {
            if (id.equals(client.getId())) continue;
            Player player = players.get(id);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.circle(player.x, player.y, player.radius);
            shapeRenderer.setColor(player.color);
            shapeRenderer.circle(player.x, player.y, player.radius - 1);
        }
        // draw me
        Player player = players.get(client.getId());
        if(player == null) return;
        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.circle(player.x, player.y, player.radius);
        shapeRenderer.setColor(player.color);
        shapeRenderer.circle(player.x, player.y, player.radius - 1);
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
                    protected void callback(DataChange dataChange) {
                        System.out.println(">>> players/:id");
                        System.out.println(dataChange.path);
                        System.out.println(dataChange.operation);
                        System.out.println(dataChange.value);
                        if (dataChange.operation.equals("add")) {
                            Player player = new Player();
                            LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) dataChange.value;

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

                            int color = (Integer) data.get("color");
                            player.color = new Color(color);

                            players.put(dataChange.path.get("id"), player);
                        } else if (dataChange.operation.equals("remove")) {
                            players.remove(dataChange.path.get("id"));
                        }
                    }
                });
                room.addPatchListener("players/:id/:attribute", new PatchListenerCallback() {
                    @Override
                    protected void callback(DataChange dataChange) {
//                        System.out.println(">>> players/:id/:attribute");
//                        System.out.println(dataChange.path);
//                        System.out.println(dataChange.operation);
//                        System.out.println(dataChange.value);

                        Player player = players.get(dataChange.path.get("id"));
                        if (player == null) return;
                        String attribute = dataChange.path.get("attribute");
                        if (attribute.equals("x")) {
                            if (dataChange.value instanceof Double) {
                                player.x = ((Double) dataChange.value).floatValue();
                            } else if (dataChange.value instanceof Integer) {
                                player.x = ((Integer) dataChange.value).floatValue();
                            } else if (dataChange.value instanceof Float) {
                                player.x = ((Float) dataChange.value).floatValue();
                            }
                        } else if (attribute.equals("y")) {
                            if (dataChange.value instanceof Double) {
                                player.y = ((Double) dataChange.value).floatValue();
                            } else if (dataChange.value instanceof Integer) {
                                player.y = ((Integer) dataChange.value).floatValue();
                            } else if (dataChange.value instanceof Float) {
                                player.y = ((Float) dataChange.value).floatValue();
                            }
                        }

                    }
                });
                room.setDefaultPatchListener(new FallbackPatchListenerCallback() {
                    @Override
                    public void callback(PatchObject patchObject) {
                        System.out.println(" >>> default listener");
                        System.out.println(patchObject.path);
                        System.out.println(patchObject.operation);
                        System.out.println(patchObject.value);
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

    public void networkUpdate() {
        if (room == null) return;
        float dx = Gdx.input.getX() - width / 2;
        float dy = height / 2 - Gdx.input.getY();
        double angle = Math.atan2(dy, dx);
        if (lastAngle != angle) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("op", "angle");
            data.put("angle", angle);
            room.send(data);
            lastAngle = angle;
        }
    }
}
