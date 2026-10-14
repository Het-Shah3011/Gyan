# Updating GYAN ads

The app reads `ads.json` from the public `main` branch. Once a release containing the ad loader is installed, changing an ad does not need another APK build or a GitHub API token.

1. Upload the ad image to `ads/` in this repository.
2. Edit `ads.json`: set `enabled` to `true`, then update `title`, `description`, `imageUrl`, `link`, and `buttonText`.
3. Commit the image and JSON change to `main`. The app refreshes the feed at most once every 30 minutes and cycles enabled ads by day.

Use public HTTPS image and destination links. Keep `ads.json` and the images publicly readable; the app intentionally uses anonymous raw GitHub URLs and has no embedded token. An empty or invalid feed hides the sponsored card and leaves the existing “Advertise with us” contact card available.

The initial template in `ads.json` is disabled until real advertiser details and an image are added.
