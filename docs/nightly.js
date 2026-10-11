// Keep an installable, verified release URL in the HTML as the no-JS/offline fallback.
// Nightlies are prereleases, so /releases/latest does not find them.
(async () => {
  try {
    const response = await fetch("https://api.github.com/repos/cascadiacollections/shoutkit-android/releases?per_page=10", { headers: { Accept: "application/vnd.github+json" } });
    if (!response.ok) return;
    const releases = await response.json();
    if (!Array.isArray(releases)) return;
    const release = releases.find(release => !release.draft && Array.isArray(release.assets) && release.assets.some(asset => /^(shoutkit|sir)-android-nightly\.apk$/.test(asset.name)));
    if (!release) return;
    const asset = release.assets.find(asset => /^(shoutkit|sir)-android-nightly\.apk$/.test(asset.name));
    const url = new URL(asset.browser_download_url);
    if (url.origin !== "https://github.com" || !url.pathname.startsWith("/cascadiacollections/shoutkit-android/releases/download/")) return;
    document.querySelectorAll("[data-nightly-download]").forEach(link => { link.href = url.href; });
    const notes = new URL(release.html_url);
    if (notes.origin === "https://github.com" && notes.pathname.startsWith("/cascadiacollections/shoutkit-android/releases/tag/")) {
      document.querySelectorAll("[data-nightly-notes]").forEach(link => { link.href = notes.href; });
    }
  } catch {
    // Rate limits and network failures preserve the known-good HTML download link.
  }
})();
