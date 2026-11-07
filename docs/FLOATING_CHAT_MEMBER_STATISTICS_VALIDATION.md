# 群员统计专项验证记录

仅 SDK 定向编译和隔离验证；未运行 Gradle、未构建 APK、未实机验收。

## member-icons

```text
PASS production native icon spans: male/female placement, host loader signature, two loads per owner, no repeated load over 1000 formats, failure text and one error
```

## member-host-evidence

```text
PASS 8.0.78 title bindings and ChatTipsBarGroup -> s3 layout IDs
e6a21e348b771de4df70802eda08fe97114d5e5e220d27a24685600a542582e3 res/layout/ba.xml
8a56a100108337e7ee9e323f2bbc21f9ffa8e4dc3e1856a5d9a138f2acb54e0f res/layout/s3.xml
0188603e06e618b42d2dd3284f736faf069f6da1a61a23435475ad6a75934068 smali_classes11/com/tencent/mm/ui/chatting/component/fg.smali
76ac7aed0c21cb5f42034df15a093a824d1437d36544e929f3a59aeede0bcc59 smali_classes3/com/tencent/mm/ui/tipsbar/ChatTipsBarGroup.smali
PASS native male/female raw resources and actual profile loader calls
27c9db29a96bb400270d8ea75c8967f1603aa90bfb47d1b60d3f7d379bf1881a smali_classes16/com/tencent/mm/plugin/profile/ui/tab/m5.smali
```

## member-target-sdk

```text
PASS floating header all production Kotlin + contact API Java SDK direct compile (not Android/R8)
```

## member-counts

```text
PASS production statistics: duplicate removal, missing/unknown gender, self excluded from friends, 200-member batches, read failure, empty/unavailable list, cancellation and cursor closure
PASS production SQL friend predicate: friend, blacklist, deleted, official, nonfriend and empty encryption identity
```

## member-life

```text
PASS whole owner: default-off zero reads, 1000 unchanged binds one read, blocked old generation discarded after switch, reattach refresh, disable/close cleanup and no stale UI callback
```

## member-layout

```text
PASS production capsule: measured title bottom +3, real occupied height, 1000 stable calls no writes/measure, width/text invalidation, disable height restore and unmeasured title hidden
PASS production wiring: capsule before notice placement, cleanup, default-off settings read/write
```

## member-inset

```text
PASS header overlap/nonaccumulation/1000 idle calls cached; preserve footer padding; restore and pending-layout guard
PASS real apply wiring and wrapper/recycler resolution contract
```

## member-backup

```text
PASS mention-all: all four independent switch combinations and custom/default command archive round trip; runtime excluded
PASS: message menu all 12 column/row configurations production archive round trip
PASS: private-chat skip default off, true/false archive round trip, runtime excluded
PASS: moments card two default-off toggles and three colors true/false round trip; runtime excluded
PASS: shortcut label background blank/single/three/alpha colors round trip; text/icon unchanged; runtime excluded
PASS: member statistics default-off and false/true/false production archive round trip; memory-only snapshots
PASS: calendar resource, opacity, zoom and offsets round trip; temporary files excluded
PASS: music background, opacity, persistent revision and transform round trip; temporary excluded
PASS: five Me switches true/false round trip, runtime cache excluded
PASS extra visibility: default off, four independent combinations archive round trip; runtime excluded
PASS: voice-to-text toggle true/false round trip and runtime exclusion
PASS nickname width default-off and true/false round trip, runtime exclusion
PASS auto merge actual settings: default-off, true/false export-import ZIP round trip and runtime cache exclusion
PASS avatar mention: default off, independent on/off, above/below, alpha three-color ZIP round trip; method cache excluded
PASS: group bar default/off/on, style, transparency, ordered group/member JSON, next number round trip; runtime cache excluded
PASS bidirectional grouping exclusion for direct/quick preference writes; both group configurations preserved
PASS removed input settings excluded from export/legacy import; header settings preserved
PASS profile real-name enabled/off, empty/custom/default prefix, three colors archive round trip; runtime excluded
PASS: automatic new/unopened stores; runtime exclusion; all value types; resources; path relocation; legacy JSON; validation; commit rollback; traversal and links
```

## member-analysis-regression

```text
PASS production owner resolution: nested wrappers, cycles, missing global API, owner priority, destroyed/finishing Activity and fallback; removed group registration
```

整包聚合定向编译受现有 ABI 缺失阻塞，未计通过。新功能实机显示、公告及滚动帧耗时待统一验收。不改变资源生命周期七阶段进度。
