// Applies the SHOUTKIT_LINKS config at the top of each page.
//
// Every element marked data-link="<channel>" ships hidden. When that channel has
// a confirmed https:// URL in the config, the element is revealed and, if it is
// an <a>, pointed at the URL. A channel with no URL never appears, so the page
// has no dead or "coming soon" buttons, with or without JavaScript.
(() => {
  "use strict";
  const links = window.SHOUTKIT_LINKS || {};
  document.querySelectorAll("[data-link]").forEach(el => {
    const url = links[el.dataset.link];
    if (typeof url !== "string" || !/^https:\/\//.test(url)) return;
    if (el.tagName === "A") el.href = url;
    el.hidden = false;
  });
})();
