package gollorum.signpost.minecraft.items;

import gollorum.signpost.minecraft.block.PostBlock;
import gollorum.signpost.minecraft.block.tiles.PostTile;
import gollorum.signpost.minecraft.utils.TileEntityUtils;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemTier;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.ToolItem;
import net.minecraft.util.ActionResultType;

import java.util.HashSet;

public class Wrench extends ToolItem {

    public static final String registryName = "tool";

    public Wrench(ItemGroup itemGroup) {
        super(0, -3, ItemTier.IRON, new HashSet<>(), new Properties().group(itemGroup));
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        return TileEntityUtils.findTileEntity(context.getWorld(), context.getPos(), PostTile.class)
            .map(tile -> PostBlock.onActivate(tile, context.getWorld(), context.getPlayer(), context.getHand()))
            .orElse(ActionResultType.PASS);
    }
}
