package fr.chixi.cubeconquest.block;

import fr.chixi.cubeconquest.Team;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CubeBlock extends Block {

    private final Team team;

    public CubeBlock(Team team, BlockBehaviour.Properties properties) {
        super(properties);
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }
}
