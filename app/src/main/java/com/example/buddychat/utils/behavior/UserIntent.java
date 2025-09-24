package com.example.buddychat.utils.behavior;

import android.util.Log;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

// =======================================================================
// Detect Intention in a User's Utterance
// =======================================================================
/** Detects user intents to end/stop a conversation in freeform speech.
 * <br> - Precompiled, case-insensitive, Unicode-aware
 * <br> - Handles punctuation, fillers ("please", "now"), and common variants
 * <br> - Simple negation guard ("don't end chat", "do not stop talking")
 * <br> ToDo: Also has the potential of checking for other things: "Buddy do a spin"
 */
public final class UserIntent {
    private static final String TAG = "[DPU_UserIntent]";
    private UserIntent() {} // no instances

    public enum Intent { END_CHAT, NOT_END_CHAT, UNKNOWN }

    // -----------------------------------------------------------------------
    // Core Phrase Sets
    // -----------------------------------------------------------------------
    // Group #1: direct commands
    private static final String DIRECT =
            "(?:end|stop|close|finish|terminate|cancel)\\s+(?:the\\s+)?(?:chat|conversation|talk|session|dialogue?)";

    // Group #2: colloquial short forms
    private static final String COLLOQ =
            "(?:we(?:'re| are)\\s+done|that(?:'s| is)\\s+all|that(?:'s| is)\\s+it|all\\s+done|enough\\s+(?:for\\s+)?now)";

    // Group #3: farewells as end-signal (optional; can be noisy)
    private static final String FAREWELL =
            "(?:good\\s*bye|goodbye|bye\\b|see\\s+you(?:\\s+later)?|we(?:'ll| will)\\s+talk\\s+later)";

    // Optional politeness/fillers between words (please/now/etc.)
    private static final String FILL = "(?:\\s*(?:please|now|thanks|thank\\s+you)\\s*)*";

    // Allow leading/trailing punctuation/space, anchor on word boundaries
    private static final String CORE =
                    "^?[\\p{Z}\\p{P}]*" +                                          // Tolerant leading noise
                    "(?:" + DIRECT + "|" + COLLOQ + "|" + FAREWELL + ")" + FILL +  // Phrases
                    "[\\p{P}\\p{Z}]*$?";                                           // Tolerant trailing noise

    private static final Pattern CORE_PAT = Pattern.compile(CORE, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // -----------------------------------------------------------------------
    // Guard for Negation
    // -----------------------------------------------------------------------
    // If a negation appears within ~5 words before the trigger, we treat it as NOT_END_CHAT.
    // Examples: "don't end the chat", "please do not stop talking", "no, don't finish the conversation"
    private static final Pattern NEGATION_WINDOW = Pattern.compile(
            "(?:don'?t|do\\s+not|please\\s+don'?t|no)\\b.{0,40}\\b(?:end|stop|close|finish|terminate|cancel)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    // =======================================================================
    // Public Access
    // =======================================================================
    /** Classify a user utterance. Returns END_CHAT, NOT_END_CHAT, or UNKNOWN. */
    public static Intent classify(String utterance) {
        if (utterance == null || utterance.trim().isEmpty()) return Intent.UNKNOWN;

        // Quick negation check first
        if (NEGATION_WINDOW.matcher(utterance).find()) return Intent.NOT_END_CHAT;

        // Core match (anywhere in the utterance is fine; not just start)
        if (CORE_PAT.matcher(utterance).find()) {
            Log.w(TAG, String.format("%s End_Chat intent detected in user utterance: %s", TAG, utterance));
            return Intent.END_CHAT;
        }

        return Intent.UNKNOWN;
    }

    /** Convenience boolean. */
    public static boolean isEndChat(String utterance) { return classify(utterance) == Intent.END_CHAT; }

}
