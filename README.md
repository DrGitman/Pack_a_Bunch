# Pack-a-Bunch website

React + JavaScript + Vite implementation of the approved Figma Website Design frames. Uses the original logo, Plus Jakarta Sans font, two supplied app screenshots, and final warm brand accents. The APK download section is the requested addition to the design.

## Run

Node 22.12+ recommended.

```sh
npm install
npm run dev
npm run build
npm run preview
```

On this computer the global npm shortcut was broken; `& 'C:/Program Files/nodejs/npm.cmd' run dev` works in PowerShell.

## Judge download

`public/downloads/pack-a-bunch-preview.apk` is the existing signed staging build supplied by the Android project, version 0.1.0-staging. It requires Android 8+ and supports arm64-v8a / x86_64. It is not a new APK build, and the website checks do not prove the app’s scanning works on a real phone.

The file is included locally and in `dist` when built, but excluded from Git because it exceeds normal Git file limits. Before a Git-based deployment, upload the APK to your release/file storage and update `src/release.json` (URL, size, version and checksum), or provide the file to the hosting build. A direct deployment of the local `dist` includes it. Check your host’s individual file-size limit before uploading. Do not deploy a Git checkout without the APK or a valid external download URL.

## Support and information

Support is configured as `support@packabunch.site` in `src/config.js`. Activate that mailbox before publishing. Information dialogs are functional, with preview-specific text. Account deletion includes the in-app instructions and an email support link; no automated web request backend is connected. Before public launch, provide reviewed full app policies and verify the external deletion-request process. No email is sent automatically by this static site.

## Motion and accessibility

One-time 12px scroll reveals, 400ms ease-out; subtle 160ms button interactions; native anchor navigation and keyboard-accessible dialogs. Reduced motion disables movement. No animation library, tracking, autoplay or scroll hijacking. Font and imagery are served locally.

## Verify

`npm test` runs Playwright using the installed Microsoft Edge browser at 320, 390, 768, 834, 1024 and 1440 pixels. Tests cover accessibility, overflow, heading overlaps, menu/dialog keyboard behavior, animation preferences and the real APK checksum. Screenshots are in `test-results`.

The layout matches the approved frames using fluid CSS; browser font rendering can differ slightly from Figma. No claim of a pixel-identical render is made without a full image diff. Header “Get the app” points to the requested new download section.

## Sources

- [Vite guide](https://vite.dev/guide/)
- [React useEffect](https://react.dev/reference/react/useEffect)
- [Intersection Observer](https://developer.mozilla.org/en-US/docs/Web/API/Intersection_Observer_API)
