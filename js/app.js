/* app.js
   Starts the page. Shows the menu, and opens a round screen
   when an unlocked card is chosen.
*/

(function () {
  const app = document.querySelector("#app");
  let state = window.Progress.load();

  function showMenu(extra) {
    const options = extra || {};
    const view = window.Menu.render({
      state: state,
      notice: options.notice || "",
      onOpenRound: showRound,
      onReset: function () {
        state = window.Progress.reset();
        showMenu({ notice: "All progress on this device was reset." });
      },
    });

    app.replaceChildren(view);

    if (options.focusRoundId) {
      const card = view.querySelector('[data-round-id="' + options.focusRoundId + '"]');
      if (card) {
        card.focus();
      }
    }
  }

  function showRound(roundId) {
    const round = window.GAME_CONFIG.rounds.find(function (item) {
      return item.id === roundId;
    });

    if (!round || !window.Progress.isUnlocked(state, round)) {
      showMenu();
      return;
    }

    const onBack = function () {
      showMenu({ focusRoundId: round.id });
    };

    const open = window.RoundScreens && window.RoundScreens[round.id];
    const screen = typeof open === "function"
      ? open({ round: round, state: state, onBack: onBack })
      : window.PlaceholderRound.render({ round: round, state: state, onBack: onBack });

    app.replaceChildren(screen);

    const heading = screen.querySelector("h1");
    if (heading) {
      heading.setAttribute("tabindex", "-1");
      heading.focus();
    }
  }

  showMenu();
})();
