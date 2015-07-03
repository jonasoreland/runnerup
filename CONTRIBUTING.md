# Contributing guidelines

Contributions are very welcome. RunnerUp is open source software licensed under the [GPL](LICENCE).

## Development

### Code style

Code should follow the [Android Code Style Guidelines](https://source.android.com/source/code-style.html).

### Tests

Unfortunately there are no tests in this project currently.

### Previewing changes

Changes can be previewed using an emulator.

### IDE

You may wish to consider [Eclipse](http://www.eclipse.org/), [Android Studio](https://developer.android.com/sdk/installing/studio.html) or [NetBeans IDE](https://netbeans.org/).

To build the app using Android Studio, see [this guide](Documentation/howto-build-with-android-studio.txt).

## Making changes

1. Fork it ( https://github.com/jonasoreland/runnerup/fork )
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request

## Where to start

- Look at the [TODO](TODO) list
- Write some tests
- Help translating at https://www.transifex.com/organization/runner-up/dashboard

## How to succeed in getting your Pull Request (PR) merged

1. Each PR should contain one logical change
2. Each PR should either contain 100% new code/features or be small enough so that it can be review quite quickly
3. If a PR gets to big, it should be split into several PRs, where e.g the first ones are refactorings needed later
4. Be sure only to modify lines you actually change. Keep reformattings and similar in separate PR.
