package com.ogtenzohd.cclogistics.util;

import java.util.Calendar;

public class HolidayManager {

    public static boolean IS_APRIL_FOOLS = false;
    public static boolean IS_CHRISTMAS = false;
    public static boolean IS_HALLOWEEN = false;

    public static void refreshHolidays() {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        IS_APRIL_FOOLS = (month == Calendar.APRIL && day == 1);
        IS_CHRISTMAS = (month == Calendar.DECEMBER && (day >= 24 && day <= 26));
        IS_HALLOWEEN = (month == Calendar.OCTOBER && (day >= 30)) || (month == Calendar.NOVEMBER && day == 1);
    }
}