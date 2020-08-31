package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private volatile MessageChannel jShellChannel = null;
    private volatile Process jShell;
    private volatile Scanner jShellInput;
    private volatile Scanner jShellError;
    private ConfigurationManager configurationManager;
    public final static IAudioPlaybackManager audioPlaybackManager;
    private StringBuilder log = new StringBuilder();

    static {
        audioPlaybackManager = new MultiSourceAudioPlaybackManager();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void backupLog() throws IOException {
        File f = new File(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "log.txt");
        f.createNewFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(log.toString());
        bw.flush();
        bw.close();
        log = new StringBuilder();
        System.gc();
    }

    @SuppressWarnings("BusyWait")
    public Main() throws IOException {
        File f = new File("config");
        // backup

        if (!f.exists()) {
            if (!f.createNewFile()) {
                throw new Error("This should never be thrown");
            }
            configurationManager = new ConfigurationManager();
        } else {
            try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(f))) {
                configurationManager = (ConfigurationManager) oos.readObject();
            } catch (Exception e) { // TODO : narrow this down
                e.printStackTrace();
                configurationManager = new ConfigurationManager();
                configurationManager.modified();
            }
        }

        var processBuilder = new ProcessBuilder("jshell");
        jShell = processBuilder.start();
        jShellError = new Scanner(jShell.getErrorStream());
        jShellInput = new Scanner(jShell.getInputStream());

        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    var msg = jShellInput.nextLine();
                    System.out.println(msg);
                    if (!msg.trim().isEmpty()) {
                        printActiveChannel(msg);
                    }
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            for (; ; ) {
                if (sc.hasNextLine()) {
                    var str = sc.nextLine();
                    try {
                        jShell.getOutputStream().write((str + "\n").getBytes());
                        jShell.getOutputStream().flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();


        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    if (jShellError.hasNextLine()) {
                        var msg = jShellError.nextLine();
                        System.out.println(msg);
                        if (!msg.trim().isEmpty()) {
                            printActiveChannel(msg.concat("\n"));
                        }
                    } else {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }).start();
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        Scanner sysIn = new Scanner(new File("token.txt"));
        var builder = new JDABuilder(AccountType.BOT);
        builder.setToken(sysIn.nextLine());
        sysIn.close();
        builder.addEventListeners(new Main());
        builder.build();
    }

    private <T> void printActiveChannel(T t) {
        if (jShellChannel != null) {
            jShellChannel.sendMessage(t.toString()).queue();
        }
    }

    private void sendPrivate(@NotNull User user, Message msg) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(msg)).queue();
    }

    private void sendPrivate(@NotNull User user, String msg) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(msg)).queue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        try {
            boolean isDm = false;
            try {
                event.getGuild();
            } catch (Exception ex) {
                isDm = true;
            }
            if (isDm) {
                var author = event.getAuthor();
                var msg = event.getMessage().getContentRaw();
                if (msg.startsWith("#")) {
                    // command
                    msg = msg.substring(1);
                    String[] args = msg.split(" ");
                    switch (args[0]) {
                        case "setrecipient" -> configurationManager.setConfiguration(author.getId(), "ActiveRecipient", args[1]);
                        case "pause" -> configurationManager.setConfiguration(author.getId(), "CanReceiveMessages", false);
                        case "resume", "unpause" -> {
                            configurationManager.setConfiguration(author.getId(), "CanReceiveMessages", true);
                            Queue<String> msgQueue = (Queue<String>) configurationManager.getConfiguration(author.getId(), "MessageQueue");
                            if (msgQueue != null) {
                                // deliver all messages
                                while (!msgQueue.isEmpty()) {
                                    String msgToSend = msgQueue.poll();
                                    sendPrivate(author, msgToSend);
                                }
                            }
                            configurationManager.modified();
                        }
                        case "shutdown" -> {
                            if (author.getAsTag().equals("nanjingchj#6822")) {
                                configurationManager.modified();
                                backupLog();
                                System.exit(0);
                            }
                        }
                        default -> sendPrivate(author, "Invalid command!");
                    }
                } else {
                    // dm
                    // get recipient
                    String activeRecipient = configurationManager.getConfiguration(author.getId(), "ActiveRecipient", String.class);
                    if (activeRecipient == null) {
                        sendPrivate(author, "You haven't set a recipient yet");
                        return;
                    }
                    var recipient = author.getJDA().getUserByTag(activeRecipient);
                    if (recipient == null) {
                        sendPrivate(author, "The recipient that you have set is invalid. Please use the following format: #setrecipient username#1234");
                        return;
                    }

                    // log messages
                    log.append("At ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))).append(" from ").append(author.getAsTag()).append(" to ").append(activeRecipient).append(": ").append(msg).append("\n");
                    if (log.length() > Short.MAX_VALUE) {
                        // too long, write to file
                        backupLog();
                    }

                    // determine if the recipient is able to receive messages

                    Boolean canReceiveMessages = configurationManager.getConfiguration(recipient.getId(), "CanReceiveMessages", Boolean.class);
                    if (canReceiveMessages == null) {
                        canReceiveMessages = true;
                        configurationManager.setConfiguration(author.getId(), "CanReceiveMessages", true);
                    }
                    if (canReceiveMessages) {
                        // deliver the msg
                        sendPrivate(recipient, author.getAsTag() + ": " + msg);
                    } else {
                        sendPrivate(author, "The recipient " + recipient.getAsTag() + " has paused all incoming messages. Don't worry, your message will automatically be delivered when they unpause it.");
                        Queue<String> msgQueue = (Queue<String>) configurationManager.getConfiguration(recipient.getId(), "MessageQueue");
                        if (msgQueue == null) {
                            msgQueue = new ArrayDeque<>(Short.MAX_VALUE);
                            configurationManager.setConfiguration(recipient.getId(), "MessageQueue", msgQueue);
                        }
                        msgQueue.add(author.getAsTag() + " at " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) + ": " + msg);
                        configurationManager.modified();
                    }
                }
            } else {
                var msg = event.getMessage().getContentRaw();
                if (msg.startsWith("#")) {
                    msg = msg.substring(1);
                    // preprocess
                    msg = preprocessUserInput(event.getGuild(), msg);
                    String[] args = msg.split(" ");
                    switch (args[0]) {
                        case "unbind" -> sendLeaveChannelMessage(event);
                        case "bind" -> {
                            sendLeaveChannelMessage(event);
                            Thread.sleep(2000);
                            jShellChannel = event.getChannel();
                            sendEnterChannelMessage(event, jShellChannel);
                        }
                        case "restart" -> {
                            jShell.destroy();
                            try {
                                jShell = new ProcessBuilder("jshell").start();
                                jShellInput = new Scanner(jShell.getInputStream());
                                jShellError = new Scanner(jShell.getErrorStream());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        case "run" -> {
                            Function<String, @Nullable Class<?>> getPrimitiveClass = s -> switch (s) {
                                case "byte" -> byte.class;
                                case "short" -> short.class;
                                case "int" -> int.class;
                                case "long" -> long.class;
                                case "float" -> float.class;
                                case "double" -> double.class;
                                case "boolean", "bool" -> boolean.class;
                                case "char" -> char.class;
                                default -> null;
                            };

                            BiFunction<String, Class<?>, @Nullable Object> parseArg = (s, clazz) -> {
                                if (clazz.equals(int.class)) {
                                    return Integer.parseInt(s);
                                } else if (clazz.equals(double.class)) {
                                    return Double.parseDouble(s);
                                } else {
                                    return null;
                                }
                            };

                            Class<?> cls = Main.class.getClassLoader().loadClass(args[1]);
                            int numArgs = Integer.parseInt(args[3]);
                            List<@NotNull Class<?>> params = new ArrayList<>(numArgs);
                            for (int i = 4; i < 4 + numArgs; i++) {
                                Class<?> primitiveClass = getPrimitiveClass.apply(args[i]);
                                if (primitiveClass == null) {
                                    params.add(Main.class.getClassLoader().loadClass(args[i]));
                                } else {
                                    params.add(primitiveClass);
                                }
                            }
                            Class<?>[] arr = new Class<?>[numArgs];
                            for (int i = 0; i < numArgs; i++) {
                                arr[i] = params.get(i);
                            }
                            Method method = cls.getMethod(args[2], arr);
                            List<Object> methodArgs = new ArrayList<>(numArgs);
                            for (int i = 4 + numArgs; i < 4 + 2 * numArgs; i++) {
                                methodArgs.add(parseArg.apply(args[i], arr[i - 4 - numArgs]));
                            }
                            method.invoke(null, methodArgs.toArray());
                        }
                        case "play" -> audioPlaybackManager.playQueued(event.getGuild(), args);
                        case "pause" -> audioPlaybackManager.pause(event.getGuild());
                        case "unpause" -> audioPlaybackManager.unpause(event.getGuild());
                        case "fastForward", "fastforward" -> audioPlaybackManager.fastForward(event.getGuild(), Double.parseDouble(args[1]));
                        case "seek" -> audioPlaybackManager.seek(event.getGuild(), Double.parseDouble(args[1]));
                        case "search" -> {
                            String queryTerms = msg.substring(7);
                            String url = "https://www.youtube.com/watch?v=" + YouTubeSearch.search(queryTerms);
                            event.getChannel().sendMessage("Found: " + url).queue();
                        }
                        case "playSearch", "playsearch" -> {
                            String queryTerms = msg.substring(11);
                            String url = "https://www.youtube.com/watch?v=" + YouTubeSearch.search(queryTerms);
                            event.getChannel().sendMessage("▶ Found: " + url + "\nAdded to queue").queue();
                            audioPlaybackManager.playQueued(event.getGuild(), new String[]{"", url});
                        }
                        case "skip" -> audioPlaybackManager.skip(event.getGuild());
                        case "set" -> {
                            switch (args[1]) {
                                case "startup" -> {
                                    final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
                                    assert startupSounds != null;
                                    startupSounds.clear();
                                    startupSounds.add(args[2].replaceAll("\"", ""));
                                    configurationManager.modified();
                                }
                                case "shutdown" -> {
                                    final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
                                    assert shutdownSounds != null;
                                    shutdownSounds.clear();
                                    shutdownSounds.add(args[2].replaceAll("\"", ""));
                                    configurationManager.modified();
                                }
                                default -> throw new UnsupportedOperationException();
                            }
                        }
                        case "configure" -> {
                            switch (args[1]) {
                                case "startup" -> configurationManager.setConfiguration(event.getGuild().getId(), "startupSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                                case "shutdown" -> configurationManager.setConfiguration(event.getGuild().getId(), "shutdownSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                                case "ext" -> configurationManager.setExtConfiguration(event.getGuild().getId(), args[2], args[3].equals("null") ? null : args[3]);
                                default -> throw new UnsupportedOperationException();
                            }
                        }
                        case "insert" -> {
                            switch (args[1]) {
                                case "startup" -> {
                                    final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
                                    assert startupSounds != null;
                                    startupSounds.add(args[2].replaceAll("\"", ""));
                                    configurationManager.modified();
                                }
                                case "shutdown" -> {
                                    final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
                                    assert shutdownSounds != null;
                                    shutdownSounds.add(args[2].replaceAll("\"", ""));
                                    configurationManager.modified();
                                }
                                default -> throw new UnsupportedOperationException();
                            }
                        }
                        case "readConfig", "readconfig" -> {
                            if ("ext".equals(args[1])) {
                                String value = configurationManager.getExtConfiguration(event.getGuild().getId(), args[2]);
                                event.getChannel().sendMessage(value == null ? "null" : value).queue();
                            } else {
                                throw new UnsupportedOperationException();
                            }
                        }
                        case "playlist" -> {
                            IPlaylistManager playlistManager = configurationManager.getConfiguration(event.getGuild().getId(), "playlistManager", IPlaylistManager.class);
                            if (playlistManager == null) {
                                playlistManager = new PlaylistManager();
                                configurationManager.setConfiguration(event.getGuild().getId(), "playlistManager", playlistManager);
                            }
                            switch (args[1]) {
                                case "load" -> playlistManager.setActivePlaylist(Objects.requireNonNull(playlistManager.getList(args[2])));
                                case "addItem", "additem" -> {
                                    String url = args[2];
                                    AudioTrack track = IAudioPlaybackManager.loadTrack(audioPlaybackManager.getAudioPlayerManger(), url);
                                    Objects.requireNonNull(playlistManager.getActivePlaylist()).add(track);
                                    configurationManager.modified();
                                }
                                case "removeItem", "removeitem" -> {
                                    Objects.requireNonNull(playlistManager.getActivePlaylist()).removeCurrent();
                                    configurationManager.modified();
                                }
                                case "insertItem", "insertitem" -> {
                                    String url = args[2];
                                    AudioTrack track = IAudioPlaybackManager.loadTrack(audioPlaybackManager.getAudioPlayerManger(), url);
                                    Objects.requireNonNull(playlistManager.getActivePlaylist()).insert(track);
                                    configurationManager.modified();
                                }
                                case "create" -> {
                                    String name = args[2];
                                    IPlaylist newPlaylist = new DefaultPlaylist(name);
                                    playlistManager.addList(newPlaylist);
                                    configurationManager.modified();
                                }
                                case "remove" -> {
                                    String name = args[2];
                                    playlistManager.removeList(name);
                                    configurationManager.modified();
                                }
                                case "next" -> {
                                    AudioTrack track = Objects.requireNonNull(playlistManager.getActivePlaylist()).next();
                                    audioPlaybackManager.playQueued(event.getGuild(), track);
                                    audioPlaybackManager.skip(event.getGuild());
                                }
                                case "previous" -> {
                                    AudioTrack track = Objects.requireNonNull(playlistManager.getActivePlaylist()).previous();
                                    audioPlaybackManager.playQueued(event.getGuild(), track);
                                    configurationManager.modified();
                                    audioPlaybackManager.skip(event.getGuild());
                                }
                                case "clear" -> {
                                    Objects.requireNonNull(playlistManager.getActivePlaylist()).clear();
                                    configurationManager.modified();
                                }
                                case "resetPos", "resetpos" -> Objects.requireNonNull(playlistManager.getActivePlaylist()).resetPosition();
                                case "addAll", "addall" -> Objects.requireNonNull(playlistManager.getActivePlaylist()).playAll(audioPlaybackManager, event.getGuild());
                                default -> throw new UnsupportedOperationException("Unknown command");
                            }
                        }
                        default -> throw new UnsupportedOperationException("Unknown command");
                    }

                } else {
                    try {
                        if (jShellChannel != null) {
                            if (!event.getMessage().getAuthor().isBot() && jShellChannel.equals(event.getChannel())) {
                                jShell.getOutputStream().write((msg + "\n").getBytes());
                                jShell.getOutputStream().flush();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            t.printStackTrace();
            for (String line : t.toString().split("\n")) {
                event.getChannel().sendMessage(line).queue();
            }
        }
    }

    private String preprocessUserInput(Guild guild, String input) {
        Pattern inputPattern = Pattern.compile("\\{(.*?)}");
        Matcher matchPattern = inputPattern.matcher(input);
        while (matchPattern.find()) {
            String original = matchPattern.group(1);
            String result = configurationManager.getExtConfiguration(guild.getId(), original);
            if (result != null) {
                input = input.replaceAll("\\{" + original + "}", result);
            }
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private void sendEnterChannelMessage(MessageReceivedEvent event, MessageChannel channel) {
        final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
        assert startupSounds != null;
        String sound = getFromListWithConfig(configurationManager.getConfiguration(event.getGuild().getId(), "startupSoundConfig", SelectionFromListConfig.class), startupSounds);

        audioPlaybackManager.playAudio(event, new String[]{"", sound});
        channel.sendMessage("Entering channel").queue();
    }

    @SuppressWarnings("unchecked")
    private void sendLeaveChannelMessage(MessageReceivedEvent event) {
        if (jShellChannel != null) {
            final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
            assert shutdownSounds != null;
            String sound = getFromListWithConfig(configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSoundConfig", SelectionFromListConfig.class), shutdownSounds);
            audioPlaybackManager.playAudio(event, new String[]{"", sound});
            jShellChannel.sendMessage("Leaving channel").queue();
            jShellChannel = null;
        }
    }

    @NotNull
    private <T> T getFromListWithConfig(SelectionFromListConfig selectionConfig, List<T> list) {
        return switch (selectionConfig) {
            case Last -> list.get(list.size() - 1);
            case First -> list.get(0);
            case Random -> list.get((int) (new Random().nextFloat() * list.size()));
        };
    }
}
