# AssaultCube iOS port plan

This document records the repository analysis and the concrete porting plan for turning the OUYA Android port into a native iPhone app targeted at ARM64 iOS device builds.

## Repository assessment

The repository under `lemonaliv-arch/assaultcube-ouya` is a fork of the original OUYA work. Its `README.md` and `OUYA_PORT.md` document a successful Android port that:

- targets OUYA / Tegra 3 / Android 4.1 API 16
- uses SDL2, SDL2_image, gl4es, OpenAL, Ogg, Vorbis, and a custom asset export flow
- runs the AssaultCube engine in a GLES2-only environment
- uses a dedicated Android app layer and native dependency build chain

The repo includes:

- the actual AssaultCube source tree under `source/src/`
- an Android app under `source/android/app/`
- a desktop Xcode project under `source/xcode/` that is not an iOS app target and is not suitable for modern iPhone deployment
- a bundled asset tree (`config/`, `packages/`, `bot/`, etc.) that must be included in any iOS app bundle or sandboxed writable area

## Key technical findings

### 1. Android-specific layer must be replaced

The Android app layer includes:

- `source/android/app/src/main/AndroidManifest.xml`
- Java/Kotlin app classes under `source/android/app/src/main/java/`
- SDL Android lifecycle integration under `org/libsdl/`
- a CMake build that imports Android-specific native libraries and `__ANDROID__` definitions
- asset extraction and launch logic for the OUYA console

These responsibilities are Android-only and cannot be reused unchanged for iOS. They must be replaced by an iOS app entry point using SDL2 iOS support and platform-specific app lifecycle handling.

### 2. The renderer is gl4es-based and is the biggest risk

The OUYA port explicitly relies on `gl4es` to translate desktop OpenGL calls to GLES2. `source/android/app/src/main/cpp/CMakeLists.txt` imports a static `libGL.a` from gl4es and builds the engine around it. This is valid on Android with GLES2, but it is not a drop-in iOS path.

Important caveat: gl4es does not have a documented official iOS/EAGL backend. That means the outcome is not "just enable SDL2 on iOS"; the project must verify that the engine can render correctly in an iOS OpenGL ES context, or the project must adopt a different render strategy that preserves the game engine behavior.

This is the main technical gate before any touch controls or packaging work can be considered reliable.

### 3. Native dependencies are not included in the repository

`OUYA_PORT.md` states that the actual native libraries were cross-compiled externally and not committed to the project. The Android CMake file imports generated libs for:

- SDL2
- SDL2_image
- OpenAL Soft
- gl4es
- libogg
- libvorbis
- libvorbisfile
- ENet

For iOS, the same dependency stack must be rebuilt with reproducible upstream versions using a proper iOS ARM64 toolchain.

### 4. The desktop Xcode project is not the right target

The existing `source/xcode` project is a Cocoa desktop app for the Mac. It is not a modern iOS app target and is not configured for:

- `arm64-apple-ios`
- `UIWindow` / `UIViewController` app lifecycle
- launch via `SDLUIKit` iOS support
- landscape-only iPhone orientation
- unsigned device IPA generation
- GitHub-hosted CI on macOS runners

### 5. Asset packaging is a real requirement

The project ships game assets under `config/`, `packages/`, and game content directories. The Android port precomputes asset lists and stages assets for export. iOS must package or extract those into app storage in a writable sandbox location, without relying on any Android export process.

## Implementation strategy

### Phase 1: repository and dependency audit

- lock the branch to `ios/assaultcube-port`
- inventory all Android-specific code paths and remove them from the iOS target plan
- confirm the upstream dependency versions and build flags for SDL2, OpenAL, libogg, libvorbis, and gl4es
- confirm the engine entry point and asset locations under the source tree

### Phase 2: create the native iOS app skeleton

- create a proper iOS Xcode project or workspace
- configure bundle identifier and Info.plist
- add SDL2 iOS app lifecycle integration
- set landscape-only orientation and foreground/background handling
- build a device target for `arm64-apple-ios`
- ensure the app can run the SDL main loop without Android-specific Java/JNI

### Phase 3: native dependency build for iOS ARM64

- build SDL2 and SDL2_image for `arm64-apple-ios`
- build OpenAL Soft, libogg, libvorbis, and libvorbisfile
- evaluate gl4es compatibility with iOS and decide whether it is viable
- if gl4es is incompatible, implement a controlled fallback strategy that preserves functionality without claiming unsupported Metal backend emulation

### Phase 4: rendering validation

- configure the correct iOS GLES context and framebuffer setup via SDL
- verify the actual 3D AssaultCube menu and map render on device-arm64 build
- investigate shader, FBO, texture, and depth-buffer issues
- only proceed once a real rendered frame is confirmed, not merely a linked executable

### Phase 5: input port

- restore or implement left-stick movement and right-area look controls
- add fire, jump, crouch, reload, weapon switching, scope, and menu actions
- support multi-touch correctly with simultaneous movement and aiming
- preserve SDL GameController support for Xbox/MFi pads

### Phase 6: assets, audio, and gameplay

- package required game assets into the app bundle or writable sandbox
- restore sound playback and server/browser compatibility
- validate offline gameplay, bots, and multiplayer DNS/socket networking
- confirm server compatibility with the official AssaultCube ecosystem

### Phase 7: CI packaging

- add a GitHub Actions workflow using a supported macOS runner label such as `xcode-27`
- verify Xcode version and installed tools
- compile arm64 device target with code signing disabled
- package an unsigned `.ipa` from a valid `Payload/<Application>.app` tree
- upload the artifact for download

### Phase 8: verification and reporting

- verify the binary is ARM64 iOS device code
- verify required frameworks and game assets are packaged
- verify no Android-only libs remain linked
- distinguish successful compile from game startup, installability, and real gameplay

## Current assessment

The repo is a good base for a native render/game port, but it is not yet a valid iOS app. The required changes are substantial and specifically center on:

- replacing Android app lifecycle and storage behavior
- verifying gl4es viability on iOS or replacing the render compatibility layer with a supported equivalent
- rebuilding the native dependency stack for arm64-apple-ios
- implementing a proper haptic / touch FPS control layer for iPhone
- producing working CI packaging without Apple Developer credentials

This means the project is feasible, but it must be treated as an engineering port with proof gates rather than a direct code reuse.

## Known blockers to avoid pretending the port is done

- gl4es is not a ready-to-use iOS backend
- the existing `source/xcode` project is a Mac desktop app, not an iPhone app
- the required native libs are not checked in and must be built for the target platform
- asset packaging and writable filesystem rules differ from Android
- an unsigned IPA is not proof of successful gameplay on device

This document is intentionally conservative. It reflects the evidence in the repository and does not claim a completed iOS deployment.
