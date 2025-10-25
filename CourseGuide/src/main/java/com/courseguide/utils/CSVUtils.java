package com.courseguide.utils;

public class CSVUtils {
    // Minimal CSV splitter: splits by comma, no quotes/escapes handling to keep it simple
    public static String[] split(String line) {
        return line.split(",");
    }
}
