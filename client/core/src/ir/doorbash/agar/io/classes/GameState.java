//
// THIS FILE HAS BEEN GENERATED AUTOMATICALLY
// DO NOT CHANGE IT MANUALLY UNLESS YOU KNOW WHAT YOU'RE DOING
// 
// GENERATED USING @colyseus/schema 0.4.41
// 

package ir.doorbash.agar.io.classes;

import io.colyseus.serializer.schema.Schema;
import io.colyseus.serializer.schema.annotations.SchemaClass;
import io.colyseus.serializer.schema.annotations.SchemaField;

@SchemaClass
public class GameState extends Schema {
	@SchemaField("0/map/ref")	
	public MapSchema<Player> players = new MapSchema<>(Player.class);

	@SchemaField("1/map/ref")	
	public MapSchema<Fruit> fruits = new MapSchema<>(Fruit.class);
}

