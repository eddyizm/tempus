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