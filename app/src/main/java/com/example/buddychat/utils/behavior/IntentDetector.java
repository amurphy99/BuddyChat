package com.example.buddychat.utils.behavior;

import java.util.regex.Pattern;

import android.util.Log;

import com.example.buddychat.utils.motors.HeadMotors;

// =======================================================================
// Checks the LLMs utterance for "intent"
// =======================================================================
// This is for the LLMs intent, while "UserIntent" is for detecting the user's intentions (ending the chat, etc.).
// Allows us to trigger behaviors in Buddy depending on the results
// The two modes ("MOVE_HEAD" | "POS_NRG") either move Buddy's head or set valence/arousal values to their face.
// Gets called in: network.ws.ChatUiCallbacks
public final class IntentDetector {
    private static final String TAG  = "[DPU_IntentDetector]";
    private static final String MODE = "MOVE_HEAD";  // "MOVE_HEAD" | "POS_NRG"
    private IntentDetector() {} // no instances

    // Types of results possible
    public enum Intent { AFFIRM, NEGATE, REASSURE, APOLOGY, UNKNOWN }

    // Define "intent" phrases (use \s+ inside multi-word phrases)
    private static final String AFFIRM_SRC = "yes|y(?:ep|ea?h)|sure|of\\s+course|absolutely|affirmative|correct|indeed|right|certainly";
    private static final String NEGATE_SRC = "no|nope|nah|negative|never|incorrect|wrong";

    // Reassurance / positive acknowledgements
    private static final String REASSURE_SRC =
            "(?:no\\s+(?:worries|problem(?:s)?|big\\s+deal|sweat|trouble)\\b"
                    + "|all\\s+good\\b"
                    + "|it'?s\\s+(?:ok(?:ay)?|fine|alright)\\b"
                    + "|that'?s\\s+(?:ok(?:ay)?|fine|alright)\\b"
                    + "|you'?re\\s+fine\\b"
                    + "|you(?:\\s*’|\\s*')?re\\s+welcome\\b|you\\s+are\\s+welcome\\b"
                    + "|thank\\s+you\\b|thanks\\b|cheers\\b|great\\b|awesome\\b)";

    // Apology / limitation / decline ("sorry...", "I can't...", etc.)
    private static final String APOLOGY_SRC =
            "(?:sorry\\b|my\\s+bad\\b|i\\s+apologize\\b|apologies\\b|pardon\\b"
                    + "|i\\s+(?:can(?:not|\\s*not|\\'?t)|won'?t|shouldn'?t)\\b"
                    + "|i(?:\\s*’|\\s*')?m\\s+(?:unable|not\\s+able)\\b"
                    + "|won'?t\\s+be\\s+able\\s+to\\b"
                    + "|can(?:not|\\'?t)\\b)";

    // Allow leading whitespace/punctuation, then phrase, then optional punctuation/space
    private static final String LEAD = "^\\s*[\\p{Punct}\\s]*";
    private static final String TAIL = "[\\p{Punct}\\s]*$?";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    // Newer two cases
    private static final Pattern REASSURE = Pattern.compile(LEAD + REASSURE_SRC + TAIL, FLAGS);
    private static final Pattern APOLOGY  = Pattern.compile(LEAD +  APOLOGY_SRC + TAIL, FLAGS);

    // Allow leading spaces, then a match, then any punctuation/spaces
    private static final Pattern AFFIRM = Pattern.compile("^\\s*" + AFFIRM_SRC + "\\b[\\p{Punct}\\s]*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NEGATE = Pattern.compile("^\\s*" + NEGATE_SRC + "\\b[\\p{Punct}\\s]*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Check an utterances intent (order matters, reassurance beats negation)
    public static Intent classify(String s) {
        if (s == null) return Intent.UNKNOWN;
        if (REASSURE.matcher(s).find()) return Intent.REASSURE;
        if (APOLOGY .matcher(s).find()) return Intent.APOLOGY;
        if (AFFIRM  .matcher(s).find()) return Intent.AFFIRM;
        if (NEGATE  .matcher(s).find()) return Intent.NEGATE;
        return Intent.UNKNOWN;
    }

    // -----------------------------------------------------------------------
    // Specific Yes/No Helpers (so callers don't need the Intent enum)
    // -----------------------------------------------------------------------
    public static boolean isYes(String s) {
        if (s == null) { return false;                      }
        else           { return (AFFIRM.matcher(s).find()); }
    }
    public static boolean isNo(String s) {
        if (s == null) { return false;                      }
        else           { return (NEGATE.matcher(s).find()); }
    }

    // =======================================================================
    // Buddy-specific behavior controls
    // =======================================================================
    /** Buddy-specific behavior controls.
     * <br>
     * Two modes: <ol>
     *     <li> Tell the robot to nod its head yes or no. </li>
     *     <li> Change the valence and arousal (assuming the face is set to "NEUTRAL"). </li>
     * </ol>
     * We will start off with the second mode until the nodding is functional.
     */
    public static void IntentDetection(String s) {
        final Intent intent = classify(s);
        Log.d(TAG, String.format("%s Detected intent: %s (%s response mode)", TAG, intent, MODE));
        intentMode1(intent);
    }

    // Mode #1: Tell Buddy to shake their head 'no' or nod their head 'yes'
    private static void intentMode1(Intent intent) {
        if      (intent == Intent.AFFIRM  ) { Emotions.setMood("HAPPY", 3_000L); HeadMotors.nodYes();  }
        else if (intent == Intent.NEGATE  ) { HeadMotors.shakeNo(); }
        else if (intent == Intent.REASSURE) { Emotions.setMood("HAPPY", 3_000L); }
        else if (intent == Intent.APOLOGY ) { Emotions.setMood("SAD",   3_000L); HeadMotors.shakeNo(); }
    }

    // Mode #2: Change the valence (positivity) & arousal (energy)
    private static void intentMode2(Intent intent) {
        if      (intent == Intent.AFFIRM) { Emotions.setPositivityEnergy(0.9F, 0.9F); }
        else if (intent == Intent.NEGATE) { Emotions.setPositivityEnergy(0.1F, 0.1F); }
        else                              { Emotions.setPositivityEnergy(0.5F, 0.5F); }
    }

}
