# RubRubMusic
RubRubMusic is a music bot for Discord.

## Inviting the bot to your server
If you want to invite the bot to your server, you can use this link: https://discord.com/api/oauth2/authorize?client_id=698581641190178876&permissions=8&scope=bot
Please contact the developer if you believe that the link is no longer valid.

## List of functions
1. JShell
2. Music playback

## List of all commands
All commands supported by RubRubMusic begin with this symbol: #

### Music playback
#### play
1. The only argument type that the bot accepts is the URL of a YouTube video.
2. If there is no voice channel called "Music", the bot will first create one. The music can only be played in this channel.

#### skip
skips the current audio track

#### playsearch {search terms}
1. searches videos on youtube and plays the most relevant one
2. spaces are allowed in the search term

#### search {search terms}
1. searches videos on youtube and returns the most relevant search result as a URL
2. spaces are allowed in the search term

#### pause
#### unpause
#### resume
#### seek {time}
#### fastforward {time}

#### playlist {option} {argument}
Options:
##### create {PlaylistName}
creates a new playlist with the name specified in the argument
##### load {PlaylistName}
loads the playlist with the name specified in the argument
##### remove {PlaylistName}
removes the playlist with the name specified in the argument
##### insertItem {URL}
inserts a new item in the at the current position
##### addItem {URL}
adds a new item to the end of the playlist
##### removeItem
removes the current item
##### addAll
adds all songs in the active playlist into the queue
##### next
plays the next item
##### previous
plays the previous item

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

## Contact
If you encounter any problems while using the bot, please create a new issue in the repository. The developer will address the problem when he has time.
