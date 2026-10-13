/* menu.js
   Draws the six round cards.
   It does not decide who is unlocked. progress.js does that from config.js.
*/

(function () {
  const SVG_NS = "http://www.w3.org/2000/svg";

  function starIcon() {
    const svg = document.createElementNS(SVG_NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.classList.add("star-icon");

    const polygon = document.createElementNS(SVG_NS, "polygon");
    polygon.setAttribute(
      "points",
      "12,2 15.1,8.3 22,9.3 17,14.1 18.2,21 12,17.8 5.8,21 7,14.1 2,9.3 8.9,8.3"
    );
    svg.appendChild(polygon);
    return svg;
  }

  function lockIcon() {
    const svg = document.createElementNS(SVG_NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.classList.add("lock-icon");

    const shackle = document.createElementNS(SVG_NS, "path");
    shackle.setAttribute("d", "M8 11V8a4 4 0 0 1 8 0v3");
    shackle.setAttribute("fill", "none");
    shackle.setAttribute("stroke", "currentColor");
    shackle.setAttribute("stroke-width", "2.2");
    shackle.setAttribute("stroke-linecap", "round");

    const body = document.createElementNS(SVG_NS, "rect");
    body.setAttribute("x", "5");
    body.setAttribute("y", "10.5");
    body.setAttribute("width", "14");
    body.setAttribute("height", "9.5");
    body.setAttribute("rx", "2.4");
    body.setAttribute("fill", "currentColor");

    svg.append(shackle, body);
    return svg;
  }

  function render(options) {
    const state = options.state;
    const view = document.createElement("div");
    view.className = "view menu-view";

    const hero = document.createElement("header");
    hero.className = "hero";

    const kicker = document.createElement("p");
    kicker.className = "kicker";
    kicker.textContent = "Hello!";

    const title = document.createElement("h1");
    title.textContent = "Mini Games";

    const lede = document.createElement("p");
    lede.className = "lede";
    lede.textContent = "Pick a round. Locked rounds will tell you how to open them.";

    hero.append(kicker, title, lede);

    const list = document.createElement("ul");
    list.className = "round-list";
    list.setAttribute("aria-label", "Rounds");

    const message = document.createElement("p");
    message.className = "unlock-message";
    message.id = "unlock-message";
    message.setAttribute("role", "status");
    if (options.notice) {
      message.textContent = options.notice;
      message.classList.add("is-visible");
    }

    window.GAME_CONFIG.rounds.forEach(function (round) {
      const unlocked = window.Progress.isUnlocked(state, round);
      const entry = state.rounds[round.id];
      const earned = Boolean(entry && entry.starEarned);

      const item = document.createElement("li");
      item.className = "round-slot";

      const button = document.createElement("button");
      button.type = "button";
      button.className = unlocked ? "round-card is-unlocked" : "round-card is-locked";
      button.dataset.roundId = String(round.id);
      button.dataset.unlocked = unlocked ? "true" : "false";

      const text = document.createElement("span");
      text.className = "round-card-text";

      const number = document.createElement("span");
      number.className = "round-number";
      number.textContent = "Round " + round.id;

      const name = document.createElement("span");
      name.className = "round-name";
      name.textContent = round.name;

      const stateLabel = document.createElement("span");
      stateLabel.className = "round-state";
      if (!unlocked) {
        const pill = document.createElement("span");
        pill.className = "lock-pill";
        pill.appendChild(lockIcon());
        stateLabel.appendChild(pill);
      }
      const stateText = document.createElement("span");
      stateText.textContent = unlocked ? "Unlocked" : "Locked";
      stateLabel.appendChild(stateText);

      text.append(number, name, stateLabel);

      const star = document.createElement("span");
      star.className = earned ? "star-badge is-earned" : "star-badge is-empty";
      star.appendChild(starIcon());
      const starText = document.createElement("span");
      starText.className = "visually-hidden";
      starText.textContent = earned ? "Star earned" : "No star yet";
      star.appendChild(starText);

      button.append(text, star);

      button.addEventListener("click", function () {
        if (!window.Progress.isUnlocked(state, round)) {
          const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
          if (!reduceMotion) {
            button.classList.remove("is-wiggling");
            // Restart the wiggle if the same card is tapped again.
            void button.offsetWidth;
            button.classList.add("is-wiggling");
          }
          message.textContent = window.Progress.unlockMessage(round);
          message.classList.add("is-visible");
          return;
        }
        options.onOpenRound(round.id);
      });

      item.appendChild(button);
      list.appendChild(item);
    });

    const testPanel = document.createElement("section");
    testPanel.className = "test-panel";
    testPanel.setAttribute("aria-label", "Developer testing only");

    const testHeading = document.createElement("h2");
    testHeading.textContent = "Developer testing only";

    const testHelp = document.createElement("p");
    testHelp.textContent = "Clears saved progress on this device.";

    const resetButton = document.createElement("button");
    resetButton.type = "button";
    resetButton.className = "text-button";
    resetButton.id = "reset-progress";
    resetButton.textContent = "Reset all progress";
    resetButton.addEventListener("click", options.onReset);

    testPanel.append(testHeading, testHelp, resetButton);
    view.append(hero, list, message, testPanel);
    return view;
  }

  window.Menu = { render: render };
})();
