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

    // Reserved Shizuku service cleanup method.
    void destroy() = 16777114;
}
