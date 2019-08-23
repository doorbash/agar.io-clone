//
// THIS FILE HAS BEEN GENERATED AUTOMATICALLY
// DO NOT CHANGE IT MANUALLY UNLESS YOU KNOW WHAT YOU'RE DOING
// 
// GENERATED USING @colyseus/schema 0.4.41
// 

package ir.doorbash.agar.io.classes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.colyseus.serializer.schema.Schema;
import io.colyseus.serializer.schema.annotations.SchemaClass;
import io.colyseus.serializer.schema.annotations.SchemaField;

@SchemaClass
public class Player extends Schema {
	@SchemaField("0/float32")	
	public float x = 0;

	@SchemaField("1/float32")	
	public float y = 0;

	@SchemaField("2/float32")	
	public float radius = 0;

	@SchemaField("3/int32")	
	public int color = 0;

	public Vector2 position = new Vector2();
	public Color _color;
	public Color _strokeColor;
}

