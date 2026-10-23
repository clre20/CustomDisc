# CustomDisc

CustomDisc是一款專為 Paper伺服器設計的自訂唱片與號角插件。外觀沿用原版唱片與山羊角，音樂換成你自己的 .ogg 音檔，原版的音樂完全不受影響。全程使用箱子介面建立與編輯，插件會自動產生資源包並由內建的 HTTP 伺服器發送給玩家，也支援透過 Geyser 讓基岩版玩家聽到自訂音樂。

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
  * 有安裝 [HttpHub](https://github.com/clre20/HttpHub) 時，改由 HttpHub 提供下載，和其他插件共用同一個端口，CustomDisc 不再另外開端口。
* **號角與冷卻**：
  * 冷卻照原版：吹任何一支號角後，玩家身上所有山羊角（含原版）一起冷卻。
  * 自訂號角的冷卻可自訂，但不能低於音檔長度。
  * 26.4 起由原版處理，插件自動停用此功能。
* **基岩版支援（Geyser）**：
  * 安裝 Geyser-Spigot 與 Floodgate 時，自動產生基岩版資源包並在基岩版玩家登入時送出，不需手動放進 Geyser 的 packs 資料夾。
* **所有文字都可自訂**：
  * 聊天訊息、介面標題、按鈕、物品名稱與描述、後台訊息全部放在 `messages.yml`，支援變數替換。
  * `config.yml` 與 `messages.yml` 缺少的項目會在啟動與 `/cd reload` 時自動補上，保留原本的值與註解。
* **版本檢查**：
  * 啟動時在後台向 GitHub Releases 檢查一次，也可以用 `/cd version` 手動檢查；結果快取 10 分鐘，不會定時輪詢。
  * 有新版本時，具有 `customdisc.version` 權限的管理員登入 3 秒後會收到提醒；兩項功能都能在 `config.yml` 關閉。
* **其他**：
  * 自動讀取 ogg 的長度與聲道，立體聲音檔會提示（立體聲不會隨距離變小聲）。
  * 箱子介面帶有插件識別（自訂 InventoryHolder 與 `customdisc:gui` 標籤），方便其他插件辨識。
  * 讀檔、打包與網路請求都在背景執行，不影響伺服器 TPS。

## 指令與權限

本插件主指令為 `/cdisc`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/cd` 或 `/cd help` | 顯示指令說明清單 | 遊戲內 / 控制台 | `customdisc.help` | 所有人 |
| `/cd version` | 查看插件版本並檢查更新，有新版時登入會收到提醒 | 遊戲內 / 控制台 | `customdisc.version` | OP |
| `/cd list [disc\|horn]` | 唱片／號角列表，並顯示原生或插件播放 | 遊戲內 / 控制台 | `customdisc.list` | OP |
| `/cd create <disc\|horn> <名稱>` | 開啟建立介面 | 僅限玩家 | `customdisc.create` | OP |
| `/cd edit <disc\|horn> <名稱>` | 開啟編輯介面，已發出的物品自動更新 | 僅限玩家 | `customdisc.edit` | OP |
| `/cd delete <disc\|horn> <名稱>` | 刪除，已發出的物品變成「已失效」 | 遊戲內 / 控制台 | `customdisc.delete` | OP |
| `/cd give <disc\|horn> <名稱> [玩家] [數量]` | 給予唱片或號角 | 遊戲內 / 控制台 | `customdisc.give`（給他人需 `customdisc.give.others`） | OP |
| `/cd reload` | 重新載入設定、訊息、資料與音檔 | 遊戲內 / 控制台 | `customdisc.reload` | OP |
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

## 安裝與設定

1. 把 jar 放進 `plugins/`，啟動一次伺服器。
2. **建議同時安裝 [HttpHub](https://github.com/clre20/HttpHub)**：資源包改由 HttpHub 的端口（預設 `52010`）提供，只需要在防火牆開放這一個端口，下面的 `host`／`port`／`public-url` 就不會使用。
   沒有安裝 HttpHub 時：在防火牆開放 `resource-pack.port`（預設 `52012`，TCP）。`resource-pack.public-url` 預設為 `auto`，會自動使用玩家連線時輸入的位址，通常不用改；若玩家要從其他網域下載（例如經過反向代理），再填完整網址。
   * 這個端口是 TCP，不能和其他插件自己開的 HTTP 伺服器用同一個號碼（例如 PMonitor 用的 `52011`）。
3. 自動產生的資料包位於 `<存檔>/datapacks/customdisc`，請勿停用或刪除。

| 設定 | 說明 |
| :--- | :--- |
| `prefix` | 所有訊息的開頭文字（其他文字都在 `messages.yml`） |
| `update-checker.check-on-startup` | 啟動時是否檢查更新 |
| `update-checker.notify-on-join` | 有新版時是否在管理員登入後提醒 |
| `resource-pack.use-httphub` | 有裝 HttpHub 時是否改由它提供下載（預設開啟） |
| `resource-pack.host` / `port` | 內建 HTTP 伺服器綁定的位址與端口（沒有使用 HttpHub 時） |
| `resource-pack.public-url` | 玩家下載資源包用的網址；`auto` = 使用玩家連線的位址 |
| `resource-pack.required` | 是否強制下載，拒絕即踢出 |
| `bedrock.enabled` | 是否產生基岩版資源包（需要 Geyser） |
| `playback.native-mode` | 是否使用原生模式 |
| `chat-input.timeout` / `cancel-word` | 聊天室輸入的等待秒數與取消字 |
| `defaults.*` | 新建唱片／號角的預設音量、範圍、冷卻 |


# CustomDisc

CustomDiscis a custom music disc and goat horn plugin designed for Paper servers. Custom items reuse the look of vanilla music discs and goat horns but play your own .ogg files, while vanilla music stays completely untouched. Everything is created and edited through chest GUIs, the plugin builds the resource pack automatically and serves it from a built-in HTTP server, and Bedrock players can hear the custom music too through Geyser.

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
  * When [HttpHub](https://github.com/clre20/HttpHub) is installed, the pack is served through HttpHub and shares its single port with other plugins; CustomDisc opens no port of its own.
* **Goat Horns & Cooldowns**:
  * Vanilla-style cooldown: after blowing any horn, every goat horn the player holds (including vanilla ones) goes on cooldown.
  * Custom horn cooldowns are configurable but never shorter than the sound file.
  * From 26.4 onward vanilla handles this and the plugin disables its own version.
* **Bedrock Support (Geyser)**:
  * With Geyser-Spigot and Floodgate installed, a Bedrock resource pack is generated and sent to Bedrock players when they join; no need to copy it into Geyser's packs folder.
* **Fully Customizable Text**:
  * Chat messages, GUI titles, buttons, item names and lore, and console messages all live in `messages.yml`, with placeholder support.
  * Missing entries in `config.yml` and `messages.yml` are added automatically on startup and `/cd reload`, keeping your values and comments.
* **Update Checker**:
  * Checks GitHub Releases once in the background on startup, or manually with `/cd version`; results are cached for 10 minutes with no periodic polling.
  * When a new version is available, admins with `customdisc.version` get a reminder 3 seconds after joining; both features can be turned off in `config.yml`.
* **Other**:
  * Ogg length and channel count are read automatically, with a warning for stereo files (stereo audio doesn't fade with distance).
  * Chest GUIs carry a plugin signature (a custom InventoryHolder and a `customdisc:gui` tag) so other plugins can identify them.
  * File reading, pack building and network requests all run in the background without affecting server TPS.

## Commands & Permissions

The primary command for this plugin is `/cdisc`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/cd` or `/cd help` | Display command help list | In-game / Console | `customdisc.help` | Everyone |
| `/cd version` | Show the plugin version and check for updates; admins are reminded on join when a new version exists | In-game / Console | `customdisc.version` | OP |
| `/cd list [disc\|horn]` | List discs / horns and whether each is native or plugin-played | In-game / Console | `customdisc.list` | OP |
| `/cd create <disc\|horn> <name>` | Open the creation GUI | Player only | `customdisc.create` | OP |
| `/cd edit <disc\|horn> <name>` | Open the editing GUI; existing items update automatically | Player only | `customdisc.edit` | OP |
| `/cd delete <disc\|horn> <name>` | Delete; existing items become "invalid" | In-game / Console | `customdisc.delete` | OP |
| `/cd give <disc\|horn> <name> [player] [amount]` | Give a disc or horn | In-game / Console | `customdisc.give` (`customdisc.give.others` to give to others) | OP |
| `/cd reload` | Reload the config, messages, data and sound files | In-game / Console | `customdisc.reload` | OP |
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

## Installation & Configuration

1. Put the jar in `plugins/` and start the server once.
2. **Installing [HttpHub](https://github.com/clre20/HttpHub) is recommended**: the pack is then served on HttpHub's port (default `52010`), the only port you need to open, and the `host` / `port` / `public-url` options below are not used.
   Without HttpHub: open `resource-pack.port` (default `52012`, TCP) in your firewall. `resource-pack.public-url` defaults to `auto`, which uses the address the player connected with, so it usually needs no change; set a full URL only if players should download from another domain (e.g. behind a reverse proxy).
   * This is a TCP port and cannot be the same number as another plugin's own HTTP server (e.g. PMonitor's `52011`).
3. The generated data pack lives in `<world>/datapacks/customdisc`; do not disable or delete it.

| Option | Description |
| :--- | :--- |
| `prefix` | Prefix for all plugin messages (all other text is in `messages.yml`) |
| `update-checker.check-on-startup` | Check for updates on startup |
| `update-checker.notify-on-join` | Remind admins on join when a new version exists |
| `resource-pack.use-httphub` | Serve the pack through HttpHub when it is installed (on by default) |
| `resource-pack.host` / `port` | Address and port the built-in HTTP server binds to (when not using HttpHub) |
| `resource-pack.public-url` | URL players download the resource pack from; `auto` = the player's connection address |
| `resource-pack.required` | Require the pack; players who decline are kicked |
| `bedrock.enabled` | Generate a Bedrock resource pack (requires Geyser) |
| `playback.native-mode` | Use native mode |
| `chat-input.timeout` / `cancel-word` | Chat input timeout in seconds and the cancel word |
| `defaults.*` | Default volume, range and cooldown for new discs / horns |
