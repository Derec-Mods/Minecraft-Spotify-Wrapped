package io.github.derexxd.minecraftWrapped;

public class UploadResult {

    boolean success;
    int httpStatus;
    String snapshotId;
    String slug;
    boolean duplicate;
    String error;

    UploadResult(boolean success, int httpStatus, String snapshotId, String slug, boolean duplicate, String error) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.snapshotId = snapshotId;
        this.slug = slug;
        this.duplicate = duplicate;
        this.error = error;
    }
}

