# wallabag - Android App [![Build Status](https://travis-ci.org/wallabag/android-app.svg?branch=master)](https://travis-ci.org/wallabag/android-app)

<img src="/readme/wallabag logo.png" align="left"
width="200" hspace="10" vspace="10">

wallabag is a self-hosted read-it-later app.  
Unlike other services, wallabag is free and open source.  
wallabag for Android is a companion app for [wallabag](https://www.wallabag.org). You need a wallabag account first, which you are going to use in this app.

wallabag is available on the Google Play Store and F-Droid.

<p align="left">
<a href="https://play.google.com/store/apps/details?id=fr.gaulupeau.apps.InThePoche">
    <img alt="Get it on Google Play"
        height="80"
        src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
</a>  
<a href="https://f-droid.org/app/fr.gaulupeau.apps.InThePoche">
    <img alt="Get it on F-Droid"
        height="80"
        src="https://f-droid.org/badge/get-it-on.png" />
        </a>
        </p>

## About

wallabag has been made for you to comfortably read and archive your articles.
You can download wallabag from wallabag.org and follow the instructions to install it on your own server.
Alternatively, you can directly sign up for [wallabag.it](https://wallabag.it) or [Framabag](https://framabag.org).

This android application allows you to put a link in your wallabag instance, letting you read your wallabag links offline.

This application was originally created by Jonathan GAULUPEAU and released under the GNU GPLv3.
wallabag is a creation from Nicolas Lœuillet released under the MIT License (Expat License).

## Features

The android app lets you:
- Connect to your self-hosted wallabag instance or connect to your [wallabag.it](https://wallabag.it) or [Framabag](https://framabag.org) account.
- Supports wallabag 2.0 and higher.
- Completely ad-free.
- Increase and decrease the size of the font and also switch between a serif or sans-serif font for a more comfortable reading experience.
- Switch between numerous themes.
- Possibility to cache images locally for offline reading.
- Get articles read via Text-to-Speech feature.
- Needs no special permissions on Android 6.0+.

## Screenshots

[<img src="/readme/Wallabag%20Reading%20List.png" align="left"
width="200"
    hspace="10" vspace="10">](/readme/Wallabag%20Reading%20List.png)
[<img src="/readme/Wallabag%20Article%20View.png" align="center"
width="200"
    hspace="10" vspace="10">](/readme/Wallabag%20Article%20View.png)

## Permissions

On Android versions prior to Android 6.0, wallabag requires the following permissions:
- Full Network Access.
- View Network Connections.
- Run at startup.
- Read and write access to external storage.

The "Run at startup" permission is only used if Auto-Sync feature is enabled and is not utilised otherwise. The network access permissions are made use of for downloading content. The external storage permission is used to cache article images for viewing offline.

## Contributing

wallabag app is a free and open source project developed by volunteers. Any contributions are welcome. Here are a few ways you can help:
 * [Report bugs and make suggestions.](https://github.com/wallabag/android-app/issues)
 * [Translate the app](https://hosted.weblate.org/projects/wallabag/android-app/) (you don't have to create an account).
 * Write some code. Please follow the code style used in the project to make a review process faster.

## Legacy Versions of the App

We have two legacy branches, where old versions of this app can be found. Those old versions of the app are not distributed via F-Droid or Google Play. For some releases you can find a compiled version of the app, e.g. for [version 2.2.0](https://github.com/wallabag/android-app/releases/tag/2.2.0). Of course you can build a legacy version from the source. 

### Branch legacy

The [legacy](https://github.com/wallabag/android-app/tree/legacy) branch was started to keep a version, which supports wallabag server version 1.x. This branch is not maintained anymore.

### Branch legacy-older-android-5.0

The branch [legacy-older-android-5.0](https://github.com/wallabag/android-app/tree/legacy-older-android-5.0) has been started on 2019-03-06. It keeps a version of the application, which still supports Android versions below 5.0 Lollipop.

The dependency (okhttp 3.13.x++) we use in this app forced us to upgrade the API level of the app to Android 5.0 as a minimum. That is what can be found in the master branch. According to the [okhttp Readme](https://github.com/square/okhttp#requirements), the okhttp team "will backport critical fixes to the 3.12.x branch through December 31, 2020."

## License

This application is released under GNU GPLv3 (see [LICENSE](LICENSE)).
Some of the used libraries are released under different licenses.
