package ovh.not.javamusicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import static ovh.not.javamusicbot.CommandUtils.formatDuration;

public class LoadResultHandler implements AudioLoadResultHandler {
    private final CommandManager commandManager;
    private final GuildMusicManager musicManager;
    private final Command.Context context;
    public boolean verbose = true;

    public LoadResultHandler(CommandManager commandManager, GuildMusicManager musicManager, Command.Context context) {
        this.commandManager = commandManager;
        this.musicManager = musicManager;
        this.context = context;
    }

    @Override
    public void trackLoaded(AudioTrack audioTrack) {
        boolean playing = musicManager.player.getPlayingTrack() != null;
        musicManager.scheduler.queue(audioTrack);
        if (playing && verbose) {
            context.reply(String.format("Queued **%s** `[%s]`", audioTrack.getInfo().title,
                    formatDuration(audioTrack.getDuration())));
        }
    }

    @Override
    public void playlistLoaded(AudioPlaylist audioPlaylist) {
        if (audioPlaylist.getSelectedTrack() != null) {
            trackLoaded(audioPlaylist.getSelectedTrack());
        } else if (audioPlaylist.isSearchResult()) {
            int playlistSize = audioPlaylist.getTracks().size();
            int size = playlistSize > 5 ? 5 : playlistSize;
            AudioTrack[] audioTracks = new AudioTrack[size];
            for (int i = 0; i < audioTracks.length; i++) {
                audioTracks[i] = audioPlaylist.getTracks().get(i);
            }
            Selection.Formatter<AudioTrack, String> formatter = track -> String.format("%s by %s `[%s]`",
                    track.getInfo().title, track.getInfo().author, formatDuration(track.getDuration()));
            Selection<AudioTrack, String> selection = new Selection<>(audioTracks, formatter, (found, track) -> {
                if (!found) {
                    context.reply("Selection cancelled!");
                    return;
                }
                trackLoaded(track);
            });
            commandManager.selectors.put(context.event.getMember(), selection);
            context.reply(selection.createMessage());
        } else {
            audioPlaylist.getTracks().forEach(musicManager.scheduler::queue);
            context.reply(String.format("Added **%d songs** to the queue!", audioPlaylist.getTracks().size()));
        }
    }

    @Override
    public void noMatches() {
        if (verbose) context.reply("No song matches found! Usage: `!!!p <link>`\n" +
                "To play music from youtube, use `!!!p ytsearch: <your search term>`");
    }

    @Override
    public void loadFailed(FriendlyException e) {
        e.printStackTrace();
        if (verbose) context.reply("An error occurred!");
    }
}