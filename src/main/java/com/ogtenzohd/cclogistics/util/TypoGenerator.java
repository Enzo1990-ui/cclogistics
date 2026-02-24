package com.ogtenzohd.cclogistics.util;

import net.minecraft.util.RandomSource;

public class TypoGenerator {

    private static final String[][] PHONETIC_SWAPS = {
        {"c", "k"}, {"k", "c"}, {"s", "z"}, {"z", "s"}, {"ph", "f"}, {"f", "ph"},
        {"tion", "shun"}, {"sion", "shun"}, {"ght", "te"}, {"ea", "ee"}, {"ee", "ea"},
        {"oo", "u"}, {"u", "oo"}, {"ou", "ow"}, {"ow", "ou"}, {"ie", "ei"}, {"ei", "ie"}
    };

    /**
     * Attempts to misspell the item name based on the citizen's intelligence.
     */
    public static String generateSpellingMistake(String originalText, int intelligence, int baseChance, RandomSource rand) {
        if (originalText == null || originalText.length() <= 3) return originalText;

        // Calculate chance PER LETTER. 
        int mistakeChancePerLetter = Math.max(1, baseChance - (intelligence * 3));
        int mistakesToMake = 0;
        
        // 1. FLIP THE COIN FOR EVERY LETTER IN THE WORD
        for (int i = 0; i < originalText.length(); i++) {
            if (rand.nextInt(100) < mistakeChancePerLetter) {
                mistakesToMake++; 
            }
        }

        if (mistakesToMake == 0) return originalText;

        StringBuilder sb = new StringBuilder(originalText);

        // 2. APPLY A MISTAKE FOR EVERY "HEADS" ROLLED
        for (int i = 0; i < mistakesToMake; i++) {
            if (sb.length() <= 3) break; 

            int severityType;
            if (intelligence < 5) severityType = rand.nextInt(4);
            else if (intelligence < 12) severityType = 1 + rand.nextInt(3);
            else severityType = 3;

            switch (severityType) {
                case 0: // PHONETIC (Sound it out)
                    String[] swap = PHONETIC_SWAPS[rand.nextInt(PHONETIC_SWAPS.length)];
                    int index = sb.toString().toLowerCase().indexOf(swap[0]);
                    if (index > 0) { 
                        sb.replace(index, index + swap[0].length(), swap[1]);
                    } else {
                        applyMechanicalTypo(sb, rand); // Fallback
                    }
                    break;

                case 1: // VOWEL CONFUSION
                    int vIndex = 1 + rand.nextInt(sb.length() - 2);
                    boolean foundVowel = false;
                    for(int v = vIndex; v < sb.length() - 1; v++) {
                        if ("aeiou".indexOf(Character.toLowerCase(sb.charAt(v))) != -1) {
                            sb.setCharAt(v, "aeiou".charAt(rand.nextInt(5)));
                            foundVowel = true;
                            break;
                        }
                    }
                    if (!foundVowel) applyMechanicalTypo(sb, rand);
                    break;

                case 2: // CONSONANT MISTAKE (Double or drop)
                    int cIndex = 1 + rand.nextInt(sb.length() - 2);
                    char c = sb.charAt(cIndex);
                    if ("aeiou ".indexOf(Character.toLowerCase(c)) == -1) {
                        if (c == sb.charAt(cIndex + 1)) sb.deleteCharAt(cIndex); 
                        else sb.insert(cIndex, c); 
                    } else {
                        applyMechanicalTypo(sb, rand);
                    }
                    break;

                case 3: // MECHANICAL TYPO (Fat fingers)
                    applyMechanicalTypo(sb, rand);
                    break;
            }
        }

        return sb.toString();
    }

    private static void applyMechanicalTypo(StringBuilder sb, RandomSource rand) {
        if (sb.length() <= 3) return;
        int idx = 1 + rand.nextInt(sb.length() - 2);
        if (rand.nextBoolean()) { 
            char t = sb.charAt(idx);
            sb.setCharAt(idx, sb.charAt(idx + 1));
            sb.setCharAt(idx + 1, t);
        } else { 
            sb.deleteCharAt(idx);
        }
    }
}