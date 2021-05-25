package fi.ylihallila.server.scripts;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * Manages the scripts related to QuPath Edu Server.
 *
 * Scripts are added to the executor on startup and only on error are they cancelled.
 */
public class ScriptManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    /**
     * All running scripts. A script can be cancelled using {@link ScheduledFuture#cancel(boolean)}.
     */
    private final List<ScheduledFuture<?>> scheduledScripts = Lists.newArrayList();

    public ScriptManager(Script... scripts) {
        for (Script script : scripts) {
            scheduleScript(script);
        }
    }

    /**
     * Schedules a new script.
     *
     * The script will run for the first time after {@link Script#getInterval()} has passed.
     */
    public ScheduledFuture<?> scheduleScript(Script script) {
        var scheduledFuture = executor.scheduleAtFixedRate(script, 0, script.getInterval(), TimeUnit.SECONDS);
        scheduledScripts.add(scheduledFuture);

        logger.info("Scheduled a new script: {}", script.getName());

        return scheduledFuture;
    }

    public List<ScheduledFuture<?>> getScheduledScripts() {
        return scheduledScripts;
    }

    public ScheduledExecutorService getExecutorService() {
        return executor;
    }
}
