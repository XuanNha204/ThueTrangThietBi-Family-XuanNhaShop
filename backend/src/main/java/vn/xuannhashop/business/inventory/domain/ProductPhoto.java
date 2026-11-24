package vn.xuannhashop.business.inventory.domain;

import java.util.UUID;

public record ProductPhoto(UUID id, boolean illustrative, String credit, String sourceUrl, String licenseUrl) {}
