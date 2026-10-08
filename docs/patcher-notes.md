# cavla / morphe-patcher notes (shared)

## Toolchain (this device, Termux aarch64)
- morphe plugin **1.3.4**, morphe-patcher **1.15.1** (from template). Build via `scripts/build.sh`.
- **aapt2:** SDK build-tools 34.0.4 aapt2 (2.19) CANNOT parse android-36; build-tools 36.0.0 aapt2 is a non-runnable stock ELF. Use Termux `aapt2` (2.20): `-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2` (baked into scripts/build.sh). The global ~/.gradle/gradle.properties override (34.0.4) outranks the project one, so the -P flag is required.
- Force `_JAVA_OPTIONS=-Xmx1536m` (the profile sets 1024m, which makes gradle fork a daemon that fails to connect under `--no-daemon`).

## DOM API in patcher 1.15.1 (IMPORTANT for resource patches)
- `document("AndroidManifest.xml")` returns `app.morphe.patcher.util.Document`, which **implements `org.w3c.dom.Document` + `Closeable`**. Use it with `.use { document -> ... }`.
- The `app.morphe.util.getNode(...)` / `app.morphe.util.asSequence(...)` helpers used by hoo-dles/official patches **DO NOT EXIST in 1.15.1** → `Unresolved reference 'util'`. Use raw DOM instead:
  - manifest root: `document.documentElement`
  - by tag: `document.getElementsByTagName("application").item(0) as Element`
  - iterate NodeList: `(0 until length).mapNotNull { item(it) as? Element }`
- `resourcePatch { ... finalize { ... } }`, `execute { }`, `stringOption`, `booleanOption` all exist in 1.15.1.

## Universal patches
- Omit `compatibleWith(...)` → patch offered for every app. Used by the manifest family under `app.cavla.patches.all.manifest.*`.

## Patch inventory (universal / all)
- `all/manifest/packagename/ChangePackageNamePatch` — appends `.cavla` to package (side-by-side install). Options: packageName, updatePermissions, updateProviders. For side-by-side of apps with content providers (likely Wolt), enable **updateProviders** (and often updatePermissions) at apply time or install fails on duplicate authorities.
- `all/manifest/debug/EnableDebugPatch` — sets `android:debuggable=true`.

## Releasing a bundle Morphe Manager can load (IMPORTANT)
Morphe Manager runs on Android and loads patches from **`classes.dex`** inside the `.mpp`.
A dexless bundle shows **"Unnamed / Metadata N/A"** and won't apply.
- `buildAndroid` only merges `classes.dex` on a **clean** build (incremental builds skip it).
- `generatePatchesList` rebuilds the `.mpp` **without** `classes.dex`.
Always produce release artifacts with `scripts/release.sh` (clean -> buildAndroid -> stash
dex .mpp -> generatePatchesList -> restore dex .mpp). Verify: the `.mpp` must contain `classes.dex`.
Add a source in Morphe Manager with the **repo URL** (`https://github.com/<owner>/<repo>`),
not the `.mpp` asset URL — Manager resolves the latest release itself.
