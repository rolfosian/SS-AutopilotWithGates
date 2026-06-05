package data.scripts.autopilotwithgates.util;

import com.fs.starfarer.api.EveryFrameScript;

public abstract class BaseEveryFrameScript implements EveryFrameScript {
    protected boolean isDone = false;
    protected boolean runWhilePaused;

    public BaseEveryFrameScript(boolean runWhilePaused) {
        this.runWhilePaused = runWhilePaused;
    }

    @Override
    public abstract void advance(float arg0);

    @Override
    public boolean isDone() {
        return this.isDone;
    }

    @Override
    public boolean runWhilePaused() {
        return this.runWhilePaused;
    }
}
