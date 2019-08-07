package gollorum.signpost.management;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import gollorum.signpost.SPEventHandler;
import gollorum.signpost.blocks.tiles.BigPostPostTile;
import gollorum.signpost.blocks.tiles.PostPostTile;
import gollorum.signpost.modIntegration.SignpostAdapter;
import gollorum.signpost.network.NetworkHandler;
import gollorum.signpost.network.handlers.SendAllWaystoneNamesHandler;
import gollorum.signpost.network.messages.ChatMessage;
import gollorum.signpost.network.messages.TeleportRequestMessage;
import gollorum.signpost.util.BaseInfo;
import gollorum.signpost.util.BigBaseInfo;
import gollorum.signpost.util.BoolRun;
import gollorum.signpost.util.DoubleBaseInfo;
import gollorum.signpost.util.MyBlockPos;
import gollorum.signpost.util.MyBlockPosSet;
import gollorum.signpost.util.Paintable;
import gollorum.signpost.util.Sign;
import gollorum.signpost.util.StonedHashSet;
import gollorum.signpost.util.StringSet;
import gollorum.signpost.util.collections.Lurchpaerchensauna;
import gollorum.signpost.util.collections.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class PostHandler {

	private static StonedHashSet allWaystones = new StonedHashSet();
	private static Lurchpaerchensauna<MyBlockPos, DoubleBaseInfo> posts = new Lurchpaerchensauna<MyBlockPos, DoubleBaseInfo>();
	private static Lurchpaerchensauna<MyBlockPos, BigBaseInfo> bigPosts = new Lurchpaerchensauna<MyBlockPos, BigBaseInfo>();
	//ServerSide
	public static Lurchpaerchensauna<UUID, TeleportInformation> awaiting =  new Lurchpaerchensauna<UUID, TeleportInformation>(); 
	
	/**
	 * UUID = the player;
	 * StringSet = the discovered waystones;
	 */
	public static Lurchpaerchensauna<UUID, StringSet> playerKnownWaystones = new Lurchpaerchensauna<UUID, StringSet>(){
		@Override
		public StringSet get(Object obj){
			StringSet pair = super.get(obj);
			if(pair == null){
				return put((UUID) obj, new StringSet());
			}else{
				return pair;
			}
		}
	};

	/**
	 * UUID = the player;
	 * Pair.MyBlockPosSet = the discovered waystones;
	 * Pair.b.a = the waystones left to place;
	 * Pair.b.b = the signposts left to place;
	 */
	public static Lurchpaerchensauna<UUID, Pair<MyBlockPosSet, Pair<Integer, Integer>>> playerKnownWaystonePositions = new Lurchpaerchensauna<UUID, Pair<MyBlockPosSet, Pair<Integer, Integer>>>(){
		@Override
		public Pair<MyBlockPosSet, Pair<Integer, Integer>> get(Object obj){
			Pair<MyBlockPosSet, Pair<Integer, Integer>> pair = super.get(obj);
			if(pair == null){
				Pair<MyBlockPosSet, Pair<Integer, Integer>> p = new Pair<MyBlockPosSet, Pair<Integer, Integer>>();
				p.a  = new MyBlockPosSet();
				p.b = new Pair<Integer, Integer>();
				p.b.a = ClientConfigStorage.INSTANCE.getMaxWaystones();
				p.b.b = ClientConfigStorage.INSTANCE.getMaxSignposts();
				return put((UUID) obj, p);
			}else{
				return pair;
			}
		}
	};

	public static boolean doesPlayerKnowWaystone(EntityPlayerMP player, BaseInfo waystone){
		if(ClientConfigStorage.INSTANCE.isDisableDiscovery()){
			return true;
		}else{
			return doesPlayerKnowNativeWaystone(player, waystone) || getPlayerKnownWaystones(player).contains(waystone);
		}
	}

	public static boolean doesPlayerKnowNativeWaystone(EntityPlayerMP player, BaseInfo waystone){
		if(ClientConfigStorage.INSTANCE.isDisableDiscovery()){
			return true;
		}else if(playerKnownWaystonePositions.get(player.getUniqueID()).a.contains(waystone.blockPos)){
			if(playerKnownWaystones.containsKey(player.getUniqueID())){
				playerKnownWaystones.get(player.getUniqueID()).remove(waystone.getName());
			}
			return true;
		}else{
			return playerKnownWaystones.get(player.getUniqueID()).contains(waystone.getName());
		}
	}
	
	public static void init(){
		allWaystones = new StonedHashSet();
		playerKnownWaystones = new Lurchpaerchensauna<UUID, StringSet>(){
			@Override
			public StringSet get(Object obj){
				StringSet pair = super.get(obj);
				if(pair == null){
					return put((UUID) obj, new StringSet());
				}else{
					return pair;
				}
			}
		};
		playerKnownWaystonePositions = new Lurchpaerchensauna<UUID, Pair<MyBlockPosSet, Pair<Integer, Integer>>>(){
			@Override
			public Pair<MyBlockPosSet, Pair<Integer, Integer>> get(Object obj){
				Pair<MyBlockPosSet, Pair<Integer, Integer>> pair = super.get(obj);
				if(pair == null){
					Pair<MyBlockPosSet, Pair<Integer, Integer>> p = new Pair<MyBlockPosSet, Pair<Integer, Integer>>();
					p.a  = new MyBlockPosSet();
					p.b = new Pair<Integer, Integer>();
					p.b.a = ClientConfigStorage.INSTANCE.getMaxWaystones();
					p.b.b = ClientConfigStorage.INSTANCE.getMaxSignposts();
					return put((UUID) obj, p);
				}else{
					return pair;
				}
			}
		};
		posts = new Lurchpaerchensauna<MyBlockPos, DoubleBaseInfo>();
		bigPosts = new Lurchpaerchensauna<MyBlockPos, BigBaseInfo>();
		awaiting = new Lurchpaerchensauna<UUID, TeleportInformation>();
	}

	public static Lurchpaerchensauna<MyBlockPos, DoubleBaseInfo> getPosts() {
		return posts;
	}

	public static void setPosts(Lurchpaerchensauna<MyBlockPos, DoubleBaseInfo> posts) {
		PostHandler.posts = posts;
		refreshDoublePosts();
	}

	public static Lurchpaerchensauna<MyBlockPos, BigBaseInfo> getBigPosts() {
		return bigPosts;
	}

	public static void setBigPosts(Lurchpaerchensauna<MyBlockPos, BigBaseInfo> bigPosts) {
		PostHandler.bigPosts = bigPosts;
		refreshBigPosts();
	}

	public static List<Sign> getSigns(MyBlockPos pos){
		List<Sign> ret = new LinkedList();

		DoubleBaseInfo doubleBase = getPosts().get(pos);
		if(doubleBase != null){
			ret.add(doubleBase.sign1);
			ret.add(doubleBase.sign2);
		}else{
			BigBaseInfo bigBase = getBigPosts().get(pos);
			if(bigBase != null){
				ret.add(bigBase.sign);
			}
		}
		
		return ret;
	}
	
	public static Paintable getPost(MyBlockPos pos){
		Paintable ret = getPosts().get(pos);
		if(ret == null){
			ret = getBigPosts().get(pos);
		}
		if(ret == null){
			pos.getTile();
			ret = getPosts().get(pos);
			if(ret == null){
				ret = getBigPosts().get(pos);
			}
		}
		return ret;
	}
	
	private static void refreshPosts(){
		refreshDoublePosts();
		refreshBigPosts();
	}

	public static void refreshDoublePosts(){
		for(Entry<MyBlockPos, DoubleBaseInfo> now: posts.entrySet()){
			PostPostTile tile = (PostPostTile) now.getKey().getTile();
			if(tile!=null){
				tile.isWaystone();
				tile.getBases();
			}
		}
	}

	public static void refreshBigPosts(){
		for(Entry<MyBlockPos, BigBaseInfo> now: bigPosts.entrySet()){
			BigPostPostTile tile = (BigPostPostTile) now.getKey().getTile();
			if(tile!=null){
				tile.isWaystone();
				tile.getBases();
			}
		}
	}
	
	public static BaseInfo getWSbyName(String name){
		if(ClientConfigStorage.INSTANCE.deactivateTeleportation()){
			return new BaseInfo(name, null, null);
		}else{
			for(BaseInfo now:getAllWaystones()){
				if(name.equals(now.getName())){
					return now;
				}
			}
			return null;
		}
	}
	
	public static BaseInfo getForceWSbyName(String name){
		if(name==null || name.equals("null")){
			return null;
		}
		for(BaseInfo now:getAllWaystones()){
			if(name.equals(now.getName())){
				return now;
			}
		}
		return new BaseInfo(name, null, null);
	}
	
	public static class TeleportInformation{
		public BaseInfo destination;
		public int stackSize;
		public WorldServer world;
		public BoolRun boolRun;
		public TeleportInformation(BaseInfo destination, int stackSize, WorldServer world, BoolRun boolRun) {
			this.destination = destination;
			this.stackSize = stackSize;
			this.world = world;
			this.boolRun = boolRun;
		}
	}
	
	/**
	 * @return whether the player could pay
	 */
	public static boolean pay(EntityPlayer player, int x1, int y1, int z1, int x2, int y2, int z2){
		if(canPay(player, x1, y1, z1, x2, y2, z2)){
			doPay(player, x1, y1, z1, x2, y2, z2);
			return true;
		}else{
			return false;
		}
	}
	
	public static boolean canPay(EntityPlayer player, int x1, int y1, int z1, int x2, int y2, int z2){
		if(ClientConfigStorage.INSTANCE.getCost() == null || ConfigHandler.isCreative(player)){
			return true;
		}else{
			int playerItemCount = 0;
			for(ItemStack now: player.inventory.mainInventory){
				if(now != null && now.getItem() !=null && now.getItem().getClass() == ClientConfigStorage.INSTANCE.getCost().getClass()){
					playerItemCount += now.stackSize;
				}
			}
			return playerItemCount>=getStackSize(x1, y1, z1, x2, y2, z2);
		}
	}

	public static void doPay(EntityPlayer player, int x1, int y1, int z1, int x2, int y2, int z2){
		if(ClientConfigStorage.INSTANCE.getCost() == null || ConfigHandler.isCreative(player)){
			return;
		}else{
			int stackSize = getStackSize(x1, y1, z1, x2, y2, z2);
			while(stackSize-->0){
				player.inventory.consumeInventoryItem(ClientConfigStorage.INSTANCE.getCost());
			}
		}
	}
	
	public static int getStackSize(int x1, int y1, int z1, int x2, int y2, int z2){
		if(ClientConfigStorage.INSTANCE.getCostMult()==0){
			return 1;
		}else{
			int dx = x1-x2; int dy = y1-y2; int dz = z1-z2;
			return (int) Math.sqrt(dx*dx+dy*dy+dz*dz) / ClientConfigStorage.INSTANCE.getCostMult() + 1;
		}
	}
	
	public static int getStackSize(MyBlockPos pos1, MyBlockPos pos2){
		return getStackSize(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z);
	}
	

	public static void confirm(final EntityPlayerMP player){
		final TeleportInformation info = awaiting.get(player.getUniqueID());
		SPEventHandler.scheduleTask(new Runnable(){
			@Override
			public void run() {
				if(info==null){
					NetworkHandler.netWrap.sendTo(new ChatMessage("signpost.noConfirm"), player);
					return;
				}else{
					doPay(player, (int)player.posX, (int)player.posY, (int)player.posZ, info.destination.pos.x, info.destination.pos.y+1, info.destination.pos.z);
					SPEventHandler.cancelTask(info.boolRun);
					ServerConfigurationManager manager = player.mcServer.getConfigurationManager();
					if(!(player.getServerForPlayer().getWorldInfo().getWorldName().equals(info.world.getWorldInfo().getWorldName()))){
						manager.transferEntityToWorld(player, player.dimension, player.getServerForPlayer(), info.world, new SignTeleporter(info.world));
					}
					if(!(player.dimension==info.destination.pos.dim)){
						manager.transferPlayerToDimension(player, info.destination.pos.dim, new SignTeleporter(info.world));
//						player.changeDimension(info.destination.pos.dim);
					}
					player.setPositionAndUpdate(info.destination.pos.x+0.5, info.destination.pos.y+1, info.destination.pos.z+0.5);
				}
			}
		}, 1);
	}

	public static void teleportMe(BaseInfo destination, final EntityPlayerMP player, int stackSize){
		if(ClientConfigStorage.INSTANCE.deactivateTeleportation()){
			return;
		}
		if(canTeleport(player, destination)){
			WorldServer world = (WorldServer) destination.pos.getWorld();
			if(world == null){
				NetworkHandler.netWrap.sendTo(new ChatMessage("signpost.errorWorld", "<world>", destination.pos.world), player);
			}else{
				SPEventHandler.scheduleTask(awaiting.put(player.getUniqueID(), new TeleportInformation(destination, stackSize, world, new BoolRun(){
					private short ticksLeft = 2400;
					@Override
					public boolean run() {
						if(ticksLeft--<=0){
							awaiting.remove(player.getUniqueID());
							return true;
						}
						return false;
					}
				})).boolRun);
				NetworkHandler.netWrap.sendTo(new TeleportRequestMessage(stackSize, destination.getName()), player);
			}
		}
	}
	
	public static StonedHashSet getByWorld(String world){
		StonedHashSet ret = new StonedHashSet();
		for(BaseInfo now: getAllWaystones()){
			if(now.pos.sameWorld(world)){
				ret.add(now);
			}
		}
		return ret;
	}
	
	public static boolean updateWS(BaseInfo newWS, boolean destroyed){
		if(destroyed){
			if(allWaystones.remove(getWSbyName(newWS.getName()))){
				for(Entry<UUID, Pair<MyBlockPosSet, Pair<Integer, Integer>>> now: playerKnownWaystonePositions.entrySet()){
				}
				return true;
			}
			return false;
		}
		for(BaseInfo now: allWaystones){
			if(now.update(newWS)){
				return true;
			}
		}
		return allWaystones.add(newWS);
	}
	
	public static boolean addAllDiscoveredByName(UUID player, StringSet ws){
		MyBlockPosSet set = new MyBlockPosSet();
		StringSet newStrs = new StringSet();
		newStrs.addAll(ws);
		for(String now: ws){
			for(BaseInfo base: getAllWaystones()){
				if(base.getName().equals(now)){
					set.add(base.blockPos);
					newStrs.remove(now);
				}
			}
		}
		ws = newStrs;
		boolean ret = false;
		if(!ws.isEmpty()) if(playerKnownWaystones.containsKey(player)){
			ret = playerKnownWaystones.get(player).addAll(ws);
		}else{
			StringSet strSet = new StringSet();
			ret = strSet.addAll(ws);
			playerKnownWaystones.put(player, strSet);
		}
		if(playerKnownWaystonePositions.containsKey(player)){
			return ret | playerKnownWaystonePositions.get(player).a.addAll(set);
		}else{
			MyBlockPosSet newSet = new MyBlockPosSet();
			ret = ret | newSet.addAll(set);
			Pair<MyBlockPosSet, Pair<Integer, Integer>> pair = new Pair<MyBlockPosSet, Pair<Integer, Integer>>();
			pair.a  = newSet;
			pair.b = new Pair<Integer, Integer>();
			pair.b.a = ClientConfigStorage.INSTANCE.getMaxWaystones();
			pair.b.b = ClientConfigStorage.INSTANCE.getMaxSignposts();
			playerKnownWaystonePositions.put(player, pair);
			return ret;
		}
	}
	
	public static boolean addAllDiscoveredByPos(UUID player, MyBlockPosSet ws){
		if(playerKnownWaystonePositions.containsKey(player)){
			return playerKnownWaystonePositions.get(player).a.addAll(ws);
		}else{
			MyBlockPosSet newSet = new MyBlockPosSet();
			boolean ret = newSet.addAll(ws);
			Pair<MyBlockPosSet, Pair<Integer, Integer>> pair = new Pair<MyBlockPosSet, Pair<Integer, Integer>>();
			pair.a  = newSet;
			pair.b = new Pair<Integer, Integer>();
			pair.b.a = ClientConfigStorage.INSTANCE.getMaxWaystones();
			pair.b.b = ClientConfigStorage.INSTANCE.getMaxSignposts();
			playerKnownWaystonePositions.put(player, pair);
			return ret;
		}
	}
	
	public static boolean addDiscovered(UUID player, BaseInfo ws){
		if(ws==null){
			return false;
		}
		if(playerKnownWaystonePositions.containsKey(player)){
			boolean ret = playerKnownWaystonePositions.get(player).a.add(ws.blockPos);
			ret = ret |! (playerKnownWaystonePositions.containsKey(player) && playerKnownWaystones.get(player).remove(ws.getName()));
			return ret;
		}else{
			MyBlockPosSet newSet = new MyBlockPosSet();
			newSet.add(ws.blockPos);
			Pair<MyBlockPosSet, Pair<Integer, Integer>> pair = new Pair<MyBlockPosSet, Pair<Integer, Integer>>();
			pair.a  = newSet;
			pair.b = new Pair<Integer, Integer>();
			pair.b.a = ClientConfigStorage.INSTANCE.getMaxWaystones();
			pair.b.b = ClientConfigStorage.INSTANCE.getMaxSignposts();
			playerKnownWaystonePositions.put(player, pair);
			return !(playerKnownWaystonePositions.containsKey(player) && playerKnownWaystones.get(player).remove(ws.getName()));
		}
	}
	
	public static void refreshDiscovered(){
		HashSet<UUID> toDelete = new HashSet<UUID>();
		HashMap<UUID, MyBlockPosSet> toAdd = new HashMap<UUID, MyBlockPosSet>();
		for(Entry<UUID, StringSet> now: playerKnownWaystones.entrySet()){
			StringSet newSet = new StringSet();
			MyBlockPosSet newPosSet = new MyBlockPosSet();
			for(String str: now.getValue()){
				for(BaseInfo base: allWaystones){
					if(base.hasName() && base.getName().equals(str)){
						newPosSet.add(base.blockPos);
						newSet.add(str);
					}
				}
			}
			toAdd.put(now.getKey(), newPosSet);
			now.getValue().removeAll(newSet);
			if(now.getValue().isEmpty()){
				toDelete.add(now.getKey());
			}
		}
		
		for(UUID now: toDelete){
			playerKnownWaystones.remove(now);
		}
		
		for(Entry<UUID, MyBlockPosSet> now: toAdd.entrySet()){
			addAllDiscoveredByPos(now.getKey(), now.getValue());
		}
	}
	
	public static boolean canTeleport(EntityPlayerMP player, BaseInfo target){
		if(doesPlayerKnowWaystone(player, target)){
			if(new MyBlockPos(player).checkInterdimensional(target.blockPos)){
				return true;
			}else{
				NetworkHandler.netWrap.sendTo(new ChatMessage("signpost.guiWorldDim"), player);
			}
		}else{
			NetworkHandler.netWrap.sendTo(new ChatMessage("signpost.notDiscovered", "<Waystone>", target.getName()), player);
		}
		return false;
	}
	
	public static WorldServer getWorldByName(String world, int dim){
		WorldServer ret = null;
		forLoop:
			for(WorldServer now: FMLCommonHandler.instance().getMinecraftServerInstance().worldServers){
			if(now.getWorldInfo().getWorldName().equals(world)){
				ret = now;
				continue forLoop;
			}
		}
		if(dim!=0 || world==null){
			ret = FMLCommonHandler.instance().getMinecraftServerInstance().worldServerForDimension(dim);
		}
		return ret;
	}

	public static boolean addRep(BaseInfo ws) {
		BaseInfo toDelete = allWaystones.getByPos(ws.blockPos);
		allWaystones.removeByPos(toDelete.blockPos);
		allWaystones.add(ws);
		return true;
	}

	public static EntityPlayer getPlayerByName(String name){
		for(Object player : MinecraftServer.getServer().getConfigurationManager().playerEntityList){
			if(player instanceof EntityPlayer && ((EntityPlayer)player).getCommandSenderName().equals(name)){
				return (EntityPlayer) player;
			}
		}
		return null;
	}

	private static class SignTeleporter extends Teleporter{

		public SignTeleporter(WorldServer worldIn) {super(worldIn);}

		@Override
		public void placeInPortal(Entity entityIn, double p_77185_2_, double p_77185_4_, double p_77185_6_, float p_77185_8_){}

		@Override
		public boolean placeInExistingPortal(Entity entityIn, double p_77185_2_, double p_77185_4_, double p_77185_6_, float p_77185_8_){return true;}

		@Override
		public boolean makePortal(Entity entityIn){return true;}

		@Override
		public void removeStalePortalLocations(long worldTime){}
	}

	public static StonedHashSet getAllWaystones() {
		StonedHashSet ret = SignpostAdapter.INSTANCE.getExternalBaseInfos();
		ret.addAll(allWaystones);
		return ret;
	}

	public static Collection<String> getAllWaystoneNames(){
		Collection<String> ret = getAllWaystones().select(new Function<BaseInfo, String>() {
			@Override
			public String apply(BaseInfo b) {
				return b.getName();
			}
		});
		if(FMLCommonHandler.instance().getEffectiveSide().equals(Side.CLIENT)) {
			ret.addAll(SendAllWaystoneNamesHandler.cachedWaystoneNames);
		}
		return ret;
	}

	public static StonedHashSet getNativeWaystones(){
		return allWaystones;
	}
	
	public static void setNativeWaystones(StonedHashSet set){
		allWaystones = set;
	}
	
	public static StonedHashSet getPlayerKnownWaystones(EntityPlayerMP player){
		StonedHashSet ret = SignpostAdapter.INSTANCE.getExternalPlayerBaseInfos(player);
		for(BaseInfo now: allWaystones){
			if(doesPlayerKnowNativeWaystone(player, now)){
				ret.add(now);
			}
		}
		return ret;
	}
	
	public static boolean addWaystone(BaseInfo baseInfo){
		return allWaystones.add(baseInfo);
	}
}
