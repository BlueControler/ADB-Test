package io.njdldkl.android.adbtest.adb;

import android.os.Bundle;

interface IRemoteShellService {
    Bundle execute(String command);
    void destroy();
}
