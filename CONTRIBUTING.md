# Contributing

By the time being, this file contains important notes about Android Development
and how this project organizes some of its workflow.

### Log (de)obfuscation

Crash logs are obfuscated on release APK's. Final users are provided with a
gracious crash landing page (sad puppy) to share the logs:

[Example Picture]

The following command uses `mappings.txt` to return de-obfuscated logs:

> [!IMPORTANT]
> To generate a mapping file, you must build a release apk under its respective git tag.
>
> In other words, each release has a unique mapping.txt

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/retrace app/build/outputs/mapping/tempusRelease/mapping.txt stack_error_transcript.txt 
```

Now the final users don't need to install debug apk's to generate readable logs.

### Pull PR's from GitHub

> [!NOTE]
> Source: https://stackoverflow.com/a/30584951

Forking allows to have a copy of the repo and submit new PR's, however
to review existent PR's we have to pull them locally.

To ease that chore, add upstream as a new remote:

```bash
git remote add upstream git@github.com:eddyizm/tempus.git
```

Then pull the PR from it into a local branch:
```bash
git fetch upstream pull/$ID/head:pr-$ID
```

Now you can checkout to the PR locally:
```bash
git checkout pr-$ID
```