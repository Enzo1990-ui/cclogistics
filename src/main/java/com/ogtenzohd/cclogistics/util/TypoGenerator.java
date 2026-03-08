package com.ogtenzohd.cclogistics.util;

import net.minecraft.util.RandomSource;

public class TypoGenerator {

    private static final String[][] PHONETIC_MISTAKES = { // https://phonetic-spelling.com/ was a godsend
        {"c", "k"}, {"k", "c"}, {"s", "z"}, {"z", "s"}, {"ph", "f"}, {"f", "ph"},
        {"tion", "shun"}, {"sion", "shun"}, {"ght", "te"}, {"ea", "ee"}, {"ee", "ea"},
        {"oo", "u"}, {"u", "oo"}, {"ou", "ow"}, {"ow", "ou"}, {"ie", "ei"}, {"ei", "ie"}
    };

    public static String generateSpellingMistake(String originalText, int intelligence, int baseChance, RandomSource rand) {
        if (originalText == null || originalText.length() <= 3) return originalText;

        // CHANCE PER LETTER. - maybe a bit extreme but its more fun! 
        int mistakeChancePerLetter = Math.max(1, baseChance - (intelligence * 3));
        int mistakesToMake = 0;
        
        for (int i = 0; i < originalText.length(); i++) {
            if (rand.nextInt(100) < mistakeChancePerLetter) {
                mistakesToMake++; 
            }
        }

        if (mistakesToMake == 0) return originalText;

        StringBuilder sb = new StringBuilder(originalText);

        for (int i = 0; i < mistakesToMake; i++) {
            if (sb.length() <= 3) break; 

            int howBad;
            if (intelligence < 5) howBad = rand.nextInt(4);
            else if (intelligence < 12) howBad = 1 + rand.nextInt(3);
            else howBad = 3;

            switch (howBad) {
                case 0:
                    String[] swap = PHONETIC_MISTAKES[rand.nextInt(PHONETIC_MISTAKES.length)];
                    int index = sb.toString().toLowerCase().indexOf(swap[0]);
                    if (index > 0) { 
                        sb.replace(index, index + swap[0].length(), swap[1]);
                    } else {
                        applyTypo(sb, rand);
                    }
                    break;

                case 1:
                    int vIndex = 1 + rand.nextInt(sb.length() - 2);
                    boolean vowelChange = false;
                    for(int v = vIndex; v < sb.length() - 1; v++) {
                        if ("aeiou".indexOf(Character.toLowerCase(sb.charAt(v))) != -1) {
                            sb.setCharAt(v, "aeiou".charAt(rand.nextInt(5)));
                            vowelChange = true;
                            break;
                        }
                    }
                    if (!vowelChange) applyTypo(sb, rand);
                    break;

                case 2:
                    int cIndex = 1 + rand.nextInt(sb.length() - 2);
                    char c = sb.charAt(cIndex);
                    if ("aeiou ".indexOf(Character.toLowerCase(c)) == -1) {
                        if (c == sb.charAt(cIndex + 1)) sb.deleteCharAt(cIndex); 
                        else sb.insert(cIndex, c); 
                    } else {
                        applyTypo(sb, rand);
                    }
                    break;

                case 3:
                    applyTypo(sb, rand);
                    break;
            }
        }

        return sb.toString();
    }

    private static void applyTypo(StringBuilder sb, RandomSource rand) {
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