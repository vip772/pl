# DexKit compatibility boundaries

Baseline: 42eeca9. This stage does not enable host idle recycling.

- Internal FeatureContext consumers use managed query scopes; no production call to context.dexKitBridge() remains.
- FeatureContext.dexKitBridge(), DexBridgeHolder.getDexKitBridge(), and ScriptDexKitBridge.bridge() acquire the manager process-lifetime raw pin before returning a native bridge. Repeated exposure does not multiply pins.
- ScriptDexKitBridge.holder() returns a managed service without exposing native state. Access to holder/finder must not require a current native generation or acquire a raw pin.
- Main-process script dexKitBridge global intentionally calls bridge(), so even a script that never uses this global pins the generation. Unloading the script cannot prove escaped references are gone and does not revoke this pin.
- Appbrand constructs ScriptPluginBridge with no DexKit service; dexKit, dexKitBridge, dexFinder, dexBridgeHolder remain null.
- ModuleEntry private createDexKitBridge returns a raw value only to synchronous early initialization under the shared native gate; this is not an external compatibility getter.

Validation: scripts/test_dex_query_consumers.py (real Java/Kotlin DexKit ABI, managed holder with no native generation, compatibility source contracts), test_internal_dex_scopes.py, test_dex_bridge_manager.py (including repeated raw pin and idle close exclusion), test_dex_detached_results.py, test_dex_finder_gates.py, test_agent_external_idle.py, audit_dex_entries.py all exit 0. Evidence: /tmp/pl-dex-compat-20261101-resume2/. Audit output counts candidates; audit exit 0 alone is not proof of lifecycle safety.

Historical evidence preserved: /tmp/pl-resource-regression-final-83/DONE.json (83 passed, sourceStable), /tmp/pl-resource-final83-verified.json (83 unique, listMatch, no source drift), /tmp/pl-api-sdk.final.exit (0), and the original failed resume snapshot. These belong to their historical source snapshots and are not a fresh full regression of this stage.

Actions build jobs for dfadf36 (41277668937) and 42eeca9 (41281456669) succeeded. This stage still requires its own Actions build. No device acceptance has been performed. The 109-feature audit and remaining feature gaps are open. Enable idle recycling only after the remaining work and final device acceptance.
