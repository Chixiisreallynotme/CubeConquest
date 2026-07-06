import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
public class TestEntityLoad {
    static {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {});
    }
}
