package com.example.maophoneagent;

interface IShellUserService {

    // Execute a safe diagnostic command.
    String runId() = 1;

    // Execute Tap on a specific non-default display
    String tap(int displayId, int x, int y) = 2;

    // Execute Swipe on a specific non-default display
    String swipe(
        int displayId,
        int x1,
        int y1,
        int x2,
        int y2,
        int durationMs
    ) = 3;

    // Execute Back on a specific non-default display.
    String back(int displayId) = 4;

    // Execute Home on a specific non-default display.
    String home(int displayId) = 5;

    // Launch App on a specific non-default display.
    String launchApp(int displayId, String packageName) = 10;

    // List installed package names, including packages without launcher activities.
    String listInstalledPackages() = 11;

    // Create and retain one experimental virtual display in the Shell process.
    String createVirtualDisplay(int width, int height, int densityDpi) = 12;

    // Query the display owned by this service; does not enumerate external displays.
    String getVirtualDisplayInfo() = 13;

    // Release the display and its rendering resources.
    String releaseVirtualDisplay() = 14;

    // Return a bounded in-memory JPEG preview, or an empty array before rendering starts.
    byte[] getVirtualDisplayPreview() = 15;

    // Reserved Shizuku service cleanup method.
    void destroy() = 16777114;
}
