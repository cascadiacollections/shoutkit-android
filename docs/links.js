// Applies the SHOUTKIT_LINKS config at the top of each page.
//
// Store and support buttons ship in their "coming soon" state: an <a> with no
// href, so it is not focusable or clickable. When a URL is pasted into the
// config, the matching buttons become real links with their live label, and
// anything marked data-hide-until-live is revealed. Without JavaScript the
// page keeps the honest "coming soon" state.
(() => {
  "use strict";
  const links = window.SHOUTKIT_LINKS || {};
  document.querySelectorAll("[data-link]").forEach(el => {
    const url = links[el.dataset.link];
    if (typeof url !== "string" || !/^https:\/\//.test(url)) return;
    if (el.tagName === "A") {
      el.href = url;
      el.removeAttribute("aria-disabled");
      el.classList.remove("is-soon");
      if (el.dataset.live) el.textContent = el.dataset.live;
    }
    if (el.hasAttribute("data-hide-until-live")) el.hidden = false;
  });
})();
