package com.ogtenzohd.cclogistics.util;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypoGenerator {

    private static final Random RANDOM = new Random();
    private static final char[] VOWELS = {'a', 'e', 'i', 'o', 'u'};

    // Phonetic replacements for when they literally don't know how to spell it
    private static final String[][] PHONETIC_SWAPS = {
        {"c", "k"}, {"k", "c"},
        {"s", "z"}, {"z", "s"},
        {"ph", "f"}, {"f", "ph"},
        {"tion", "shun"}, {"sion", "shun"},
        {"ght", "te"}, // Light -> Lite
        {"ea", "ee"}, {"ee", "ea"},
        {"oo", "u"}, {"u", "oo"},
        {"ou", "ow"}, {"ow", "ou"},
        {"ie", "ei"}, {"ei", "ie"},
        {"y", "i"}, {"i", "y"}
    };

    /**
     * Attempts to misspell the item name based on the citizen's intelligence.
     * @param originalText The real item name (e.g., "Create: Brass Casing")
     * @param intelligence Level of the citizen (1-20+). Higher means fewer/minor mistakes.
     * @return The potentially misspelled string.
     */
    public static String generateSpellingMistake(String originalText, int intelligence) {
        // Base chance to make any mistake at all. 
        // Lvl 1 = 40% chance to mess up. Lvl 20 = 0% chance.
        int mistakeChance = Math.max(0, 40 - (intelligence * 2)); 
        
        if (mistakeChance == 0 || RANDOM.nextInt(100) >= mistakeChance) {
            return originalText;
        }

        if (originalText == null || originalText.length() <= 3) {
            return originalText; // Too short to safely mangle
        }

        StringBuilder sb = new StringBuilder(originalText);
        String lowerText = originalText.toLowerCase();

        // Determine WHAT kind of mistake they make based on their intelligence
        // Lower intelligence allows for more severe categories of mistakes
        int mistakeCategory;
        if (intelligence < 5) {
            mistakeCategory = RANDOM.nextInt(6); // Can make ANY mistake, including severe ones
        } else if (intelligence < 10) {
            mistakeCategory = RANDOM.nextInt(4); // Vowels, doubles, mechanical
        } else {
            mistakeCategory = 3; // Only makes mechanical "fat-finger" typos
        }

        switch (mistakeCategory) {
            case 0: // PHONETIC MISSPELLING (They sound it out)
                applyPhoneticMistake(sb, lowerText);
                break;

            case 1: // CONSONANT DOUBLING (They don't know when to double a letter)
                applyConsonantMistake(sb);
                break;

            case 2: // VOWEL BLINDNESS (Guessing the vowel)
                applyVowelMistake(sb);
                break;

            case 3: // MECHANICAL TYPO (Standard fat fingers: swap, drop, double)
                applyMechanicalTypo(sb);
                break;

            case 4: // RANDOM CAPITALIZATION (e.g., "iRon inGot")
                applyCaseMistake(sb);
                break;

            case 5: // APATHY (Word is too long, they give up and drop the last letters)
                if (sb.length() > 8) {
                    sb.delete(sb.length() - (1 + RANDOM.nextInt(3)), sb.length());
                } else {
                    applyPhoneticMistake(sb, lowerText); // Fallback
                }
                break;
        }

        return sb.toString();
    }

    private static void applyPhoneticMistake(StringBuilder sb, String lowerText) {
        // Try up to 3 times to find a phonetic match in the word
        for (int i = 0; i < 3; i++) {
            String[] swap = PHONETIC_SWAPS[RANDOM.nextInt(PHONETIC_SWAPS.length)];
            String target = swap[0];
            String replacement = swap[1];

            int index = lowerText.indexOf(target);
            if (index != -1 && index > 0) { // Keep the very first letter intact for readability
                sb.replace(index, index + target.length(), replacement);
                return;
            }
        }
        applyMechanicalTypo(sb); // Fallback if no phonetics match
    }

    private static void applyConsonantMistake(StringBuilder sb) {
        char[] chars = sb.toString().toCharArray();
        for (int i = 1; i < chars.length - 1; i++) {
            if (!isVowel(chars[i]) && chars[i] != ' ') {
                if (chars[i] == chars[i+1]) {
                    // It's a double consonant, make it single (Cobblestone -> Coblestone)
                    sb.deleteCharAt(i);
                    return;
                } else if (RANDOM.nextBoolean()) {
                    // It's a single, make it double (Iron -> Irron)
                    sb.insert(i, chars[i]);
                    return;
                }
            }
        }
        applyMechanicalTypo(sb); // Fallback
    }

    private static void applyVowelMistake(StringBuilder sb) {
        char[] chars = sb.toString().toCharArray();
        int targetIndex = 1 + RANDOM.nextInt(chars.length - 2);
        
        // Scan forward to find a vowel
        for (int i = targetIndex; i < chars.length; i++) {
            if (isVowel(chars[i])) {
                sb.setCharAt(i, getRandomVowel());
                return;
            }
        }
        applyMechanicalTypo(sb); // Fallback
    }

    private static void applyMechanicalTypo(StringBuilder sb) {
        int targetIndex = 1 + RANDOM.nextInt(sb.length() - 2);
        if (sb.charAt(targetIndex) == ' ') targetIndex++; // Avoid spaces
        if (targetIndex >= sb.length() - 1) return;

        int type = RANDOM.nextInt(3);
        if (type == 0) { // Swap
            char temp = sb.charAt(targetIndex);
            sb.setCharAt(targetIndex, sb.charAt(targetIndex + 1));
            sb.setCharAt(targetIndex + 1, temp);
        } else if (type == 1) { // Drop
            sb.deleteCharAt(targetIndex);
        } else { // Duplicate
            sb.insert(targetIndex, sb.charAt(targetIndex));
        }
    }

    private static void applyCaseMistake(StringBuilder sb) {
        int targetIndex = 1 + RANDOM.nextInt(sb.length() - 1);
        char c = sb.charAt(targetIndex);
        if (Character.isLetter(c)) {
            if (Character.isLowerCase(c)) {
                sb.setCharAt(targetIndex, Character.toUpperCase(c));
            } else {
                sb.setCharAt(targetIndex, Character.toLowerCase(c));
            }
        }
    }

    private static boolean isVowel(char c) {
        char lower = Character.toLowerCase(c);
        for (char v : VOWELS) {
            if (lower == v) return true;
        }
        return false;
    }

    private static char getRandomVowel() {
        return VOWELS[RANDOM.nextInt(VOWELS.length)];
    }
}