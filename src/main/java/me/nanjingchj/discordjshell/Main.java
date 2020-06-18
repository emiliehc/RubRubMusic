package me.nanjingchj.discordjshell;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private volatile MessageChannel jShellChannel = null;
    private volatile Process jShell;
    private volatile Scanner jShellInput;
    private volatile Scanner jShellError;
    private ConfigurationManager configurationManager;
    private final AudioPlaybackManager audioPlaybackManager;

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
                }
            }
        }).start();

        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            for (; ; ) {
                var str = sc.nextLine();
                try {
                    jShell.getOutputStream().write((str + "\n").getBytes());
                    jShell.getOutputStream().flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();


        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    var msg = jShellError.nextLine();
                    System.out.println(msg);
                    if (!msg.trim().isEmpty()) {
                        printActiveChannel(msg.concat("\n"));
                    }

                }
            }

        }).start();

        audioPlaybackManager = new AudioPlaybackManager();
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

    @SuppressWarnings("unchecked")
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
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
                    case "play" -> audioPlaybackManager.play(event.getGuild(), event, args);
                    case "skip" -> audioPlaybackManager.playAudio(event, new String[]{"", "https://www.youtube.com/watch?v=Wch3gJG2GJ4"});
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
                    case "add" -> {
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
                    case "readConfig" -> {
                        if ("ext".equals(args[1])) {
                            String value = configurationManager.getExtConfiguration(event.getGuild().getId(), args[2]);
                            event.getChannel().sendMessage(value == null ? "null" : value).queue();
                        } else {
                            throw new UnsupportedOperationException();
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
