RunnerUp
========

[![Build Status](https://travis-ci.org/jonasoreland/runnerup.svg?branch=master)](https://travis-ci.org/jonasoreland/runnerup)

An open source run tracker for Android.

Inspiration Garmin 410, RunKeeper and Everywhere Run.

## Features

* GPL.
* Mediocre GUI.
* Highly configurable audio cues.
* Messy code (my first android project even and first Java project in +10 years).
* Automatic upload, download and feed updates from various providers including Digifit, Endomondo, Facebook, FunBeat, Garmin, GoogleFit, jogg.se, MapMyRun, Nike, RunKeeper, RunningAHEAD, Runtastic, Strava ([see here for details](SYNCHRONIZERS.md)).
* Interval wizard (ala Garmin 410).
* Download workouts from Garmin Connect.
* Great ideas for future features.

## Release
Releases can be downloaded either:

* on the [Play Store](https://play.google.com/store/apps/details?id=org.runnerup).
* on [F-Droid](https://f-droid.org/repository/browse/?fdid=org.runnerup).
* directly on [GitHub](https://github.com/jonasoreland/runnerup/releases/tag/v1.47).


## Build
Depending on your IDE, see according documentation:

* for Android Studio, please see [how to build with Android Studio](Documentation/howto-build-with-android-studio.txt).
* for NetBeans IDE, please see [how to build with NetBeans IDE](Documentation/howto-build-with-netbeans-ide.txt).
* for Eclipse, please see [how to build with Eclipse](Documentation/howto-build-with-eclipse.txt). __UPDATE: Eclipse is currently broken__.

## Dependencies
* [Ant Plugin](http://www.thisisant.com): Used to retrieve heart rate monitor data.
* [Google Play Services API](http://developer.android.com/google/play-services/index.html): Used to communicate with wear device.
* [GraphView](https://github.com/jjoe64/GraphView.git): Used to plot data in a nicely way.
* [MapBox](https://mapbox.com): Used to display map to the user.

## Contributing

Patches, forks, pull requests, suggestions or harsh flame is welcome!
Please read the [contributing guidelines](CONTRIBUTING.md).

### How can I contribute?

Thanks for asking! You can check [TODO list](TODO.md) and [open issues](https://github.com/jonasoreland/runnerup/issues). You can also work on your own wishes :-).

### Translations

<img border="0" src="https://www.transifex.com/projects/p/runner-up-android/resource/stringsxml/chart/image_png"/><br/><a target="_blank" href="https://www.transifex.com/projects/p/runner-up-android/resource/stringsxml/"><img border="0" src="https://ds0k0en9abmn1.cloudfront.net/static/charts/images/tx-logo-micro.646b0065fce6.png"/></a>

Interested in helping to translate RunnerUp? Contribute [on Transifex](https://www.transifex.com/projects/p/runner-up-android).

## License
This project is under GNU GPL v3. See [LICENSE](LICENSE.md) for more information.

## Donations
If your already donate to <a href="http://www.unhcr.org">UNHCR</a>, <a href="http://www.unicef.org/">UNICEF</a> and/or other important things, you might donate using paypal <a href="https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=runnerup%2eandroid%40gmail%2ecom&lc=US&item_name=RunnerUp&button_subtype=services&currency_code=EUR&tax_rate=25%2e000&bn=PP%2dBuyNowBF%3abtn_buynow_LG%2egif%3aNonHosted"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" alt="[paypal]" /></a>.
