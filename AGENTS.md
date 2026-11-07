# LearnThenWatch repository instructions

- This repository contains one Android TV app (`app`), written in Java. Product: 学后看 / LearnThenWatch. Root directory name is arbitrary.
- Production application ID is `dev.learnthenwatch.tv`; debug adds `.debug`. Java namespace is `dev.learnthenwatch.tv`. Use the actual application ID for own-package checks and test broadcasts.
- Read `docs/BUILDING.md` for build/release details. Baseline checks: `bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (Windows: `gradlew.bat`) and `python tools/validate_curriculum.py`.
- For installing an existing release on a TV, follow `docs/DEPLOYMENT-AGENT.md`. Require an explicit device and real repository or supplied release. Do not install third-party players.
- Learning and viewing state is written only through `LearningRepository`. Preserve reward idempotence, first-answer records, review scheduling and confirmed-playback accounting.
- Keep TV D-pad focus, Back history, PIN protection and offline audio behavior intact. UI changes require the relevant isolated on-device checks and visual inspection.
- Device tests and screenshots use disposable records; preserve production data, installed apps and unrelated accessibility services. Run device tools sequentially on an awake TV, and restore temporary system changes.
- Never commit or print signing secrets, PINs or private device data. Release signing files live outside the repository. APKs and generated outputs remain ignored.
- Update CHANGELOG and relevant docs for behavior changes. Preserve third-party notices and per-sentence attribution when modifying content. Original project code/docs use MIT; third-party material retains its own license.
