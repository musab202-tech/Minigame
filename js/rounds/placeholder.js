/* placeholder.js
   Shared "not built yet" screen.
   Each round file calls this until that round's real game is written.

   The test panel is temporary. It is turned on per round in config.js
   with developerTestControls. Rounds 1–3 use it so unlock rules can be
   checked before any gameplay exists.
*/

(function () {
  function render(options) {
    const round = options.round;
    const state = options.state;
    const view = document.createElement("div");
    view.className = "view round-view";

    const back = document.createElement("button");
    back.type = "button";
    back.className = "back-button";
    back.textContent = "Back";
    back.addEventListener("click", options.onBack);

    const eyebrow = document.createElement("p");
    eyebrow.className = "kicker";
    eyebrow.textContent = "Round " + round.id;

    const title = document.createElement("h1");
    title.textContent = round.name;

    const coming = document.createElement("p");
    coming.className = "coming-soon";
    coming.textContent = "Gameplay coming soon";

    view.append(back, eyebrow, title, coming);

    if (round.developerTestControls === true) {
      view.appendChild(buildTestPanel(round, state));
    }

    return view;
  }

  function buildTestPanel(round, state) {
    const panel = document.createElement("section");
    panel.className = "test-panel";
    panel.setAttribute("aria-label", "Developer testing only");

    const heading = document.createElement("h2");
    heading.textContent = "Developer testing only";

    const help = document.createElement("p");
    help.textContent = "These buttons save test progress on this device only.";

    const status = document.createElement("p");
    status.className = "test-status";
    status.setAttribute("role", "status");

    const actions = document.createElement("div");
    actions.className = "test-actions";

    const clearButton = document.createElement("button");
    clearButton.type = "button";
    clearButton.className = "test-button";
    clearButton.dataset.action = "mark-cleared";
    clearButton.textContent = "Mark cleared";

    const starButton = document.createElement("button");
    starButton.type = "button";
    starButton.className = "test-button";
    starButton.dataset.action = "award-star";
    starButton.textContent = "Award star";

    function paint() {
      const entry = state.rounds[round.id];
      const cleared = Boolean(entry && entry.cleared);
      const earned = Boolean(entry && entry.starEarned);
      status.textContent = "Cleared: " + (cleared ? "Yes" : "No") + ". Star: " + (earned ? "Yes" : "No") + ".";
      clearButton.disabled = cleared;
      starButton.disabled = earned;
    }

    clearButton.addEventListener("click", function () {
      window.Progress.markCleared(state, round.id);
      paint();
    });

    starButton.addEventListener("click", function () {
      window.Progress.awardStar(state, round.id);
      paint();
    });

    actions.append(clearButton, starButton);
    panel.append(heading, help, status, actions);
    paint();
    return panel;
  }

  window.PlaceholderRound = { render: render };
})();
