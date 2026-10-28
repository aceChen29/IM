package com.ace.im.common.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class FormatDateUtil {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatDate(Date date) {
        return DATE_TIME_FORMATTER.format(date.toInstant().atZone(SHANGHAI_ZONE));
    }

    public static long parseDateTimeToMillis(String dateTimeStr) {
        LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        return localDateTime.atZone(SHANGHAI_ZONE).toInstant().toEpochMilli();
    }
}
