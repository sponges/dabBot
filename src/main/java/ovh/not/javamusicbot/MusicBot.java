package ovh.not.javamusicbot;

import com.google.gson.Gson;
import com.moandjiezana.toml.Toml;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

import javax.security.auth.login.LoginException;
import java.io.File;

public final class MusicBot {
    private static final String CONFIG_PATH = "config.toml";
    private static final String CONSTANTS_PATH = "constants.toml";
    public static final String USER_AGENT = "dabBot (https://github.com/sponges/JavaMusicBot)";
    public static final Gson GSON = new Gson();

    public static void main(String[] args) {
        int shardCount = Integer.parseInt(args[0]);
        int minShard = Integer.parseInt(args[1]);
        int maxShard = Integer.parseInt(args[2]);
        Config config = new Toml().read(new File(CONFIG_PATH)).to(Config.class);
        Constants constants = new Toml().read(new File(CONSTANTS_PATH))
                .to(Constants.class);
        for (int shard = minShard; shard < maxShard + 1;) {
            System.out.println("Starting shard " + shard + "...");
            CommandManager commandManager = new CommandManager(config, constants);
            JDA jda;
            try {
                jda = new JDABuilder(AccountType.BOT)
                        .setToken(config.token)
                        .useSharding(shard, shardCount)
                        .addListener(new Listener(config, commandManager))
                        .buildBlocking();
            } catch (LoginException | InterruptedException | RateLimitedException e) {
                e.printStackTrace();
                return;
            }
            jda.getPresence().setGame(Game.of(config.game));
            shard++;
        }
    }
}
