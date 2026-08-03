package dev.z1ppzy.guiok;

public record ReloadResult(boolean successful, String message) {
    public static ReloadResult success() {
        return new ReloadResult(true, "ok");
    }

    public static ReloadResult failure(String message) {
        return new ReloadResult(false, message);
    }
}
