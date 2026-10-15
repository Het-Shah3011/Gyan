# Updating GYAN ads

The app reads `ads.json` from the public `main` branch. Once a release containing the ad loader is installed, changing an ad does not need another APK build or a GitHub API token.

1. Upload the advertiser's image to `ads/` in this repository. Use a new filename for each replacement (for example, `ads/cafe-october.png`) so phone and GitHub caches do not keep showing the old image.
2. Edit `ads.json`: update `title`, `description`, `imageUrl`, `link`, and `buttonText`. Set `enabled` to `true` to show the ad or `false` to hide it. `imageUrl` should be the public raw GitHub URL for the uploaded image.
3. Commit the image and JSON change to the public `main` branch. The app refreshes the feed at most once every 30 minutes while running; no new APK or GitHub API token is needed.

Use public HTTPS image and destination links. Keep `ads.json` and the images publicly readable; the app intentionally uses anonymous raw GitHub URLs and has no embedded token. An empty or invalid feed hides the sponsored card and leaves the existing “Advertise with us” contact card available.

The current sample ad uses `ads/gyan-banner.png` and links to the latest GYAN release. Replace those details with an advertiser's image and destination when ready. Keep the separate “Advertise with us” contact card for enquiries.
