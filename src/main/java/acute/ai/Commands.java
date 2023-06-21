package acute.ai;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class Commands implements TabExecutor {

    private final CraftGPT craftGPT;
    public Commands(CraftGPT craftGPT) {
        this.craftGPT = craftGPT;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> commands = List.of("wand", "help", "stop", "info", "backstory", "rawprompt", "reload", "dryrun", "debug", "name", "temperature", "remove", "create", "clear", "save", "displayname");
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands, completions);
            return completions;
        }
        return null;
    }

    public void helpCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgpt.help")) {
            sayNoPermission(sender);
        } else {
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW + "==========| " + ChatColor.GRAY + "CraftGPT Help" +
                    ChatColor.YELLOW + " |==========");
            String messageStr = CraftGPT.CHAT_PREFIX + "Join the Discord ";
            if (sender instanceof Player) {
                TextComponent link = new TextComponent("here");
                link.setUnderlined(true);
                link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        craftGPT.DISCORD_URL));
                link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GOLD + "discord.com")));
                BaseComponent message = new TextComponent(TextComponent.fromLegacyText(messageStr));
                message.addExtra(link);
                sender.spigot().sendMessage(message);
            }
            else {
                sender.sendMessage(messageStr + "at " + craftGPT.DISCORD_URL);
            }
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg wand" + ChatColor.GRAY + " Get a magic wand");
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg stop" + ChatColor.GRAY + " Exit chat mode");
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg reload" + ChatColor.GRAY + " Reload config.yml and plugin data");
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg create" + ChatColor.GRAY + " Enable AI for selected mob");
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg remove" + ChatColor.GRAY + " Remove AI for selected mob");
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "/cg clear" + ChatColor.GRAY + " Clear mob-builder settings");

        }
    }

    public void reloadCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgpt.reload")) {
            sayNoPermission(sender);
        } else {
            craftGPT.saveDefaultConfig();
            craftGPT.reloadConfig();
            craftGPT.writeData(craftGPT);
            craftGPT.readData(craftGPT);
            craftGPT.enableOpenAI();
            sender.sendMessage(craftGPT.CHAT_PREFIX + "Config and data reloaded!");
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CraftGPTListener craftGPTListener = new CraftGPTListener(craftGPT);
        if (command.getName().equalsIgnoreCase("craftgpt") || command.getName().equalsIgnoreCase("cg")) {

            // Commands that can be run with no API key
            if (!craftGPT.apiKeySet) {
                if (args.length > 0) {
                    if (args[0].equals("help")) {
                        helpCommand(sender);
                    } else if (args[0].equals("reload")) {
                        reloadCommand(sender);
                    } else {
                        sender.sendMessage(craftGPT.CHAT_PREFIX + "Malformed command or must run command as player!");
                    }
                } else {
                        sender.sendMessage(craftGPT.CHAT_PREFIX + "CraftGPT requires an OpenAI API key set in the config!");
                    }
            }

            // Commands that require an API key
            else {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 0) {
                        if (args[0].equals("help")) {
                            helpCommand(sender);
                        } else if (args[0].equals("reload")) {
                            reloadCommand(sender);
                        } else if (args[0].equals("displayname")) {
                            if (args.length > 1) {
                                String name = "";
                                for (int i = 1; i < args.length - 1; i++) {
                                    name = name + args[i] + " ";
                                }
                                name = name + args[args.length - 1];
                                craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().setCustomNameVisible(true);
                                craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().setCustomName(name);
                                player.sendMessage(craftGPT.CHAT_PREFIX + "Name set to: " + ChatColor.GOLD + name);
                            } else {
                                player.sendMessage(craftGPT.CHAT_PREFIX + "No name provided. Try again!");
                            }
                        } else if (args[0].equals("iterate")) {
                            if (!player.hasPermission("craftgpt.iterate")) {
                                sayNoPermission(player);
                            } else {
                                player.sendMessage(craftGPT.CHAT_PREFIX + "Coming soon.");
                            }
                            /*
                            for (int i = 1; i < 21; i++) {
                                player.performCommand("npc select " + i);
                                player.performCommand("npc look");
                            }
                             */
                        } else if (args[0].equals("save")) {
                            if (!player.hasPermission("craftgpt.save")) {
                                sayNoPermission(player);
                            } else {
                                craftGPT.writeData(craftGPT);
                                player.sendMessage(craftGPT.CHAT_PREFIX + "Saved data.json!");
                            }
                        } else if (args[0].equals("wand")) {
                            if (!player.hasPermission("craftgpt.wand")) {
                                sayNoPermission(player);
                            } else {
                                ItemStack wand = new ItemStack(Material.SPYGLASS, 1);
                                ItemMeta wandMeta = wand.getItemMeta();
                                wandMeta.setDisplayName(craftGPT.CHAT_PREFIX + "Magic Wand");
                                wandMeta.getPersistentDataContainer().set(craftGPT.magicWandKey, PersistentDataType.STRING, "shhh!");
                                List<String> wandLore = new ArrayList<>();
                                wandLore.add(ChatColor.GRAY + "Click mobs to toggle selection!");
                                wandMeta.setLore(wandLore);
                                wand.setItemMeta(wandMeta);
                                player.getInventory().addItem(wand);
                            }
                        } else if (args[0].equals("stop")) {
                            if (craftGPT.chattingMobs.containsKey(player.getUniqueId())) {
                                craftGPTListener.exitChat(player);
                            } else {
                                player.sendMessage(craftGPT.CHAT_PREFIX + "Not currently chatting!");
                            }
                        } else if (args[0].equals("create")) {
                            if (!player.hasPermission("craftgpt.create")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if ((craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().isDead())) {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is dead!");
                                        craftGPTListener.toggleSelecting(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                        return true;
                                    } else if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        craftGPTListener.createAIMob(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }
                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("remove")) {
                            if (!player.hasPermission("craftgpt.remove")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                        craftGPTListener.createAIMob(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                    } else {
                                        craftGPTListener.removeAIMob(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                    }
                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("clear")) {
                            if (!player.hasPermission("craftgpt.clear")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    MobBuilder selection = craftGPT.selectingPlayers.get(player.getUniqueId());
                                    selection.setName(null);
                                    selection.setRawPrompt(null);
                                    selection.setBackstory(null);
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "AI mob builder cleared!");
                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("name")) {
                            if (!player.hasPermission("craftgpt.name")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (args.length > 1) {
                                            String name = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                name = name + args[i] + " ";
                                            }
                                            name = name + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setName(name);
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Name set to: " + ChatColor.GOLD + name);
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "No name provided. Try again!");
                                        }
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }

                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("temperature")) {
                            if (!player.hasPermission("craftgpt.temperature")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (args.length > 1) {
                                            try {
                                                Float temp = Float.parseFloat(args[1]);
                                                craftGPT.selectingPlayers.get(player.getUniqueId()).setTemperature(temp);
                                                player.sendMessage(craftGPT.CHAT_PREFIX + "Temperature set to: " + ChatColor.GOLD + temp);
                                            } catch (NumberFormatException e) {
                                                player.sendMessage(craftGPT.CHAT_PREFIX + args[1] + "is not a number!");

                                            }
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "No temperature provided. Try again!");
                                        }
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }

                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("backstory")) {
                            if (!player.hasPermission("craftgpt.backstory")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (craftGPT.selectingPlayers.get(player.getUniqueId()).getRawPrompt() != null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Can't provide a backstory with a raw prompt!");
                                            return true;
                                        }
                                        if (args.length > 1) {
                                            String backstory = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                backstory = backstory + args[i] + " ";
                                            }
                                            backstory = backstory + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setBackstory(backstory);
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Backstory set to: " + ChatColor.GOLD + backstory);
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "No backstory provided. Try again!");
                                        }
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }

                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("rawprompt")) {
                            if (!player.hasPermission("craftgpt.wand")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (craftGPT.selectingPlayers.get(player.getUniqueId()).getBackstory() != null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Can't provide a raw prompt with a backstory!");
                                            return true;
                                        }
                                        if (args.length > 1) {
                                            String rawPrompt = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                rawPrompt = rawPrompt + args[i] + " ";
                                            }
                                            rawPrompt = rawPrompt + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setRawPrompt(rawPrompt);
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Raw prompt set to: " + ChatColor.GOLD + rawPrompt);
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "No prompt provided. Try again!");
                                        }
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }

                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("info")) {
                            if (!player.hasPermission("craftgpt.info")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        AIMob aiMob = craftGPT.craftGPTData.get(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString());
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Name: " + ChatColor.GOLD + aiMob.getName());
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Temperature: " + ChatColor.GOLD + aiMob.getTemperature());
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "System prompt: " + ChatColor.GOLD + aiMob.getMessages().get(0).getContent());
                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is NOT AI-enabled!");
                                    }
                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else if (args[0].equals("debug")) {
                            if (!player.hasPermission("craftgpt.debug")) {
                                sayNoPermission(player);
                            } else {
                                if (!craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                    craftGPT.debuggingPlayers.add(player.getUniqueId());
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "You are now in debug mode!");
                                } else {
                                    craftGPT.debuggingPlayers.remove(player.getUniqueId());
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "You have exited debug mode!");
                                }
                            }
                        } else if (args[0].equals("dryrun")) {
                            if (!player.hasPermission("craftgpt.dryrun")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        MobBuilder selection = craftGPT.selectingPlayers.get(player.getUniqueId());
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "+++++++++++++++++++++++++++++++++++++++++");
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Dry run for selected " + ChatColor.GOLD + craftGPTListener.getMobName(selection.getEntity()));
                                        if (selection.getName() != null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Name: " + ChatColor.GOLD + selection.getName());
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Name: " + ChatColor.GOLD + ChatColor.MAGIC + "ChatGPT");
                                        }
                                        if (selection.getTemperature() != null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Temperature: " + ChatColor.GOLD + selection.getTemperature());
                                        } else {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Temperature: " + ChatColor.GOLD + craftGPT.getConfig().getDouble("default-temperature"));

                                        }
                                        if (selection.getBackstory() == null && selection.getRawPrompt() == null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Backstory: " + ChatColor.GOLD + ChatColor.MAGIC + "ChatGPT");
                                        } else if (selection.getBackstory() != null && selection.getRawPrompt() == null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Backstory: " + ChatColor.GOLD + selection.getBackstory());
                                        } else if (selection.getBackstory() == null && selection.getRawPrompt() != null) {
                                            player.sendMessage(craftGPT.CHAT_PREFIX + "Raw Prompt: " + ChatColor.GOLD + selection.getRawPrompt());
                                        }
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "+++++++++++++++++++++++++++++++++++++++++");

                                    } else {
                                        player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already AI-enabled!");
                                    }

                                } else {
                                    player.sendMessage(craftGPT.CHAT_PREFIX + "No mob selected!");
                                }
                            }
                        } else {
                            player.sendMessage(CraftGPT.CHAT_PREFIX + "Malformed command! Try " + ChatColor.RED + "/cg help");
                        }
                    } else {
                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW + "====| " + ChatColor.GRAY + "ChatGPT v" +
                                craftGPT.getDescription().getVersion() + ChatColor.YELLOW + " |====");
                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW + "====| " + ChatColor.GRAY + "Author: zizmax");
                    }
                } else {
                    sender.sendMessage(craftGPT.CHAT_PREFIX + "Cannot use /craftgpt as console!");
                }
            }
        }

        return true;
    }

    private void sayNoPermission(CommandSender sender){
        sender.sendMessage(CraftGPT.CHAT_PREFIX + "You do not have permission to do that!");
    }
}
