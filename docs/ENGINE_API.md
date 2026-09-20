# ENGINE_API.md — Go↔Kotlin contract (ZentraDL, upstream v1.9.3)

> Contract-first (BRIEF 14.6): Go patches and Kotlin `EngineClient` BOTH build
> against this file. Change it deliberately; both sides update together.
> Source of truth for upstream paths: `pkg/api/service.go`, `pkg/rest/server.go`,
> `pkg/download/model.go`, `pkg/base/model.go`, `pkg/protocol/{http,bt}/model.go`.

## 1. Transport

Primary: in-process bind (`InvokeAsync(method,path,query,body)` →
`Dispatch()`; events via `SubscribeTaskEvents(mask)`). No port, no token.
Fallback/external (browser-extension bridge): TCP `127.0.0.1:<random>` +
`X-Api-Token` or `Authorization: Bearer`. Bind loopback ONLY, never 0.0.0.0.
No WebSocket. Push = event mask bits
(done=bit0, error, start, progress, pause, delete); event JSON
`{"type":"task.done|…","taskId","name?","error?"}` at `refreshInterval`
(default 350 ms). Else poll `status` ≤1 Hz foreground (1 s fg, slower/none bg).

## 2. Envelope + errors

All REST: `Result{code,msg,data}`. Codes: `0` ok · `1000` error · `1001`
unauthorized · `1002` invalidParam · `2001` taskNotFound. Filters:
`?id=&status=&notStatus=`; deletes take `?force=true` (force = delete files).

## 3. Endpoints (26)

| # | Method | Path | Body → returns |
|---|---|---|---|
| 1 | GET | `/api/v1/info` | `{version,runtime,os,arch}` |
| 2 | POST | `/api/v1/resolve` | `ResolveTask{req,opts}` → `{id,res}` |
| 3 | POST | `/api/v1/tasks` | `CreateTask{rid?,req?,opts?}` → taskId |
| 4 | POST | `/api/v1/tasks/batch` | `[{req,opts}]` → [taskId] |
| 5 | PATCH | `/api/v1/tasks/{id}` | `{req?,opts?}` → ok (HTTP: URL swap; BT: `selectFiles` only) |
| 6/7 | PUT | `/api/v1/tasks/{id}/pause`, `/api/v1/tasks/pause` | filter → ok |
| 8/9 | PUT | `/api/v1/tasks/{id}/continue`, `/api/v1/tasks/continue` | filter → ok |
| 10/11 | DELETE | `/api/v1/tasks/{id}?force=`, `/api/v1/tasks?force=` | filter → ok |
| 12 | GET | `/api/v1/tasks/{id}` | full `Task` |
| 13 | GET | `/api/v1/tasks[?id&status&notStatus]` | `[Task]` |
| 14 | GET | `/api/v1/tasks/{id}/status` | `TaskRuntimeStatus` (light poll) |
| 15 | GET | `/api/v1/tasks/{id}/stats[?pieces=…]` | protocol stats (§6; pieces flags = OUR patch) |
| 16/17 | GET/PUT | `/api/v1/config` | `DownloaderStoreConfig` |
| 18–25 | … | `/api/v1/extensions…` (list/get/install/switch/settings/update/delete) | `InstallExtension{devMode,url}`, identity = `author@name` |
| 26 | POST | `/api/v1/webhook/test` | `{url}` must return 200 |

## 4. Models (Kotlin mirrors in `:engine`, kotlinx.serialization)

```kotlin
// Status: ready|running|pause|wait|error|done ; Protocol: http|bt|ed2k|hls
data class Task(val id: String, val name: String, val protocol: String,
  val meta: FetcherMeta, val status: String, val progress: Progress,
  val createdAt: Long, val updatedAt: Long)
data class Progress(val used: Long, val speed: Long, val downloaded: Long,
  val uploadSpeed: Long = 0, val uploaded: Long = 0,
  val extractStatus: String? = null, val extractProgress: Long? = null)
data class TaskRuntimeStatus(val status: String, val used: Long, val speed: Long,
  val downloaded: Long, val total: Long, val uploadSpeed: Long, val uploaded: Long,
  val files: List<FileRuntimeStatus>)
data class FileRuntimeStatus(val index: Int, val size: Long, val downloaded: Long)
data class Request(val url: String, val extra: Map<String,String> = mapOf(),
  val labels: Map<String,String> = mapOf(),
  val proxy: ReqProxy = ReqProxy(), val skipVerifyCert: Boolean = false)
data class ReqProxy(val mode: String = "follow", val scheme: String? = null,
  val host: String? = null, val usr: String? = null, val pwd: String? = null)
data class Resource(val name: String, val size: Long, val range: Boolean,
  val files: List<ResFile>, val hash: String? = null)
data class Options(val name: String? = null, val path: String,
  val selectFiles: List<Int> = listOf(), val extra: Map<String,String> = mapOf())
```

Config keys that exist: `downloadDir, maxRunning=5,
protocolConfig{http:{userAgent,connections=16},
bt:{listenPort,trackers[],seedKeep,seedRatio=1.0,seedTime=7200},…},
proxy{enable,system,scheme,host,usr,pwd}, webhook, script, autoTorrent,
archive{autoExtract,deleteAfterExtract}, api{…}`.

## 5. PieceMap (upstream, verified-complete only)

`{encoding:"bitset-v1", pieceCount, pieceSize, completedPieces, data:b64}`
1 bit/piece LSB-first; nil while metadata-missing/verifying (retain last).
Flutter decoder: `ui/flutter/lib/api/model/piece_map_codec.dart` (port logic).

## 6. OUR extensions (core patches; default payload unchanged)

`GET /api/v1/tasks/{id}/stats?pieces=<flags>&downsample=<N>` where flags ∈
`map` (default on, bitset-v1) `states` `avail` `flight` `files`. Response adds
`pieceDetail?`:

```json
{"total":5000,"pieceLength":2097152,
 "runs":[[2,1234],[0,3760],[1,6]],
 "availability":[5,5,4],"inFlight":[4994,4995],"verifying":[12],
 "fileRanges":[{"file":0,"begin":0,"end":2499}],
 "downsample":1,"contiguousFromStartPct":24.68}
```

- `runs`: RLE `[stateIdx,count]`; states 0=missing 1=downloading 2=downloaded
  3=verifying 4=skipped 5=high-priority. `availability` parallel array or
  heat-buckets when down-sampled. Cell aggregates N pieces: fill = fraction.
- ~1 Hz ONLY while Pieces tab visible; server caps downsample for >50k pieces.
- HTTP twin: `stats` for http tasks gains per-conn
  `[{id,begin,end,downloaded,speed,retries,completed,failed}]` (our patch).

## 7. EngineClient rules

Mirror paths/methods/query names verbatim (port `ui/flutter/lib/api/api.dart`).
OkHttp + kotlinx.serialization; in-process transport first, TCP+token fallback.
Push events → Flow; else adaptive poll (1 s fg). Never expose port off-device.
