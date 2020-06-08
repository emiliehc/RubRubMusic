# RubRubMusic
RubRubMusic是能被在Discord上使用的多功能Bot。

## 邀请RubRubMusic到您的服务器
如果要邀请Bot到您的服务器，可以使用以下链接：https://discord.com/api/oauth2/authorize?client_id=698581641190178876&permissions=8&scope=bot
如果您认为该链接不再有效，请与开发人员联系。

## 功能列表
1. JShell
2. 音乐播放

## 所有命令列表
RubRubMusic支持的所有命令均以以下符号开头：#
### JShell命令
注意：以下2条命令与Bot的音乐播放功能无关。
#### bind
由于技术原因，RubRubMusic一次只能绑定到一台服务器的一个频道。因此，在使用JShell之前必须先输入#bind。

#### unbind
要从通道解除绑定JShell时使用此命令。

### 音乐播放
#### play
1. Bot唯一接受的参数类型是YouTube视频的URL。
2. 如果没有名为“ Music”的语音通道，RubRubMusic将首先创建此频道。音乐只能在此频道中播放。

### 管理命令
请查看源代码，以了解支持哪些选项。
。
。
。
开玩笑的！这些是支持的选项。
为了了解以下命令的参数的真正含义，您首先需要了解该机器人进入通道后会播放“启动声音效果”。离开频道时也是如此。
#### set [startup / shutdown] {YouTube URL}
此命令设置Bot的启动/关闭声音效果。

#### configure [startup / shutdown] {模式}
此命令确定Bot进入或离开频道时如何从列表中选择其启动/关闭声音效果。

#### add [startup / shutdown] {YouTube URL}
此命令将新项目添加到启动/关闭音效列表中。


## 联系
如果您在使用Bot时遇到任何问题，请在此Repository中创建一个新的Issue。开发人员有时间时将解决该问题。
