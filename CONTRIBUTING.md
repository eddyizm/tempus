# Contributing

## Android cheat-sheet

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

The tools may be elsewhere, depending on which version of android studio. I found my `retrace` here:
```bash
$ANDROID_HOME/cmdline-tools/tools/bin/retrace
```

Now the final users don't need to install debug apk's to generate readable logs.

## GitHub cheat-sheet

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


## PR submission guidelines

This is a small step-by-step about how to submit contributions to the repository in the form of PR's. It is a very simple set of rules that you've most likely stumbled across if you are a contributor or read other people contributions on open source programs.

If you have had your PR's accepted in the past you'll be fine. If you rely on an LLM to submit contributions, be wary that it will skip and ignore most of this process and that you'll have to do manual steps to follow the rules.

#### 1. Use the app

This is by far the most ignored rule on most PR's rejected here, but we've been on this situation so many times that we will have to state the obvious as an actual rule. **Start by using the app**.

Nobody magically finds out that line 764 on file `/some/very/deep/and/intricate/path` is calling `myMethodLetterA()` instead of `myMethodLetterB` and calls it eureka.

#### 2. Check the issue tracker

After you used the app you'll notice something feels off. Perhaps a button doesn't work or you expect something to behave differently. Most of the time you'll be right on this, Android apps follow a pattern of behavior and you'll notice right away when this is not followed. This branches in two ways:

* There's a bug. The app is actually broken and you managed to trigger the bug.
* There's a lack of functionality. The apps doesn't provide something you've seen in another app. You know it can be done.

The first thing you have to think about is that you may not be the first one noticing this. The issue tracker is where all of these discoveries are centralized and discussed. Someone may have brought the topic before, and even a dev may have stepped in and explained why it is the way it is.

If you were actually the first one noticing it, open an issue. We have templates for each common scenario. Templates standardize people's experiences in a set of sections we can easily review without burning out each party with follow-up questions.

It is important to have your contribution baked by an issue, even if you plan on opening the PR yourself, because we might mark it as `won't fix` and not make it eligible for completion. There's also a chance that you'll be requested to split your issue in separated ones to keep the scope small and the diff legible.

It is really hard to one-shot the process and use the PR comment as an issue (problem+solution in one go), you must really nail it on the problem and that requires to be either a die hard contributor or a diehard user. You still can't break rule 1. It's all about experience, gut feeling and risking that your hard work gets rejected.

The safe path is using the issue tracker, we're always open to discuss changes on the app.

#### 3. Fork & Branch

This is obvious, or else you wouldn't be able to open a PR in the first place, but for the love of Zeus don't make this your first step!

Even if you already have the repository forked, this is not by any means the step 1.

If this is your first step, you're PR will be rejected as you are looking for a problem while writing a solution.

#### 4. Type what you did with a keyboard

Q: *But with what else could I write it with?* 

A: An LLM.

If you ask an LLM to do a summary of what you did, it will give you a summary of what you just did. You moved around some functions, created some files and added some comments. If you left the PR comment blank instead it would have the same effect on the review process: rejected for explaining nothing.

Now if you let an LLM do a summary of what you *tried* to do, that is different. You'll be using the LLM to proxy what you typed in your own words. We want explanations, to hear the reasons or limitations of your solution. Your PR will be linked on the changelog and that explanation will reach the user base for them to check what was the idea behind it.

You'll notice that it is really inhuman to write by hand *"when `methodByGone()` has reached `topLimit` under the `myFavFile.kt` it while-loops on the `stretcherFork` on a race condition metastasis..."* There's a tab called **Files changed** on the PR, we can check this ourselves as a diff with the full context of the file.

If you try to circumvent this by writing the line-by-line explanation on the issue, it will get closed. We've been flexible about this rule, however it has been incredibly abused as we are expected to guess and find out for ourselves. We do PR reviewing, not guesswork.

---

If you don't rely on LLM's to submit contributions, you'll notice this rule set is intuitive and you are most likely following it without noticing. We all learnt by trial an error, we just don't want to allow people to take shortcuts that offload all the responsibility to the project maintainers.
