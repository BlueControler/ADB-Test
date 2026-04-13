package io.njdldkl.android.adbtest.agent;

import android.os.Bundle;

interface IRemoteShellService {
    Bundle execute(String command);
    void destroy();
}
