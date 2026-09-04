# Repository Instructions

- Except for cases where the Android platform requires XML (`app_icon`, `service`, etc.), adding new XML files is prohibited.
- UI, icons, and anything else expressible in Kotlin must be implemented using `.kt` files; do not add new drawable, layout, or other XML resources for them.
- When it is unclear whether something qualifies as an XML exception, confirm with the user first.

## Git commits and pushes

- When the user explicitly asks to commit or push code, perform only the necessary lightweight checks and the corresponding Git operations; do not automatically expand this into a deep code review, temporary worktree isolation verification, a full build or test run, a Release Gate, a release check, or any other heavyweight process. Only run such checks when the user explicitly requests them.

## Kotlin visibility

- The `internal` keyword must not be used inside the `gkd-app` module; since no other module references `gkd-app`, externally visible declarations should omit the visibility modifier (using Kotlin's default `public`), and `private` should only be used to narrow scope where needed.
- A `_xxx` backing property that corresponds one-to-one with a public property and exists only to narrow visibility or mutability must use Explicit Backing Fields instead; ordinary private fields, caches, or generated-code-style naming that have no such direct correspondence are not restricted by this. The unused lambda parameter placeholder `_` is not subject to this restriction either.

## Kotlin static initialization

- Lists, collections, maps, and their sort results composed of `object`/`data object` singletons inside a `companion object` or `object` must be initialized with `by lazy`; constructing such collections directly during static initialization is prohibited, to avoid circular initialization issues on the JVM, JS, and Wasm.

## Compose and state boundaries

- Except for overlay-window Compose, any Composable in the app's Compose tree can obtain `mainVm` via `LocalMainViewModel`, without needing to forward app-level operations like navigation, global dialogs, or opening URLs layer by layer.
- Route pages and their private Composables can obtain the page ViewModel directly, and handle platform UI behaviors such as permissions and Activity Result. Reusable components must not obtain the page ViewModel, and should only receive the state and event callbacks they need.
- App-level read-only Flows should be collected directly by the Composable that actually consumes them, rather than being copied into the page's `UiState` or ViewModel. Use `collectAsStateWithLifecycle` for ordinary Flows, the dedicated API for Paging, and keep high-frequency state in the smallest consuming subtree.
- Service start/stop, persistence, and other business side effects must be triggered by explicit events and handled by a ViewModel, Repository, or Store; a Composable must not perform writes by observing state.
- `XxxUiState` and `XxxUiActions` should only be used when reuse, standalone previews, or a complex page contract genuinely requires them. `UiState` may only represent an immutable page snapshot, and must not contain a Flow, Paging, or high-frequency state; extract a private builder function only when the same mapping has multiple construction paths.
- A ViewModel's mutable state must be `private`, exposing only immutable state and explicit business methods. Read-only `StateFlow`s should use Explicit Backing Fields; `_xxxFlow`/`xxxFlow` dual properties and `.asStateFlow()` are prohibited.
- Pure UI state such as scrolling, focus, menus, animation, dragging, and multi-select should stay in Compose; reusable interaction logic can be encapsulated as `rememberXxxState`, but must not access the ViewModel, database, Store, Service, or navigation. Multiple fields that need atomic consistency must be provided as a single immutable snapshot from the source of truth, and business state must not be passed through `CompositionLocal`.
- When a Composable needs to conditionally decide whether to render subsequent UI, an early `return` must not be used; the UI must instead be wrapped in the corresponding conditional block. This restriction does not apply to a labeled return inside an event or coroutine lambda.

## State and side effects

- Room's observable queries should remain a cold `Flow`; aggregate them first inside the ViewModel according to the page's consistency boundary, then convert the final page snapshot into a `StateFlow<Loadable<XxxUiState>>`. `Loading` means the first full emission has not yet been received; `Ready(emptyList())` means loading has completed with an empty result. Do not fake the initial value with an empty collection, and do not use a counter, `attachLoad`, or similar side-channel state to infer whether multiple queries have finished loading.
- Derived display state produced by `combine`, `map`, `stateIn`, etc. may only be used for rendering and transient UI synchronization; it must not drive database, file, or network writes, or Service start/stop, via `collect`, `onEach`, or a state watch.
- Persistence and business side effects must be triggered by an explicit user event, system event, or domain method, and completed inside the Repository/Store according to the business consistency boundary. Syncing a single source-of-truth state to an idempotent external projection is allowed, but the sync callback must not read other state to assemble a write.
- `debounce`, `conflate`, `collectLatest`, and mutexes may only control scheduling or concurrency; they cannot replace an atomic update across multiple state sources. State that needs to be read consistently should be aggregated into a single immutable state object.

## Build and testing

- Regular tests only compile the `gkd` flavor; unless the user gives an explicit instruction, running any build task for the `play` flavor is prohibited.

## Testing strategy

- New tests must verify observable behavior, clearly stating the input under test, the expected output, and the specific regression being guarded against. Prioritize coverage of pure functions, boundary conditions, error paths, platform/version compatibility differences, and regression scenarios for already-fixed defects.
- Do not split apart production logic that should otherwise be aggregated, widen declaration visibility, or expose test-only APIs just to add a test; tests must adapt to a reasonable production design rather than shaping production code in reverse.
- Do not add tests that merely restate a static declaration in production code — this includes enum members, constant values or collections, sequential numbering, member relationships derivable from the same registry, and constraints already guaranteed by Kotlin's type system.
- Only add a stability test for a constant or identifier when it belongs to an external protocol, a persistence format, or a cross-version compatibility contract, and note in the test name or comment which compatibility behavior is being protected.

## Android API research

- For locating source for Android framework Java/AIDL APIs, comparing signatures or availability across versions, analyzing why an API is missing, or generating Java hidden-API access code, you must use the project's `android-api-diff` skill: `.agents/skills/android-api-diff/SKILL.md`.
- Follow that skill's routing to use the `android-api-diff` CLI, and keep its default JSON output; do not implement or simulate Android API version checks yourself.
- When installing or updating the project-level skill, run `android-api-diff skill install` from the project root.
