package th.in.midnight_network.greenchunk.slime;

import java.util.Random;
import org.bukkit.World;

public final class SlimeChunkService {
    private static final long SLIME_SALT = 987234911L;

    public boolean isSlimeChunk(World world, int chunkX, int chunkZ) {
        long seed = world.getSeed()
                + (long) (chunkX * chunkX * 4_987_142)
                + (long) (chunkX * 5_947_611)
                + (long) (chunkZ * chunkZ) * 4_392_871L
                + (long) (chunkZ * 389_711);
        return new Random(seed ^ SLIME_SALT).nextInt(10) == 0;
    }
}
