/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package skinsrestorer.bukkit.commands;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import skinsrestorer.bukkit.SkinsRestorer;
import skinsrestorer.shared.api.SkinsRestorerAPI;
import skinsrestorer.shared.format.SkinProfile;
import skinsrestorer.shared.storage.ConfigStorage;
import skinsrestorer.shared.storage.CooldownStorage;
import skinsrestorer.shared.storage.LocaleStorage;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.C;
import skinsrestorer.shared.utils.MySQL;
import skinsrestorer.shared.utils.SkinFetchUtils;
import skinsrestorer.shared.utils.SkinFetchUtils.SkinFetchFailedException;
import skinsrestorer.shared.utils.Updater;

public class Commands implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, final String[] args) {
		if (command.getName().equalsIgnoreCase("skin")) {
			if (ConfigStorage.getInstance().DISABLE_SKIN_COMMAND) {
				sender.sendMessage(C.c(LocaleStorage.getInstance().UNKNOWN_COMMAND));
				return true;
			}
			if (!sender.hasPermission("skinsrestorer.playercmds")) {
				sender.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_HAS_NO_PERMISSION));
				return true;
			}
			if (!(sender instanceof Player)) {
				sender.sendMessage("This commands are only for players");
				return true;
			}
			final Player player = (Player) sender;
			if (args.length == 0) {
				player.sendMessage(C.c(LocaleStorage.getInstance().USE_SKIN_HELP));
			} else if ((args.length == 1) && args[0].equalsIgnoreCase("help")) {
				helpCommand(player);
			} else if ((args.length == 1) && args[0].equalsIgnoreCase("clear")) {
				clearCommand(player);
			} else if ((args.length == 2) && args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("change")) {
				changeCommand(player, args);
			} else
				player.sendMessage(C.c(LocaleStorage.getInstance().USE_SKIN_HELP));
		} else if (command.getName().equalsIgnoreCase("skinsrestorer")) {
			if (!sender.hasPermission("skinsrestorer.cmds")) {
				sender.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_HAS_NO_PERMISSION));
				return true;
			}
			if (args.length == 0) {
				sender.sendMessage(C.c(LocaleStorage.getInstance().ADMIN_USE_SKIN_HELP));
			} else if ((args.length == 1) && args[0].equalsIgnoreCase("help")) {
				helpCommandAdmin(sender);
			} else if ((args.length == 1) && args[0].equalsIgnoreCase("info")){
				infoCommand(sender, args);
			} else if ((args.length == 2) && args[0].equalsIgnoreCase("drop")) {
				dropData(sender, args);
			} else if ((args.length == 2) && args[0].equalsIgnoreCase("update")) {
				updateCommand(sender, args);
			} else if ((args.length == 3) && args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("change")) {
                changeCommand(sender, args);
			} else
				sender.sendMessage(C.c(LocaleStorage.getInstance().ADMIN_USE_SKIN_HELP));
			return false;
		}
		return false;
	}

	//////////////////////////////////// PLAYER COMMANDS
	//////////////////////////////////// //////////////////////////////////////
	// Skin help command.
	public void helpCommand(Player p) {
		p.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_HELP));
	}

	// Skin clear command.
	public void clearCommand(Player player) {
		if (SkinStorage.getInstance().isSkinDataForced(player.getName())) {
			SkinStorage.getInstance().removeSkinData(player.getName());
			SkinsRestorerAPI.removeSkin(player);
			player.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_SKIN_CHANGE_SKIN_DATA_CLEARED));
			return;
		}
		SkinsRestorerAPI.removeSkin(player);
		player.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_SKIN_CHANGE_SKIN_DATA_CLEARED));
	}

	// Skin change command.
	public void changeCommand(final Player player, final String[] args) {
		if (!player.hasPermission("skinsrestorer.bypasscooldown")) {
			if (CooldownStorage.getInstance().isAtCooldown(player.getUniqueId())) {
				player.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_SKIN_COOLDOWN.replace("%s",
						"" + ConfigStorage.getInstance().SKIN_CHANGE_COOLDOWN)));
				return;
			}
			CooldownStorage.getInstance().setCooldown(player.getUniqueId(),
					ConfigStorage.getInstance().SKIN_CHANGE_COOLDOWN, TimeUnit.SECONDS);
		}
		SkinsRestorer.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				String from = args[1];
				if (!player.hasPermission("skinsrestorer.disabledskins")) {
					for (String disabledSkins : ConfigStorage.getInstance().DISABLED_SKINS) {
						if (disabledSkins.equalsIgnoreCase(from)) {

							player.sendMessage(C.c(LocaleStorage.getInstance().DISABLED_SKIN));
							return;
						}
					}
				}
				try {
					SkinProfile skinprofile = SkinFetchUtils.fetchSkinProfile(from, null);
					SkinStorage.getInstance().setSkinData(player.getName(), skinprofile);
					skinprofile.attemptUpdate();
					SkinsRestorerAPI.applySkin(player);
					player.sendMessage(C.c(LocaleStorage.getInstance().PLAYER_SKIN_CHANGE_SUCCESS));
				} catch (SkinFetchFailedException e) {
					player.sendMessage(C.c(LocaleStorage.getInstance().SKIN_FETCH_FAILED) + e.getMessage());
				}
			}
		});
		return;
	}

	//////////////////////////////////// ADMIN COMMANDS
	//////////////////////////////////// //////////////////////////////////////
	// Admin Help Command
	public void helpCommandAdmin(CommandSender sender) {
		sender.sendMessage(C.c(LocaleStorage.getInstance().ADMIN_HELP));
	}

	// Drop data command
	public void dropData(CommandSender sender, String[] args) {
		SkinStorage.getInstance().removeSkinData(args[1]);
		if (Bukkit.getPlayer(args[1]) != null) {
			SkinsRestorerAPI.removeSkin(Bukkit.getPlayer(args[1]));
		}
		sender.sendMessage(C.c(LocaleStorage.getInstance().SKIN_DATA_DROPPED.replace("%player", args[1])));
	}

	// Skin update command
	public void updateCommand(final CommandSender sender, final String[] args) {
		SkinsRestorer.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				String name = args[1];
				try {
					SkinStorage.getInstance().getOrCreateSkinData(name).attemptUpdate();
					if (Bukkit.getPlayer(args[1]) != null) {
						SkinsRestorerAPI.applySkin(Bukkit.getPlayer(args[1]));
					}
					sender.sendMessage(C.c(LocaleStorage.getInstance().SKIN_DATA_UPDATED));
				} catch (SkinFetchFailedException e) {
					sender.sendMessage(C.c(LocaleStorage.getInstance().SKIN_FETCH_FAILED) + e.getMessage());
				}
			}
		});
	}

	public void infoCommand(CommandSender sender, String[] args){
		sender.sendMessage(C.c("&7=========== &9SkinsRestorer Info &7============"));
		String version = SkinsRestorer.getInstance().getVersion();
		sender.sendMessage(C.c("  \n&2&lVersion Info"));
		sender.sendMessage(C.c("   &fYour SkinsRestorer version is &9"+version));
		if (ConfigStorage.getInstance().UPDATE_CHECK&&Updater.updateAvailable()){
			sender.sendMessage(C.c("  \n&2&lUpdates Info"));
		sender.sendMessage(C.c("   &fThe latest version is &9"+Updater.getHighest()));
		}
		if (ConfigStorage.getInstance().USE_MYSQL){
		   sender.sendMessage(C.c("  \n&2&lMySQL Info"));
		   if (MySQL.isConnected()){
			   sender.sendMessage(C.c("    &aMySQL connection is OK."));
		   }else{
			   sender.sendMessage(C.c("    &cMySQL is enabled, but not connected!\n    In order to use MySQL please fill the \n    config with the required info!"));
		   }
		}
		sender.sendMessage(C.c("  \n&2&lOther Info"));
		sender.sendMessage(C.c("    &fMCAPI &7| &9"+ConfigStorage.getInstance().MCAPI_ENABLED));
		sender.sendMessage(C.c("    &fBot feature &7| &9"+ConfigStorage.getInstance().USE_BOT_FEATURE));
		sender.sendMessage(C.c("    &fAutoIn Skins &7| &9"+ConfigStorage.getInstance().USE_AUTOIN_SKINS));
		sender.sendMessage(C.c("    &fDisable /Skin Command &7| &9"+ConfigStorage.getInstance().DISABLE_SKIN_COMMAND));
		sender.sendMessage(C.c("    &fSkin Change Cooldown | &9"+ConfigStorage.getInstance().SKIN_CHANGE_COOLDOWN+" Seconds"));
	}
	// Admin skin change command.
	public void changeCommand(final CommandSender sender, final String[] args) {
		SkinsRestorer.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				String from = args[2];
				try {
					SkinProfile skinprofile = SkinFetchUtils.fetchSkinProfile(from, null);
					SkinStorage.getInstance().setSkinData(args[1], skinprofile);
					if (Bukkit.getPlayer(args[1]) != null) {
						SkinsRestorerAPI.applySkin(Bukkit.getPlayer(args[1]));
					}
					sender.sendMessage(C.c(LocaleStorage.getInstance().ADMIN_SET_SKIN.replace("%player", args[1])));
				} catch (SkinFetchFailedException e) {
					sender.sendMessage(C.c(LocaleStorage.getInstance().SKIN_FETCH_FAILED) + e.getMessage());
				}
			}
		});
		return;
	}
}
