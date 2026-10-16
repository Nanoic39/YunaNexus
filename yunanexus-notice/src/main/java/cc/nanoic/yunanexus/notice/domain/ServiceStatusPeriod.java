package cc.nanoic.yunanexus.notice.domain;

/** A UTC time bucket. Null availability means no observation. */
public record ServiceStatusPeriod(
        String startAt, String endAt, String status, Double availability, long samples) {}
