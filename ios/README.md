# AssaultCube iOS bootstrap scaffold

This directory contains the first native iOS build scaffold for the AssaultCube OUYA port. It does not claim to be a complete game port or playable iPhone build.

What is here:
- a GitHub Actions workflow for `xcode-27` on a GitHub-hosted macOS runner
- a minimal iOS app target using Objective-C and a CMake build
- a device-target arm64 packaging flow that produces an unsigned `.ipa`

Why this exists:
- the project first needs a working native iOS application layer and CI target before the AssaultCube engine and dependencies can be wired in successfully
- the existing repo is Android-focused and still depends on gl4es and Android-specific app logic

This is intentionally a bootstrap step. The next work is to replace the Android app layer with SDL2 iOS lifecycle integration and then port the actual AssaultCube engine and native dependency stack.
