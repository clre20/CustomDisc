# CustomDisc

CustomDisc 是一款專為 Paper 伺服器設計的自訂唱片與號角插件。外觀沿用原版唱片與山羊角，音樂換成你自己的 .ogg 音檔，原版的音樂完全不受影響。全程使用箱子介面建立與編輯，插件會自動產生資源包並由內建的 HTTP 伺服器發送給玩家，也支援透過 Geyser 讓基岩版玩家聽到自訂音樂。

## 功能特性

* **箱子介面建立與編輯**：
  * 依步驟完成建立：選擇外觀 → 名稱與描述 → 音檔 → 循環／冷卻 → 音量與範圍 → 確認儲存。
  * 所有點擊一律只接受左鍵，右鍵、Shift、數字鍵與拖曳都會被取消。
  * 名稱與描述在聊天室輸入（輸入內容不會被其他玩家看到），支援 `&` 顏色碼與 `&#RRGGBB`，描述用 `|` 分行。
  * 總覽頁可直接試聽（只有自己聽得到），不用先儲存。
* **已發出的物品自動更新**：
  * 物品只記錄唱片 ID，名稱、描述等都從設定檔讀取；編輯儲存後，玩家手上的舊物品會在進服、打開容器或撿起時自動更新。
  * 刪除後，已發出的物品會變成「已失效」，保留外觀但無法播放或吹奏。
* **原生模式**：
  * 插件把自訂唱片與號角寫成資料包，伺服器下次啟動時註冊為真正的原版唱片與樂器，比較器、漏斗都照原版運作。
  * 剛建立、還沒重啟的唱片由插件自己播放，建立後立刻能用。
  * 比較器訊號強度預設沿用外觀唱片的原版數值，也可以自訂 0–15。
* **獨立資源包**：
  * 插件自行打包資源包並用內建 HTTP 伺服器發送，與 `server.properties` 的資源包分開，兩者同時套用。
  * 可設定強制下載，拒絕下載即踢出伺服器；內容沒變時玩家不需重新下載。
* **號角與冷卻**：
  * 冷卻照原版：吹任何一支號角後，玩家身上所有山羊角（含原版）一起冷卻。
  * 自訂號角的冷卻可自訂，但不能低於音檔長度。
* **唱片機輪播（1.21.9～26.3）**：
  * 唱片機正上方放單個箱子、陷阱箱或任何銅箱，箱內唱片會依格子順序輪播，規則與 26.4 的原版 Jukebox Playlists 相同。
  * 26.4 起由原版處理，插件自動停用此功能。
* **基岩版支援（Geyser）**：
  * 安裝 Geyser-Spigot 與 Floodgate 時，自動產生基岩版資源包並在基岩版玩家登入時送出，不需手動放進 Geyser 的 packs 資料夾。
* **其他**：
  * 自動讀取 ogg 的長度與聲道，立體聲音檔會提示（立體聲不會隨距離變小聲）。
  * 插件更新時 `config.yml` 自動補上新設定，保留原本的值與註解。
  * 啟動時與輸入 `/cd` 時檢查 GitHub 上的新版本。

## 指令與權限

本插件主指令為 `/cdisc`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/cd` | 查看插件版本並檢查更新 | 遊戲內 / 控制台 | `customdisc.use` | 所有人 |
| `/cd help` | 顯示指令說明清單 | 遊戲內 / 控制台 | `customdisc.help` | 所有人 |
| `/cd list [disc\|horn]` | 唱片／號角列表，並顯示原生或插件播放 | 遊戲內 / 控制台 | `customdisc.list` | OP |
| `/cd create <disc\|horn> <名稱>` | 開啟建立介面 | 僅限玩家 | `customdisc.create` | OP |
| `/cd edit <disc\|horn> <名稱>` | 開啟編輯介面，已發出的物品自動更新 | 僅限玩家 | `customdisc.edit` | OP |
| `/cd delete <disc\|horn> <名稱>` | 刪除，已發出的物品變成「已失效」 | 遊戲內 / 控制台 | `customdisc.delete` | OP |
| `/cd give <disc\|horn> <名稱> [玩家] [數量]` | 給予唱片或號角 | 遊戲內 / 控制台 | `customdisc.give`（給他人需 `customdisc.give.others`） | OP |
| `/cd reload` | 重新載入設定、資料與音檔 | 遊戲內 / 控制台 | `customdisc.reload` | OP |
| — | 吹號角時無視冷卻 | — | `customdisc.horn.bypasscooldown` | 無 |

> 指令別名：`/c-disc`、`/cd` 等同 `/cdisc`；`/cd del` 等同 `/cd delete`。
>
> 名稱只能使用 `a-z`、`0-9`、`_`，顯示名稱可以在介面裡另外設定中文。

## 使用範例

### 1. 建立第一張自訂唱片
1. 把音檔放進 `plugins/CustomDisc/sounds/`（建議單聲道、英文檔名），再重新載入：
   ```bash
   /cd reload
   ```
2. 開啟建立介面，照著步驟完成：
   ```bash
   /cd create disc my_song
   ```
3. 拿到唱片，放進唱片機即可播放：
   ```bash
   /cd give disc my_song
   ```

### 2. 從控制台發給玩家
```bash
cd give disc my_song Steve 5
```

### 3. 用箱子做點唱機
在唱片機正上方放一個箱子，把多張唱片放進箱子，再放一張唱片進唱片機。一張播完會自動換成箱子裡的下一張，播完一輪從頭開始。

## 安裝與設定

1. 把 jar 放進 `plugins/`，啟動一次伺服器。
2. 在 `config.yml` 把 `resource-pack.public-url` 改成玩家連得到的網址，並在防火牆開放 `resource-pack.port`（預設 `52011`，TCP）。
3. 自動產生的資料包位於 `<存檔>/datapacks/customdisc`，請勿停用或刪除。

| 設定 | 說明 |
| :--- | :--- |
| `prefix` | 所有訊息的開頭文字 |
| `update-checker.enabled` | 啟動時是否檢查更新 |
| `resource-pack.host` / `port` | 內建 HTTP 伺服器綁定的位址與端口 |
| `resource-pack.public-url` | 玩家下載資源包用的網址 |
| `resource-pack.required` | 是否強制下載，拒絕即踢出 |
| `bedrock.enabled` | 是否產生基岩版資源包（需要 Geyser） |
| `playback.native-mode` | 是否使用原生模式 |
| `chat-input.timeout` / `cancel-word` | 聊天室輸入的等待秒數與取消字 |
| `defaults.*` | 新建唱片／號角的預設音量、範圍、冷卻 |


# CustomDisc

CustomDisc (C-Disc) is a custom music disc and goat horn plugin designed for Paper (Minecraft 1.21.x and 26.x) servers. Custom items reuse the look of vanilla music discs and goat horns but play your own .ogg files, while vanilla music stays completely untouched. Everything is created and edited through chest GUIs, the plugin builds the resource pack automatically and serves it from a built-in HTTP server, and Bedrock players can hear the custom music too through Geyser.

## Features

* **Chest GUI Creation & Editing**:
  * Step-by-step creation: appearance → name & description → sound file → loop / cooldown → volume & range → confirm and save.
  * Only left clicks are accepted; right clicks, shift-clicks, number keys and dragging are all cancelled.
  * Names and descriptions are typed in chat (hidden from other players), with `&` color codes and `&#RRGGBB` support; use `|` to split description lines.
  * The overview page offers a private preview that only you can hear, without saving first.
* **Automatic Item Updates**:
  * Items only store the disc ID; names and descriptions are read from the config. After you edit and save, existing items update automatically when players join, open a container or pick them up.
  * Deleted discs and horns become "invalid": they keep their look but can no longer be played.
* **Native Mode**:
  * Custom discs and horns are written into a data pack and registered as real vanilla jukebox songs and instruments on the next server start, so comparators and hoppers behave exactly like vanilla.
  * Newly created discs are played by the plugin until the next restart, so they work immediately.
  * The comparator signal defaults to the vanilla value of the chosen disc look and can be set from 0 to 15.
* **Separate Resource Pack**:
  * The plugin builds its own resource pack and serves it from a built-in HTTP server, separate from the pack in `server.properties`; both are applied together.
  * Optionally required (players who decline are kicked); players don't re-download when the content hasn't changed.
* **Goat Horns & Cooldowns**:
  * Vanilla-style cooldown: after blowing any horn, every goat horn the player holds (including vanilla ones) goes on cooldown.
  * Custom horn cooldowns are configurable but never shorter than the sound file.
* **Jukebox Playlists (1.21.9–26.3)**:
  * Place a single chest, trapped chest or any copper chest directly above a jukebox, and the discs inside play in slot order, following the same rules as the vanilla 26.4 Jukebox Playlists.
  * From 26.4 onward vanilla handles this and the plugin disables its own version.
* **Bedrock Support (Geyser)**:
  * With Geyser-Spigot and Floodgate installed, a Bedrock resource pack is generated and sent to Bedrock players when they join; no need to copy it into Geyser's packs folder.
* **Other**:
  * Ogg length and channel count are read automatically, with a warning for stereo files (stereo audio doesn't fade with distance).
  * `config.yml` gains new options automatically on plugin updates, keeping your values and comments.
  * Checks GitHub for new versions on startup and when running `/cd`.

## Commands & Permissions

The primary command for this plugin is `/cdisc`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/cd` | Show the plugin version and check for updates | In-game / Console | `customdisc.use` | Everyone |
| `/cd help` | Display command help list | In-game / Console | `customdisc.help` | Everyone |
| `/cd list [disc\|horn]` | List discs / horns and whether each is native or plugin-played | In-game / Console | `customdisc.list` | OP |
| `/cd create <disc\|horn> <name>` | Open the creation GUI | Player only | `customdisc.create` | OP |
| `/cd edit <disc\|horn> <name>` | Open the editing GUI; existing items update automatically | Player only | `customdisc.edit` | OP |
| `/cd delete <disc\|horn> <name>` | Delete; existing items become "invalid" | In-game / Console | `customdisc.delete` | OP |
| `/cd give <disc\|horn> <name> [player] [amount]` | Give a disc or horn | In-game / Console | `customdisc.give` (`customdisc.give.others` to give to others) | OP |
| `/cd reload` | Reload the config, data and sound files | In-game / Console | `customdisc.reload` | OP |
| — | Ignore horn cooldowns | — | `customdisc.horn.bypasscooldown` | None |

> Command Aliases: `/c-disc` and `/cd` are aliases for `/cdisc`; `/cd del` is an alias for `/cd delete`.
>
> Names may only contain `a-z`, `0-9` and `_`; the display name can be set separately in the GUI.

## Usage Examples

### 1. Create Your First Custom Disc
1. Put your sound file in `plugins/CustomDisc/sounds/` (mono and an English file name recommended), then reload:
   ```bash
   /cd reload
   ```
2. Open the creation GUI and follow the steps:
   ```bash
   /cd create disc my_song
   ```
3. Get the disc and put it in a jukebox to play it:
   ```bash
   /cd give disc my_song
   ```

### 2. Give Discs from the Console
```bash
cd give disc my_song Steve 5
```

### 3. Build a Jukebox Playlist
Place a chest directly above a jukebox, fill the chest with discs, then insert a disc into the jukebox. When a disc finishes, it is swapped with the next disc in the chest, and the playlist loops back to the start.

## Installation & Configuration

1. Put the jar in `plugins/` and start the server once.
2. In `config.yml`, set `resource-pack.public-url` to an address players can reach, and open `resource-pack.port` (default `52011`, TCP) in your firewall.
3. The generated data pack lives in `<world>/datapacks/customdisc`; do not disable or delete it.

| Option | Description |
| :--- | :--- |
| `prefix` | Prefix for all plugin messages |
| `update-checker.enabled` | Check for updates on startup |
| `resource-pack.host` / `port` | Address and port the built-in HTTP server binds to |
| `resource-pack.public-url` | URL players download the resource pack from |
| `resource-pack.required` | Require the pack; players who decline are kicked |
| `bedrock.enabled` | Generate a Bedrock resource pack (requires Geyser) |
| `playback.native-mode` | Use native mode |
| `chat-input.timeout` / `cancel-word` | Chat input timeout in seconds and the cancel word |
| `defaults.*` | Default volume, range and cooldown for new discs / horns |