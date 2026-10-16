-- Additive status history migration; run with schema-owner credentials before release.
USE `yunanexus_notice`;

CREATE TABLE IF NOT EXISTS `notice_service_status_bucket` (
    `service_name` VARCHAR(32) NOT NULL,
    `bucket_start` BIGINT NOT NULL,
    `score_sum` DOUBLE NOT NULL,
    `sample_count` BIGINT NOT NULL,
    PRIMARY KEY (`service_name`, `bucket_start`)
) ENGINE=InnoDB;
