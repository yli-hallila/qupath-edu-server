package fi.ylihallila.server.scripts;

public abstract class Script implements Runnable {

    /**
     * Name of this script. Should describe what the script does and include its interval.
     */
    abstract String getName();

    /**
     * Interval between two executions in seconds.
     */
    abstract long getInterval();

}
