# Ollama Villagers
Ever wanted to get your villagers to talk with you? Here's a mod for that! This is a **SERVER ONLY** mod, it doesn't require clients to have the mod. It has not been tested in single player, but it should work too.
## Usage
When talking in the chat, the closest villager will respond by passing your message through to Ollama. The villager responds with text above its head (in place of its nametag).

The villager is told the username of the user they are talking to.

Villager can have custom personalities.
## Installation Steps
1. Install [Ollama](https://ollama.com/) on the server and download a lightweight model that will run decently fast on your server. Keep the name of the model in mind.
2. Install the mod in the `mods` folder. Currently, only Fabric 1.21 is supported.
3. Make sure you also have the Fabric API installed in the `mods` folder.
4. Run the server. When it is loaded, shut it down.
5. A folder called `ollama-villagers` has appeared in the server's root directory.
6. Edit the `config.json` file inside.
## Config options
| Entry    | Description |
| -------- | ------- |
| host  | The url of the Ollama instance. You must respect the syntax (slash at the end). |
| model | The name of the Ollama model you want the villagers to use. |
| keepAlive | How long the model should be loaded after a message. Set to 0 to unload it immediately, -1 to never unload it. |
| maxTextChars | How long the messages can be, in characters, above a villager's head |
| textHoldTicks | How many ticks the messages remain on top of the head of the villagers, after it has been fully displayed. |
| textCharsPerTick | How fast the characters appear above a villager's head. If Ollama is slow, the text may appear slower than this value. |
| requestTimeoutSeconds | How long before the mods considered the request to be a failure. |
| personalities | List of personalities. See under. |
## Personalities
This mod features "personalities". In the config file, you can add as many personalities as you want. Each personality is described by a prompt, and a weight. A villager of a given personality will be given the prompt before any conversation. The weight indicates how likely (relative to others) villagers are to have that personality.

You can not choose which villager has which personality, but as long as you don't modify the personalities, a given villager will always have the same personality, even after a server restart.
## Known shortcomings
- Conversation histories are lost after a server restart.
- Error handling is very poor (this mod was made in an afternoon) and may lead to server crashes (I have not experienced any yet).
- The text display might behave poorly if the Ollama server is very slow.
- Talking to a villager that is already talking will lead to its conversation history being ordered in the wrong way.
- Villagers are not aware of their environment, unless told by the player (maybe a cool feature to add later).