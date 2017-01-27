package ovh.not.javamusicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.util.HashMap;
import java.util.Map;

public class GuildMusicManager {
    static final Map<Guild, GuildMusicManager> GUILDS = new HashMap<>();
    private final Guild guild;
    public final AudioPlayer player;
    public final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;
    public boolean open = false;

    private GuildMusicManager(Guild guild, TextChannel textChannel, AudioPlayerManager playerManager) {
        this.guild = guild;
        this.player = playerManager.createPlayer();
        this.scheduler = new TrackScheduler(this, player, textChannel);
        this.player.addListener(scheduler);
        this.sendHandler = new AudioPlayerSendHandler(player);
        this.guild.getAudioManager().setSendingHandler(sendHandler);
    }

    public void open(VoiceChannel channel, User user) {
        try {
            guild.getAudioManager().openAudioConnection(channel);
            guild.getAudioManager().setSelfDeafened(true);
            open = true;
        } catch (PermissionException e) {
            if (user != null && !user.isBot()) {
                user.getPrivateChannel().sendMessage("**dabBot does not have permission to connect to the "
                        + channel.getName() + " voice channel.**\nTo fix this, allow dabBot to `Connect` " +
                        "and `Speak` in that voice channel.\nIf you are not the guild owner, please send " +
                        "this to them.").complete();
            } else {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        guild.getAudioManager().closeAudioConnection();
        open = false;
    }

    public static GuildMusicManager getOrCreate(Guild guild, TextChannel textChannel, AudioPlayerManager playerManager) {
        if (GUILDS.containsKey(guild)) {
            GuildMusicManager manager = GUILDS.get(guild);
            if (manager.scheduler.textChannel != textChannel) {
                manager.scheduler.textChannel = textChannel;
            }
            return manager;
        }
        GuildMusicManager musicManager = new GuildMusicManager(guild, textChannel, playerManager);
        GUILDS.put(guild, musicManager);
        return musicManager;
    }

    public static GuildMusicManager get(Guild guild) {
        return GUILDS.get(guild);
    }
}
