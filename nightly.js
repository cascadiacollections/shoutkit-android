// Points the download buttons at the best signed APK on GitHub Releases.
//
// The published HTML links to a nightly: the nightly workflow rewrites those hrefs
// to the release it just published. Here, when the API is reachable:
//   1. If the latest tagged release carries a signed APK (shoutkit-android-<version>.apk,
//      attached by release.yml), the buttons and their labels switch to it.
//   2. Otherwise the buttons move to the newest nightly. Nightlies are prereleases, so
//      /releases/latest does not find them, and immutable releases cannot roll one tag
//      forward.
// Rate limits and network failures keep the published HTML link.
(async () => {
  const api = "https://api.github.com/repos/cascadiacollections/shoutkit-android/releases";
  const base = "/cascadiacollections/shoutkit-android/releases/";
  const headers = { Accept: "application/vnd.github+json" };
  const point = (release, asset) => {
    const url = new URL(asset.browser_download_url);
    if (url.origin !== "https://github.com" || !url.pathname.startsWith(base + "download/")) return false;
    document.querySelectorAll("[data-nightly-download]").forEach(link => { link.href = url.href; });
    const notes = new URL(release.html_url);
    if (notes.origin === "https://github.com" && notes.pathname.startsWith(base + "tag/")) {
      document.querySelectorAll("[data-nightly-notes]").forEach(link => { link.href = notes.href; });
    }
    return true;
  };
  try {
    const response = await fetch(api + "/latest", { headers });
    if (response.ok) {
      const release = await response.json();
      const stable = /^shoutkit-android-(\d+\.\d+\.\d+)\.apk$/;
      const asset = !release.draft && !release.prerelease && Array.isArray(release.assets)
        ? release.assets.find(asset => stable.test(asset.name))
        : undefined;
      if (asset && point(release, asset)) {
        const version = stable.exec(asset.name)[1];
        document.querySelectorAll("[data-release-text]").forEach(el => {
          el.textContent = el.dataset.releaseText.replace("{version}", version);
        });
        return;
      }
    }
  } catch {
    // Fall through to the nightly lookup.
  }
  try {
    const response = await fetch(api + "?per_page=10", { headers });
    if (!response.ok) return;
    const releases = await response.json();
    if (!Array.isArray(releases)) return;
    const nightly = /^(shoutkit|sir)-android-nightly\.apk$/;
    const release = releases.find(release => !release.draft && Array.isArray(release.assets) && release.assets.some(asset => nightly.test(asset.name)));
    if (release) point(release, release.assets.find(asset => nightly.test(asset.name)));
  } catch {
    // Rate limits and network failures preserve the known-good HTML download link.
  }
})();
