## How to Contribute

We'd love to accept your patches and contributions to this project. There are just a few small guidelines you need to
follow.

---
## Preparing a Pull Request for Review
Ensure your change is properly formatted and adheres to our code standards by running the following command locally:

```bash
chmod +x ./scripts/prepare_for_pr.sh
./scripts/prepare_for_pr.sh
```

### Suggested Commit Message Style (optional)

To maintain a clean and understandable history, we try to follow the **Conventional Commits** specification. This style
helps explain the **intent** of each commit and allows automated tools to generate changelogs.

A commit message ideally can be structured as follows:

$$\langle\text{type}\rangle(\langle\text{scope}\rangle)\text{!}\colon\ \langle\text{description}\rangle$$

| Type         | Purpose                                                                      | Example                                                |
|:-------------|:-----------------------------------------------------------------------------|:-------------------------------------------------------|
| **feat**     | A **new feature** for the user/API (maps to a `MINOR` release).              | `feat: added @OptionGroup support`                     |
| **fix**      | A bug **fix** (maps to a `PATCH` release).                                   | `fix(core): handle null pointer in Parser`             |
| **docs**     | Documentation only changes.                                                  | `docs: update contribution guide with commit style`    |
| **style**    | Formatting fixes, white-space, missing semicolons, etc. (no code change).    | `style: format Kotlin files with Spotless`             |
| **refactor** | A code change that neither fixes a bug nor adds a feature.                   | `refactor(Parser): extract common logic to base class` |
| **test**     | Adding missing tests or correcting existing tests.                           | `test(core): add unit test for Parser`                 |
| **build**    | Changes that affect the build system or external dependencies (Gradle, npm). | `build: update Kotlin version to 1.9.0`                |
| **ci**       | Changes to our CI configuration files and scripts.                           | `ci: set up basic GitHub Actions workflow`             |

**Note on Breaking Changes:** If your change is a major, backward-incompatible API change, include an exclamation mark *
*`!`** before the colon to signal a **BREAKING CHANGE** (e.g., `feat(Parser)!: remove old configuration method`).




