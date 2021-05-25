package fi.ylihallila.server.scripts;

import fi.ylihallila.server.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoveOrphanSlidesScript extends Script {

    private final long TWO_WEEKS = TimeUnit.DAYS.toMillis(14);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override String getName() {
        return "Remove slides whose upload has failed daily";
    }

    @Override long getInterval() {
        return TimeUnit.DAYS.toSeconds(1);
    }

    @Override public void run() {
        long NOW = System.currentTimeMillis();

        Path slidesDirectory = Path.of(Constants.SLIDES_DIRECTORY);

        try {
            for (File file : Files.list(slidesDirectory).map(Path::toFile).collect(Collectors.toList())) {
                if (file.isDirectory()) {
                    continue;
                }

                if (NOW > file.lastModified() + TWO_WEEKS && file.getName().endsWith(".pending")) {
//                    Files.delete(file.toPath());
                    logger.info("Deleted {} because its upload had failed.", file.getName());
                }
            }
        } catch (IOException e) {
            logger.error("Error while deleting old slides", e);
        }
    }
}
