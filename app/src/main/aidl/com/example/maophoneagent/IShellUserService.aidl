package com.example.maophoneagent;

interface IShellUserService {

    // Execute a safe diagnostic command.
    String runId() = 1;

    // Execute Tap on a specific display
    String tap(int displayId, int x, int y) = 2;

    // Reserved Shizuku service cleanup method.
    void destroy() = 16777114;
}
