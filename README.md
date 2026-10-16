# SpacemiT Policy Service

A platform‑signed, privileged app that hosts the bound `ISpacemitPolicyManager`
and applies SpacemiT device policies. This is the enforcement side of the
device‑policy framework; the API lives in
`vendor/spacemit/framework/policy-sdk`.

## What it does

- Publishes a bound service on action `com.spacemit.policy.action.BIND`, guarded
  by `com.spacemit.policy.permission.MANAGE_DEVICE_POLICY` (`signature|privileged`).
- Enforces policies:
  - **vendor‑policy keys** → `Settings.Global` under `spacemit_policy_<key>`,
  - **framework settings** → `Settings.{Global,Secure,System}`.
- Persists every applied value in device‑protected prefs and re‑asserts them on
  `BOOT_COMPLETED` (`reapplyAll`).
- `getAppliedPolicies()` returns the applied set for compliance reporting.

Every mutating call runs `enforceCallingOrSelfPermission(MANAGE_DEVICE_POLICY)`;
keys are validated against `[A-Za-z0-9_.-]{1,64}`.

## Why a privileged app (not a system_server service)

Chosen for portability: no `frameworks/base` fork, and external apps can bind to
it. It is platform‑signed, so it holds `MANAGE_DEVICE_POLICY` and
`WRITE_SECURE_SETTINGS` by signature — no privapp allowlist or custom SELinux is
needed for the current policies.

## Extending it

- A policy value that a **native/HAL** component must read should be backed by a
  real vendor property instead of `Settings.Global`: add a `vendor_*_prop` type +
  a vendor‑domain setter (same pattern as `persist.vendor.llm.*` in this tree).
  A system/platform app cannot set `persist.vendor.*` directly (Treble).
- Give the app its own SELinux domain (seinfo + `mac_permissions`) when it grows
  beyond `platform_app` privileges.

## Build

Opt‑in: `SPACEMIT_DEVICE_POLICY=true` (see `device/spacemit/common/spacemit-features.mk`).
Installs to `/system/priv-app/SpacemitPolicyService`.
