package edgruberman.bukkit.snowbuild;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import edgruberman.bukkit.snowbuild.util.CustomPlugin;

public class Main extends CustomPlugin implements Listener {

    private final Map<Byte, Integer> snowballs = new HashMap<Byte, Integer>();

    @Override
    public void onLoad() { this.putConfigMinimum(CustomPlugin.CONFIGURATION_FILE, "1.0.0"); }

    @Override
    public void onEnable() {
        this.reloadConfig();

        final ConfigurationSection section = this.getConfig().getConfigurationSection("snowballs");
        for (final String key : section.getKeys(false))
            this.snowballs.put(Byte.valueOf(key), section.getInt(key));

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        this.snowballs.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent interaction) {
        // right click with snow in hand
        if (interaction.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (interaction.getPlayer().getItemInHand().getTypeId() != Material.SNOW.getId()) return;
        if (!interaction.getPlayer().hasPermission("snowbuild.place")) return;

        // directly clicked block is snow
        if (interaction.getClickedBlock().getTypeId() == Material.SNOW.getId()) {
            if (!this.addLayer(interaction.getPlayer(), interaction.getClickedBlock(), interaction.getClickedBlock())) return;
            interaction.setUseInteractedBlock(Result.DENY);
            interaction.setUseItemInHand(Result.DENY);
            return;
        }

        // indirectly targeted block is snow
        final Block affected = interaction.getClickedBlock().getRelative(interaction.getBlockFace());
        if (affected.getTypeId() == Material.SNOW.getId()) {
            if (!this.addLayer(interaction.getPlayer(), affected, interaction.getClickedBlock())) return;
            interaction.setUseInteractedBlock(Result.DENY);
            interaction.setUseItemInHand(Result.DENY);
            return;
        }
    }

    private boolean addLayer(final Player player, final Block snow, final Block clicked) {
        // capture original and set new
        final BlockState original = snow.getState();
        byte data = snow.getData();
        if (data >= 6) {
            snow.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, false);
        } else {
            snow.setData(++data);
        }

        // give other plugins a chance to cancel
        // TODO adjust canBuild parameter to be same as CraftEventFactory.canBuild
        final BlockPlaceEvent place = new BlockPlaceEvent(snow, original, clicked, player.getItemInHand(), player, true);
        Bukkit.getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) {
            snow.setTypeIdAndData(original.getTypeId(), original.getRawData(), false);
            return false;
        }

        // update physics, process item, produce sound
        snow.getState().update();
        player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
        snow.getWorld().playSound(snow.getLocation(), Sound.STEP_SNOW, 0.5F, 1F);

        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent place) {
        // prevent accidental snow replacement when more than 1 layer
        if (place.getBlockReplacedState().getTypeId() != Material.SNOW.getId()) return;
        if (place.getBlockReplacedState().getRawData() == 0) return;

        place.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFadeEvent(final BlockFadeEvent fade) {
        if (fade.getBlock().getTypeId() != Material.SNOW.getId()) return;
        if (fade.getBlock().getData() == 0) return;

        fade.setCancelled(true);
        fade.getBlock().setData((byte) (fade.getBlock().getData() - 1));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreakEvent(final BlockBreakEvent broken) {
        if (broken.getBlock().getTypeId() != Material.SNOW.getId()) return;
        if (!Main.SPADES.contains(broken.getPlayer().getItemInHand().getTypeId())) return;
        if (broken.getBlock().getData() == 0) return;

        final ItemStack drop = this.dropFor(broken.getBlock().getData());
        if (drop == null) return;

        broken.setCancelled(true);
        broken.getBlock().setTypeIdAndData(Material.AIR.getId(), (byte) 0, true);
        broken.getBlock().getWorld().dropItemNaturally(broken.getBlock().getLocation(), drop);
    }

    private ItemStack dropFor(final byte data) {
        final Integer count = this.snowballs.get(data);
        if (count == null) return null;

        return new ItemStack(Material.SNOW_BALL, count);
    }

    private final static List<Integer> SPADES = Arrays.asList(
            Material.WOOD_SPADE.getId()
          , Material.STONE_SPADE.getId()
          , Material.IRON_SPADE.getId()
          , Material.DIAMOND_SPADE.getId()
          );

}
