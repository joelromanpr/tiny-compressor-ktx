# Contributing

Thanks for helping improve Tiny Compressor KTX. Bug reports with a small input image (or a reproducible generator), Android version, options, expected result, and actual result are especially useful. Remove private EXIF and GPS data before attaching an image publicly.

## Make a change

1. Create a branch from `main` and keep the patch focused.
2. Add or update a test for a behavior change. For image bugs, cover the output's decoded dimensions, orientation, format, alpha, and/or final byte count as appropriate.
3. Update the README or demo if you change public behavior.
4. Run the local checks and include their result in the pull request:

```bash
./scripts/prepare_for_pr.sh
```

This checks Spotless formatting, library debug unit tests, and the demo debug build. Run `./scripts/prepare_for_pr.sh --fix` to apply formatting before those checks. Pass Gradle flags after `--`, for example `./scripts/prepare_for_pr.sh -- --stacktrace`. An Android SDK and JDK 17 are needed for the build.

Device behavior such as EXIF orientation and output decoding needs an Android device or emulator check in addition to JVM tests. Note the tested API level in the pull request.

## Commit messages and versions

[Conventional Commits](https://www.conventionalcommits.org/) help readers follow changes. Examples:

- `fix: preserve portrait orientation after JPEG compression`
- `feat: add a new output format`
- `docs: clarify EXIF privacy behavior`

Releases follow semantic versioning: patches fix compatible behavior, minor versions add compatible features, and major versions allow incompatible API changes. Mark an incompatible change with `!` in the commit subject and explain it in the pull request.

## Maintainer release checklist

1. Choose a new version and update `VERSION_NAME` in the root `gradle.properties`. Do not reuse a version already sent to Maven Central.
2. Run the checks above and review the README installation version and release notes.
3. Merge the validated release commit to `main`.
4. Create and push an annotated `vX.Y.Z` tag on that exact commit. The tag-triggered GitHub Actions release job checks version parity and the `main` commit, runs its test gates (including an API 30 emulator), then publishes through the protected environment.
5. Verify the artifact and POM on Maven Central before announcing availability or updating the README's release status.

Publishing needs the maintainer's Maven Central credentials and signing key stored in the GitHub environment. Pull requests do not publish.

## License

By contributing, you agree that your contribution is licensed under this repository's [MIT License](LICENSE).
