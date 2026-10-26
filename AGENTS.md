# OdinDuo project instructions

## Local and cloud workflow

The shared source repository is `https://github.com/Tufein/OdinDuo.git`.
The user wants changes made with Codex to be available promptly for continued
work in the cloud. Committing and pushing task changes to this repository is
authorized; do not ask for that authorization again unless the user changes it.

- Before editing, check the working tree, current branch and remote URLs, then
  fetch `origin`. Preserve existing user changes. Verify that the fetch and push
  destinations are `Tufein/OdinDuo` before publishing.
- Continue existing work on its branch. Fast-forward a clean branch when its
  remote contains newer work. For a new task on `main` or a detached checkout,
  create a descriptive `codex/` branch from the appropriate current remote base.
  Fetch work completed in the cloud before continuing it locally.
- Commit each coherent, checked set of task changes and push it promptly to
  the same branch on `origin`, setting its upstream on the first push. Always
  publish completed changes before the final response. If handing off unfinished
  work, publish a clearly described checkpoint and state which checks remain.
- Stage only files belonging to the task. Existing untracked files are not
  automatically part of a task. Keep signing keys, credentials, device captures
  and generated APK/build output out of Git, as documented in `.gitignore`.
- Respect protected `main`: open or update a pull request for the work branch.
  Follow its required CI checks. This standing authorization covers commits,
  pushes and PRs, not automatic merges or release publication.
- If a push is rejected because the remote advanced, fetch and reconcile both
  histories while preserving other work, rerun relevant checks, and retry.
  Never force-push or discard user changes to make synchronization succeed.
- Verify the remote branch points to the published commit. Report the repository,
  branch and commit in the handoff; report an authentication/network failure
  explicitly if synchronization could not finish.

GitHub is the handoff between local and cloud checkouts. In the cloud, fetch and
continue the reported branch in the environment connected to `Tufein/OdinDuo`.
A push does not refresh the filesystem of an already running cloud task.

For the user's requested direct file-transfer route, follow
`docs/direct-cloud-sync.md`. GitHub publication alone does not satisfy direct
sync. Report whether the Mac endpoint, VPN and actual cloud client were verified;
never claim that direct sync is active after testing loopback only. Pause the
sync client before changing Git branches or resetting either checkout.

## Validation

Use the checks appropriate to the change and the commands documented in
`README.md`. App/code changes normally require `./tools/check.sh` and the
relevant Gradle build, lint and UI tests. Documentation-only changes need a
diff and whitespace check, not a new Android build. Describe checks actually
performed and distinguish host/emulator results from physical Odin validation.
