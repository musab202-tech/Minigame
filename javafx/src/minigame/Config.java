package minigame;

import java.util.List;

/**
 * Tunable values for the whole collection.
 * Null means the owner has not chosen a value yet.
 * A comment that says TEMPORARY TEST VALUE is a stand-in, not a final rule.
 */
public final class Config {
    private Config() {
    }

    public static final int TOTAL_BALLS = 100;
    public static final int STARTING_CHANCES = 3;

    /** Clear when every ball is resolved and at least this many chances remain. */
    public static final int CLEAR_MIN_CHANCES_REMAINING = 1;

    /** Star when every ball is resolved and exactly this many chances remain (no misses). */
    public static final int STAR_REQUIRES_CHANCES_REMAINING = 3;

    /** TEMPORARY TEST VALUE, OWNER TO TUNE. Pixels per second at the start of a run. */
    public static final double STARTING_FALL_SPEED = 300;

    /** TEMPORARY TEST VALUE, OWNER TO TUNE. Speed steps up after this many balls. */
    public static final int SPEED_INCREASE_EVERY_BALLS = 10;

    /** TEMPORARY TEST VALUE, OWNER TO TUNE. Pixels per second added at each step. */
    public static final double SPEED_INCREASE_AMOUNT = 30;

    /** TEMPORARY TEST VALUE, OWNER TO TUNE. Fall speed never goes above this. */
    public static final double MAX_FALL_SPEED = 700;

    /** TEMPORARY TEST VALUE, OWNER TO TUNE. Seconds between balls. The first ball appears as soon as the run starts. */
    public static final double BALL_SPAWN_INTERVAL = 0.8;

    /** Pixels. */
    public static final double PLATFORM_WIDTH = 150;

    /** Pixels per second. */
    public static final double PLATFORM_SPEED = 520;

    /** Pixels. */
    public static final double BALL_SIZE = 40;

    /** Pixels. */
    public static final double PLATFORM_HEIGHT = 26;

    /** Each catch adds this much to the score. */
    public static final int POINTS_PER_CATCH = 1;

    /** Each miss costs this many chances. */
    public static final int CHANCES_PER_MISS = 1;

    /**
     * Longest slice of time one frame may move the game, in seconds.
     * Stops a ball jumping past the platform after a pause or a hitch.
     */
    public static final double MAX_FRAME_SECONDS = 0.05;

    public static Result judge(int chancesLeft, int ballsSettled) {
        boolean finishedAll = ballsSettled == TOTAL_BALLS;
        if (!finishedAll || chancesLeft == 0) {
            return new Result("Try again", false, false);
        }
        if (chancesLeft >= CLEAR_MIN_CHANCES_REMAINING) {
            boolean star = chancesLeft == STAR_REQUIRES_CHANCES_REMAINING;
            String message = star ? "Round cleared and star earned" : "Round cleared";
            return new Result(message, true, star);
        }
        return new Result("Try again", false, false);
    }

    public static String chanceSentence(int count) {
        if (count == 1) {
            return "1 chance left";
        }
        return count + " chances left";
    }

    /** Text beside the chance icons, so the count is not shown by colour alone. */
    public static String chancesStatus(int count) {
        return "Chances: " + count + " left";
    }

    /**
     * How many times the fall speed has stepped up for this ball.
     * Ball 1 is the first ball. Balls 1 to 10 stay at the starting speed.
     */
    public static int speedIncreasesForBall(int ballNumber) {
        int safeBall = Math.max(1, ballNumber);
        return (safeBall - 1) / SPEED_INCREASE_EVERY_BALLS;
    }

    /** Fall speed for the balls on screen once this ball has appeared. */
    public static double fallSpeedForBall(int ballNumber) {
        double speed = STARTING_FALL_SPEED + speedIncreasesForBall(ballNumber) * SPEED_INCREASE_AMOUNT;
        if (speed > MAX_FALL_SPEED) {
            return MAX_FALL_SPEED;
        }
        return speed;
    }

    /**
     * Friendly speed step, starting at 1.
     * Once the cap is reached the number stops climbing too.
     */
    public static int speedLevelForBall(int ballNumber) {
        int increases = speedIncreasesForBall(ballNumber);
        double uncapped = STARTING_FALL_SPEED + increases * SPEED_INCREASE_AMOUNT;
        if (uncapped <= MAX_FALL_SPEED) {
            return increases + 1;
        }
        int stepsToCap = (int) Math.ceil((MAX_FALL_SPEED - STARTING_FALL_SPEED) / SPEED_INCREASE_AMOUNT);
        return stepsToCap + 1;
    }

    public static String speedLabel(int ballNumber) {
        return "Speed " + speedLevelForBall(ballNumber);
    }

    /** The message and achievements for one finished run. */
    public record Result(String message, boolean cleared, boolean star) {
    }

    public enum UnlockType {
        ALWAYS,
        CLEARED,
        PREVIOUS_STARS
    }

    /** One menu round. Undecided gameplay stays null. */
    public static final class Round {
        public final int id;
        public final String name;
        public final String color;
        public final UnlockType unlockType;
        public final int unlockRoundId;
        public final int starMinimum;
        public final boolean developerTestControls;

        private Round(int id, String name, String color, UnlockType unlockType,
                int unlockRoundId, int starMinimum, boolean developerTestControls) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.unlockType = unlockType;
            this.unlockRoundId = unlockRoundId;
            this.starMinimum = starMinimum;
            this.developerTestControls = developerTestControls;
        }
    }

    public static final List<Round> ROUNDS = List.of(
            new Round(1, "Falling Balls", "#ff8d6b", UnlockType.ALWAYS, 0, 0, false),
            new Round(2, "Stop on Red", "#ffd84d", UnlockType.CLEARED, 1, 0, false),
            new Round(3, "Colour Hop", "#5fd0b0", UnlockType.CLEARED, 2, 0, true),
            new Round(4, "Coming soon", "#ff9ec8", UnlockType.PREVIOUS_STARS, 0, 1, false),
            new Round(5, "Coming soon", "#b6e36a", UnlockType.PREVIOUS_STARS, 0, 2, false),
            new Round(6, "Coming soon", "#cbb6ff", UnlockType.PREVIOUS_STARS, 0, 3, false)
    );

    // Round 2 rules the owner already chose.
    public static final int ROUND2_TOTAL_STAGES = 5;
    public static final int ROUND2_HITS_TO_CLEAR = 4;
    public static final int ROUND2_HITS_FOR_STAR = 5;

    /** TEMPORARY TEST VALUE, OWNER TO CONFIRM. Flip to true and the red spot shrinks each stage. */
    public static final boolean ROUND2_TARGET_SHRINKS = false;

    /**
     * TEMPORARY TEST VALUE, OWNER TO CONFIRM.
     * Hold-button diameter in pixels, one entry per stage. Stage 5 is tiny on purpose.
     */
    public static final List<Double> ROUND2_BUTTON_SIZES = List.of(240.0, 180.0, 120.0, 72.0, 36.0);

    /** TEMPORARY TEST VALUE, OWNER TO CONFIRM. Marker travel speed, pixels per second. Same every stage. */
    public static final double ROUND2_MARKER_SPEED = 240;

    /** TEMPORARY TEST VALUE, OWNER TO CONFIRM. */
    public static final double ROUND2_TRACK_HEIGHT = 420;
    public static final double ROUND2_TRACK_WIDTH = 72;
    public static final double ROUND2_MARKER_SIZE = 28;

    /** TEMPORARY TEST VALUE, OWNER TO CONFIRM. Used while ROUND2_TARGET_SHRINKS is false. */
    public static final double ROUND2_RED_ZONE_HEIGHT = 84;

    /**
     * TEMPORARY TEST VALUE, OWNER TO CONFIRM.
     * Red-spot height per stage, used only when ROUND2_TARGET_SHRINKS is true.
     */
    public static final List<Double> ROUND2_RED_ZONE_HEIGHTS = List.of(120.0, 84.0, 56.0, 34.0, 16.0);

    /**
     * TEMPORARY TEST VALUE, OWNER TO CONFIRM.
     * Center of the red spot, from 0 at the top of the bar to 1 at the bottom.
     */
    public static final double ROUND2_RED_ZONE_CENTER = 0.5;

    /** TEMPORARY TEST VALUE, OWNER TO CONFIRM. How long Hit or Miss stays up, in seconds. */
    public static final double ROUND2_FEEDBACK_SECONDS = 0.7;

    // Round 3.
    public static final int ROUND3_TIME_LIMIT_SECONDS = 20;
    public static final int ROUND3_FALL_RECOVERY_SECONDS = 2;
    public static final int ROUND3_NUMBER_OF_COLOURS = 3;
    public static final String ROUND3_CLEAR = "Reach the end of the course within the time limit";
    public static final String ROUND3_STAR_TARGET = null; // TO BE DECIDED BY OWNER

    // Rounds 4-6 gameplay is not designed yet.
    public static final String ROUND4_GAMEPLAY = null; // TO BE DECIDED BY OWNER
    public static final String ROUND5_GAMEPLAY = null; // TO BE DECIDED BY OWNER
    public static final String ROUND6_GAMEPLAY = null; // TO BE DECIDED BY OWNER

    public static Round round(int id) {
        for (Round round : ROUNDS) {
            if (round.id == id) {
                return round;
            }
        }
        return null;
    }

    /** 4 hits clear. All 5 hits also earn the star. Fewer than 4 is a miss of the round. */
    public static Result judgeRound2(int hits) {
        if (hits >= ROUND2_HITS_FOR_STAR) {
            return new Result("Round cleared and star earned", true, true);
        }
        if (hits >= ROUND2_HITS_TO_CLEAR) {
            return new Result("Round cleared", true, false);
        }
        return new Result("Try again", false, false);
    }

    public static String hitsSentence(int hits) {
        String word = hits == 1 ? "hit" : "hits";
        return hits + " " + word + " out of " + ROUND2_TOTAL_STAGES;
    }

    public static double round2ButtonSize(int stage) {
        return stageValue(ROUND2_BUTTON_SIZES, stage);
    }

    /** Red-spot height for this stage. The shrink switch picks which list is used. */
    public static double round2RedZoneHeight(int stage) {
        if (!ROUND2_TARGET_SHRINKS) {
            return ROUND2_RED_ZONE_HEIGHT;
        }
        return stageValue(ROUND2_RED_ZONE_HEIGHTS, stage);
    }

    /** Top of the red spot, in pixels down the bar. */
    public static double round2RedZoneTop(int stage) {
        double height = round2RedZoneHeight(stage);
        double top = ROUND2_RED_ZONE_CENTER * ROUND2_TRACK_HEIGHT - height / 2.0;
        double maxTop = Math.max(0, ROUND2_TRACK_HEIGHT - height);
        if (top < 0) {
            return 0;
        }
        if (top > maxTop) {
            return maxTop;
        }
        return top;
    }

    /** True when the stopped marker overlaps the red spot. */
    public static boolean round2StoppedOnRed(double markerTop, int stage) {
        double redTop = round2RedZoneTop(stage);
        double redBottom = redTop + round2RedZoneHeight(stage);
        double markerBottom = markerTop + ROUND2_MARKER_SIZE;
        return markerBottom > redTop && markerTop < redBottom;
    }

    private static double stageValue(List<Double> values, int stage) {
        int index = stage - 1;
        if (index < 0) {
            return values.getFirst();
        }
        if (index >= values.size()) {
            return values.getLast();
        }
        return values.get(index);
    }
}
