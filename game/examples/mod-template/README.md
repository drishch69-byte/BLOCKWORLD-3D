# Hello mod template

This template demonstrates the BlockWorld 0.7.5 ServiceLoader API. It adds one decorative gold-ore block after the world is loaded.

1. Build the main game first with `mvn package` from the project root.
2. Compile `src/main/java/example/HelloBlockWorldMod.java` against `target/blockworld-3d-0.7.5.jar` using Java 17.
3. Put compiled classes and `src/main/resources/META-INF/services/com.blockworld.api.BlockWorldMod` into one JAR.
4. Import that JAR through the launcher's Mods screen.

The API is a preview and may change before 1.0.0. Never enable JAR files from authors you do not trust.
