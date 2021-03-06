package ovh.not.javamusicbot.impl;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.managers.AudioManager;
import org.json.JSONObject;
import ovh.not.javamusicbot.Database;
import ovh.not.javamusicbot.Statement;
import ovh.not.javamusicbot.UserManager;
import ovh.not.javamusicbot.lib.AlreadyConnectedException;
import ovh.not.javamusicbot.lib.PermissionException;
import ovh.not.javamusicbot.lib.server.Server;
import ovh.not.javamusicbot.lib.server.ServerProperty;
import ovh.not.javamusicbot.lib.song.QueueSong;
import ovh.not.javamusicbot.lib.song.SongQueue;
import ovh.not.javamusicbot.lib.user.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.function.Consumer;

public class DiscordServer extends AudioEventAdapter implements Server {
    private final Collection<ServerProperty> serverProperties = new ArrayList<>();
    private final Database database;
    private final SongQueue songQueue;
    private final UserManager userManager;
    private final Guild guild;
    private final AudioPlayerManager audioPlayerManager;
    private final AudioPlayer audioPlayer;
    private final User owner;
    private boolean playing = false;
    private boolean paused = false;
    public VoiceChannel voiceChannel = null;

    public DiscordServer(Database database, UserManager userManager, Guild guild, AudioPlayerManager audioPlayerManager) throws SQLException {
        this.database = database;
        this.userManager = userManager;
        this.guild = guild;
        this.audioPlayerManager = audioPlayerManager;
        audioPlayer = audioPlayerManager.createPlayer();
        audioPlayer.addListener(this);
        AudioSendHandler audioSendHandler = new AudioPlayerSendHandler(audioPlayer);
        guild.getAudioManager().setSendingHandler(audioSendHandler);
        owner = userManager.get(guild.getOwner().getUser().getId());
        init();
        initProperties();
        songQueue = new DiscordSongQueue(database, this, userManager);
    }

    private void init() throws SQLException {
        try (Connection connection = database.dataSource.getConnection()) {
            PreparedStatement statement = database.prepare(connection, Statement.SERVER_SELECT);
            statement.setString(1, getId());
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.isBeforeFirst()) {
                statement = database.prepare(connection, Statement.SERVER_INSERT);
                statement.setString(1, getId());
                statement.setString(2, owner.getId());
                statement.execute();
            } else {
                resultSet.next();
                try {
                    connect(guild.getVoiceChannelById(resultSet.getString(1)));
                } catch (AlreadyConnectedException | PermissionException e) {
                    e.printStackTrace();
                }
            }
            resultSet.close();
        }
    }

    public void delete() throws SQLException {
        try (Connection connection = database.dataSource.getConnection()) {
            PreparedStatement statement = database.prepare(connection, Statement.SERVER_DELETE);
            statement.setString(1, getId());
            statement.execute();
        }
    }

    private void initProperties() throws SQLException {
        try (Connection connection = database.dataSource.getConnection()) {
            PreparedStatement statement = database.prepare(connection, Statement.SERVER_PROPERTIES_SELECT_ALL);
            statement.setString(1, getId());
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.isBeforeFirst()) {
                return;
            }
            while (resultSet.next()) {
                String property = resultSet.getString(1);
                String value = resultSet.getString(2);
                serverProperties.add(new ServerProperty(property, new JSONObject(value)));
            }
            resultSet.close();
        }
    }

    @Override
    public void play(QueueSong song) {
        if (audioPlayer.startTrack(((DiscordQueueSong) song).audioTrack, true)) {
            ((DiscordSongQueue) songQueue).setCurrent(song);
        } else {
            songQueue.add(song);
        }
    }

    @Override
    public void load(String song, User addedBy, Date dateAdded) {
        DiscordResultHandler resultHandler = new DiscordResultHandler(database, this, addedBy, dateAdded);
        audioPlayerManager.loadItem(song, resultHandler);
    }

    @Override
    public void load(String song, Consumer<AudioTrack> callback) {
        DiscordResultHandler resultHandler = new DiscordResultHandler(database, this, callback);
        audioPlayerManager.loadItem(song, resultHandler);
    }

    @Override
    public void stop() {
        audioPlayer.stopTrack();
        audioPlayer.destroy();
        ((DiscordSongQueue) songQueue).setCurrent(null);
        playing = false;
        paused = false;
    }

    @Override
    public void pause() {
        audioPlayer.setPaused(true);
    }

    @Override
    public void resume() {
        audioPlayer.setPaused(false);
    }

    @Override
    public void next() {
        DiscordSong song = (DiscordSong) songQueue.next();
        if (song == null) {
            stop();
            disconnect();
        } else {
            AudioTrack track = song.audioTrack;
            audioPlayer.startTrack(track, false);
        }
    }

    @Override
    public boolean isPlaying() {
        return isConnected() && playing;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public QueueSong getCurrentSong() {
        return songQueue.getCurrentSong();
    }

    private void updateVoiceChannel() throws SQLException {
        try (Connection connection = database.dataSource.getConnection()) {
            PreparedStatement statement = database.prepare(connection, Statement.SERVER_UPDATE_VOICE_CHANNEL);
            if (voiceChannel == null) {
                statement.setString(1, null);
            } else {
                statement.setString(1, voiceChannel.getId());
            }
            statement.setString(2, getId());
            statement.execute();
        }
    }

    @Override
    public void connect(VoiceChannel voiceChannel) throws AlreadyConnectedException, PermissionException {
        if (guild.getAudioManager().isConnected()) {
            throw new AlreadyConnectedException();
        }
        if (!guild.getSelfMember().hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
            throw new PermissionException();
        }
        try {
            AudioManager audioManager = guild.getAudioManager();
            audioManager.openAudioConnection(voiceChannel);
            audioManager.setSelfDeafened(true);
            this.voiceChannel = voiceChannel;
            try {
                updateVoiceChannel();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (net.dv8tion.jda.core.exceptions.PermissionException e) {
            throw new PermissionException();
        }
    }

    @Override
    public void disconnect() {
        guild.getAudioManager().closeAudioConnection();
        this.voiceChannel = null;
        try {
            updateVoiceChannel();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isConnected() {
        return guild.getAudioManager().isConnected();
    }

    @Override
    public Collection<ServerProperty> getProperties() {
        return serverProperties;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        playing = true;
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            next();
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        super.onTrackException(player, track, exception);
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        super.onTrackStuck(player, track, thresholdMs);
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        paused = true;
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        paused = false;
    }

    @Override
    public SongQueue getSongQueue() {
        return songQueue;
    }

    @Override
    public String getId() {
        return guild.getId();
    }

    @Override
    public User getOwner() {
        return owner;
    }
}
