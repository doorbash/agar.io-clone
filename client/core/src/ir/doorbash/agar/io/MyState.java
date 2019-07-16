package ir.doorbash.agar.io;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.colyseus.serializer.schema.Schema;
import io.colyseus.serializer.schema.annotations.SchemaClass;
import io.colyseus.serializer.schema.annotations.SchemaField;

@SchemaClass
public class MyState extends Schema {
    @SchemaField("0/map/ref")
    MapSchema<Player> players = new MapSchema<>(Player.class);

    @SchemaField("1/map/ref")
    MapSchema<Fruit> fruits = new MapSchema<>(Fruit.class);
}

@SchemaClass
class Player extends Schema {
    @SchemaField("0/float32")
    public float x;

    @SchemaField("1/float32")
    public float y;

    @SchemaField("2/float32")
    public float radius;

    @SchemaField("3/int32")
    public int color;

    public Vector2 position = new Vector2();
    public Vector2 newPosition = new Vector2();
    public Color _color;
    public Color _strokeColor;
}

@SchemaClass
class Fruit extends Schema {
    @SchemaField("0/float32")
    public float x;

    @SchemaField("1/float32")
    public float y;

    @SchemaField("2/int32")
    public int color;

    Vector2 position = new Vector2();
    Color _color;
}