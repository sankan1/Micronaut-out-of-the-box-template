package com.example.changelog;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liquibase {@code includeAll} resourceComparator: sorts paths by embedded numbers
 * numerically (so "7-..." sorts before "10-...") instead of the default lexical string
 * sort, which would otherwise put "10-..."/"11-..." before "7-...","8-...","9-...".
 */
public class NumericFilenameComparator implements Comparator<String> {

    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    @Override
    public int compare(String left, String right) {
        Matcher leftMatcher = CHUNK.matcher(left);
        Matcher rightMatcher = CHUNK.matcher(right);

        while (leftMatcher.find() && rightMatcher.find()) {
            String leftChunk = leftMatcher.group();
            String rightChunk = rightMatcher.group();

            int result = isDigits(leftChunk) && isDigits(rightChunk)
                ? Long.compare(Long.parseLong(leftChunk), Long.parseLong(rightChunk))
                : leftChunk.compareTo(rightChunk);

            if (result != 0) {
                return result;
            }
        }
        return left.length() - right.length();
    }

    private static boolean isDigits(String chunk) {
        return Character.isDigit(chunk.charAt(0));
    }
}
