# RubRubMusic
RubRubMusic is a multi-purpose bot for Discord.

## Invitation to the server
If you want to invite the bot to your server, you can use this link: https://discord.com/api/oauth2/authorize?client_id=698581641190178876&permissions=8&scope=bot

## List of functions
1. JShell
2. Music playback

## List of all commands
All commands supported by RubRubMusic begin with this symbol: #
### JShell commands
CAUTION: The following 2 commands have nothing to do with the bot's music playback function.
#### bind
For technical reasons, the bot can only be bound to one channel from one server at a time. Therefore, you must first enter `` #bind '' before using JShell.

#### unbind
This command is used when you want to unbind JShell from the channel.

### Music playback
#### play
1. The only argument type that the bot accepts is the URL of a YouTube video.
2. If there is no voice channel called "Music", the bot will first create one. The music can only be played in this channel.

### administrative commands
Take a look at the source code to see which options are supported.
.
.
.
Just kidding! Here are the options.
In order to know what the arguments of the following commands really mean, you first need to understand that the bot plays a "startup sound effect" when it enters a channel. The same applies when it leaves a channel.
#### set [startup / shutdown] {YouTube URL}
This command sets the bot's startup / shutdown sound effect.

#### configure [startup / shutdown] {mode}
This command determines how the bot chooses its startup / shutdown sound effect from a list when it enters or leaves a channel.

#### add [startup / shutdown] {YouTube URL}
This command adds a new item to the list of startup / shutdown sound effects.

##Read this document in another language
[Deutsch][0], [中文][1]

##Contact
If you encounter any problems while using the bot, please create a new issue in the repository. The developer will address the problem when he has time.

[0]: Readme.de.md
[1]: Readme.zh-cn.md
