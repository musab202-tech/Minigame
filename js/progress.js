/* progress.js
   Remembers two separate facts for each round:
   - cleared: the round has been finished
   - starEarned: the star for that round was earned
   Setting one does not change the other.

   Progress is saved on this device with localStorage.
   isUnlocked() applies the rules from config.js.
*/

(function () {
  const config = window.GAME_CONFIG;

  function freshState() {
    const rounds = {};
    config.rounds.forEach(function (round) {
      rounds[round.id] = {
        cleared: false,
        starEarned: false,
      };
    });
    return { rounds: rounds };
  }

  function isValid(data) {
    if (!data || typeof data !== "object") {
      return false;
    }
    if (!data.rounds || typeof data.rounds !== "object") {
      return false;
    }

    return config.rounds.every(function (round) {
      const entry = data.rounds[round.id];
      if (!entry || typeof entry !== "object") {
        return false;
      }
      return typeof entry.cleared === "boolean" && typeof entry.starEarned === "boolean";
    });
  }

  function save(state) {
    try {
      localStorage.setItem(config.storageKey, JSON.stringify(state));
    } catch (error) {
      // Storage can be blocked (private mode, full disk). Keep playing anyway.
    }
  }

  function load() {
    let raw = null;

    try {
      raw = localStorage.getItem(config.storageKey);
    } catch (error) {
      return freshState();
    }

    // Nothing saved yet: first visit on this device.
    if (raw === null) {
      return freshState();
    }

    try {
      const data = JSON.parse(raw);
      if (isValid(data)) {
        return data;
      }
    } catch (error) {
      // The saved text is not JSON. Fall through and start fresh.
    }

    // Missing or broken data must not crash the game.
    const clean = freshState();
    save(clean);
    return clean;
  }

  function reset() {
    const clean = freshState();
    save(clean);
    return clean;
  }

  function entryFor(state, roundId) {
    if (!state || !state.rounds) {
      return null;
    }
    return state.rounds[roundId] || null;
  }

  // Marks the round cleared. Does not award the star.
  function markCleared(state, roundId) {
    const entry = entryFor(state, roundId);
    if (!entry) {
      return false;
    }
    entry.cleared = true;
    save(state);
    return true;
  }

  // Awards the star. Does not mark the round cleared.
  function awardStar(state, roundId) {
    const entry = entryFor(state, roundId);
    if (!entry) {
      return false;
    }
    entry.starEarned = true;
    save(state);
    return true;
  }

  // Stars earned on rounds that come before this round number.
  function starsBefore(state, roundId) {
    return config.rounds.reduce(function (total, round) {
      if (round.id >= roundId) {
        return total;
      }
      const entry = entryFor(state, round.id);
      return total + (entry && entry.starEarned ? 1 : 0);
    }, 0);
  }

  function isUnlocked(state, round) {
    const rule = round && round.unlock;
    if (!rule) {
      return false;
    }

    if (rule.type === "always") {
      return true;
    }

    if (rule.type === "cleared") {
      const entry = entryFor(state, rule.roundId);
      return Boolean(entry && entry.cleared);
    }

    if (rule.type === "previousStars") {
      return starsBefore(state, round.id) >= rule.minimum;
    }

    return false;
  }

  // Short message built from the round's unlock rule in config.js.
  function unlockMessage(round) {
    const rule = round && round.unlock;

    if (!rule || rule.type === "always") {
      return "This round is unlocked.";
    }

    if (rule.type === "cleared") {
      const needed = config.rounds.find(function (item) {
        return item.id === rule.roundId;
      });
      const name = needed ? needed.name : "the earlier round";
      return "This one is locked. Clear Round " + rule.roundId + ", " + name + ", to unlock this.";
    }

    if (rule.type === "previousStars") {
      const count = rule.minimum;
      const word = count === 1 ? "star" : "stars";
      return "This one is locked. Earn at least " + count + " " + word + " from earlier rounds to unlock this.";
    }

    return "This one is locked.";
  }

  window.Progress = {
    load: load,
    save: save,
    reset: reset,
    markCleared: markCleared,
    awardStar: awardStar,
    isUnlocked: isUnlocked,
    unlockMessage: unlockMessage,
  };
})();
