# Initial-member join-time bindings

Scope: ordinary create-chatroom requests observed while join-time bindings are enabled, verified only for WeChat 8.0.78 (3180). No retrospective inference for existing groups.

## Protocol evidence

Read-only decompilation of the supplied WeChat 8.0.78 sample, classes3.dex:

- `qn.o`: request type 119, `/cgi-bin/micromsg-bin/createchatroom`.
- `doScene`: request `e.a.a`, selected members `f`, member identifier `d`, decoded by `x91.j1.g`.
- `onGYNetEnd`: response holder `e.b`, `getRetCode()`, response `a`, group identifier `i`, count `g`, members `h`. Member status `e == 0` is accepted by the native implementation; other statuses are excluded.
- Native success processing adds the current account (`b41.y1.u()`) to the room and calls `b41.u1.t` with the accepted members.
- `pc5.cb0`: the response contains no direct creation timestamp. Never use callback wall-clock time.

The passive observer snapshots account/selection at dispatch and reads successful response members after native handling. The eligible set is the intersection, excluding self. It never modifies callback results or dispatches create requests. Weak pending keys and a 32-request limit bound tracking; disabling removes both hooks and pending entries.

## Time and persistence

Persist creation evidence by account and new room ID, only when no existing persisted room record exists. Use the creator's positive native or member-detail-server join time. Missing time leaves evidence pending for a subsequent profile query. History matching is not used to invent timestamps for these initial members. Ordinary groups retain the user-selected 32580f7 history compatibility behavior.

Creation evidence survives restart. A confirmed member departure removes both its binding and initial-member eligibility. Creator departure clears remaining eligibility, preventing a later creator rejoin time from propagating. Later members are never added to the initial set. Existing native/server member timestamps retain priority.

## Offline verification

- `creation_observer.py`: production observer with simulated verified host fields; selected/success intersection, repeated callback, failed callback, exception, response error, invalid group, account switch, unobserved scene, teardown.
- `bindings.py`: production JSON store; pending time, creation persistence, initial-only propagation, old-group guard, departure/rejoin, creator departure, existing account/group isolation and source upgrade.
- `binding_integration.py`: production provider/store with a simulated observer event; creator native time propagates, profiles avoid history scans, shutdown, restart persistence, departure/rejoin.
- `run.py`: existing provider demand-loading/lifecycle regression with host fixtures.
- `scripts/check_profile_runtime_sdk.py`: targeted SDK compilation and native-parser regression, using existing ABI. No Gradle or APK build.

Offline checks do not verify hook delivery or rendering in real WeChat. Unobserved offline leave/rejoin cycles remain outside the guarantees of the membership-diff mechanism. Other WeChat versions and facing/group-code creation are unsupported by this observer.
