# BuddyRobot Speech System Integration

Buddy robot "frontend" implementation with our chat app.

* Most of the relevant code is inside `app/src/main/java/com.example.buddychat/`
* Full app runs from `MainActivity.java`
* UI for buttons and text is loaded in from `app/src/main/res/layout/activity_main.xml`
  * Then inside of `MainActivity.java` you can attach listeners to the buttons and programmatically modify each element as needed.

```
======================================================================================
                                  SYSTEM ARCHITECTURE
======================================================================================

                                +---------------------------+
                                |      MainActivity         |
          +-------------------- |  (UI Shell & Lifecycle)   |
          |                     | - Toggle Button           |
          |                     | - Shows Connection State  | 
          |                     +-------------+-------------+
          |                                   |
          |                                   v
          |                   +-------------------------------+
          |                   |       StatusController        |
          |                   |         (The Brain)           | <--------------------+
          |                   |  - Manages Chat State         |                      |
          |                   |  - Coordinates Wake/Sleep     |                      |
          |                   +---------------+---------------+                      |
          |                                   |                                      |
          |             +---------------------+-----------------------+              |
          v             |                     |                       |              |
+---------+-----------+ |           +---------+---------+   +---------+----------+   |
|     TokenManager    | |           | ChatSocketManager |   |   BehaviorTasks    |   |
|   (Auth Service)    | |           |     (Network)     |   |  (Animation/Body)  |   |
| - Login on startup  | |           +---------+---------+   | - Sleep Loop       |   |
| - 14m Auto-Refresh  | |                     ^             | - Wake Up (Once)   |   |
+---------------------+ |                     |             +--------------------+   |
                        |                     v                                      |
                        |            [ Cloud / AI Backend ]                          |
                        |                                                            |
              +---------+----------+                      +----------------------+   |
              |      BuddySTT      |                      |       BuddyTTS       |   |
              |       (Ears)       | <------------------- |       (Voice)        | --+
              | - Mic Handling     |    (Direct Echo      | - Speaking Logic     |
              | - Stop Word Check  |     Prevention)      | - Pauses STT while   |
              +---------+----------+                      |   talking            |
                        |                                 +----------------------+
                        | (Sends text directly)
                        v
              +---------+---------+
              | ChatSocketManager |
              +-------------------+
```


<hr>

# ToDo
* It could be interesting to resume STT after buddy starts talking, and make it so he will stop if he hears you
  * e.g. if you talk over him and he hears it, he will stop

* Add the no instances thing to more classes...
* Maybe move audio_triangulation into sensors ?

* There is some more about this in `ProfileManager` but I don't have a way to update the text on screen anymore...
  * There was an idea to do it in a way kind of similar to the UIUtils class, but I don't remember how...
  * Also more in `UiUtils`

### If i am able to access the logs during a live chat, there are a bunch of things I need to do
  * Figure out if the emotion should be set with the behaviors (also should it be before or after?)
  * Figure out why sometimes when it wakes up it also does a little movement with the wheels

### Things to watch for when testing
* Should we use speak happy ever (especially on wakeup)?
* On wakeup does it do the wheels spinning back and forth thing?
* IDK


### New stuff from testing 01/21:
* Need to cancel the login when stuff happens










<hr>

## 🏗 Architecture

Not exact -- some systems offload functionality to other helpers.

### 🧠 Core Logic
* **`StatusController`**: The central "Brain." Manages the state (Starting, Active, Stopping) and coordinates websocket & SDK services.
* **`MainActivity`**: The "Remote Control." Handles UI and initializes the Robot SDK, but delegates all logic to the Controller.

### 🌐 Network Layer
* **`TokenManager`**: Background class that handles authentication and automatically refreshes API tokens every 14 minutes.
* **`ChatSocketManager`**: Manages the WebSocket connection to the backend AI, including connection retries and JSON formatting.
* **`MessageHandler`**: Handles the reception of messages from the backend (e.g. chat responses, emotions to display, etc.).

### 🤖 Hardware Wrappers
* **`BuddySTT`**: Handles Speech-to-Text. Pauses on message heard until robot finishes speaking response.
* **`BuddyTTS`**: Handles Text-to-Speech. Automatically pauses the microphone while speaking to prevent echo.
* **`BehaviorTasks`**: Manages physical animations (like "Wake Up" and "Sleep").

<hr>


```
======================================================================================
                             SYSTEM LIFECYCLE & DATA FLOW
======================================================================================

1. STARTUP SEQUENCE
   [User Click] -> StatusController.start()
        |
        +-- (Async) --> ChatSocketManager.connect()
                           |
                           +-- [Success] --> StatusController.startSuccess()
                                                  |
                                                  +--> BehaviorTasks.Wake()
                                                  +--> BuddyTTS.speak("Hello")
                                                  +--> BuddySTT.startListening()

2. ACTIVE CHAT LOOP
   [User Speech]
        |
        v
   [BuddySTT] -> (Filter: Is Chat Active?)
        |
        +-- [Yes] --> ChatSocketManager.sendString(text) ----> [CLOUD]
        |
        +-- [No]  --> (Ignore)

3. SHUTDOWN SEQUENCE
   [Stop Click] OR [Token Error] OR [User says "Goodbye"]
        |
        v
   StatusController.stop()
        |
        +--> BuddyTTS.speak("Goodbye")
        +--> BehaviorTasks.Sleep()
        +--> ChatSocketManager.endChat()
        +--> BuddySTT.stopListening()
```






