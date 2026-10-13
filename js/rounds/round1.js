/* round1.js
   Falling Balls.
   Balls fall from the top. The player slides a platform to catch them.
   Rules and speeds come from this round's entry in config.js.
   Leaving mid-run does not save. A later worse run cannot undo a clear or a star,
   because progress.js only ever turns those flags on.
*/

(function () {
  const SVG_NS = "http://www.w3.org/2000/svg";

  window.RoundScreens = window.RoundScreens || {};
  window.RoundScreens[1] = function (options) {
    const round = options.round;
    const state = options.state;
    const view = document.createElement("div");
    view.className = "view round-view fall-view";

    const toolbar = document.createElement("div");
    toolbar.className = "fall-toolbar";

    const back = document.createElement("button");
    back.type = "button";
    back.className = "back-button";
    back.textContent = "Back";

    const pauseButton = document.createElement("button");
    pauseButton.type = "button";
    pauseButton.className = "text-button";
    pauseButton.textContent = "Pause";
    pauseButton.hidden = true;

    toolbar.append(back, pauseButton);

    const title = document.createElement("h1");
    title.textContent = round.name;

    const intro = buildIntro(round);
    const hud = buildHud();
    const field = buildField();
    const result = buildResult();

    hud.root.hidden = true;
    field.root.hidden = true;
    result.root.hidden = true;

    view.append(toolbar, title, intro.root, hud.root, field.root, result.root);

    // Run state. Reset every time Start or Replay is pressed.
    let mode = "start";
    let score = 0;
    let chances = round.startingChances;
    let spawned = 0;
    let settled = 0;
    let spawnTimer = 0;
    let platformX = 0;
    let balls = [];
    let heldLeft = false;
    let heldRight = false;
    let dragging = false;
    let frameId = 0;
    let loopOn = false;
    let lastTime = 0;
    let cleaned = false;

    function leave() {
      if (cleaned) {
        return;
      }
      cleaned = true;
      mode = "left";
      stopLoop();
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      options.onBack();
    }

    back.addEventListener("click", leave);
    result.menuButton.addEventListener("click", leave);

    intro.startButton.addEventListener("click", beginPlay);
    result.replayButton.addEventListener("click", beginPlay);
    pauseButton.addEventListener("click", togglePause);

    field.root.addEventListener("pointerdown", onPointerDown);
    field.root.addEventListener("pointermove", onPointerMove);
    field.root.addEventListener("pointerup", onPointerUp);
    field.root.addEventListener("pointercancel", onPointerUp);

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);

    // Stop the loop if this screen is removed some other way.
    const observer = new MutationObserver(function () {
      if (!view.isConnected) {
        cleaned = true;
        mode = "left";
        stopLoop();
        window.removeEventListener("keydown", onKeyDown);
        window.removeEventListener("keyup", onKeyUp);
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });

    return view;

    function beginPlay() {
      mode = "starting";
      intro.root.hidden = true;
      result.root.hidden = true;
      hud.root.hidden = false;
      field.root.hidden = false;
      field.pausedLabel.hidden = true;
      pauseButton.hidden = false;
      pauseButton.textContent = "Pause";

      // The field must be visible before it has a width.
      window.requestAnimationFrame(function () {
        if (cleaned) {
          return;
        }
        resetRun();
        spawnBall();
        paintHud();
        mode = "playing";
        startLoop();
      });
    }

    function resetRun() {
      clearBalls();
      score = 0;
      chances = round.startingChances;
      spawned = 0;
      settled = 0;
      spawnTimer = 0;
      const size = fieldSize();
      const width = platformPixelWidth(size.width);
      platformX = Math.max(0, (size.width - width) / 2);
      placePlatform(size);
      buildLifeIcons();
    }

    function togglePause() {
      if (mode === "playing") {
        mode = "paused";
        stopLoop();
        pauseButton.textContent = "Resume";
        field.pausedLabel.hidden = false;
        return;
      }
      if (mode === "paused") {
        mode = "playing";
        pauseButton.textContent = "Pause";
        field.pausedLabel.hidden = true;
        startLoop();
      }
    }

    function startLoop() {
      if (loopOn) {
        return;
      }
      loopOn = true;
      lastTime = 0;
      frameId = window.requestAnimationFrame(frame);
    }

    function stopLoop() {
      loopOn = false;
      window.cancelAnimationFrame(frameId);
    }

    function frame(now) {
      if (!loopOn) {
        return;
      }
      frameId = window.requestAnimationFrame(frame);
      if (!view.isConnected) {
        stopLoop();
        return;
      }
      if (!lastTime) {
        lastTime = now;
        return;
      }
      // A long gap (a hidden tab) must not fling a ball past the platform.
      let dt = (now - lastTime) / 1000;
      lastTime = now;
      if (dt > 0.05) {
        dt = 0.05;
      }
      if (mode !== "playing") {
        return;
      }
      update(dt);
    }

    function update(dt) {
      const size = fieldSize();
      if (size.width < 1 || size.height < 1) {
        return;
      }
      movePlatform(dt, size);
      stepBalls(dt, size);
      if (mode !== "playing") {
        return;
      }
      spawnTimer += dt;
      if (spawned < round.totalBalls && spawnTimer >= round.ballSpawnInterval) {
        spawnTimer -= round.ballSpawnInterval;
        spawnBall();
        paintHud();
      }
      if (spawned === round.totalBalls && balls.length === 0) {
        finish();
      }
    }

    function movePlatform(dt, size) {
      const width = platformPixelWidth(size.width);
      if (!dragging) {
        let direction = 0;
        if (heldLeft) {
          direction -= 1;
        }
        if (heldRight) {
          direction += 1;
        }
        platformX += direction * round.platformSpeed * dt;
      }
      platformX = clamp(platformX, 0, Math.max(0, size.width - width));
      placePlatform(size);
    }

    function stepBalls(dt, size) {
      const radius = round.ballSize / 2;
      const platformTop = size.height - round.platformHeight;
      const platformWidth = platformPixelWidth(size.width);
      const kept = [];

      for (let i = 0; i < balls.length; i += 1) {
        if (mode !== "playing") {
          break;
        }
        const ball = balls[i];
        const previousY = ball.y;
        ball.y += round.ballFallSpeed * dt;

        const overPlatform = ball.x + radius >= platformX && ball.x - radius <= platformX + platformWidth;
        const reachedPlatform = ball.y + radius >= platformTop && previousY - radius <= size.height;
        const reachedBottom = ball.y + radius >= size.height;

        if (overPlatform && reachedPlatform) {
          // Each caught ball adds 1 to the score.
          score += 1;
          settled += 1;
          ball.el.remove();
          paintHud();
        } else if (reachedBottom) {
          // Each miss costs one chance.
          settled += 1;
          chances -= 1;
          ball.el.remove();
          paintHud();
          if (chances <= 0) {
            chances = 0;
            paintHud();
            finish();
            break;
          }
        } else {
          placeBall(ball);
          kept.push(ball);
        }
      }

      if (mode === "playing") {
        balls = kept;
      }
    }

    function spawnBall() {
      const size = fieldSize();
      const radius = round.ballSize / 2;
      const ball = {
        x: radius + Math.random() * Math.max(0, size.width - radius * 2),
        y: radius,
        el: document.createElement("span"),
      };
      ball.el.className = "fall-ball tone-" + ((spawned % 5) + 1);
      ball.el.style.width = round.ballSize + "px";
      ball.el.style.height = round.ballSize + "px";
      field.root.appendChild(ball.el);
      placeBall(ball);
      balls.push(ball);
      spawned += 1;
    }

    function finish() {
      if (mode === "result" || mode === "left") {
        return;
      }
      mode = "result";
      stopLoop();
      clearBalls();
      dragging = false;

      const outcome = judgeRun(round, chances, settled);
      // Only turn achievements on. Never call anything that would clear them.
      if (outcome.cleared) {
        window.Progress.markCleared(state, round.id);
      }
      if (outcome.star) {
        window.Progress.awardStar(state, round.id);
      }

      hud.root.hidden = true;
      field.root.hidden = true;
      pauseButton.hidden = true;
      result.root.hidden = false;
      result.message.textContent = outcome.message;
      result.scoreLine.textContent = "Caught " + score + " of " + round.totalBalls;
      result.chanceLine.textContent = chanceSentence(chances);
      result.message.focus();
    }

    function paintHud() {
      hud.progress.textContent = "Ball " + spawned + " of " + round.totalBalls;
      hud.score.textContent = "Score: " + score;
      hud.chances.textContent = chanceSentence(chances);
      const icons = hud.lives.children;
      for (let i = 0; i < icons.length; i += 1) {
        const lost = i >= chances;
        icons[i].classList.toggle("is-lost", lost);
        icons[i].classList.toggle("is-full", !lost);
      }
    }

    function buildLifeIcons() {
      hud.lives.replaceChildren();
      for (let i = 0; i < round.startingChances; i += 1) {
        const life = document.createElement("span");
        life.className = "fall-life is-full";
        life.appendChild(heartIcon());
        hud.lives.appendChild(life);
      }
    }

    function placeBall(ball) {
      const radius = round.ballSize / 2;
      ball.el.style.left = (ball.x - radius) + "px";
      ball.el.style.top = (ball.y - radius) + "px";
    }

    function placePlatform(size) {
      const width = platformPixelWidth(size.width);
      field.platform.style.width = width + "px";
      field.platform.style.height = round.platformHeight + "px";
      field.platform.style.transform = "translateX(" + platformX + "px)";
    }

    function platformPixelWidth(fieldWidth) {
      return Math.min(round.platformWidth, Math.max(0, fieldWidth));
    }

    function fieldSize() {
      return {
        width: field.root.clientWidth,
        height: field.root.clientHeight,
      };
    }

    function clearBalls() {
      balls.forEach(function (ball) {
        ball.el.remove();
      });
      balls = [];
    }

    function onKeyDown(event) {
      if (event.key === "ArrowLeft" || event.key === "a" || event.key === "A") {
        heldLeft = true;
        event.preventDefault();
      } else if (event.key === "ArrowRight" || event.key === "d" || event.key === "D") {
        heldRight = true;
        event.preventDefault();
      } else if (event.key === "Escape") {
        togglePause();
      }
    }

    function onKeyUp(event) {
      if (event.key === "ArrowLeft" || event.key === "a" || event.key === "A") {
        heldLeft = false;
      } else if (event.key === "ArrowRight" || event.key === "d" || event.key === "D") {
        heldRight = false;
      }
    }

    function onPointerDown(event) {
      if (mode !== "playing") {
        return;
      }
      dragging = true;
      try {
        field.root.setPointerCapture(event.pointerId);
      } catch (error) {
        // A real press can capture the pointer. Ignore if this browser refuses.
      }
      movePlatformToPointer(event);
    }

    function onPointerMove(event) {
      if (!dragging || mode !== "playing") {
        return;
      }
      movePlatformToPointer(event);
    }

    function onPointerUp(event) {
      dragging = false;
      if (field.root.hasPointerCapture(event.pointerId)) {
        field.root.releasePointerCapture(event.pointerId);
      }
    }

    function movePlatformToPointer(event) {
      const rect = field.root.getBoundingClientRect();
      const size = fieldSize();
      const width = platformPixelWidth(size.width);
      const localX = event.clientX - rect.left;
      platformX = clamp(localX - width / 2, 0, Math.max(0, size.width - width));
      placePlatform(size);
    }
  };

  // Decides the result from the config thresholds. Does not save anything.
  function judgeRun(rules, chancesLeft, ballsSettled) {
    const finishedAll = ballsSettled === rules.totalBalls;
    if (!finishedAll || chancesLeft === 0) {
      return { message: "Try again", cleared: false, star: false };
    }
    if (chancesLeft >= rules.clearMinChancesRemaining) {
      const star = chancesLeft === rules.starRequiresChancesRemaining;
      return {
        message: star ? "Round cleared and star earned" : "Round cleared",
        cleared: true,
        star: star,
      };
    }
    return { message: "Try again", cleared: false, star: false };
  }

  function chanceSentence(count) {
    if (count === 1) {
      return "1 chance left";
    }
    return count + " chances left";
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function heartIcon() {
    const svg = document.createElementNS(SVG_NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.classList.add("fall-life-icon");
    const path = document.createElementNS(SVG_NS, "path");
    path.setAttribute("d", "M12 20s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9z");
    svg.appendChild(path);
    return svg;
  }

  function buildIntro(round) {
    const root = document.createElement("div");
    root.className = "fall-panel";

    const line1 = document.createElement("p");
    line1.className = "fall-help";
    line1.textContent = "Catch the falling balls with the platform. Use the arrow keys, A and D, or drag.";

    const line2 = document.createElement("p");
    line2.className = "fall-help";
    line2.textContent = "There are " + round.totalBalls + " balls. You start with " + round.startingChances + " chances. Each miss costs one chance.";

    const clearWord = round.clearMinChancesRemaining === 1 ? "chance" : "chances";
    const line3 = document.createElement("p");
    line3.className = "fall-help";
    line3.textContent = "Finish every ball with at least " + round.clearMinChancesRemaining + " " + clearWord + " left to clear the round. Finish with " + round.starRequiresChancesRemaining + " chances left to earn the star.";

    const startButton = document.createElement("button");
    startButton.type = "button";
    startButton.className = "fall-primary";
    startButton.textContent = "Start";

    root.append(line1, line2, line3, startButton);
    return { root: root, startButton: startButton };
  }

  function buildHud() {
    const root = document.createElement("div");
    root.className = "fall-hud";

    const progress = document.createElement("p");
    progress.className = "fall-readout";

    const score = document.createElement("p");
    score.className = "fall-readout";

    const chanceRow = document.createElement("div");
    chanceRow.className = "fall-chance-row";

    const chances = document.createElement("p");
    chances.className = "fall-readout";

    const lives = document.createElement("div");
    lives.className = "fall-lives";
    lives.setAttribute("aria-hidden", "true");

    chanceRow.append(chances, lives);
    root.append(progress, score, chanceRow);
    return {
      root: root,
      progress: progress,
      score: score,
      chances: chances,
      lives: lives,
    };
  }

  function buildField() {
    const root = document.createElement("div");
    root.className = "fall-field";

    const platform = document.createElement("div");
    platform.className = "fall-platform";

    const pausedLabel = document.createElement("p");
    pausedLabel.className = "fall-paused";
    pausedLabel.textContent = "Paused";
    pausedLabel.hidden = true;

    root.append(platform, pausedLabel);
    return { root: root, platform: platform, pausedLabel: pausedLabel };
  }

  function buildResult() {
    const root = document.createElement("div");
    root.className = "fall-panel";

    const message = document.createElement("p");
    message.className = "fall-result";
    message.tabIndex = -1;
    message.setAttribute("role", "status");

    const scoreLine = document.createElement("p");
    scoreLine.className = "fall-help";

    const chanceLine = document.createElement("p");
    chanceLine.className = "fall-help";

    const actions = document.createElement("div");
    actions.className = "fall-toolbar";

    const replayButton = document.createElement("button");
    replayButton.type = "button";
    replayButton.className = "fall-primary";
    replayButton.textContent = "Replay";

    const menuButton = document.createElement("button");
    menuButton.type = "button";
    menuButton.className = "text-button";
    menuButton.textContent = "Back to menu";

    actions.append(replayButton, menuButton);
    root.append(message, scoreLine, chanceLine, actions);
    return {
      root: root,
      message: message,
      scoreLine: scoreLine,
      chanceLine: chanceLine,
      replayButton: replayButton,
      menuButton: menuButton,
    };
  }
})();
