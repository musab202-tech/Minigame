package minigame;

import java.util.prefs.Preferences;

/**
 * Remembers cleared and starEarned separately for each round.
 * Saved on this device only. A later worse run cannot turn a flag back off.
 */
public final class Progress {
    private static final String KEY = "minigame-progress";
    private final boolean[] cleared = new boolean[Config.ROUNDS.size() + 1];
    private final boolean[] starEarned = new boolean[Config.ROUNDS.size() + 1];

    private Progress() {
    }

    public static Progress load() {
        Progress progress = new Progress();
        String raw = null;
        try {
            raw = Preferences.userNodeForPackage(Progress.class).get(KEY, null);
        } catch (Exception error) {
            return progress;
        }
        if (raw == null) {
            return progress;
        }
        if (!progress.read(raw)) {
            Progress fresh = new Progress();
            fresh.save();
            return fresh;
        }
        return progress;
    }

    public void reset() {
        for (Config.Round round : Config.ROUNDS) {
            cleared[round.id] = false;
            starEarned[round.id] = false;
        }
        save();
    }

    public void markCleared(int roundId) {
        if (!known(roundId)) {
            return;
        }
        cleared[roundId] = true;
        save();
    }

    public void awardStar(int roundId) {
        if (!known(roundId)) {
            return;
        }
        starEarned[roundId] = true;
        save();
    }

    public boolean isCleared(int roundId) {
        return known(roundId) && cleared[roundId];
    }

    public boolean starEarned(int roundId) {
        return known(roundId) && starEarned[roundId];
    }

    public boolean isUnlocked(Config.Round round) {
        if (round.unlockType == Config.UnlockType.ALWAYS) {
            return true;
        }
        if (round.unlockType == Config.UnlockType.CLEARED) {
            return isCleared(round.unlockRoundId);
        }
        if (round.unlockType == Config.UnlockType.PREVIOUS_STARS) {
            return starsBefore(round.id) >= round.starMinimum;
        }
        return false;
    }

    public String unlockMessage(Config.Round round) {
        if (round.unlockType == Config.UnlockType.CLEARED) {
            Config.Round needed = Config.round(round.unlockRoundId);
            String name = needed == null ? "the earlier round" : needed.name;
            return "This one is locked. Clear Round " + round.unlockRoundId + ", " + name + ", to unlock this.";
        }
        if (round.unlockType == Config.UnlockType.PREVIOUS_STARS) {
            String word = round.starMinimum == 1 ? "star" : "stars";
            return "This one is locked. Earn at least " + round.starMinimum + " " + word
                    + " from earlier rounds to unlock this.";
        }
        return "This round is unlocked.";
    }

    private int starsBefore(int roundId) {
        int total = 0;
        for (Config.Round round : Config.ROUNDS) {
            if (round.id < roundId && starEarned[round.id]) {
                total += 1;
            }
        }
        return total;
    }

    private boolean known(int roundId) {
        return roundId > 0 && roundId < cleared.length;
    }

    private boolean read(String raw) {
        String[] parts = raw.split(";");
        if (parts.length != Config.ROUNDS.size()) {
            return false;
        }
        boolean[] seen = new boolean[cleared.length];
        for (String part : parts) {
            String[] fields = part.split(",");
            if (fields.length != 3) {
                return false;
            }
            int id;
            try {
                id = Integer.parseInt(fields[0]);
            } catch (NumberFormatException error) {
                return false;
            }
            if (!known(id) || seen[id] || Config.round(id) == null) {
                return false;
            }
            if (!isBooleanWord(fields[1]) || !isBooleanWord(fields[2])) {
                return false;
            }
            cleared[id] = Boolean.parseBoolean(fields[1]);
            starEarned[id] = Boolean.parseBoolean(fields[2]);
            seen[id] = true;
        }
        for (Config.Round round : Config.ROUNDS) {
            if (!seen[round.id]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBooleanWord(String word) {
        return "true".equals(word) || "false".equals(word);
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        for (Config.Round round : Config.ROUNDS) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(round.id).append(',').append(cleared[round.id]).append(',').append(starEarned[round.id]);
        }
        try {
            Preferences prefs = Preferences.userNodeForPackage(Progress.class);
            prefs.put(KEY, text.toString());
            prefs.flush();
        } catch (Exception error) {
            // Storage can be blocked. Keep playing anyway.
        }
    }
}
