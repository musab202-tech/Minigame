/* config.js
   Every tunable rule lives in this file.
   The menu and the save system read from here, so names and
   unlock rules are not copied into other files.

   Null means the owner has not chosen a value yet.
   Leave those null. Do not invent numbers for them.
*/

(function () {
  window.GAME_CONFIG = {
    // Saved in this browser only. This is not an online record.
    storageKey: "minigame-progress",

    /* Unlock rule types
       always:
         { type: "always" }
         The round is open from the start.
       cleared:
         { type: "cleared", roundId: 1 }
         Opens when that round's cleared flag is true.
         A star does not clear a round, and clearing does not award a star.
       previousStars:
         { type: "previousStars", minimum: 2 }
         Opens when earlier rounds (smaller round numbers) have at least
         `minimum` stars between them.
         A star on this round does not count toward unlocking itself.
    */
    rounds: [
      {
        id: 1,
        name: "Falling Balls",
        totalBalls: 100,
        startingChances: 6,
        // Clear when every ball is resolved and at least this many chances remain.
        clearMinChancesRemaining: 1,
        // Star when every ball is resolved and exactly this many chances remain (no misses).
        starRequiresChancesRemaining: 6,
        clearCondition: "Every ball resolved, chances remaining >= clearMinChancesRemaining",
        starTarget: "Every ball resolved, chances remaining === starRequiresChancesRemaining",
        // Feel. These stay constant for the whole run. Units: pixels, or seconds.
        ballFallSpeed: 200,
        ballSpawnInterval: 1,
        platformWidth: 150,
        platformSpeed: 520,
        ballSize: 40,
        platformHeight: 26,
        unlock: { type: "always" },
        // The real game replaces the temporary test buttons for this round.
        developerTestControls: false,
      },
      {
        id: 2,
        name: "Stop on Red",
        totalStages: 5,
        hitsRequiredToClear: 4,
        hitsRequiredForStar: 5,
        targetAreaShrinksEachStage: null, // TO BE DECIDED BY OWNER
        buttonSizePerStage: null, // TO BE DECIDED BY OWNER
        markerSpeed: null, // TO BE DECIDED BY OWNER
        unlock: { type: "cleared", roundId: 1 },
        developerTestControls: true,
      },
      {
        id: 3,
        name: "Colour Hop",
        timeLimitSeconds: 20,
        fallRecoverySeconds: 2,
        numberOfColours: 3,
        clearCondition: "Reach the end of the course within the time limit",
        starTarget: null, // TO BE DECIDED BY OWNER
        unlock: { type: "cleared", roundId: 2 },
        developerTestControls: true,
      },
      {
        id: 4,
        name: "Coming soon",
        gameplay: null, // TO BE DECIDED BY OWNER
        unlock: { type: "previousStars", minimum: 1 },
        developerTestControls: false,
      },
      {
        id: 5,
        name: "Coming soon",
        gameplay: null, // TO BE DECIDED BY OWNER
        unlock: { type: "previousStars", minimum: 2 },
        developerTestControls: false,
      },
      {
        id: 6,
        name: "Coming soon",
        gameplay: null, // TO BE DECIDED BY OWNER
        unlock: { type: "previousStars", minimum: 3 },
        developerTestControls: false,
      },
    ],
  };
})();
