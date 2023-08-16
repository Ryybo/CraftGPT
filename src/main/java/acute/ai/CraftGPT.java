package acute.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.*;
import okhttp3.Authenticator;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;
import retrofit2.Retrofit;

import javax.net.SocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.theokanning.openai.service.OpenAiService.*;

public final class CraftGPT extends JavaPlugin {

    public NamespacedKey magicWandKey = new NamespacedKey(this, "secret");

    public NamespacedKey autoSpawnChunkFlagKey = new NamespacedKey(this, "chunk-flag");

    public boolean debug = false;
    public boolean apiKeySet = false;
    public boolean apiConnected = false;
    public OpenAiService openAIService;

    public static final Random random = new Random();

    public List<String> waitingOnAPIList = new ArrayList<>();

    ConcurrentHashMap<UUID, Entity> chattingPlayers = new ConcurrentHashMap<>();

    ArrayList<UUID> debuggingPlayers = new ArrayList<>();

    ConcurrentHashMap<UUID, AIMob> selectingPlayers = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new ClassTypeAdapter()).setPrettyPrinting().create();

    ConcurrentHashMap<String, AIMob> craftGPTData = new ConcurrentHashMap<>();

    private File usageFile;
    private FileConfiguration usageFileConfig;

    public static final String CHAT_PREFIX = ChatColor.GOLD + "[" + ChatColor.GRAY + "Craft" + ChatColor.GREEN + "GPT" + ChatColor.GOLD + "] " + ChatColor.GRAY;
    public static final String DISCORD_URL = "https://discord.gg/BXhUUQEymg";
    public static final String SPIGOT_URL = "https://www.spigotmc.org/resources/craftgpt.110635/";
    public static final String UPDATE_AVAILABLE = "Update available! Download v%s ";
    public static final String UP_TO_DATE = "CraftGPT is up to date: (%s)";
    public static final String UNRELEASED_VERSION = "Version (%s) is more recent than the one publicly available. Dev build?";
    public static final String UPDATE_CHECK_FAILED = "Could not check for updates. Reason: ";
    public static final int spigotID = 110635;


    @Override
    public void onEnable() {
        getLogger().info("+----------------------------------------------------------------+");
        getLogger().info("|                      CraftGPT Community                        |");
        getLogger().info("+================================================================+");
        //getLogger().info("| * Please report bugs at: https://git.io/JkJLD                  |");
        getLogger().info("| * Join the Discord at: https://discord.gg/BXhUUQEymg           |");
        getLogger().info("| * Enjoying the plugin? Leave a review and share with a friend! |");
        getLogger().info("+----------------------------------------------------------------+");

        // Register events
        getServer().getPluginManager().registerEvents(new CraftGPTListener(this), this);

        // Register commands
        getCommand("craftgpt").setExecutor(new Commands(this));
        getCommand("craftgpt").setTabCompleter(new Commands(this));


        // Save/read config.yml
        Path path = Paths.get("plugins/CraftGPT/config.yml");
        if (Files.exists(path)) { // Only save a backup if one already exists to prevent overwriting backup with defaults
            try {
                Files.copy(path,
                        Paths.get("plugins/CraftGPT/config.bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().warning("Failed to create backup config!");
            }
        }
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        debug = getConfig().getBoolean("debug");
        if (debug) {
            getLogger().info(CHAT_PREFIX + "Debug mode enabled!");
        }


        // Save/read usage.yml
        createUsageFile(false);

        // Load data.json
        craftGPTData = readData(this);
        if (craftGPTData != null) getLogger().info(String.format("Loaded %s AI-enabled mobs.", craftGPTData.size()));

        getLogger().info(String.format("Loaded %s events.", getConfig().getConfigurationSection("events").getKeys(false).size()));

        // Connect to bStats
        int bStatsId = 18710;
        Metrics metrics = new Metrics(this, bStatsId);

        enableOpenAI();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("Registered with PlaceholderAPI");
        }

        // Check for updates
        UpdateChecker.init(this, spigotID).requestUpdateCheck().whenComplete((result, exception) -> {
            if (result.requiresUpdate()) {
                this.getLogger().warning((String.format(
                        UPDATE_AVAILABLE, result.getNewestVersion()) + "at " + SPIGOT_URL));
                return;
            }

            UpdateChecker.UpdateReason reason = result.getReason();
            if (reason == UpdateChecker.UpdateReason.UP_TO_DATE) {
                this.getLogger().info(String.format(UP_TO_DATE, result.getNewestVersion()));
            } else if (reason == UpdateChecker.UpdateReason.UNRELEASED_VERSION) {
                this.getLogger().info(String.format(UNRELEASED_VERSION, result.getNewestVersion()));
            } else {
                this.getLogger().warning(UPDATE_CHECK_FAILED + reason);
            }
        });


        getLogger().info("Enabled");

    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling...");
        if (!chattingPlayers.isEmpty()) {
            getLogger().info("Ending chats...");
            Set<UUID> chattingUUIDs = new HashSet<>(chattingPlayers.keySet());
            for (UUID uuid: chattingUUIDs) {
                CraftGPTListener craftGPTListener = new CraftGPTListener(this);
                craftGPTListener.exitChat(getServer().getPlayer(uuid));
            }
        }
        getLogger().info("Writing save data...");
        writeData(this);
        saveUsageFile();
        getLogger().warning("Disabled");
    }

    public FileConfiguration getUsageFile() {
        return this.usageFileConfig;
    }

    public void saveUsageFile() {
        try {
            getUsageFile().save(usageFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveUsageFileAsync() {

        // Can't run/schedule async tasks when disabled!
        if (!this.isEnabled()) {
            saveUsageFile();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    getUsageFile().save(usageFile);
                    getUsageFile().load(usageFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InvalidConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    public void createUsageFile(boolean overwrite) {
        usageFile = new File(getDataFolder(), "usage.yml");
        if (!usageFile.exists() || overwrite || usageFile.length() == 0L) {
            usageFile.getParentFile().mkdirs();
            saveResource("usage.yml", true);
        }
        this.usageFileConfig = YamlConfiguration.loadConfiguration(usageFile);
    }

    public void enableOpenAI() {
        String key = getConfig().getString("api_key");
        if (key == null || key.length() < 15) {
            getLogger().severe("No API key specified in config! Must set an API key for CraftGPT to work!");
            return;
        }
        else {
            apiKeySet = true;
        }

        // Create HTTP client and OpenAI connection with configurable proxy and timeout
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client;
        Duration timeout = Duration.ofSeconds(getConfig().getInt("timeout"));
        if (getConfig().getBoolean("proxy.enabled")) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(getConfig().getString("proxy.host"), getConfig().getInt("proxy.port")));
            if (getConfig().getBoolean("proxy.authentication.enabled")) {

                getLogger().info("Authenticating to HTTP proxy...");
                Authenticator proxyAuthenticator = new Authenticator() {

                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(getConfig().getString("proxy.authentication.username"), getConfig().getString("proxy.authentication.password"));
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    }
                };

                client = defaultClient(key, timeout)
                        .newBuilder()
                        .proxyAuthenticator(proxyAuthenticator)
                        .proxy(proxy)
                        .build();

            } else {
                client = defaultClient(key, timeout)
                        .newBuilder()
                        .proxy(proxy)
                        .build();
            }

            getLogger().info("Connecting to OpenAI via proxy (" + getConfig().getString("proxy.host") + ":" + getConfig().getInt("proxy.port") + ")...");

        } else {
            client = defaultClient(key, timeout)
                    .newBuilder()
                    .build();
        }
        Retrofit retrofit = defaultRetrofit(client, mapper);
        OpenAiApi api = retrofit.create(OpenAiApi.class);
        openAIService = new OpenAiService(api);


        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                getLogger().info("Connecting to OpenAI...");
                String response = CraftGPTListener.tryNonChatRequest("Say hi", "Hi!", .1f, 2);
                if (response == null) {
                    getLogger().severe("Tried 3 times and couldn't connect to OpenAI for the error(s) printed above!");
                    getLogger().severe("Read the error message carefully before asking for help in the Discord. Almost all errors are resolved by ensuring you have a valid and billable API key.");
                } else {
                    long end = System.currentTimeMillis();
                    getLogger().info("Connected to OpenAI!" + " (" +  ((end-start) / 1000f) + "s)");
                    apiConnected = true;
                }
            }
        }.runTaskAsynchronously(this);
    }

    public void writeData(CraftGPT craftGPT) {
        long start = System.currentTimeMillis();

        Path path = Paths.get(craftGPT.getDataFolder() + "/data.json");
        try {
            if(!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                getLogger().severe("No data.json exists! Creating empty one.");
                // Initialize with empty JSON
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(craftGPTData, bufferedWriter);
            long end = System.currentTimeMillis();
            getLogger().info("Wrote data.json! (" + (end-start) + "ms)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //fixme Probably much better way of handling this. The ChatMessage type couldn't be automatically parsed by gson
    public class ClassTypeAdapter implements JsonSerializer<Class<ChatMessage>>, JsonDeserializer<Class<?>> {
        @Override
        public JsonElement serialize(Class<ChatMessage> src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getName());
        }

        @Override
        public Class<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Class.forName(json.getAsString());
            } catch (ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
        }
    }

    public ConcurrentHashMap<String, AIMob> readData(CraftGPT craftGPT) {
        Path path = Paths.get(craftGPT.getDataFolder() + "/data.json");
        try {
            if (!Files.exists(path)) {
                getLogger().info("Creating data.json");
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                // Initialize with empty JSON
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            JsonReader jsonReader = new JsonReader(bufferedReader);


            ConcurrentHashMap<String, AIMob> map = gson.fromJson(jsonReader, new TypeToken<ConcurrentHashMap<String, AIMob>>() {}.getType());
            getLogger().info("Read data.json!");

            return map;
        } catch (IOException e) {
            return null;
        }
    }

    public String rawProgressBar(int current, int max, int totalBars, char symbol, ChatColor completedColor,
                                 ChatColor notCompletedColor) {
        if (current > max) current = max;
        float percent = (float) current / max;
        int progressBars = (int) (totalBars * percent);

        return Strings.repeat("" + completedColor + symbol, progressBars)
                + Strings.repeat("" + notCompletedColor + symbol, totalBars - progressBars);
    }

    public String colorProgressBar(int current, int max, int totalBars) {
        ChatColor completedColor = ChatColor.GREEN;
        double percentage = (double) current / max;
        if (percentage > .5) {
            completedColor = ChatColor.YELLOW;
        }
        if (percentage > .75) {
            completedColor = ChatColor.RED;
        }

        return rawProgressBar(current, max, totalBars, '|', completedColor, ChatColor.GRAY);
    }

    public String getPlayerUsageProgressBar(Player player) {
        return colorProgressBar((int) getPlayerUsagePercentage(player), 100, 40);
    }

    public double getPlayerUsagePercentage(Player player) {
        long limit = CraftGPTListener.getTokenLimit(player);
        long usage = getUsageFile().getLong("players." + player.getUniqueId() + ".total-usage");
        DecimalFormat dfZero = new DecimalFormat("0.00");
        return Double.valueOf(dfZero.format(100.0 * usage / limit));
    }

    public double getGlobalUsagePercentage() {
        long limit = getConfig().getLong("global-usage-limit");
        long usage = getUsageFile().getLong("global-total-usage");
        DecimalFormat dfZero = new DecimalFormat("0.00");
        return Double.valueOf(dfZero.format(100.0 * usage / limit));
    }

    public String getGlobalUsageProgressBar() {
        return colorProgressBar((int) getGlobalUsagePercentage(), 100, 40);
    }


    public boolean isAIMob(Entity entity) {
        if (craftGPTData.containsKey(entity.getUniqueId().toString())) return true;
        else return false;
    }

    public AIMob getAIMob(Entity entity) {
        if (isAIMob(entity)) {
            return craftGPTData.get(entity.getUniqueId().toString());
        } else {
            return null;
        }
    }

    public boolean isChatting(Player player) {
        if (chattingPlayers.containsKey(player.getUniqueId())) return true;
        else return false;
    }



}
