package me.pride.abilities.blossom;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PlantAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.healing.HealingWaters;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Blossom extends PlantAbility implements AddonAbility, ComboAbility {
	private final String path = "ExtraAbilities.Prride.Blossom.";
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RADIUS)
	private double radius;
	@Attribute(Attribute.DURATION)
	private long duration;
	private int growth;
	private boolean revert;
	@Attribute(Attribute.DURATION)
	private long revert_duration;
	@Attribute("Delay")
	private double max_delay;
	
	private double delay;
	
	public Blossom(Player player) {
		super(player);
		
		if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
			return;
		} else if (bPlayer.isOnCooldown(this)) {
			return;
		} else if (RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.radius = ConfigManager.getConfig().getDouble(path + "Radius");
		this.duration = ConfigManager.getConfig().getLong(path + "Duration");
		this.revert = ConfigManager.getConfig().getBoolean(path + "PlantsRevert");
		this.revert_duration = ConfigManager.getConfig().getInt(path + "RevertDuration");
		this.max_delay = ConfigManager.getConfig().getDouble(path + "AgeableGrowthDelay");
		this.growth = ConfigManager.getConfig().getInt(path + "GrowthRate");
		
		if (CoreAbility.hasAbility(player, HealingWaters.class)) {
			getAbility(player, HealingWaters.class).remove();
		}
		start();
	}
	
	@Override
	public void progress() {
		if (!bPlayer.canBendIgnoreBinds(this)) {
			remove();
			return;
		}
		if (System.currentTimeMillis() > getStartTime() + duration) {
			remove();
			return;
		}
		if (!player.isSneaking()) {
			remove();
			return;
		}
		blossom();
	}
	
	private void blossom() {
		Predicate<Block> plantCanGenerate = b -> {
			boolean canGeneratePlants = b.getType() == Material.GRASS_BLOCK || b.getType() == Material.DIRT || (b.getType().name().contains("LEAVES") && isAir(b.getRelative(BlockFace.UP).getType()));
			boolean canGenerateMushroom = spawnableMushroom(b.getType());
			boolean canGenerateSeaPlants = isWater(b.getType()) && spawnableInWater(b.getRelative(BlockFace.DOWN).getType());
			return !RegionProtection.isRegionProtected(player, b.getLocation(), this) && (canGeneratePlants || canGenerateMushroom || canGenerateSeaPlants);
		};
		List<Block> blocks =
				GeneralMethods.getBlocksAroundPoint(player.getLocation(), radius)
						.stream()
						.filter(plantCanGenerate)
						.collect(Collectors.toList());
		
		if (blocks.size() > 0) {
			delay += 0.05;
			if (delay > max_delay) {
				for (Block b : GeneralMethods.getBlocksAroundPoint(player.getLocation(), radius)) {
					if (b.getBlockData() instanceof Ageable) {
						Ageable crop = (Ageable) b.getBlockData();
						
						if (crop.getAge() < crop.getMaximumAge()) {
							crop.setAge(crop.getAge() + 1);
							b.setBlockData(crop);
							ParticleEffect.VILLAGER_HAPPY.display(b.getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.2);
						}
					}
					/*
					if (isGrowable(b.getType())) {
						b.getRelative(BlockFace.UP).setType(b.getType());
						ParticleEffect.VILLAGER_HAPPY.display(b.getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.2);
					}
					 */
				}
				delay = 0;
			}
			Block block = blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()));
			
			for (int i = 0; i < growth; i++) {
				if (spawnPlants(block, (data, spawnAbove) -> {
					if (spawnAbove) {
						if (isAir(block.getRelative(BlockFace.UP).getType())) {
							ParticleEffect.VILLAGER_HAPPY.display(block.getRelative(BlockFace.UP).getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.2);
							if (revert) {
								new TempBlock(block.getRelative(BlockFace.UP), data, revert_duration);
							} else {
								block.getRelative(BlockFace.UP).setType(data.getMaterial());
							}
						}
					} else {
						ParticleEffect.VILLAGER_HAPPY.display(block.getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.2);
						if (revert) {
							new TempBlock(block, data, revert_duration);
						} else {
							block.setType(data.getMaterial());
						}
					}
				}));
			}
		}
	}
	
	private boolean spawnPlants(Block block, BiConsumer<BlockData, Boolean> consumer) {
		Predicate<Block> condition = b -> {
			if (isWater(b)) {
				if (spawnableInWater(b.getRelative(BlockFace.DOWN).getType()) && (isWater(b.getRelative(BlockFace.UP)) || isAir(b.getRelative(BlockFace.UP).getType()))) {
					consumer.accept(getRandomizedSeaPlants(), false);
					return true;
				}
			} else if (b.getType() == Material.DIRT) {
				consumer.accept(Material.GRASS_BLOCK.createBlockData(), false);
				return true;
			} else if (b.getType() == Material.GRASS_BLOCK) {
				consumer.accept(getRandomizedPlants(), true);
				return true;
			} else if (spawnableMushroom(b.getType())) {
				BlockData data = ThreadLocalRandom.current().nextBoolean() ? Material.RED_MUSHROOM.createBlockData() : Material.BROWN_MUSHROOM.createBlockData();
				consumer.accept(data, true);
				return true;
			}
			return false;
		};
		return condition.test(block);
	}
	
	private boolean spawnableInWater(Material material) {
		switch (material) {
			case GRASS_BLOCK:
			case DIRT:
			case GRAVEL:
			case SAND:
			case STONE:
				return true;
		}
		return false;
	}
	
	private boolean spawnableMushroom(Material material) {
		if (material.name().contains("_LOG")) {
			return true;
		} else if (material == Material.MYCELIUM || material == Material.SOUL_SAND || material == Material.PODZOL) {
			return true;
		}
		return false;
	}
	
	private BlockData getRandomizedPlants() {
		if (ThreadLocalRandom.current().nextBoolean()) {
			return ThreadLocalRandom.current().nextBoolean() ? Material.GRASS.createBlockData() : Material.FERN.createBlockData();
		} else {
			return getRandomizedFlowers();
		}
	}
	
	private BlockData getRandomizedSeaPlants() {
		if (ThreadLocalRandom.current().nextBoolean()) {
			return Material.SEAGRASS.createBlockData();
		} else {
			return ThreadLocalRandom.current().nextBoolean() ? Material.TALL_SEAGRASS.createBlockData() : Material.KELP_PLANT.createBlockData();
		}
	}
	
	private BlockData getRandomizedFlowers() {
		Material[] flowers = { Material.DANDELION, Material.POPPY, Material.ALLIUM, Material.LILY_OF_THE_VALLEY, Material.CORNFLOWER };
		
		return flowers[ThreadLocalRandom.current().nextInt(flowers.length)].createBlockData();
	}
	
	private boolean isGrowable(Material material) {
		switch (material) {
			case SUGAR_CANE:
			case CACTUS:
			case BAMBOO:
			case BAMBOO_SAPLING:
				return true;
		}
		return false;
	}
	
	@Override
	public boolean isSneakAbility() {
		return false;
	}
	
	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}
	
	@Override
	public String getName() {
		return "Blossom";
	}
	
	@Override
	public Location getLocation() {
		return null;
	}
	
	@Override
	public void remove() {
		bPlayer.addCooldown(this);
		super.remove();
	}
	
	@Override
	public void load() {
		ProjectKorra.log.info(getName() + " by " + getAuthor() + " " + getVersion() + " loaded!");
		
		ConfigManager.getConfig().addDefault(path + "Enabled", true);
		ConfigManager.getConfig().addDefault(path + "Cooldown", 6500);
		ConfigManager.getConfig().addDefault(path + "Duration", 2500);
		ConfigManager.getConfig().addDefault(path + "Radius", 6);
		ConfigManager.getConfig().addDefault(path + "AgeableGrowthDelay", 2);
		ConfigManager.getConfig().addDefault(path + "GrowthRate", 2);
		ConfigManager.getConfig().addDefault(path + "PlantsRevert", false);
		ConfigManager.getConfig().addDefault(path + "RevertTime", 20000);
		ConfigManager.defaultConfig.save();
	}
	
	@Override
	public void stop() { }
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.Blossom.Enabled", true);
	}
	
	@Override
	public String getDescription() {
		return "Waterbenders are able to redirect energy paths within plants in order to initiate plant growth "
				+ "and cause deep roots of trees to grow flowers from underground! When used on mycelium and soul sand, these plants will "
				+ "blossom into mushrooms. When used near seed sources, they will grow immediately.";
	}
	
	@Override
	public String getInstructions() {
		return ChatColor.GOLD + "HealingWaters (Right click block twice) > HealingWaters (Hold sneak)";
	}
	
	@Override
	public String getAuthor() {
		return "Prride & Shookified";
	}
	
	@Override
	public String getVersion() {
		return "Version 1";
	}
	
	@Override
	public Object createNewComboInstance(Player player) {
		return new Blossom(player);
	}
	
	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> info = new ArrayList<>();
		info.add(new AbilityInformation("HealingWaters", ClickType.RIGHT_CLICK_BLOCK));
		info.add(new AbilityInformation("HealingWaters", ClickType.RIGHT_CLICK_BLOCK));
		info.add(new AbilityInformation("HealingWaters", ClickType.SHIFT_DOWN));
		return info;
	}
}
