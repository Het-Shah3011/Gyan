# GitHub releases and app updates

GYAN checks `https://github.com/Het-Shah3011/Gyan/releases.atom` for the latest
release. This is GitHub's public Atom feed: the installed app uses a normal
HTTPS request, with no API token, analytics SDK, or GYAN server. A newer release
opens its GitHub page; downloading the correct APK and confirming Android's
installer remain under the user's control.

## Publish a signed update

Android only accepts an in-place update when the new APK has the same signing
certificate as the installed app. Keep the release keystore private and back it
up securely. Add these repository **Actions secrets** before publishing:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release `.jks` keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Update `versionCode` and `versionName` in `app/build.gradle.kts`, commit the
change, then push a tag matching the version, for example `v1.2.0`. The workflow
builds signed Play and Samsung APKs, attaches them to a GitHub Release named for
the tag, and provides release notes. The GYAN in-app update checker finds that
release through the public Atom feed.

The release workflow uses GitHub Actions' built-in `GITHUB_TOKEN` to publish
assets to this repository. You do not create or maintain a personal API token.

Do not publish unsigned APKs or change the release key: users will not be able
to install them over their existing copy.

## Contact form card

When your Google Form is ready, paste its public responder URL into
`GYAN_CONTACT_FORM_URL` in `app/src/main/java/com/gyan/app/ui/ContactUsCard.kt`.
The button shows a setup hint until this value is filled in.
